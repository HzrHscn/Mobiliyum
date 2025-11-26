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

        // Başlıkları Ayarla
        view.findViewById<TextView>(R.id.tvAdminWelcome).text = "Hoşgeldiniz, ${user?.fullName ?: "Yönetici"}"
        view.findViewById<TextView>(R.id.tvAdminRole).text = "Yetki: ${role.name}"

        // Butonları Bağla
        val btnAnnouncements = view.findViewById<LinearLayout>(R.id.btnAnnouncements)
        val btnStores = view.findViewById<LinearLayout>(R.id.btnPendingStores)
        val btnUsers = view.findViewById<LinearLayout>(R.id.btnUsers)
        val btnReports = view.findViewById<LinearLayout>(R.id.btnReports)

        // --- 1. DUYURU GÖNDERME ---
        btnAnnouncements.setOnClickListener {
            showAnnouncementDialog()
        }

        // --- 2. DİĞER BUTONLAR (Şimdilik) ---
        btnStores.setOnClickListener {
            // İleride buraya onay bekleyen mağazaları listeleyen Dialog gelecek
            showSimpleInfoDialog("Mağaza Başvuruları", "Şu an onay bekleyen yeni mağaza başvurusu bulunmamaktadır.")
        }

        btnUsers.setOnClickListener {
            // İleride buraya kullanıcı istatistikleri gelecek
            countUsersAndShow()
        }

        btnReports.setOnClickListener {
            showReportsDialog()
        }

        return view
    }

    // --- DUYURU GÖNDERME MANTIĞI ---
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

    // --- YARDIMCI FONKSİYONLAR ---

    private fun showSimpleInfoDialog(title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    // Kullanıcı Sayısını Çeken Fonksiyon
    private fun countUsersAndShow() {
        db.collection("users").get()
            .addOnSuccessListener { result ->
                showSimpleInfoDialog("Kullanıcı İstatistikleri", "Toplam Kayıtlı Kullanıcı: ${result.size()}")
            }
            .addOnFailureListener {
                Toast.makeText(context, "Veri alınamadı.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showReportsDialog() {
        // İstatistikleri hesaplamak biraz sürebilir, önce yükleniyor gösterelim
        Toast.makeText(context, "Raporlar hazırlanıyor...", Toast.LENGTH_SHORT).show()

        var totalStores = 0
        var totalStoreClicks = 0
        var topProduct = "Yok"
        var topFavProduct = "Yok"

        // 1. Mağaza İstatistikleri
        db.collection("stores").get().addOnSuccessListener { storeDocs ->
            totalStores = storeDocs.size()
            for (doc in storeDocs) {
                totalStoreClicks += doc.getLong("clickCount")?.toInt() ?: 0
            }

            // 2. En Çok Tıklanan Ürün
            db.collection("products")
                .orderBy("clickCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { productDocs ->
                    if (!productDocs.isEmpty) {
                        val p = productDocs.documents[0]
                        topProduct = "${p.getString("name")} (${p.getLong("clickCount")} tık)"
                    }

                    // 3. En Çok Favorilenen Ürün (Şimdilik clickCount ile aynı mantık, favori eklenince değişir)
                    // (Buraya favori sorgusu gelecek)

                    // Tüm veriler hazır, Dialogu Göster
                    val reportMessage = """
                        📊 <b>GENEL İSTATİSTİKLER</b><br><br>
                        🏪 <b>Toplam Mağaza:</b> $totalStores<br>
                        👆 <b>Toplam Mağaza Görüntüleme:</b> $totalStoreClicks<br>
                        🔥 <b>En Popüler Ürün:</b><br> $topProduct<br>
                        ❤️ <b>En Çok Favorilenen:</b><br> (Veri bekleniyor)
                    """.trimIndent()

                    AlertDialog.Builder(context)
                        .setTitle("Yönetici Raporları")
                        .setMessage(android.text.Html.fromHtml(reportMessage, android.text.Html.FROM_HTML_MODE_LEGACY))
                        .setPositiveButton("Tamam", null)
                        .show()
                }
        }
    }

}