package com.example.mobiliyum

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.ActivityMainBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private var activeAnnouncementId: String = ""

    // Fragmentlar
    private val storesFragment = StoresFragment() // Artık Ana Ekran
    private val productsFragment = ProductsFragment() // YENİ
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
    private val welcomeFragment = WelcomeFragment()

    // Web Sitesi için HomeFragment'ı tutuyoruz ama menüden değil, Hesabım'dan açılacak
    val webFragment = HomeFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // BottomNavigation başlangıçta gizli (Login kontrolü bitene kadar)
        binding.bottomNavigationView.visibility = View.GONE
        binding.bottomNavigationView.itemIconTintList = null

        setupNavigation()
        setupNotificationPermissions()
        NotificationHelper.createNotificationChannel(this)
        listenForAnnouncements()

        // --- OTURUM KONTROLÜ VE BAŞLANGIÇ EKRANI ---
        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                FavoritesManager.loadUserFavorites {
                    // Giriş yapılıysa direkt Mağazalar (Stores) sayfasını aç
                    loadFragment(storesFragment)
                    binding.bottomNavigationView.visibility = View.VISIBLE

                    // Menüde Mağazalar sekmesini seçili yap
                    binding.bottomNavigationView.selectedItemId = R.id.nav_stores

                    FavoritesManager.startRealTimePriceAlerts(this)
                }
            } else {
                // Giriş yoksa Karşılama Ekranı
                loadFragment(welcomeFragment)
            }
        }

        // Bildirim kapatma butonu
        binding.btnCloseNotif.setOnClickListener {
            hideNotification()
            if (activeAnnouncementId.isNotEmpty()) {
                saveDismissedAnnouncement(activeAnnouncementId)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (UserManager.isLoggedIn()) {
            FavoritesManager.startRealTimePriceAlerts(this)
        }
    }

    private fun setupNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> { loadFragment(storesFragment); true } // Mağazalar
                R.id.nav_products -> { loadFragment(productsFragment); true } // Ürünler
                R.id.nav_cart -> { loadFragment(cartFragment); true } // Sepet
                R.id.nav_profile -> { loadFragment(accountFragment); true } // Hesabım
                else -> false
            }
        }
    }

    // Public yaptık ki diğer fragmentlardan (örn. AccountFragment) çağrılabilsin
    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null) // Geri tuşuyla önceki sekmeye dönülebilmesi için
            .commit()
    }

    private fun setupNotificationPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    // --- DUYURU DİNLEME SİSTEMİ (Aynı kalıyor) ---
    private fun listenForAnnouncements() {
        val db = FirebaseFirestore.getInstance()
        db.collection("announcements")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                val doc = snapshots.documents[0]
                val id = doc.id
                val title = doc.getString("title")
                val message = doc.getString("message")
                if (!isAnnouncementDismissed(id)) {
                    activeAnnouncementId = id
                    showNotification(title ?: "Duyuru", message ?: "")
                }
            }
    }

    private fun saveDismissedAnnouncement(id: String) {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("dismissed_announce_id", id)
            apply()
        }
    }

    private fun isAnnouncementDismissed(id: String): Boolean {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val dismissedId = sharedPref.getString("dismissed_announce_id", "")
        return id == dismissedId
    }

    private fun showNotification(title: String, message: String) {
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

    fun showBottomNav() { binding.bottomNavigationView.visibility = View.VISIBLE }
    fun hideBottomNav() { binding.bottomNavigationView.visibility = View.GONE }

    fun updateCartBadge() {
        val count = CartManager.getCartItemCount()
        val badge = binding.bottomNavigationView.getOrCreateBadge(R.id.nav_cart)
        badge.isVisible = count > 0
        badge.number = count
    }

    // Fragmentlardan sekmeyi değiştirmek için bu fonksiyon
    fun switchToTab(tabId: Int) {
        binding.bottomNavigationView.selectedItemId = tabId
    }
}