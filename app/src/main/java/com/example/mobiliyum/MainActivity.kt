package com.example.mobiliyum

import android.animation.ObjectAnimator
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

    // Bildirim Bileşenleri
    private lateinit var notificationCard: CardView
    private lateinit var tvNotifTitle: TextView
    private lateinit var tvNotifBody: TextView
    private lateinit var btnCloseNotif: ImageView

    private val homeFragment = HomeFragment()
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
    private val storesFragment = StoresFragment()
    private val welcomeFragment = WelcomeFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNavigationView)

        // Bildirim View'larını Bağla
        notificationCard = findViewById(R.id.notificationCard)
        tvNotifTitle = findViewById(R.id.tvNotifTitle)
        tvNotifBody = findViewById(R.id.tvNotifBody)
        btnCloseNotif = findViewById(R.id.btnCloseNotif)

        bottomNav.visibility = View.GONE

        // Oturum Kontrolü
        UserManager.checkSession { isLoggedIn ->
            if (isLoggedIn) {
                loadFragment(homeFragment)
                bottomNav.visibility = View.VISIBLE
            } else {
                loadFragment(welcomeFragment)
            }
        }

        setupNavigation()
        listenForAnnouncements() // BİLDİRİM DİNLEYİCİSİ

        // Kapatma butonu
        btnCloseNotif.setOnClickListener {
            hideNotification()
        }
    }

    // --- GERÇEK ZAMANLI BİLDİRİM DİNLEYİCİSİ ---
    private fun listenForAnnouncements() {
        val db = FirebaseFirestore.getInstance()

        // Uygulama açıldıktan sonra eklenen YENİ duyuruları dinle
        // (Basitlik için son ekleneni kontrol ediyoruz)
        db.collection("announcements")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                if (snapshots != null && !snapshots.isEmpty) {
                    // Değişiklik türünü kontrol et (Sadece yeni eklenenler için)
                    for (dc in snapshots.documentChanges) {
                        if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val title = dc.document.getString("title")
                            val message = dc.document.getString("message")
                            val timestamp = dc.document.getDate("date")

                            // Sadece son 1 dakika içinde atılmışsa göster (Eskileri tekrar gösterme)
                            val now = java.util.Date()
                            val diff = now.time - (timestamp?.time ?: 0)

                            if (diff < 60000) { // 60 saniye
                                showNotification(title ?: "Duyuru", message ?: "")
                            }
                        }
                    }
                }
            }
    }

    private fun showNotification(title: String, message: String) {
        tvNotifTitle.text = title
        tvNotifBody.text = message

        notificationCard.visibility = View.VISIBLE

        // Yukarıdan aşağı kayma animasyonu
        notificationCard.translationY = -300f
        ObjectAnimator.ofFloat(notificationCard, "translationY", 0f).apply {
            duration = 500
            start()
        }
    }

    private fun hideNotification() {
        // Yukarı kayarak kaybolma
        ObjectAnimator.ofFloat(notificationCard, "translationY", -300f).apply {
            duration = 300
            start()
        }.doOnEnd {
            notificationCard.visibility = View.GONE
        }
    }

    // Animator listener için extension (doOnEnd için core-ktx kütüphanesi gerekir,
    // yoksa basitçe visibility'i animasyon bitiminde değil hemen de kapatabiliriz)
    private fun android.animation.Animator.doOnEnd(action: (android.animation.Animator) -> Unit) {
        this.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action(animation)
            }
        })
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

    fun showBottomNav() { bottomNav.visibility = View.VISIBLE }
    fun hideBottomNav() { bottomNav.visibility = View.GONE }
}