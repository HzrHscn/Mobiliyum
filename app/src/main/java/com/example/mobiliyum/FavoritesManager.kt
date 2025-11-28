package com.example.mobiliyum

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object FavoritesManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val localFavorites = mutableSetOf<Int>()

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
                "savedPrice" to currentPriceDouble, // Fiyatı kaydet ki sonra düşüşü anlayalım
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

    // --- FİYAT KONTROLÜ (Bu fonksiyon MainActivity'den çağrılacak) ---
    fun checkPriceDrops(context: Context) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).collection("favorites")
            .whereEqualTo("priceAlert", true)
            .get()
            .addOnSuccessListener { favDocs ->
                if (favDocs.isEmpty) return@addOnSuccessListener

                for (fav in favDocs) {
                    val pid = fav.getString("productId") ?: continue
                    val savedPrice = fav.getDouble("savedPrice") ?: 0.0
                    val lastNotifiedPrice = fav.getDouble("lastNotifiedPrice") ?: savedPrice

                    db.collection("products").document(pid).get().addOnSuccessListener { prodDoc ->
                        if (prodDoc.exists()) {
                            val currentPriceStr = prodDoc.getString("price") ?: "0"
                            val currentPrice = PriceUtils.parsePrice(currentPriceStr)

                            // Eğer fiyat, son bildiğimizden düşükse BİLDİRİM AT
                            if (currentPrice < lastNotifiedPrice) {
                                val name = prodDoc.getString("name") ?: "Ürün"
                                NotificationHelper.sendPriceDropNotification(context, name, lastNotifiedPrice, currentPrice)

                                fav.reference.update(mapOf(
                                    "savedPrice" to currentPrice,
                                    "lastNotifiedPrice" to currentPrice
                                ))
                            }
                            // Fiyat artmışsa sadece kaydı güncelle
                            else if (currentPrice != savedPrice) {
                                fav.reference.update("savedPrice", currentPrice)
                            }
                        }
                    }
                }
            }
    }
}