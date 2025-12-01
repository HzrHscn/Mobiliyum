package com.example.mobiliyum

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    // Bildirim Bileşenleri (Tepeden Düşen Kart)
    private lateinit var notificationCard: CardView
    private lateinit var tvNotifTitle: TextView
    private lateinit var tvNotifBody: TextView
    private lateinit var btnCloseNotif: ImageView

    // Aktif Duyuru ID'si (Kapatınca kaydetmek için)
    private var activeAnnouncementId: String = ""

    // Fragmentlar
    private val homeFragment = HomeFragment()
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
    private val storesFragment = StoresFragment()
    private val welcomeFragment = WelcomeFragment()
    private val notificationsFragment = NotificationsFragment() // Ekstra olarak dursun

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View Bağlantıları
        bottomNav = findViewById(R.id.bottomNavigationView)
        notificationCard = findViewById(R.id.notificationCard)
        tvNotifTitle = findViewById(R.id.tvNotifTitle)
        tvNotifBody = findViewById(R.id.tvNotifBody)
        btnCloseNotif = findViewById(R.id.btnCloseNotif)

        // Başlangıçta menüyü gizle
        bottomNav.visibility = View.GONE

        // 1. Navigasyon Ayarlarını Yükle
        setupNavigation()

        // 2. Android 13+ için Bildirim İzni İste
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 3. Bildirim Kanalını Oluştur
        NotificationHelper.createNotificationChannel(this)

        // 4. Duyuruları Dinle (Uygulama açıkken tepeden düşen kart için)
        listenForAnnouncements()

        NotificationHelper.createNotificationChannel(this)

        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                FavoritesManager.loadUserFavorites {
                    loadFragment(homeFragment)
                    bottomNav.visibility = View.VISIBLE

                    // --- CANLI TAKİBİ BAŞLAT ---
                    FavoritesManager.startRealTimePriceAlerts(this)
                }
            } else {
                loadFragment(welcomeFragment)
            }
        }

        // Bildirim Kapatma Butonu
        btnCloseNotif.setOnClickListener {
            hideNotification()
            // Eğer bir duyuru ID'si varsa, bunu "görüldü" olarak kaydet
            if (activeAnnouncementId.isNotEmpty()) {
                saveDismissedAnnouncement(activeAnnouncementId)
            }
        }
    }

    // Uygulama her ön plana geldiğinde fiyatları tekrar kontrol et
    override fun onResume() {
        super.onResume()
        // Uygulama ön plana gelince takibi tazele
        if (UserManager.isLoggedIn()) {
            FavoritesManager.startRealTimePriceAlerts(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // İstersen arka planda pil tüketmesin diye durdurabilirsin
        // Ama bildirim arka planda da gelsin istiyorsan burayı boş bırak.
        // FavoritesManager.stopTracking()
    }

    private fun setupNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { loadFragment(homeFragment); true }
                R.id.nav_stores -> { loadFragment(storesFragment); true }
                R.id.nav_cart -> { loadFragment(cartFragment); true }
                R.id.nav_profile -> { loadFragment(accountFragment); true }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    // --- DUYURU DİNLEME SİSTEMİ ---
    private fun listenForAnnouncements() {
        val db = FirebaseFirestore.getInstance()

        // En son atılan 1 duyuruyu sürekli dinle
        db.collection("announcements")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                // Son duyuruyu al
                val doc = snapshots.documents[0]
                val id = doc.id
                val title = doc.getString("title")
                val message = doc.getString("message")

                // KONTROL: Bu ID daha önce kapatıldı mı?
                if (!isAnnouncementDismissed(id)) {
                    // Kapatılmamışsa göster
                    activeAnnouncementId = id
                    showNotification(title ?: "Duyuru", message ?: "")
                }
            }
    }

    // SharedPreferences kullanarak "Bu duyuru görüldü" diye kaydet
    private fun saveDismissedAnnouncement(id: String) {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("dismissed_announce_id", id)
            apply()
        }
    }

    // Kontrol et: Kayıtlı ID, gelen ID ile aynı mı?
    private fun isAnnouncementDismissed(id: String): Boolean {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val dismissedId = sharedPref.getString("dismissed_announce_id", "")
        return id == dismissedId
    }

    private fun showNotification(title: String, message: String) {
        // Eğer zaten görünüyorsa tekrar animasyon yapma
        if (notificationCard.visibility == View.VISIBLE && tvNotifTitle.text == title) return

        tvNotifTitle.text = title
        tvNotifBody.text = message

        notificationCard.visibility = View.VISIBLE
        notificationCard.translationY = -300f

        ObjectAnimator.ofFloat(notificationCard, "translationY", 0f).apply {
            duration = 500
            start()
        }
    }

    private fun hideNotification() {
        ObjectAnimator.ofFloat(notificationCard, "translationY", -300f).apply {
            duration = 300
            start()
        }.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                notificationCard.visibility = View.GONE
            }
        })
    }

    // Fragmentlardan çağrılacak yardımcılar
    fun showBottomNav() { bottomNav.visibility = View.VISIBLE }
    fun hideBottomNav() { bottomNav.visibility = View.GONE }
}