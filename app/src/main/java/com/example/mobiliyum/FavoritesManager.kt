package com.example.mobiliyum

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

object FavoritesManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val localFavorites = mutableSetOf<Int>()
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
            db.runBatch { batch ->
                batch.delete(userFavRef)
                batch.update(productRef, "favoriteCount", FieldValue.increment(-1))
            }.addOnSuccessListener {
                localFavorites.remove(product.id)
                onResult(false)
            }
        } else {
            val currentPriceDouble = PriceUtils.parsePrice(product.price)
            val favData = hashMapOf(
                "productId" to productIdStr,
                "productName" to product.name,
                "savedPrice" to currentPriceDouble,
                "lastNotifiedPrice" to currentPriceDouble,
                "priceAlert" to true,
                "addedAt" to Date()
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

    // --- GERÇEK ZAMANLI FİYAT TAKİBİ ---
    fun startRealTimePriceAlerts(context: Context) {
        val uid = auth.currentUser?.uid ?: return

        activeListeners.forEach { it.remove() }
        activeListeners.clear()

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

                            fav.reference.get().addOnSuccessListener { updatedFav ->
                                if (!updatedFav.exists()) return@addOnSuccessListener

                                val savedPrice = updatedFav.getDouble("savedPrice") ?: 0.0
                                val lastNotified = updatedFav.getDouble("lastNotifiedPrice") ?: savedPrice

                                if (currentPrice < lastNotified) {
                                    val name = snapshot.getString("name") ?: "Ürün"

                                    NotificationHelper.sendPriceDropNotification(context, name, lastNotified, currentPrice)

                                    // DÜZELTME: HashMap yerine NotificationItem kullanımı
                                    val notifRef = db.collection("users").document(uid).collection("notifications").document()
                                    val notifItem = NotificationItem(
                                        id = notifRef.id,
                                        title = "İndirim Yakaladın! 🎉",
                                        message = "$name fiyatı düştü! ${lastNotified.toInt()}₺ -> ${currentPrice.toInt()}₺",
                                        date = Date(),
                                        type = "price_alert",
                                        relatedId = pid,
                                        isRead = false
                                    )
                                    notifRef.set(notifItem)

                                    fav.reference.update(mapOf(
                                        "savedPrice" to currentPrice,
                                        "lastNotifiedPrice" to currentPrice
                                    ))
                                }
                                else if (currentPrice > savedPrice) {
                                    fav.reference.update("savedPrice", currentPrice)
                                }
                            }
                        }
                    activeListeners.add(listener)
                }
            }
    }

    fun containsProfanity(text: String): Boolean {
        val badWords = listOf("küfür1", "küfür2", "argo", "hakaret")
        val lowerText = text.lowercase()
        return badWords.any { lowerText.contains(it) }
    }

    fun followStore(storeId: Int, onResult: (Boolean) -> Unit) {
        val user = UserManager.getCurrentUser() ?: return
        val uid = user.id
        db.collection("users").document(uid)
            .update("followedStores", FieldValue.arrayUnion(storeId))
            .addOnSuccessListener {
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

    fun stopTracking() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}