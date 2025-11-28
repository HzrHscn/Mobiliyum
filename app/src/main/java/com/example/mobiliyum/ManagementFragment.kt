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
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

class ManagementFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()

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
        val btnStores = view.findViewById<LinearLayout>(R.id.btnPendingStores)
        val btnUsers = view.findViewById<LinearLayout>(R.id.btnUsers)
        val btnReports = view.findViewById<LinearLayout>(R.id.btnReports)

        btnAnnouncements.setOnClickListener {
            showAnnouncementDialog()
        }

        btnStores.setOnClickListener {
            showSimpleInfoDialog("Mağaza Başvuruları", "Şu an onay bekleyen yeni mağaza başvurusu bulunmamaktadır.")
        }

        btnUsers.setOnClickListener {
            // Kullanıcı Yönetimi kısmı şu an bakımda
            Toast.makeText(context, "Bu alan düzenleniyor...", Toast.LENGTH_SHORT).show()
        }

        // RAPORLAR BUTONU
        btnReports.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ReportsFragment()) // Yeni fragment'a git
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    // --- RAPOR EKRANI ---
    private fun showReportsDialog() {
        // XML'i şişir (Inflate)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reports, null)

        // Bileşenleri Bağla
        val tvStores = dialogView.findViewById<TextView>(R.id.tvTotalStores)
        val tvClicks = dialogView.findViewById<TextView>(R.id.tvTotalClicks)
        val tvTopStore = dialogView.findViewById<TextView>(R.id.tvTopStore)
        val tvTopProduct = dialogView.findViewById<TextView>(R.id.tvTopProduct)
        val tvTopFav = dialogView.findViewById<TextView>(R.id.tvTopFavorite)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Kapat", null)
            .create()

        dialog.show()

        // 1. Mağaza İstatistikleri ve En Popüler Mağaza
        db.collection("stores")
            .orderBy("clickCount", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { storeDocs ->
                val totalStores = storeDocs.size()
                var totalClick = 0

                if (!storeDocs.isEmpty) {
                    val bestStore = storeDocs.documents[0]
                    val clicks = bestStore.getLong("clickCount") ?: 0
                    tvTopStore.text = "${bestStore.getString("name")} ($clicks Görüntülenme)"
                } else {
                    tvTopStore.text = "Veri Yok"
                }

                for (doc in storeDocs) {
                    totalClick += doc.getLong("clickCount")?.toInt() ?: 0
                }

                tvStores.text = totalStores.toString()
                tvClicks.text = totalClick.toString()
            }

        // 2. En Çok İncelenen Ürün
        db.collection("products")
            .orderBy("clickCount", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { productDocs ->
                if (!productDocs.isEmpty) {
                    val p = productDocs.documents[0]
                    val clicks = p.getLong("clickCount") ?: 0
                    tvTopProduct.text = "${p.getString("name")} ($clicks Tık)"
                } else {
                    tvTopProduct.text = "Henüz veri yok"
                }
            }

        // 3. En Çok Favorilenen Ürün
        db.collection("products")
            .orderBy("favoriteCount", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { productDocs ->
                if (!productDocs.isEmpty) {
                    val p = productDocs.documents[0]
                    val count = p.getLong("favoriteCount") ?: 0
                    tvTopFav.text = "${p.getString("name")} ($count Favori)"
                } else {
                    tvTopFav.text = "Henüz favori yok"
                }
            }
    }

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
                    Toast.makeText(context, "Başlık ve mesaj boş olamaz.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun sendAnnouncementToFirebase(title: String, message: String) {
        val announcement = hashMapOf(
            "title" to title,
            "message" to message,
            "date" to Date(),
            "author" to (UserManager.getCurrentUser()?.fullName ?: "Admin"),
            "isActive" to true
        )

        db.collection("announcements")
            .add(announcement)
            .addOnSuccessListener {
                Toast.makeText(context, "Duyuru başarıyla yayınlandı!", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showSimpleInfoDialog(title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun countUsersAndShow() {
        db.collection("users").get()
            .addOnSuccessListener { result ->
                showSimpleInfoDialog("Kullanıcı İstatistikleri", "Toplam Kayıtlı Kullanıcı: ${result.size()}")
            }
            .addOnFailureListener {
                Toast.makeText(context, "Veri alınamadı.", Toast.LENGTH_SHORT).show()
            }
    }
}