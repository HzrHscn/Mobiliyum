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
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private val auth = FirebaseAuth.getInstance()
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
    // Listener'ları sakla (Memory leak önleme)
    private val activeListeners = ArrayList<ListenerRegistration>()

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

        binding.bottomNavigationView.visibility = View.GONE
        binding.bottomNavigationView.itemIconTintList = null

        setupNavigation()

        // 1. FavoritesManager'ı başlat (Cache sistemi için)
        FavoritesManager.initialize(this)

        // 2. KANAL OLUŞTUR (Geliştirilmiş çoklu kanal)
        NotificationHelper.createNotificationChannels(this)

        // 3. İZİN İSTE (Android 13+)
        askNotificationPermission()

        // 4. GİRİŞ VE VERİ YÜKLEME
        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                FavoritesManager.loadUserFavorites {
                    loadFragment(storesFragment)
                    binding.bottomNavigationView.visibility = View.VISIBLE
                    binding.bottomNavigationView.selectedItemId = R.id.nav_stores

                    // === BİLDİRİM DİNLEYİCİLERİNİ BAŞLAT ===
                    startNotificationListeners()
                }

                // Bildirimden tıklandıysa
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

    // === BİLDİRİM İZNİ ===
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    // === BİLDİRİM DİNLEYİCİLERİ (OPTİMİZE) ===

    private fun startNotificationListeners() {
        // Eski listener'ları temizle
        stopAllListeners()

        // 1. Fiyat alarmları
        FavoritesManager.startRealTimePriceAlerts(this)

        // 2. Kişisel bildirimler (Manager duyurusu vb.)
        listenForUserNotifications()

        // 3. Genel duyurular (THROTTLED - Son görülen kontrolü ile)
        listenForGlobalAnnouncements()
    }

    private fun listenForUserNotifications() {
        val uid = auth.currentUser?.uid ?: return

        val listener = db.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .limit(20) // Son 20 okunmamış bildirim
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                for (doc in snapshots.documentChanges) {
                    if (doc.type == DocumentChange.Type.ADDED) {
                        val item = doc.document.toObject(NotificationItem::class.java)

                        // Bildirim gönder (tip'e göre farklı kanal)
                        NotificationHelper.sendNotification(
                            this,
                            item.title,
                            item.message,
                            item.type,
                            item.relatedId
                        )
                    }
                }
            }

        activeListeners.add(listener)
    }

    private fun listenForGlobalAnnouncements() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastCheckedTimestamp = prefs.getLong("last_announcement_check", 0)

        val listener = db.collection("announcements")
            .whereEqualTo("type", "general")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(5) // Son 5 duyuru
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val timestamp = doc.getDate("date")?.time ?: 0L

                        // Sadece son kontrolden sonraki duyuruları bildir
                        if (timestamp > lastCheckedTimestamp) {
                            val title = doc.getString("title") ?: "Duyuru"
                            val message = doc.getString("message") ?: ""

                            NotificationHelper.sendNotification(
                                this,
                                title,
                                message,
                                "general"
                            )
                        }
                    }
                }

                // Son kontrol zamanını güncelle
                prefs.edit()
                    .putLong("last_announcement_check", System.currentTimeMillis())
                    .apply()
            }

        activeListeners.add(listener)
    }

    private fun stopAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // === REKLAM MANTIGI ===
    private fun checkAndShowAd() {
        val adConfig = DataManager.currentAdConfig
        val now = System.currentTimeMillis()

        if (adConfig != null && adConfig.isActive && adConfig.imageUrl.isNotEmpty()) {
            if (now < adConfig.endDate) {
                // Son gösterim zamanını kontrol et (günde 1 kez göster)
                val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val lastShown = prefs.getLong("last_ad_shown", 0)
                val oneDayMs = 24 * 60 * 60 * 1000L

                if (now - lastShown > oneDayMs) {
                    showAdDialog(adConfig)
                    prefs.edit().putLong("last_ad_shown", now).apply()
                }
            }
        }
    }

    private fun showAdDialog(adConfig: AdConfig) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_popup_ad)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val imgAd = dialog.findViewById<ImageView>(R.id.imgAd)
        val txtTitle = dialog.findViewById<TextView>(R.id.txtAdTitle)
        val btnGo = dialog.findViewById<Button>(R.id.btnGoToStore)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseAd)

        txtTitle.text = adConfig.title

        val params = imgAd.layoutParams
        params.height = if (adConfig.orientation == "VERTICAL") {
            (450 * resources.displayMetrics.density).toInt()
        } else {
            (200 * resources.displayMetrics.density).toInt()
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
                val product = DataManager.cachedProducts.find {
                    it.id.toString() == adConfig.targetProductId
                }
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
                val store = DataManager.cachedStores.find { it.id == targetIdInt }
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

    // === STANDART FONKSİYONLAR ===

    /*fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }*/

    fun loadFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val tx = supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)

        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }

    private fun setupNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> { loadFragment(storesFragment); false } //false yaptım sil
                R.id.nav_products -> { loadFragment(productsFragment); false } //false yaptım sil
                R.id.nav_cart -> { loadFragment(cartFragment); false } //false yaptım sil
                R.id.nav_profile -> { loadFragment(accountFragment); false } //false yaptım sil
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        /*if (UserManager.isLoggedIn()) { tek yerden çalışsın sil
            FavoritesManager.startRealTimePriceAlerts(this)
        }*/
    }

    override fun onPause() {
        super.onPause()
        // Arka plana geçerken listener'ları durdur (batarya tasarrufu)
        FavoritesManager.stopAllListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllListeners()
        FavoritesManager.stopAllListeners()
    }

    // Uygulama içi bildirim kartı
    private fun showNotification(title: String, message: String) {
        if (binding.notificationCard.visibility == View.VISIBLE) return
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

    fun switchToTab(tabId: Int) {
        binding.bottomNavigationView.selectedItemId = tabId
    }
}