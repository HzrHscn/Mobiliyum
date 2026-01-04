package com.example.mobiliyum

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import java.util.Date
import java.util.concurrent.TimeUnit

object FavoritesManager {
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
    private val auth = FirebaseAuth.getInstance()
    // Memory cache
    private val localFavorites = mutableSetOf<Int>()
    private val followedStoreIds = HashSet<Int>()
    private val activeListeners = ArrayList<ListenerRegistration>()
    // Fiyat takip cache (Son bilinen fiyatlar - Firestore okuma azaltmak için)
    private val priceCache = HashMap<String, Double>()
    // Bildirim throttling (5 dakikada bir aynı üründen bildirim)
    private val lastNotificationTime = HashMap<String, Long>()
    private const val NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000L // 5 dakika
    // SharedPreferences cache
    private lateinit var prefs: SharedPreferences
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L // 30 dakika
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("FavoritesCache", Context.MODE_PRIVATE)
        loadFromLocalCache()
    }

    // === CACHE YÖNETİMİ ===

    private fun loadFromLocalCache() {
        // Local cache'den favori ID'leri yükle
        val cachedFavs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        localFavorites.clear()
        localFavorites.addAll(cachedFavs.mapNotNull { it.toIntOrNull() })

        val cachedStores = prefs.getStringSet("followed_stores", emptySet()) ?: emptySet()
        followedStoreIds.clear()
        followedStoreIds.addAll(cachedStores.mapNotNull { it.toIntOrNull() })
    }

    private fun saveToLocalCache() {
        prefs.edit().apply {
            putStringSet("favorites", localFavorites.map { it.toString() }.toSet())
            putStringSet("followed_stores", followedStoreIds.map { it.toString() }.toSet())
            putLong("last_sync", System.currentTimeMillis())
            apply()
        }
    }

    private fun isCacheValid(): Boolean {
        val lastSync = prefs.getLong("last_sync", 0)
        return (System.currentTimeMillis() - lastSync) < CACHE_EXPIRY_MS
    }

    // === FAVORI YÜKLEME (OPTİMİZE) ===

    fun loadUserFavorites(onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            android.util.Log.e("FavoritesManager", "❌ UID yok, giriş yapılmamış!")
            onComplete()
            return
        }

        android.util.Log.d("FavoritesManager", "📂 loadUserFavorites başladı - UID: $uid")

        // Cache geçerliyse Firebase'e gitme
        if (isCacheValid() && localFavorites.isNotEmpty()) {
            android.util.Log.d("FavoritesManager", "✅ Cache geçerli, Firebase'e gidilmedi")
            onComplete()
            return
        }

        android.util.Log.d("FavoritesManager", "🔄 Firebase'den favoriler yükleniyor...")

        // BATCH READ: Tek sorguda hem favoriler hem mağaza takipleri
        var completedTasks = 0
        val totalTasks = 2

        // 1. Favorileri yükle
        db.collection("users").document(uid).collection("favorites")
            .get()
            .addOnSuccessListener { docs ->
                android.util.Log.d("FavoritesManager", "📦 Favori dökümanları alındı: ${docs.size()} adet")

                localFavorites.clear()
                priceCache.clear()

                for (doc in docs) {
                    val pid = doc.getString("productId")?.toIntOrNull()
                    val lastPrice = doc.getDouble("lastNotifiedPrice")

                    if (pid != null) {
                        localFavorites.add(pid)
                        if (lastPrice != null) {
                            priceCache[pid.toString()] = lastPrice
                        }
                        android.util.Log.d("FavoritesManager", "  ➕ Favori eklendi: Ürün #$pid")
                    }
                }

                saveToLocalCache()
                android.util.Log.d("FavoritesManager", "✅ Favoriler yüklendi: ${localFavorites.size} ürün")

                completedTasks++
                if (completedTasks == totalTasks) onComplete()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FavoritesManager", "❌ Favori yükleme hatası: ${e.message}")
                completedTasks++
                if (completedTasks == totalTasks) onComplete()
            }

        // 2. Mağaza takiplerini yükle
        db.collection("users").document(uid).collection("followed_stores")
            .get()
            .addOnSuccessListener { docs ->
                android.util.Log.d("FavoritesManager", "📦 Takip edilen mağazalar alındı: ${docs.size()} adet")

                followedStoreIds.clear()
                for (doc in docs) {
                    doc.getLong("storeId")?.toInt()?.let {
                        followedStoreIds.add(it)
                        android.util.Log.d("FavoritesManager", "  ➕ Mağaza takip ediliyor: #$it")
                    }
                }

                saveToLocalCache()
                android.util.Log.d("FavoritesManager", "✅ Mağaza takipleri yüklendi: ${followedStoreIds.size} mağaza")

                completedTasks++
                if (completedTasks == totalTasks) onComplete()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FavoritesManager", "❌ Mağaza takip yükleme hatası: ${e.message}")
                completedTasks++
                if (completedTasks == totalTasks) onComplete()
            }
    }

    private fun fetchFavoritesFromServer(uid: String, onComplete: () -> Unit) {
        db.collection("users").document(uid).collection("favorites")
            .get()
            .addOnSuccessListener { docs ->
                processFavorites(docs.documents)
                onComplete()
            }
            .addOnFailureListener { onComplete() }
    }

    private fun fetchFollowedStoresFromServer(uid: String, onComplete: () -> Unit) {
        db.collection("users").document(uid).collection("followed_stores")
            .get()
            .addOnSuccessListener { docs ->
                processFollowedStores(docs.documents)
                onComplete()
            }
            .addOnFailureListener { onComplete() }
    }

    private fun processFavorites(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        localFavorites.clear()
        priceCache.clear()

        for (doc in docs) {
            val pid = doc.getString("productId")?.toIntOrNull()
            val lastPrice = doc.getDouble("lastNotifiedPrice")

            if (pid != null) {
                localFavorites.add(pid)
                if (lastPrice != null) {
                    priceCache[pid.toString()] = lastPrice
                }
            }
        }
        saveToLocalCache()
    }

    private fun processFollowedStores(docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        followedStoreIds.clear()
        for (doc in docs) {
            doc.getLong("storeId")?.toInt()?.let { followedStoreIds.add(it) }
        }
        saveToLocalCache()
    }

    // === FİYAT TAKİBİ (OPTİMİZE - EN ÖNEMLİ KISIM) ===

    fun startRealTimePriceAlerts(context: Context) {
        val uid = auth.currentUser?.uid ?: run {
            android.util.Log.e("FavoritesManager", "UID yok!")
            return
        }

        // Eski listener'ları temizle
        stopAllListeners()

        if (localFavorites.isEmpty()) {
            android.util.Log.d("FavoritesManager", "Favori yok, fiyat takibi başlatılmadı")
            return
        }

        val favoriteIdsList = localFavorites.toList()

        android.util.Log.d("FavoritesManager", "🔔 Fiyat takibi başlatılıyor: ${favoriteIdsList.size} ürün")

        // İlk açılışta mevcut fiyatları cache'e al (bildirim gönderme)
        var isInitialLoad = priceCache.isEmpty()

        favoriteIdsList.chunked(10).forEach { chunk ->
            val listener = db.collection("products")
                .whereIn("id", chunk)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        android.util.Log.e("FavoritesManager", "❌ Snapshot hatası: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshots == null) {
                        android.util.Log.e("FavoritesManager", "❌ Snapshot null!")
                        return@addSnapshotListener
                    }

                    android.util.Log.d("FavoritesManager", "📦 Snapshot alındı: ${snapshots.documents.size} ürün, ${snapshots.documentChanges.size} değişiklik")

                    // İLK YÜKLEME: Sadece cache'e kaydet
                    if (isInitialLoad) {
                        android.util.Log.d("FavoritesManager", "⏳ İlk yükleme - sadece cache'e kaydediliyor")
                        for (doc in snapshots.documents) {
                            val productId = doc.getLong("id")?.toString() ?: continue
                            val currentPriceStr = doc.getString("price") ?: continue
                            val currentPrice = PriceUtils.parsePrice(currentPriceStr)

                            priceCache[productId] = currentPrice
                            android.util.Log.d("FavoritesManager", "  💾 Cache: Ürün #$productId = $currentPrice")
                        }
                        isInitialLoad = false
                        return@addSnapshotListener
                    }

                    // SONRAKI GÜNCELLEMELER: Sadece değişenleri kontrol et
                    for (docChange in snapshots.documentChanges) {
                        val changeType = docChange.type
                        android.util.Log.d("FavoritesManager", "📝 Değişiklik tipi: $changeType")

                        if (changeType == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            val doc = docChange.document
                            val productId = doc.getLong("id")?.toString() ?: continue

                            android.util.Log.d("FavoritesManager", "🔄 Ürün #$productId değişti, kontrol ediliyor...")
                            checkPriceChange(context, uid, doc)
                        }
                    }
                }

            activeListeners.add(listener)
        }

        android.util.Log.d("FavoritesManager", "✅ Listener'lar eklendi: ${activeListeners.size} adet")
    }

    // checkPriceChange metodunu da düzelt:
    private fun checkPriceChange(
        context: Context,
        uid: String,
        doc: com.google.firebase.firestore.DocumentSnapshot
    ) {
        val productId = doc.getLong("id")?.toString() ?: run {
            android.util.Log.e("FavoritesManager", "❌ Ürün ID yok!")
            return
        }

        val productName = doc.getString("name") ?: "Ürün"
        val currentPriceStr = doc.getString("price") ?: run {
            android.util.Log.e("FavoritesManager", "❌ Fiyat string yok!")
            return
        }

        val currentPrice = PriceUtils.parsePrice(currentPriceStr)

        android.util.Log.d("FavoritesManager", "💰 Ürün #$productId ($productName): Güncel fiyat = $currentPrice")

        // Cache'deki son fiyatı kontrol et
        val lastKnownPrice = priceCache[productId]

        if (lastKnownPrice == null) {
            android.util.Log.d("FavoritesManager", "  ℹ️ İlk fiyat kaydı: $currentPrice")
            priceCache[productId] = currentPrice
            return
        }

        android.util.Log.d("FavoritesManager", "  📊 Son bilinen fiyat: $lastKnownPrice → Yeni fiyat: $currentPrice")

        // Fiyat düşmediyse işlem yapma
        if (currentPrice >= lastKnownPrice) {
            android.util.Log.d("FavoritesManager", "  ⬆️ Fiyat düşmedi (eşit veya arttı)")
            priceCache[productId] = currentPrice
            return
        }

        // FIYAT DÜŞTÜ!
        val priceDropPercent = ((lastKnownPrice - currentPrice) / lastKnownPrice * 100).toInt()
        android.util.Log.d("FavoritesManager", "  🎉 FİYAT DÜŞTÜ! %$priceDropPercent indirim!")

        // THROTTLING: Son 5 dakikada bildirim atıldıysa tekrar atma
        val now = System.currentTimeMillis()
        val lastNotifTime = lastNotificationTime[productId] ?: 0

        if (now - lastNotifTime < NOTIFICATION_COOLDOWN_MS) {
            android.util.Log.d("FavoritesManager", "  ⏸️ Throttling: Son bildirimden ${(now - lastNotifTime) / 1000}s geçti")
            priceCache[productId] = currentPrice
            return
        }

        // BİLDİRİM GÖNDER
        android.util.Log.d("FavoritesManager", "  🔔 BİLDİRİM GÖNDERİLİYOR!")

        NotificationHelper.sendNotification(
            context,
            "💰 %$priceDropPercent İndirim!",
            "$productName fiyatı düştü! ${PriceUtils.formatPriceStyled(currentPrice)}",
            "price_alert",
            productId
        )

        // Cache güncelle
        priceCache[productId] = currentPrice
        lastNotificationTime[productId] = now

        // Firebase'deki lastNotifiedPrice'ı güncelle
        savePriceAlertNotification(uid, productId, productName, lastKnownPrice, currentPrice)

        db.collection("users").document(uid)
            .collection("favorites").document(productId)
            .update("lastNotifiedPrice", currentPrice)
            .addOnSuccessListener {
                android.util.Log.d("FavoritesManager", "  ✅ Firebase güncellendi")
            }
            .addOnFailureListener {
                android.util.Log.e("FavoritesManager", "  ❌ Firebase güncelleme hatası: ${it.message}")
            }
    }

    private fun savePriceAlertNotification(
        uid: String,
        productId: String,
        productName: String,
        oldPrice: Double,
        newPrice: Double
    ) {
        // BATCH kullanarak birden fazla bildirimi tek seferde yaz
        val notifRef = db.collection("users").document(uid)
            .collection("notifications").document()

        val item = NotificationItem(
            id = notifRef.id,
            title = "🔥 Fiyat Düştü!",
            message = "$productName: ${PriceUtils.formatPriceStyled(oldPrice)} → ${PriceUtils.formatPriceStyled(newPrice)}",
            date = Date(),
            type = "price_alert",
            relatedId = productId,
            isRead = false
        )

        notifRef.set(item)
            .addOnFailureListener {
                // Hata logla ama UI'ı bloklamasın
                android.util.Log.e("FavoritesManager", "Notification save failed: ${it.message}")
            }
    }

    fun stopAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // === FAVORİ EKLEME/ÇIKARMA (OPTİMİZE) ===

    private const val MAX_FAVORITE_LIMIT = 50

    fun toggleFavorite(
        product: Product,
        context: Context? = null,
        onResult: (Boolean) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return
        val pidStr = product.id.toString()
        val ref = db.collection("users").document(uid)
            .collection("favorites").document(pidStr)

        if (localFavorites.contains(product.id)) {
            // FAVORİDEN ÇIKAR
            ref.delete().addOnSuccessListener {
                localFavorites.remove(product.id)
                priceCache.remove(pidStr)
                saveToLocalCache()
                onResult(false)
            }
            return
        }

        // === FAVORİ EKLEME ===
        if (localFavorites.size >= MAX_FAVORITE_LIMIT) {
            context?.let {
                Toast.makeText(
                    it,
                    "En fazla $MAX_FAVORITE_LIMIT favori ekleyebilirsiniz.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onResult(false)
            return
        }

        val priceVal = PriceUtils.parsePrice(product.price)

        val data = hashMapOf(
            "productId" to pidStr,
            "productName" to product.name,
            "lastNotifiedPrice" to priceVal,
            "priceAlert" to true,
            "addedAt" to Date()
        )

        ref.set(data).addOnSuccessListener {
            localFavorites.add(product.id)
            priceCache[pidStr] = priceVal
            saveToLocalCache()
            onResult(true)
        }
    }

    fun updatePriceAlert(productId: Int, isEnabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("favorites").document(productId.toString())
            .update("priceAlert", isEnabled)
    }

    // === MAĞAZA TAKİP ===

    fun followStore(storeId: Int, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("followed_stores")
            .document(storeId.toString())
            .set(mapOf("storeId" to storeId, "followedAt" to Date()))
            .addOnSuccessListener {
                followedStoreIds.add(storeId)
                saveToLocalCache()
                onComplete()
            }
    }

    fun unfollowStore(storeId: Int, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("followed_stores")
            .document(storeId.toString())
            .delete()
            .addOnSuccessListener {
                followedStoreIds.remove(storeId)
                saveToLocalCache()
                onComplete()
            }
    }

    // === GETTER ===

    fun isFavorite(productId: Int) = localFavorites.contains(productId)
    fun isFollowing(storeId: Int) = followedStoreIds.contains(storeId)
    fun getFavoriteCount() = localFavorites.size
}