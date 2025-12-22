package com.example.mobiliyum

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    // Firebase Referansları (Sınıf seviyesinde tanımlı, hata almazsın)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Fragmentlar
    private val storesFragment = StoresFragment()
    private val productsFragment = ProductsFragment()
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
    private val notificationsFragment = NotificationsFragment()
    private val welcomeFragment = WelcomeFragment()
    val webFragment = HomeFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navbar başlangıçta gizli
        binding.bottomNavigationView.visibility = View.GONE
        binding.bottomNavigationView.itemIconTintList = null

        setupNavigation()

        // 1. KANAL OLUŞTUR (Çok Önemli, Android 8+ için)
        NotificationHelper.createNotificationChannel(this)

        // 2. İZİN İSTE (Android 13+ İçin)
        askNotificationPermission()

        // 3. GİRİŞ VE VERİ YÜKLEME
        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                FavoritesManager.loadUserFavorites {
                    // Giriş varsa direkt Mağazalar'ı aç
                    loadFragment(storesFragment)
                    binding.bottomNavigationView.visibility = View.VISIBLE
                    binding.bottomNavigationView.selectedItemId = R.id.nav_stores

                    // --- BİLDİRİM DİNLEMELERİNİ BAŞLAT ---
                    // 1. Fiyat Alarmları
                    FavoritesManager.startRealTimePriceAlerts(this)
                    // 2. Kişisel Bildirimler (Manager'dan gelen vb.)
                    listenForUserNotifications()
                    // 3. Genel Duyurular (Admin'den gelen) - Artık Popup değil, Bildirim
                    listenForGlobalAnnouncements()
                }

                // Bildirimden tıklandıysa Bildirimler sayfasına git
                if (intent.getStringExtra("open_fragment") == "notifications") {
                    loadFragment(notificationsFragment)
                }
            } else {
                loadFragment(welcomeFragment)
            }
        }

        binding.btnCloseNotif.setOnClickListener { hideNotification() }

        // Veri Senkronizasyonu ve Reklam
        DataManager.syncDataSmart(this) { success ->
            if (success && auth.currentUser != null) checkAndShowAd()
        }
    }

    // --- BİLDİRİM İZNİ (ZORUNLU) ---
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    // --- 1. KİŞİSEL BİLDİRİMLERİ DİNLE (Manager Duyurusu / Fiyat Alarmı) ---
    private fun listenForUserNotifications() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                for (doc in snapshots.documentChanges) {
                    // Sadece YENİ eklenenleri bildir
                    if (doc.type == DocumentChange.Type.ADDED) {
                        val item = doc.document.toObject(NotificationItem::class.java)
                        // Telefona Bildirim At
                        NotificationHelper.sendNotification(this, item.title, item.message)
                    }
                }
            }
    }

    // --- 2. GENEL DUYURULARI DİNLE (Popup Yerine Bildirim) ---
    private fun listenForGlobalAnnouncements() {
        // "announcements" koleksiyonunu dinle (general tipinde olanları)
        db.collection("announcements")
            .whereEqualTo("type", "general")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1) // Sadece en son atılanı takip et
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                // Değişiklik türüne bak (Sadece yeni eklenenler için bildirim at)
                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val title = doc.getString("title") ?: "Duyuru"
                        val message = doc.getString("message") ?: ""
                        val id = doc.id

                        // Daha önce bu bildirimi aldık mı kontrol et (Local)
                        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                        val lastNotifiedId = prefs.getString("last_notified_announce_id", "")

                        if (lastNotifiedId != id) {
                            // Yeni duyuru -> Bildirim At
                            NotificationHelper.sendNotification(this, title, message)

                            // ID'yi kaydet ki tekrar atmasın
                            prefs.edit().putString("last_notified_announce_id", id).apply()
                        }
                    }
                }
            }
    }

    // --- REKLAM MANTIĞI ---
    private fun checkAndShowAd() {
        val adConfig = DataManager.currentAdConfig
        val now = System.currentTimeMillis()

        if (adConfig != null && adConfig.isActive && adConfig.imageUrl.isNotEmpty()) {
            if (now < adConfig.endDate) {
                showAdDialog(adConfig)
            }
        }
    }

    private fun showAdDialog(adConfig: AdConfig) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_popup_ad)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Genişlik: Ekranın %90'ı
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val imgAd = dialog.findViewById<ImageView>(R.id.imgAd)
        val txtTitle = dialog.findViewById<TextView>(R.id.txtAdTitle)
        val btnGo = dialog.findViewById<Button>(R.id.btnGoToStore)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseAd)

        txtTitle.text = adConfig.title

        // XML'de "fitXY" olduğu için kodla müdahale etmeye gerek yok, sadece yükseklik ayarı yapabiliriz
        val params = imgAd.layoutParams

        // Dikey/Yatay moduna göre yükseklik (dpToPx kullanarak)
        if (adConfig.orientation == "VERTICAL") {
            params.height = (450 * resources.displayMetrics.density).toInt()
        } else {
            params.height = (200 * resources.displayMetrics.density).toInt()
        }
        imgAd.layoutParams = params

        Glide.with(this)
            .load(adConfig.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgAd)

        // Yönlendirme
        if (adConfig.type == "PRODUCT" && adConfig.targetProductId.isNotEmpty()) {
            btnGo.text = "Ürüne Git"
            btnGo.visibility = View.VISIBLE
            btnGo.setOnClickListener {
                dialog.dismiss()
                val product = DataManager.cachedProducts?.find { it.id.toString() == adConfig.targetProductId }
                if (product != null) {
                    val fragment = ProductDetailFragment()
                    val bundle = Bundle()
                    bundle.putParcelable("product_data", product)
                    fragment.arguments = bundle
                    loadFragment(fragment)
                } else {
                    switchToTab(R.id.nav_products)
                }
            }
        } else if (adConfig.targetStoreId.isNotEmpty()) {
            btnGo.text = "Mağazaya Git"
            btnGo.visibility = View.VISIBLE
            btnGo.setOnClickListener {
                dialog.dismiss()
                val targetIdInt = adConfig.targetStoreId.toIntOrNull() ?: 0
                val store = DataManager.cachedStores?.find { it.id == targetIdInt }
                if (store != null) {
                    val fragment = StoreDetailFragment()
                    val bundle = Bundle()
                    bundle.putInt("id", store.id)
                    bundle.putString("name", store.name)
                    bundle.putString("image", store.imageUrl)
                    bundle.putString("location", store.location)
                    fragment.arguments = bundle
                    loadFragment(fragment)
                } else {
                    switchToTab(R.id.nav_stores)
                }
            }
        } else {
            btnGo.visibility = View.GONE
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- STANDART FONKSİYONLAR ---

    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> { loadFragment(storesFragment); true }
                R.id.nav_products -> { loadFragment(productsFragment); true }
                R.id.nav_cart -> { loadFragment(cartFragment); true }
                R.id.nav_profile -> { loadFragment(accountFragment); true }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (UserManager.isLoggedIn()) FavoritesManager.startRealTimePriceAlerts(this)
    }

    // Uygulama içi (In-App) bildirim kartı için
    private fun showNotification(title: String, message: String) {
        if (binding.notificationCard.visibility == View.VISIBLE) return
        binding.tvNotifTitle.text = title
        binding.tvNotifBody.text = message
        binding.notificationCard.visibility = View.VISIBLE
        binding.notificationCard.translationY = -300f
        ObjectAnimator.ofFloat(binding.notificationCard, "translationY", 0f).apply { duration = 500; start() }
    }

    private fun hideNotification() {
        ObjectAnimator.ofFloat(binding.notificationCard, "translationY", -300f).apply { duration = 300; start() }
            .addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { binding.notificationCard.visibility = View.GONE }
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

    fun switchToTab(tabId: Int) { binding.bottomNavigationView.selectedItemId = tabId }
}