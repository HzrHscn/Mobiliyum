package com.example.mobiliyum

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

object FavoritesManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val localFavorites = mutableSetOf<Int>()
    private val followedStoreIds = HashSet<Int>()
    private val activeListeners = ArrayList<ListenerRegistration>()

    // Favori ve Takip Yükleme
    fun loadUserFavorites(onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("favorites").get()
            .addOnSuccessListener { docs ->
                localFavorites.clear()
                for (d in docs) { d.getString("productId")?.toIntOrNull()?.let { localFavorites.add(it) } }

                // Mağaza Takiplerini de Yükle
                db.collection("users").document(uid).collection("followed_stores").get()
                    .addOnSuccessListener { storeDocs ->
                        followedStoreIds.clear()
                        for (s in storeDocs) { s.getLong("storeId")?.toInt()?.let { followedStoreIds.add(it) } }
                        onComplete()
                    }.addOnFailureListener { onComplete() }
            }.addOnFailureListener { onComplete() }
    }

    fun isFavorite(productId: Int) = localFavorites.contains(productId)
    fun isFollowing(storeId: Int) = followedStoreIds.contains(storeId)

    // Fiyat Takibi (Live Listener)
    fun startRealTimePriceAlerts(context: Context) {
        val uid = auth.currentUser?.uid ?: return

        // Eski dinleyicileri temizle
        activeListeners.forEach { it.remove() }
        activeListeners.clear()

        // Sadece alarmı açık olan favorileri dinle
        db.collection("users").document(uid).collection("favorites")
            .whereEqualTo("priceAlert", true)
            .get()
            .addOnSuccessListener { favDocs ->
                for (fav in favDocs) {
                    val pid = fav.getString("productId") ?: continue

                    val listener = db.collection("products").document(pid)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                            val currentPriceStr = snapshot.getString("price") ?: "0"
                            val currentPrice = PriceUtils.parsePrice(currentPriceStr)

                            // Favori kaydındaki son bilinen fiyatı al
                            fav.reference.get().addOnSuccessListener { updatedFav ->
                                val lastNotified = updatedFav.getDouble("lastNotifiedPrice") ?: currentPrice

                                // FİYAT DÜŞTÜ MÜ?
                                if (currentPrice < lastNotified) {
                                    val name = snapshot.getString("name") ?: "Ürün"

                                    // BİLDİRİM GÖNDER
                                    NotificationHelper.sendNotification(
                                        context,
                                        "İndirim Alarmı! \uD83D\uDD25",
                                        "$name fiyatı düştü! ${PriceUtils.formatPriceStyled(currentPrice)}"
                                    )

                                    // Bildirimi Kaydet (Geçmiş için)
                                    val notifRef = db.collection("users").document(uid).collection("notifications").document()
                                    val item = NotificationItem(
                                        id = notifRef.id,
                                        title = "İndirim Yakaladın!",
                                        message = "$name fiyatı düştü! Eski: ${PriceUtils.formatPriceStyled(lastNotified)} -> Yeni: ${PriceUtils.formatPriceStyled(currentPrice)}",
                                        date = Date(),
                                        type = "price_alert",
                                        relatedId = pid,
                                        isRead = false
                                    )
                                    notifRef.set(item)

                                    // Tekrar bildirim atmaması için güncelle
                                    fav.reference.update("lastNotifiedPrice", currentPrice)
                                }
                            }
                        }
                    activeListeners.add(listener)
                }
            }
    }

    fun toggleFavorite(product: Product, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val pidStr = product.id.toString()
        val ref = db.collection("users").document(uid).collection("favorites").document(pidStr)

        if (isFavorite(product.id)) {
            ref.delete().addOnSuccessListener { localFavorites.remove(product.id); onResult(false) }
        } else {
            val priceVal = PriceUtils.parsePrice(product.price)
            val data = hashMapOf(
                "productId" to pidStr,
                "productName" to product.name,
                "lastNotifiedPrice" to priceVal, // Başlangıç fiyatı
                "priceAlert" to true,
                "addedAt" to Date()
            )
            ref.set(data).addOnSuccessListener { localFavorites.add(product.id); onResult(true) }
        }
    }

    fun updatePriceAlert(productId: Int, isEnabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("favorites")
            .document(productId.toString()).update("priceAlert", isEnabled)
    }

    fun followStore(storeId: Int, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("followed_stores")
            .document(storeId.toString()).set(mapOf("storeId" to storeId))
            .addOnSuccessListener { followedStoreIds.add(storeId); onComplete() }
    }

    fun unfollowStore(storeId: Int, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("followed_stores")
            .document(storeId.toString()).delete()
            .addOnSuccessListener { followedStoreIds.remove(storeId); onComplete() }
    }
}