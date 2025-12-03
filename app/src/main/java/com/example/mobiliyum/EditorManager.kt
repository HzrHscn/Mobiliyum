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
    fun submitShowcaseRequest(productIds: ArrayList<Int>, onSuccess: () -> Unit) {
        val user = UserManager.getCurrentUser() ?: return
        val ref = db.collection("store_requests").document()

        val request = StoreRequest(
            id = ref.id,
            storeId = user.storeId ?: 0,
            requesterId = user.id,
            requesterName = user.fullName,
            type = "SHOWCASE_UPDATE",
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
                    } else if (request.type == "SHOWCASE_UPDATE" || request.type == "SHOWCASE") {
                        publishShowcase(request)
                    }
                }
                onComplete()
            }
    }

    private fun publishAnnouncement(req: StoreRequest) {
        val data = hashMapOf(
            "title" to req.title,
            "message" to req.message,
            "date" to Date(),
            "type" to "store_update",
            "relatedId" to req.storeId.toString(),
            "author" to req.requesterName
        )
        db.collection("announcements").add(data)

        sendNotificationToFollowers(req.storeId, req.title, req.message)
    }

    private fun publishShowcase(req: StoreRequest) {
        db.collection("stores").document(req.storeId.toString())
            .update("featuredProductIds", req.selectedProductIds)
    }

    private fun sendNotificationToFollowers(storeId: Int, title: String, message: String) {
        db.collection("stores").document(storeId.toString())
            .collection("followers")
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
                for (doc in documents) {
                    val userId = doc.id
                    val notifRef = db.collection("users").document(userId)
                        .collection("notifications").document()

                    val item = NotificationItem(
                        id = notifRef.id,
                        title = "📢 Mağaza Duyurusu",
                        message = title,
                        date = Date(),
                        type = "store_update",
                        relatedId = storeId.toString(),
                        senderName = "Mağaza",
                        isRead = false
                    )
                    batch.set(notifRef, item)
                }
                batch.commit()
            }
    }
}