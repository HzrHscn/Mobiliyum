package com.example.mobiliyum

import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

object EditorManager {
    private val db = FirebaseFirestore.getInstance()

    // 1. EDİTÖR: Duyuru Talebi Gönder
    fun submitAnnouncementRequest(title: String, message: String, onSuccess: () -> Unit) {
        val user = UserManager.getCurrentUser() ?: return
        val ref = db.collection("store_requests").document()

        val request = StoreRequest(
            id = ref.id,
            storeId = user.storeId ?: 0,
            requesterId = user.id,
            requesterName = user.fullName,
            type = "ANNOUNCEMENT",
            title = title,
            message = message
        )
        ref.set(request).addOnSuccessListener { onSuccess() }
    }

    // 2. EDİTÖR: Vitrin Talebi Gönder
    fun submitShowcaseRequest(productIds: List<Int>, onSuccess: () -> Unit) {
        val user = UserManager.getCurrentUser() ?: return
        val ref = db.collection("store_requests").document()

        val request = StoreRequest(
            id = ref.id,
            storeId = user.storeId ?: 0,
            requesterId = user.id,
            requesterName = user.fullName,
            type = "SHOWCASE",
            selectedProductIds = productIds
        )
        ref.set(request).addOnSuccessListener { onSuccess() }
    }

    // 3. MÜDÜR: Bekleyen Editör Taleplerini Çek
    fun getPendingRequests(storeId: Int, onSuccess: (List<StoreRequest>) -> Unit) {
        db.collection("store_requests")
            .whereEqualTo("storeId", storeId)
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { docs ->
                val list = docs.toObjects(StoreRequest::class.java)
                // En yeniler üstte
                onSuccess(list.sortedByDescending { it.requestDate })
            }
    }

    // 4. MÜDÜR: Talebi Onayla/Reddet
    fun processRequest(request: StoreRequest, isApproved: Boolean, onComplete: () -> Unit) {
        val status = if (isApproved) "APPROVED" else "REJECTED"

        db.collection("store_requests").document(request.id)
            .update("status", status)
            .addOnSuccessListener {
                if (isApproved) {
                    if (request.type == "ANNOUNCEMENT") {
                        publishAnnouncement(request)
                    } else if (request.type == "SHOWCASE") {
                        publishShowcase(request)
                    }
                }
                onComplete()
            }
    }

    private fun publishAnnouncement(req: StoreRequest) {
        // A) Duyuruyu Genel Listeye Ekle
        val data = hashMapOf(
            "title" to req.title,
            "message" to req.message,
            "date" to Date(),
            "type" to "store_update",
            "relatedId" to req.storeId.toString(),
            "author" to req.requesterName
        )
        db.collection("announcements").add(data)

        // B) Takipçilere Bildirim Gönder
        sendNotificationToFollowers(req.storeId, req.title, req.message)
    }

    private fun publishShowcase(req: StoreRequest) {
        db.collection("stores").document(req.storeId.toString())
            .update("featuredProductIds", req.selectedProductIds)
    }

    // --- BİLDİRİM SİSTEMİ ---
    private fun sendNotificationToFollowers(storeId: Int, title: String, message: String) {
        // Mağazayı takip edenleri bul (stores/{id}/followers koleksiyonundan)
        db.collection("stores").document(storeId.toString())
            .collection("followers")
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()

                for (doc in documents) {
                    val userId = doc.id // Takipçinin ID'si

                    val notifRef = db.collection("users").document(userId)
                        .collection("notifications").document()

                    val notification = hashMapOf(
                        "title" to "📢 Mağaza Duyurusu",
                        "message" to title, // Başlık mesaj olarak görünsün
                        "detail" to message, // Detay tıklandığında açılsın
                        "date" to Date(),
                        "read" to false,
                        "type" to "announcement",
                        "relatedId" to storeId.toString()
                    )
                    batch.set(notifRef, notification)
                }

                batch.commit().addOnSuccessListener {
                    // Bildirimler başarıyla gönderildi
                }
            }
    }
}