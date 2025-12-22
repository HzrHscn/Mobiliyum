package com.example.mobiliyum

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private var activeAnnouncementId: String = ""

    private val storesFragment = StoresFragment()
    private val productsFragment = ProductsFragment()
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
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
        setupNotificationPermissions()
        NotificationHelper.createNotificationChannel(this)
        listenForAnnouncements()

        // --- GİRİŞ KONTROLÜ VE AÇILIŞ ---
        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                FavoritesManager.loadUserFavorites {
                    // Giriş varsa direkt Mağazalar'ı aç
                    loadFragment(storesFragment)
                    binding.bottomNavigationView.visibility = View.VISIBLE
                    binding.bottomNavigationView.selectedItemId = R.id.nav_stores

                    // Fiyat bildirimlerini başlat (Arka planda dinler)
                    FavoritesManager.startRealTimePriceAlerts(this)
                }
            } else {
                loadFragment(welcomeFragment)
            }
        }

        binding.btnCloseNotif.setOnClickListener {
            hideNotification()
            if (activeAnnouncementId.isNotEmpty()) saveDismissedAnnouncement(activeAnnouncementId)
        }

        // --- VERİ SENKRONİZASYONU (USAGE OPTİMİZASYONLU) ---
        DataManager.syncDataSmart(this) { success ->
            // Reklam kontrolü
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (success && currentUser != null) {
                checkAndShowAd()
            }
        }
    }

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

        // Genişlik ayarı: Ekranın %90'ı kadar olsun ama CardView'da max width var zaten.
        // Yine de garanti olsun diye bırakabilirsin veya kaldırabilirsin.
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val imgAd = dialog.findViewById<ImageView>(R.id.imgAd)
        val txtTitle = dialog.findViewById<TextView>(R.id.txtAdTitle)
        val btnGo = dialog.findViewById<Button>(R.id.btnGoToStore)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseAd)

        txtTitle.text = adConfig.title

        // --- ZORUNLU BOYUTLANDIRMA ---
        fun dpToPx(dp: Int): Int {
            return (dp * resources.displayMetrics.density).toInt()
        }

        // LayoutParams alıyoruz
        val params = imgAd.layoutParams

        // Dikey veya Yatay seçimine göre yüksekliği "sert" bir şekilde ayarlıyoruz.
        if (adConfig.orientation == "VERTICAL") {
            // DİKEY REKLAM: Yüksekliği artırıyoruz (4:5 oranına yakın)
            // Genişlik 340dp olduğu için yükseklik 425-450dp civarı idealdir.
            params.height = dpToPx(450)
        } else {
            // YATAY REKLAM: Yüksekliği kısıyoruz (16:9 oranına yakın)
            // Genişlik 340dp olduğu için yükseklik 190-200dp civarı idealdir.
            params.height = dpToPx(200)
        }
        imgAd.layoutParams = params

        // GÖRSEL YÜKLEME (Düzeltildi)
        Glide.with(this)
            .load(adConfig.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgAd)

        // --- YÖNLENDİRME ---
        if (adConfig.type == "PRODUCT" && adConfig.targetProductId.isNotEmpty()) {
            btnGo.text = "Ürüne Git"
            btnGo.visibility = View.VISIBLE
            btnGo.setOnClickListener {
                dialog.dismiss()
                // Cache'den ürünü bul ve gönder
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

    private fun setupNotificationPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun listenForAnnouncements() {
        val db = FirebaseFirestore.getInstance()
        db.collection("announcements")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                val doc = snapshots.documents[0]
                if (!isAnnouncementDismissed(doc.id)) {
                    activeAnnouncementId = doc.id
                    showNotification(doc.getString("title") ?: "Duyuru", doc.getString("message") ?: "")
                }
            }
    }

    private fun saveDismissedAnnouncement(id: String) {
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putString("dismissed_announce_id", id).apply()
    }

    private fun isAnnouncementDismissed(id: String): Boolean {
        return getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).getString("dismissed_announce_id", "") == id
    }

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