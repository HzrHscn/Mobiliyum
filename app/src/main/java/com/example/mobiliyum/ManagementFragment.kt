package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ManagementFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Basit bir layout oluşturuyoruz (XML kullanmadan kod ile)
        val layout = android.widget.LinearLayout(context)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        layout.setBackgroundColor(android.graphics.Color.WHITE)

        val title = TextView(context)
        title.textSize = 24f
        title.setPadding(0,0,0,32)
        layout.addView(title)

        val content = TextView(context)
        content.textSize = 16f
        layout.addView(content)

        // --- DÜZELTME BURADA ---
        // Eskiden: val user = UserManager.getCurrentUser() (User nesnesi dönüyordu)
        // Şimdi: UserManager.getUserRole() ve FirebaseUser kullanıyoruz.

        val role = UserManager.getUserRole()
        val firebaseUser = UserManager.getCurrentUser() // Bu artık FirebaseUser nesnesi
        val email = firebaseUser?.email ?: ""

        // Şimdilik "storeId" bilgisini e-posta adresinden simüle ediyoruz.
        // Gerçek veritabanı (Firestore) bağlanınca bu ID oradan gelecek.
        val storeId = if (email.contains("cilek")) 2 else if (email.contains("saloni")) 4 else -1

        if (!UserManager.isLoggedIn()) {
            title.text = "Hata"
            content.text = "Giriş yapmalısınız."
        } else {
            when (role) {
                UserRole.SRV -> {
                    title.text = "MOBİLİYUM SAHİBİ PANELİ"
                    content.text = "Tüm Mağazaların Ciro Raporları:\n\n" +
                            "- Çilek: 1.2M ₺\n" +
                            "- Saloni: 3.4M ₺\n" +
                            "- Nills: 900B ₺\n\n" +
                            "Toplam Ziyaretçi: 15.000"
                }
                UserRole.ADMIN -> {
                    title.text = "SİSTEM YÖNETİCİSİ"
                    content.text = "Sistem Durumu: Aktif\n" +
                            "Veritabanı: Firebase Bağlı\n" +
                            "Loglar: Temiz\n" +
                            "Aktif Kullanıcı: $email"
                }
                UserRole.MANAGER -> {
                    val storeName = if(storeId == 2) "Çilek Odası" else "Mağaza"
                    title.text = "$storeName MÜDÜR PANELİ"
                    content.text = "Bu ayki satışlarınız: 450.000 ₺\n" +
                            "Personel Performansı: İyi\n\n" +
                            "[Personel Ekle] [Rapor Al]"
                }
                UserRole.EDITOR -> {
                    title.text = "EDİTÖR PANELİ"
                    content.text = "Yetkili Olduğunuz Mağaza ID: $storeId\n\n" +
                            "Buradan ürün fiyatlarını güncelleyebilir, yeni fotoğraf yükleyebilirsiniz."
                }
                else -> {
                    content.text = "Yetkisiz Erişim."
                }
            }
        }

        return layout
    }
}