package com.example.mobiliyum

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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

    // 3. KULLANICI: YORUM EKLE (TRANSACTION İLE GÜVENLİ HESAPLAMA)
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
            date = java.util.Date(),
            isVerified = false // Yeni yorumlar onaysız başlar
        )

        db.runTransaction { transaction ->
            // 1. Ürünün en güncel halini veritabanından oku
            val snapshot = transaction.get(productRef)

            // 2. Mevcut değerleri al
            val currentTotalRating = snapshot.getDouble("totalRating") ?: (snapshot.getDouble("rating")!! * snapshot.getLong("reviewCount")!!)
            val currentCount = snapshot.getLong("reviewCount") ?: 0

            // 3. Matematiği Yap: Toplam / Adet
            val newTotalRating = currentTotalRating + rating
            val newCount = currentCount + 1
            val newAverage = (newTotalRating / newCount).toFloat()

            // 4. Yazma İşlemleri
            transaction.set(reviewRef, review)
            transaction.update(productRef, mapOf(
                "totalRating" to newTotalRating,
                "reviewCount" to newCount,
                "rating" to newAverage
            ))
        }.addOnSuccessListener {
            onComplete(true)
        }.addOnFailureListener { e ->
            onComplete(false)
        }
    }

    // 4. YÖNETİCİ: BEKLEYEN TALEPLERİ ÇEK (DÜZELTİLDİ: Mağaza Filtreli)
    // storeId parametresi eklendi. Eğer null ise (Admin) hepsi gelir, doluysa (Manager) sadece o mağaza gelir.
    fun getPendingRequests(storeId: Int? = null, onSuccess: (List<PurchaseRequest>) -> Unit, onFailure: (String) -> Unit) {

        var query: Query = db.collection("purchase_requests")
            .whereEqualTo("status", "PENDING")

        // EĞER MAĞAZA ID VARSA FİLTRELE
        if (storeId != null) {
            query = query.whereEqualTo("storeId", storeId)
        }

        query.get()
            .addOnSuccessListener { docs ->
                val list = docs.toObjects(PurchaseRequest::class.java)
                // Kod içinde tarihe göre sırala (En yeni en üstte)
                val sortedList = list.sortedByDescending { it.requestDate }
                onSuccess(sortedList)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Veri çekilemedi")
            }
    }

    // 5. YÖNETİCİ: TALEBİ ONAYLA VEYA REDDET
    fun processRequest(request: PurchaseRequest, isApproved: Boolean, onComplete: (Boolean) -> Unit) {
        val status = if (isApproved) "APPROVED" else "REJECTED"

        db.runBatch { batch ->
            val reqRef = db.collection("purchase_requests").document(request.id)
            batch.update(reqRef, "status", status)

            if (isApproved) {
                val userPermRef = db.collection("users").document(request.userId)
                    .collection("approved_purchases").document(request.productId.toString())

                val permissionData = hashMapOf(
                    "productId" to request.productId,
                    "productName" to request.productName,
                    "grantedAt" to java.util.Date()
                )
                batch.set(userPermRef, permissionData)

                val notifRef = db.collection("users").document(request.userId).collection("notifications").document()
                val notif = hashMapOf(
                    "title" to "Satın Alım Onaylandı ✅",
                    "message" to "${request.productName} için satın alımınız doğrulandı. Artık değerlendirme yapabilirsiniz!",
                    "date" to java.util.Date(),
                    "type" to "general"
                )
                batch.set(notifRef, notif)
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
                val sorted = list.sortedByDescending { it.date }
                onSuccess(sorted)
            }
    }
}