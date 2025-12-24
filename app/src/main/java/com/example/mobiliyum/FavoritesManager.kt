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
            onComplete()
            return
        }

        // Cache geçerliyse Firebase'e gitme
        if (isCacheValid() && localFavorites.isNotEmpty()) {
            onComplete()
            return
        }

        // BATCH READ: Tek sorguda hem favoriler hem mağaza takipleri
        val batch = db.batch()
        var completedTasks = 0
        val totalTasks = 2

        // 1. Favorileri cache'den çek (offline persistence)
        db.collection("users").document(uid).collection("favorites")
            .get(Source.CACHE) // Önce cache'e bak
            .addOnSuccessListener { cachedDocs ->
                if (cachedDocs.isEmpty) {
                    // Cache boşsa server'dan çek
                    fetchFavoritesFromServer(uid) {
                        completedTasks++
                        if (completedTasks == totalTasks) onComplete()
                    }
                } else {
                    processFavorites(cachedDocs.documents)
                    completedTasks++
                    if (completedTasks == totalTasks) onComplete()
                }
            }
            .addOnFailureListener {
                // Cache hatası varsa server'dan çek
                fetchFavoritesFromServer(uid) {
                    completedTasks++
                    if (completedTasks == totalTasks) onComplete()
                }
            }

        // 2. Mağaza takiplerini yükle
        db.collection("users").document(uid).collection("followed_stores")
            .get(Source.CACHE)
            .addOnSuccessListener { cachedStores ->
                if (cachedStores.isEmpty) {
                    fetchFollowedStoresFromServer(uid) {
                        completedTasks++
                        if (completedTasks == totalTasks) onComplete()
                    }
                } else {
                    processFollowedStores(cachedStores.documents)
                    completedTasks++
                    if (completedTasks == totalTasks) onComplete()
                }
            }
            .addOnFailureListener {
                fetchFollowedStoresFromServer(uid) {
                    completedTasks++
                    if (completedTasks == totalTasks) onComplete()
                }
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
        val uid = auth.currentUser?.uid ?: return

        // Eski listener'ları temizle (MEMORY LEAK ÖNLENİYOR)
        stopAllListeners()

        if (localFavorites.isEmpty()) return

        // TOPLU SORGULAMA: Tek listener ile tüm favorileri dinle
        // ❌ YANLIŞ: Her ürün için ayrı listener (100 ürün = 100 read/sn)
        // ✅ DOĞRU: whereIn ile toplu dinleme (100 ürün = 1 read başlangıç + değişiklik olanlar)

        val favoriteIdsList = localFavorites.toList()

        // Firestore whereIn limiti 10, bu yüzden chunk'lara böl
        favoriteIdsList.chunked(10).forEach { chunk ->
            val listener = db.collection("products")
                .whereIn("id", chunk)
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener

                    // Sadece değişen dökümanları kontrol et
                    for (docChange in snapshots.documentChanges) {
                        if (docChange.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            val doc = docChange.document
                            checkPriceChange(context, uid, doc)
                        }
                    }
                }

            activeListeners.add(listener)
        }
    }

    private fun checkPriceChange(
        context: Context,
        uid: String,
        doc: com.google.firebase.firestore.DocumentSnapshot
    ) {
        val productId = doc.getLong("id")?.toString() ?: return
        val currentPriceStr = doc.getString("price") ?: return
        val currentPrice = PriceUtils.parsePrice(currentPriceStr)

        // Cache'deki son fiyatı kontrol et (Firestore read yok!)
        val lastKnownPrice = priceCache[productId] ?: currentPrice

        // Fiyat düşmediyse işlem yapma
        if (currentPrice >= lastKnownPrice) {
            priceCache[productId] = currentPrice
            return
        }

        // THROTTLING: Son 5 dakikada bildirim atıldıysa tekrar atma
        val now = System.currentTimeMillis()
        val lastNotifTime = lastNotificationTime[productId] ?: 0

        if (now - lastNotifTime < NOTIFICATION_COOLDOWN_MS) {
            // Fiyatı cache'e kaydet ama bildirim atma
            priceCache[productId] = currentPrice
            return
        }

        // === BİLDİRİM GÖNDER ===
        val productName = doc.getString("name") ?: "Ürün"
        val priceDropPercent = ((lastKnownPrice - currentPrice) / lastKnownPrice * 100).toInt()

        NotificationHelper.sendNotification(
            context,
            "💰 %$priceDropPercent İndirim!",
            "$productName fiyatı düştü! ${PriceUtils.formatPriceStyled(currentPrice)}"
        )

        // Bildirim kaydını Firestore'a kaydet (ASYNC - UI bloklama yok)
        savePriceAlertNotification(uid, productId, productName, lastKnownPrice, currentPrice)

        // Cache güncelle
        priceCache[productId] = currentPrice
        lastNotificationTime[productId] = now

        // Firebase'deki lastNotifiedPrice'ı güncelle (BATCH ile optimize edilebilir)
        db.collection("users").document(uid)
            .collection("favorites").document(productId)
            .update("lastNotifiedPrice", currentPrice)
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