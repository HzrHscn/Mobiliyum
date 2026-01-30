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
import android.widget.Toast
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
            runOnUiThread {
                try {
                    if (isLoggedIn) {
                        android.util.Log.d("MainActivity", "✅ Kullanıcı oturum açık")

                        // İlk açılış kontrolü
                        initializeNotificationTracking()

                        FavoritesManager.loadUserFavorites {
                            runOnUiThread {
                                android.util.Log.d("MainActivity", "✅ Favoriler yüklendi")

                                loadFragment(storesFragment, addToBackStack = false)
                                binding.bottomNavigationView.visibility = View.VISIBLE
                                binding.bottomNavigationView.selectedItemId = R.id.nav_stores

                                // Listener'ları başlat
                                android.util.Log.d("MainActivity", "🔔 Bildirim listener'ları başlatılıyor...")
                                startNotificationListeners()
                            }
                        }

                        if (intent.getStringExtra("open_fragment") == "notifications") {
                            loadFragment(notificationsFragment, addToBackStack = false)
                        }
                    } else {
                        android.util.Log.d("MainActivity", "❌ Kullanıcı oturum yok")
                        loadFragment(welcomeFragment, addToBackStack = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ Giriş hatası: ${e.message}", e)
                    e.printStackTrace()
                    // Hata durumunda welcome'a yönlendir
                    loadFragment(welcomeFragment, addToBackStack = false)
                }
            }
        }

        binding.btnCloseNotif.setOnClickListener { hideNotification() }

        // Veri Senkronizasyonu ve Reklam
        DataManager.syncDataSmart(this) { success ->
            if (success) {
                // Reklam config'i yüklendikten SONRA kontrol et
                if (auth.currentUser != null) {
                    checkAndShowAd()
                }
            }
        }

        // Offline banner kontrolü
        NetworkMonitor.addListener { isOnline ->
            runOnUiThread {
                if (isOnline) {
                    binding.tvOfflineBanner?.visibility = View.GONE
                } else {
                    binding.tvOfflineBanner?.visibility = View.VISIBLE
                }
            }
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

    private fun initializeNotificationTracking() {
        val prefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)

        // Eğer hiç ayarlanmamışsa (ilk açılış)
        if (!prefs.contains("last_seen_user_notification")) {
            // Şu anki zamanı kaydet (geçmiş bildirimleri gösterme)
            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong("last_seen_user_notification", now)
                .putLong("last_seen_announcement", now)
                .apply()
            android.util.Log.d("MainActivity", "🆕 İlk açılış - Bildirim takibi başlatıldı")
        }
    }

    // === BİLDİRİM DİNLEYİCİLERİ (OPTİMİZE) ===

    private fun startNotificationListeners() {
        android.util.Log.d("MainActivity", "🚀 startNotificationListeners BAŞLADI")

        // Eski listener'ları temizle
        stopAllListeners()

        val uid = auth.currentUser?.uid
        if (uid == null) {
            android.util.Log.e("MainActivity", "❌ UID yok, listener başlatılamadı")
            return
        }

        android.util.Log.d("MainActivity", "👤 Kullanıcı UID: $uid")

        // 1. Fiyat alarmları
        android.util.Log.d("MainActivity", "💰 Fiyat takibi başlatılıyor...")
        FavoritesManager.startRealTimePriceAlerts(this)

        // 2. Kişisel bildirimler
        android.util.Log.d("MainActivity", "👤 Kişisel bildirimler dinleniyor...")
        listenForUserNotifications()

        // 3. Genel duyurular
        android.util.Log.d("MainActivity", "📢 Genel duyurular dinleniyor...")
        listenForGlobalAnnouncements()

        android.util.Log.d("MainActivity", "✅ TÜM LISTENER'LAR BAŞLATILDI")
    }

    private fun listenForUserNotifications() {
        val uid = auth.currentUser?.uid ?: run {
            android.util.Log.e("MainActivity", "❌ listenForUserNotifications: UID yok!")
            return
        }

        android.util.Log.d("MainActivity", "📝 Kullanıcı bildirim listener'ı kuruluyor: $uid")

        // SharedPreferences'tan son görülen bildirim zamanını al
        val prefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
        val lastSeenTimestamp = prefs.getLong("last_seen_user_notification", 0L)

        android.util.Log.d("MainActivity", "📅 Son görülen bildirim: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSeenTimestamp))}")

        val listener = db.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("MainActivity", "❌ Snapshot hatası: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    android.util.Log.e("MainActivity", "❌ Snapshot null!")
                    return@addSnapshotListener
                }

                android.util.Log.d("MainActivity", "📦 Bildirim snapshot alındı - Toplam: ${snapshots.documents.size}, Değişiklik: ${snapshots.documentChanges.size}")

                var newNotificationCount = 0
                val currentTime = System.currentTimeMillis()

                for (docChange in snapshots.documentChanges) {
                    // Sadece YENİ eklenen bildirimleri kontrol et
                    if (docChange.type == DocumentChange.Type.ADDED) {
                        val item = docChange.document.toObject(NotificationItem::class.java)
                        val notifTimestamp = item.date.time

                        // ⚠️ KRİTİK: Sadece son görülenden SONRA oluşan bildirimleri göster
                        if (notifTimestamp > lastSeenTimestamp) {
                            android.util.Log.d("MainActivity", "🔔 YENİ BİLDİRİM!")
                            android.util.Log.d("MainActivity", "  📌 Başlık: ${item.title}")
                            android.util.Log.d("MainActivity", "  📌 Zaman: ${java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(item.date)}")

                            // Bildirimi gönder
                            NotificationHelper.sendNotification(
                                this,
                                item.title,
                                item.message,
                                item.type,
                                item.relatedId
                            )

                            newNotificationCount++
                        } else {
                            android.util.Log.d("MainActivity", "⏭️ ESKİ BİLDİRİM ATLANDI: ${item.title}")
                        }
                    }
                }

                android.util.Log.d("MainActivity", "✅ Gösterilen yeni bildirim: $newNotificationCount")

                // Son görülme zamanını GÜNCELLE (şu anki zaman)
                if (newNotificationCount > 0) {
                    prefs.edit()
                        .putLong("last_seen_user_notification", currentTime)
                        .apply()
                    android.util.Log.d("MainActivity", "💾 Son görülme zamanı güncellendi")
                }
            }

        activeListeners.add(listener)
        android.util.Log.d("MainActivity", "✅ Kullanıcı bildirim listener'ı eklendi")
    }


    private fun listenForGlobalAnnouncements() {
        android.util.Log.d("MainActivity", "📢 Genel duyuru listener'ı kuruluyor...")

        // SharedPreferences'tan son görülen duyuru zamanını al
        val prefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
        val lastSeenTimestamp = prefs.getLong("last_seen_announcement", 0L)

        android.util.Log.d("MainActivity", "📅 Son görülen duyuru: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSeenTimestamp))}")

        val listener = db.collection("announcements")
            .whereEqualTo("type", "general")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(5) // Son 5 duyuruyu dinle
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("MainActivity", "❌ Duyuru hatası: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) {
                    android.util.Log.d("MainActivity", "📭 Duyuru yok")
                    return@addSnapshotListener
                }

                android.util.Log.d("MainActivity", "📦 Duyuru snapshot - Toplam: ${snapshots.documents.size}, Değişiklik: ${snapshots.documentChanges.size}")

                var newAnnouncementCount = 0
                val currentTime = System.currentTimeMillis()

                for (docChange in snapshots.documentChanges) {
                    // Sadece YENİ eklenen duyuruları kontrol et
                    if (docChange.type == DocumentChange.Type.ADDED) {
                        val doc = docChange.document
                        val announcementDate = doc.getDate("date")
                        val announcementTimestamp = announcementDate?.time ?: 0L

                        // ⚠️ KRİTİK: Sadece son görülenden SONRA oluşan duyuruları göster
                        if (announcementTimestamp > lastSeenTimestamp) {
                            val title = doc.getString("title") ?: "Duyuru"
                            val message = doc.getString("message") ?: ""

                            android.util.Log.d("MainActivity", "📢 YENİ DUYURU!")
                            android.util.Log.d("MainActivity", "  📌 Başlık: $title")

                            NotificationHelper.sendNotification(
                                this,
                                title,
                                message,
                                "general"
                            )

                            newAnnouncementCount++
                        } else {
                            android.util.Log.d("MainActivity", "⏭️ ESKİ DUYURU ATLANDI")
                        }
                    }
                }

                android.util.Log.d("MainActivity", "✅ Gösterilen yeni duyuru: $newAnnouncementCount")

                // Son görülme zamanını GÜNCELLE
                if (newAnnouncementCount > 0) {
                    prefs.edit()
                        .putLong("last_seen_announcement", currentTime)
                        .apply()
                    android.util.Log.d("MainActivity", "💾 Son görülme zamanı güncellendi")
                }
            }

        activeListeners.add(listener)
        android.util.Log.d("MainActivity", "✅ Duyuru listener'ı eklendi")
    }

    private fun stopAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }

    // === REKLAM MANTIGI ===
    private fun checkAndShowAd() {
        val adConfig = DataManager.currentAdConfig
        val now = System.currentTimeMillis()

        // Reklam var mı ve aktif mi?
        if (adConfig == null || !adConfig.isActive || adConfig.imageUrl.isEmpty()) {
            android.util.Log.d("AdSystem", "Reklam yok veya aktif değil")
            return
        }

        // Süre dolmuş mu?
        if (now >= adConfig.endDate) {
            android.util.Log.d("AdSystem", "Reklam süresi dolmuş")
            return
        }

        // HER AÇILIŞTA GÖSTER (Session başına 1 kez)
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastShownSession = prefs.getLong("last_ad_shown_session", 0)

        // 10 saniye içinde tekrar gösterme (hızlı açıp kapama durumu)
        if (now - lastShownSession < 10000) {
            android.util.Log.d("AdSystem", "Reklam 10 saniye içinde zaten gösterildi")
            return
        }

        android.util.Log.d("AdSystem", "Reklam gösteriliyor")
        showAdDialog(adConfig)

        // Bu session için kaydet
        prefs.edit().putLong("last_ad_shown_session", now).apply()
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

                if (targetIdInt == 0) {
                    android.util.Log.e("MainActivity", "❌ Geçersiz mağaza ID")
                    Toast.makeText(this, "Geçersiz mağaza", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                android.util.Log.d("MainActivity", "🏪 Mağazaya yönlendiriliyor: ID=$targetIdInt")

                // Önbellekteki mağazayı bul
                val store = DataManager.cachedStores.find { it.id == targetIdInt }

                // StoreDetailFragment oluştur
                val fragment = StoreDetailFragment()
                val bundle = Bundle()

                // ID her zaman gönder
                bundle.putInt("id", targetIdInt)

                // Eğer cache'de varsa diğer bilgileri de gönder
                if (store != null) {
                    android.util.Log.d("MainActivity", "✅ Mağaza cache'de bulundu: ${store.name}")
                    bundle.putString("name", store.name)
                    bundle.putString("image", store.imageUrl)
                    bundle.putString("location", store.location)
                } else {
                    android.util.Log.d("MainActivity", "⚠️ Mağaza cache'de yok, fragment Firestore'dan çekecek")
                    // Fragment kendi verisini çekecek (loadStoreFromFirestore)
                }

                fragment.arguments = bundle
                loadFragment(fragment)
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
        android.util.Log.d("MainActivity", "Fragment yükleniyor: ${fragment.javaClass.simpleName}")

        val tx = supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)

        if (addToBackStack) {
            tx.addToBackStack(null)
        }

        tx.commit()
    }

    private fun setupNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            android.util.Log.d("MainActivity", "Nav item seçildi: ${item.itemId}")

            when (item.itemId) {
                R.id.nav_stores -> {
                    loadFragment(storesFragment, addToBackStack = false)
                    true // ✅ true dönmeli ki seçim güncellensin
                }
                R.id.nav_products -> {
                    loadFragment(productsFragment, addToBackStack = false)
                    true
                }
                R.id.nav_cart -> {
                    loadFragment(cartFragment, addToBackStack = false)
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(accountFragment, addToBackStack = false)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume çağrıldı")

        if (UserManager.isLoggedIn()) {
            android.util.Log.d("MainActivity", "Kullanıcı giriş yapmış, listener'lar başlatılıyor")
            startNotificationListeners()
        } else {
            android.util.Log.d("MainActivity", "Kullanıcı giriş yapmamış")
        }
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
        android.util.Log.d("MainActivity", "switchToTab çağrıldı: $tabId")

        // Programatik olarak seçim yaparken listener tetiklenir
        binding.bottomNavigationView.selectedItemId = tabId
    }
}