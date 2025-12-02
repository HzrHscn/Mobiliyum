package com.example.mobiliyum

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

object ReviewManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 1. KULLANICI: SATIN ALMA ONAYI İSTE
    fun requestPurchaseVerification(product: Product, orderInfo: String, onResult: (Boolean) -> Unit) {
        val user = UserManager.getCurrentUser()
        if (user == null) {
            onResult(false)
            return
        }

        val newReqRef = db.collection("purchase_requests").document()
        val request = PurchaseRequest(
            id = newReqRef.id,
            userId = user.id,
            userName = user.fullName,
            productId = product.id,
            productName = product.name,
            storeId = product.storeId,
            orderNumber = orderInfo,
            status = "PENDING"
        )

        newReqRef.set(request)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // 2. KULLANICI: İZİN KONTROLÜ
    fun checkReviewPermission(productId: Int, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .collection("approved_purchases").document(productId.toString())
            .get()
            .addOnSuccessListener { doc ->
                onResult(doc.exists())
            }
            .addOnFailureListener { onResult(false) }
    }

    // 3. KULLANICI: YORUM EKLE (DÜZELTİLDİ: NULL POINTER HATASI GİDERİLDİ)
    fun addReview(product: Product, rating: Float, comment: String, onComplete: (Boolean) -> Unit) {
        val user = UserManager.getCurrentUser() ?: return

        val reviewRef = db.collection("reviews").document()
        val productRef = db.collection("products").document(product.id.toString())

        val review = Review(
            id = reviewRef.id,
            productId = product.id,
            userId = user.id,
            userName = user.fullName,
            rating = rating,
            comment = comment,
            date = Date(),
            isVerified = true,
            // YENİ ALANLAR:
            productName = product.name,
            productImageUrl = product.imageUrl
        )

        db.runTransaction { transaction ->
            val snapshot = transaction.get(productRef)

            // GÜVENLİ HESAPLAMA: Değerler null ise 0 kabul et (Çökme önleyici)
            val currentRating = snapshot.getDouble("rating") ?: 0.0
            val currentCountLong = snapshot.getLong("reviewCount") ?: 0L

            // Eğer totalRating yoksa eskiden hesapla, o da yoksa 0.0
            val currentTotalRating = snapshot.getDouble("totalRating")
                ?: (currentRating * currentCountLong)

            val newTotalRating = currentTotalRating + rating
            val newCount = currentCountLong + 1
            val newAverage = (newTotalRating / newCount).toFloat()

            // Yazma İşlemleri
            transaction.set(reviewRef, review)
            transaction.update(productRef, mapOf(
                "totalRating" to newTotalRating,
                "reviewCount" to newCount,
                "rating" to newAverage
            ))
        }.addOnSuccessListener {
            onComplete(true)
        }.addOnFailureListener { e ->
            // Hata logu eklenebilir: Log.e("ReviewError", e.message.toString())
            onComplete(false)
        }
    }

    // 4. YÖNETİCİ: BEKLEYEN TALEPLERİ ÇEK
    fun getPendingRequests(storeId: Int? = null, onSuccess: (List<PurchaseRequest>) -> Unit, onFailure: (String) -> Unit) {
        var query: Query = db.collection("purchase_requests")
            .whereEqualTo("status", "PENDING")

        if (storeId != null) {
            query = query.whereEqualTo("storeId", storeId)
        }

        query.get()
            .addOnSuccessListener { docs ->
                val list = docs.toObjects(PurchaseRequest::class.java)
                onSuccess(list.sortedByDescending { it.requestDate })
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Veri çekilemedi")
            }
    }

    // 5. YÖNETİCİ: TALEBİ ONAYLA VEYA REDDET (DÜZELTİLDİ: BİLDİRİM NESNESİ)
    fun processRequest(request: PurchaseRequest, isApproved: Boolean, onComplete: (Boolean) -> Unit) {
        val status = if (isApproved) "APPROVED" else "REJECTED"

        db.runBatch { batch ->
            val reqRef = db.collection("purchase_requests").document(request.id)
            batch.update(reqRef, "status", status)

            if (isApproved) {
                // A) İzin Belgesi Oluştur
                val userPermRef = db.collection("users").document(request.userId)
                    .collection("approved_purchases").document(request.productId.toString())

                val permissionData = hashMapOf(
                    "productId" to request.productId,
                    "productName" to request.productName,
                    "grantedAt" to Date()
                )
                batch.set(userPermRef, permissionData)

                // B) Bildirim Gönder (NotificationItem kullanarak)
                val notifRef = db.collection("users").document(request.userId).collection("notifications").document()

                val notification = NotificationItem(
                    id = notifRef.id,
                    title = "Satın Alım Onaylandı ✅",
                    message = "${request.productName} için doğrulama başarılı. Artık puan ve yorum yapabilirsiniz!",
                    date = Date(),
                    type = "general",
                    relatedId = request.productId.toString(),
                    isRead = false
                )
                batch.set(notifRef, notification)
            }
        }.addOnSuccessListener {
            onComplete(true)
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    // 6. YORUMLARI GETİR
    fun getReviews(productId: Int, onSuccess: (List<Review>) -> Unit) {
        db.collection("reviews")
            .whereEqualTo("productId", productId)
            .get()
            .addOnSuccessListener { docs ->
                val list = docs.toObjects(Review::class.java)
                onSuccess(list.sortedByDescending { it.date })
            }
    }

    fun getUserReviews(onSuccess: (List<Review>) -> Unit) {
        val userId = UserManager.getCurrentUser()?.id ?: return
        db.collection("reviews")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                val list = docs.toObjects(Review::class.java)
                onSuccess(list.sortedByDescending { it.date })
            }
            .addOnFailureListener {
                onSuccess(emptyList()) // Hata olursa boş liste dön
            }
    }
}