package com.example.mobiliyum

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private val homeFragment = HomeFragment()
    private val cartFragment = CartFragment()
    private val accountFragment = AccountFragment()
    private val storesFragment = StoresFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNavigationView)

        // Başlangıçta HomeFragment'i (Web Sitesini) yükle
        loadFragment(homeFragment)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(homeFragment)
                    true
                }
                R.id.nav_stores -> {
                    // ARTIK WEB SİTESİNİ DEĞİL, NATIVE LİSTEMİZİ AÇIYORUZ
                    loadFragment(storesFragment)
                    true
                }
                R.id.nav_cart -> {
                    // İşte fark burada! Siteye gitmiyoruz, kendi tasarımımızı açıyoruz.
                    loadFragment(cartFragment)
                    true
                }
                R.id.nav_profile -> {
                    // Giriş ekranını açıyoruz
                    loadFragment(accountFragment)
                    true
                }
                else -> false
            }
        }
    }

    // Fragment değiştirme fonksiyonu
    private fun loadFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        // fragmentContainer içine seçilen fragmenti yerleştir
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()
    }
}