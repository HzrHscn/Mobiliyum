package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagementFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    // Bekleyen sayısını yazdırmak için TextView referansı
    private var tvPendingCount: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_management, container, false)

        val user = UserManager.getCurrentUser()
        val role = UserManager.getUserRole()

        view.findViewById<TextView>(R.id.tvAdminWelcome).text = "Hoşgeldiniz, ${user?.fullName ?: "Yönetici"}"
        view.findViewById<TextView>(R.id.tvAdminRole).text = "Yetki: ${role.name}"

        val btnAnnouncements = view.findViewById<LinearLayout>(R.id.btnAnnouncements)
        val btnPendingRequests = view.findViewById<LinearLayout>(R.id.btnPendingStores)
        val btnUsers = view.findViewById<LinearLayout>(R.id.btnUsers)
        val btnReports = view.findViewById<LinearLayout>(R.id.btnReports)

        // Buton metinlerini güncelle
        val tvPendingTitle = btnPendingRequests.getChildAt(1) as? TextView
        tvPendingTitle?.text = "Satın Alma Onayları"

        // "Bekleyen: 0" yazan TextView'i bul (LinearLayout içindeki 3. eleman - index 2)
        tvPendingCount = btnPendingRequests.getChildAt(2) as? TextView

        // Sayfa açılınca sayıyı güncelle
        updatePendingCount()

        btnAnnouncements.setOnClickListener { showAnnouncementDialog() }
        btnPendingRequests.setOnClickListener { showPendingRequestsDialog() }

        btnUsers.setOnClickListener {
            Toast.makeText(context, "Bu alan düzenleniyor...", Toast.LENGTH_SHORT).show()
        }

        btnReports.setOnClickListener { showReportsDialog() }

        return view
    }

    private fun updatePendingCount() {
        // Sadece sayıyı almak için sorgu
        db.collection("purchase_requests")
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { docs ->
                tvPendingCount?.text = "Bekleyen: ${docs.size()}"
            }
    }

    // --- SATIN ALMA ONAY EKRANI ---
    private fun showPendingRequestsDialog() {
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        val title = TextView(context)
        title.text = "Yükleniyor..." // Önce yükleniyor yazsın
        title.textSize = 18f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        title.setPadding(0, 0, 0, 16)
        layout.addView(title)

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        layout.addView(recyclerView)

        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("Kapat", null)
            .create()

        // Verileri Çek (Yeni Hata Yakalamalı Fonksiyon)
        ReviewManager.getPendingRequests(
            onSuccess = { requests ->
                if (requests.isEmpty()) {
                    title.text = "Bekleyen talep yok"
                    Toast.makeText(context, "Şu an onay bekleyen talep yok.", Toast.LENGTH_SHORT).show()
                } else {
                    title.text = "Bekleyen Doğrulamalar (${requests.size})"
                    recyclerView.adapter = RequestAdapter(requests) { request, approved ->
                        // Onayla/Reddet işlemi
                        ReviewManager.processRequest(request, approved) { success ->
                            if (success) {
                                Toast.makeText(context, if(approved) "Onaylandı" else "Reddedildi", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                updatePendingCount() // Ana ekrandaki sayıyı da güncelle
                            } else {
                                Toast.makeText(context, "Hata oluştu.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onFailure = { error ->
                title.text = "Hata Oluştu"
                Toast.makeText(context, "Veri alınamadı: $error", Toast.LENGTH_LONG).show()
            }
        )

        dialog.show()
    }

    // --- ADAPTER ---
    inner class RequestAdapter(
        private val items: List<PurchaseRequest>,
        private val onAction: (PurchaseRequest, Boolean) -> Unit
    ) : RecyclerView.Adapter<RequestAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvUser: TextView = v.findViewById(R.id.tvReqUserName)
            val tvProduct: TextView = v.findViewById(R.id.tvReqProductName)
            val tvOrder: TextView = v.findViewById(R.id.tvReqOrderInfo)
            val tvDate: TextView = v.findViewById(R.id.tvReqDate)
            val btnApprove: View = v.findViewById(R.id.btnApprove)
            val btnReject: View = v.findViewById(R.id.btnReject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_purchase_request, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvUser.text = item.userName
            holder.tvProduct.text = item.productName
            holder.tvOrder.text = item.orderNumber
            holder.tvDate.text = SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(item.requestDate)

            holder.btnApprove.setOnClickListener { onAction(item, true) }
            holder.btnReject.setOnClickListener { onAction(item, false) }
        }
        override fun getItemCount() = items.size
    }

    // --- DİĞER FONKSİYONLAR (Değişmedi) ---
    private fun showAnnouncementDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_send_announcement, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etAnnounceTitle)
        val etMessage = dialogView.findViewById<TextInputEditText>(R.id.etAnnounceMessage)

        AlertDialog.Builder(context)
            .setTitle("Duyuru Yayınla")
            .setView(dialogView)
            .setPositiveButton("Gönder") { _, _ ->
                val title = etTitle.text.toString().trim()
                val message = etMessage.text.toString().trim()
                if (title.isNotEmpty() && message.isNotEmpty()) {
                    sendAnnouncementToFirebase(title, message)
                } else {
                    Toast.makeText(context, "Alanlar boş olamaz.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun sendAnnouncementToFirebase(title: String, message: String) {
        if (message.length > 200) {
            Toast.makeText(context, "Mesaj çok uzun! (Max 200)", Toast.LENGTH_LONG).show()
            return
        }

        val user = UserManager.getCurrentUser() ?: return
        val role = UserManager.getUserRole()

        var type = "general"
        var relatedId = ""
        var senderName = user.fullName

        if (role == UserRole.ADMIN || role == UserRole.SRV) {
            type = "store_update"
            relatedId = "6"
            senderName = "Allinset (Yönetici)"
        } else if (role == UserRole.MANAGER || role == UserRole.EDITOR) {
            type = "store_update"
            relatedId = user.storeId?.toString() ?: ""
        }

        if (type == "store_update" && relatedId.isEmpty()) {
            Toast.makeText(context, "Mağaza ID bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }

        val announcement = hashMapOf(
            "title" to title,
            "message" to message,
            "date" to Date(),
            "author" to senderName,
            "type" to type,
            "relatedId" to relatedId
        )

        db.collection("announcements").add(announcement)
            .addOnSuccessListener { Toast.makeText(context, "Duyuru Yayınlandı!", Toast.LENGTH_LONG).show() }
            .addOnFailureListener { Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun showReportsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reports, null)
        val tvStores = dialogView.findViewById<TextView>(R.id.tvTotalStores)
        val tvClicks = dialogView.findViewById<TextView>(R.id.tvTotalClicks)
        val tvTopStore = dialogView.findViewById<TextView>(R.id.tvTopStore)
        val tvTopProduct = dialogView.findViewById<TextView>(R.id.tvTopProduct)
        val tvTopFav = dialogView.findViewById<TextView>(R.id.tvTopFavorite)

        val dialog = AlertDialog.Builder(context).setView(dialogView).setPositiveButton("Kapat", null).create()
        dialog.show()

        db.collection("stores").orderBy("clickCount", Query.Direction.DESCENDING).get().addOnSuccessListener { storeDocs ->
            val totalStores = storeDocs.size()
            var totalClick = 0
            if (!storeDocs.isEmpty) {
                val bestStore = storeDocs.documents[0]
                val clicks = bestStore.getLong("clickCount") ?: 0
                tvTopStore.text = "${bestStore.getString("name")} ($clicks Görüntülenme)"
            } else {
                tvTopStore.text = "Veri Yok"
            }
            for (doc in storeDocs) totalClick += doc.getLong("clickCount")?.toInt() ?: 0
            tvStores.text = totalStores.toString()
            tvClicks.text = totalClick.toString()
        }

        db.collection("products").orderBy("clickCount", Query.Direction.DESCENDING).limit(1).get().addOnSuccessListener { productDocs ->
            if (!productDocs.isEmpty) {
                val p = productDocs.documents[0]
                val clicks = p.getLong("clickCount") ?: 0
                tvTopProduct.text = "${p.getString("name")} ($clicks Tık)"
            } else {
                tvTopProduct.text = "Henüz veri yok"
            }
        }

        db.collection("products").orderBy("favoriteCount", Query.Direction.DESCENDING).limit(1).get().addOnSuccessListener { productDocs ->
            if (!productDocs.isEmpty) {
                val p = productDocs.documents[0]
                val count = p.getLong("favoriteCount") ?: 0
                tvTopFav.text = "${p.getString("name")} ($count Favori)"
            } else {
                tvTopFav.text = "Henüz favori yok"
            }
        }
    }

    private fun showSimpleInfoDialog(title: String, message: String) {
        AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton("Tamam", null).show()
    }

    private fun countUsersAndShow() {
        db.collection("users").get()
            .addOnSuccessListener { showSimpleInfoDialog("Kullanıcı İstatistikleri", "Toplam Kayıtlı Kullanıcı: ${it.size()}") }
    }
}