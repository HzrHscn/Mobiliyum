package com.example.mobiliyum

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FavoritesManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val localFavorites = mutableSetOf<Int>()

    // Dinleyicileri hafızada tutalım ki gerekirse durdurabilelim
    private val activeListeners = ArrayList<ListenerRegistration>()

    fun loadUserFavorites(onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("favorites").get()
            .addOnSuccessListener { documents ->
                localFavorites.clear()
                for (doc in documents) {
                    doc.getString("productId")?.toIntOrNull()?.let { localFavorites.add(it) }
                }
                onComplete()
            }
    }

    fun isFavorite(productId: Int) = localFavorites.contains(productId)

    fun toggleFavorite(product: Product, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val productIdStr = product.id.toString()
        val productRef = db.collection("products").document(productIdStr)
        val userFavRef = db.collection("users").document(uid).collection("favorites").document(productIdStr)

        if (isFavorite(product.id)) {
            // SİLME
            db.runBatch { batch ->
                batch.delete(userFavRef)
                batch.update(productRef, "favoriteCount", FieldValue.increment(-1))
            }.addOnSuccessListener {
                localFavorites.remove(product.id)
                onResult(false)
            }
        } else {
            // EKLEME
            val currentPriceDouble = PriceUtils.parsePrice(product.price)
            val favData = hashMapOf(
                "productId" to productIdStr,
                "productName" to product.name,
                "savedPrice" to currentPriceDouble, // Referans Fiyat
                "lastNotifiedPrice" to currentPriceDouble, // Son bildirim atılan fiyat
                "priceAlert" to true,
                "addedAt" to java.util.Date()
            )
            db.runBatch { batch ->
                batch.set(userFavRef, favData)
                batch.update(productRef, "favoriteCount", FieldValue.increment(1))
            }.addOnSuccessListener {
                localFavorites.add(product.id)
                onResult(true)
            }
        }
    }

    fun updatePriceAlert(productId: Int, isEnabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("favorites")
            .document(productId.toString())
            .update("priceAlert", isEnabled)
    }

    // --- GERÇEK ZAMANLI FİYAT TAKİBİ (YENİ) ---
    fun startRealTimePriceAlerts(context: Context) {
        val uid = auth.currentUser?.uid ?: return

        // Önceki dinleyicileri temizle (Çoklu çalışmayı önle)
        activeListeners.forEach { it.remove() }
        activeListeners.clear()

        // 1. Favorileri Çek
        db.collection("users").document(uid).collection("favorites")
            .whereEqualTo("priceAlert", true)
            .get()
            .addOnSuccessListener { favDocs ->
                for (fav in favDocs) {
                    val pid = fav.getString("productId") ?: continue

                    // 2. Her ürün için CANLI bir kanca (Listener) tak
                    val listener = db.collection("products").document(pid)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                            val currentPriceStr = snapshot.getString("price") ?: "0"
                            val currentPrice = PriceUtils.parsePrice(currentPriceStr)

                            // Favori kaydındaki eski fiyatları kontrol etmemiz lazım
                            // Snapshot içinde sadece ürün verisi var, kullanıcının 'savedPrice' verisini tekrar çekelim
                            fav.reference.get().addOnSuccessListener { updatedFav ->
                                if (!updatedFav.exists()) return@addOnSuccessListener

                                val savedPrice = updatedFav.getDouble("savedPrice") ?: 0.0
                                val lastNotified = updatedFav.getDouble("lastNotifiedPrice") ?: savedPrice

                                // *** FİYAT DÜŞTÜ MÜ? ***
                                // (Şu anki fiyat, son bildirim atılan fiyattan düşükse)
                                if (currentPrice < lastNotified) {
                                    val name = snapshot.getString("name") ?: "Ürün"

                                    // BİLDİRİM AT
                                    NotificationHelper.sendPriceDropNotification(context, name, lastNotified, currentPrice)

                                    // Veritabanına bildirimi kaydet
                                    val notifData = hashMapOf(
                                        "title" to "İndirim Yakaladın! 🎉",
                                        "message" to "$name fiyatı düştü! ${lastNotified.toInt()}₺ -> ${currentPrice.toInt()}₺",
                                        "date" to java.util.Date(),
                                        "type" to "price_alert",
                                        "relatedId" to pid // EKSİK OLAN BUYDU! ARTIK ÜRÜN ID'Sİ KAYDEDİLİYOR
                                    )
                                    db.collection("users").document(uid).collection("notifications").add(notifData)

                                    // Tekrar bildirim atmamak için güncelle
                                    fav.reference.update(mapOf(
                                        "savedPrice" to currentPrice,
                                        "lastNotifiedPrice" to currentPrice
                                    ))
                                }
                                // Fiyat artmışsa sadece referansı güncelle (Bildirim yok)
                                else if (currentPrice > savedPrice) {
                                    fav.reference.update("savedPrice", currentPrice)
                                }
                            }
                        }

                    // Dinleyiciyi listeye ekle (Uygulama kapanırken temizlemek için)
                    activeListeners.add(listener)
                }
            }
    }

    // --- ARGO FİLTRESİ ---
    fun containsProfanity(text: String): Boolean {
        val badWords = listOf("küfür1", "küfür2", "argo", "hakaret") // Burayı genişletirsin
        val lowerText = text.lowercase()
        return badWords.any { lowerText.contains(it) }
    }

    // --- MAĞAZA TAKİP SİSTEMİ ---

    fun followStore(storeId: Int, onResult: (Boolean) -> Unit) {
        val user = UserManager.getCurrentUser() ?: return
        val uid = user.id

        // Firestore'da kullanıcının takip listesine ekle
        db.collection("users").document(uid)
            .update("followedStores", FieldValue.arrayUnion(storeId))
            .addOnSuccessListener {
                // Yerel kullanıcı verisini de güncelle (Anlık UI değişimi için)
                user.followedStores.add(storeId)
                onResult(true)
            }
    }

    fun unfollowStore(storeId: Int, onResult: (Boolean) -> Unit) {
        val user = UserManager.getCurrentUser() ?: return
        val uid = user.id

        db.collection("users").document(uid)
            .update("followedStores", FieldValue.arrayRemove(storeId))
            .addOnSuccessListener {
                user.followedStores.remove(storeId)
                onResult(true)
            }
    }

    fun isFollowing(storeId: Int): Boolean {
        val user = UserManager.getCurrentUser()
        return user?.followedStores?.contains(storeId) == true
    }

    // Uygulama kapanırken veya kullanıcı çıkış yaparken çağrılmalı
    fun stopTracking() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}