package com.example.mobiliyum

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.ActivityMainBinding // ViewBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Aktif Duyuru ID'si (Kapatınca kaydetmek için)
    private var activeAnnouncementId: String = ""

    // Fragmentlar
    private val homeFragment = HomeFragment()
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
    private val storesFragment = StoresFragment()
    private val welcomeFragment = WelcomeFragment()
    // private val notificationsFragment = NotificationsFragment() // İhtiyaç olursa açılır

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding Kurulumu
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Başlangıçta menüyü gizle
        binding.bottomNavigationView.visibility = View.GONE

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

        // Kullanıcı Oturum Kontrolü
        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                FavoritesManager.loadUserFavorites {
                    loadFragment(homeFragment)
                    binding.bottomNavigationView.visibility = View.VISIBLE

                    // --- CANLI TAKİBİ BAŞLAT ---
                    FavoritesManager.startRealTimePriceAlerts(this)
                }
            } else {
                loadFragment(welcomeFragment)
            }
        }

        // Bildirim Kapatma Butonu
        binding.btnCloseNotif.setOnClickListener {
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
        if (UserManager.isLoggedIn()) {
            FavoritesManager.startRealTimePriceAlerts(this)
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
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
        if (binding.notificationCard.visibility == View.VISIBLE && binding.tvNotifTitle.text == title) return

        binding.tvNotifTitle.text = title
        binding.tvNotifBody.text = message

        binding.notificationCard.visibility = View.VISIBLE
        binding.notificationCard.translationY = -300f

        ObjectAnimator.ofFloat(binding.notificationCard, "translationY", 0f).apply {
            duration = 500
            start()
        }
    }

    private fun hideNotification() {
        ObjectAnimator.ofFloat(binding.notificationCard, "translationY", -300f).apply {
            duration = 300
            start()
        }.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.notificationCard.visibility = View.GONE
            }
        })
    }

    // Fragmentlardan çağrılacak yardımcılar
    fun showBottomNav() { binding.bottomNavigationView.visibility = View.VISIBLE }
    fun hideBottomNav() { binding.bottomNavigationView.visibility = View.GONE }

    // Sepet Badge'ini güncellemek için yardımcı (Opsiyonel ama önerilir)
    fun updateCartBadge() {
        val count = CartManager.getCartItemCount()
        val badge = binding.bottomNavigationView.getOrCreateBadge(R.id.nav_cart)
        badge.isVisible = count > 0
        badge.number = count
    }
}