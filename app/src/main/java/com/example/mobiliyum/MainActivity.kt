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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore

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
        //uploadInitialStores() //İşlem bitti ve veritabanına yüklendi SİLİNEBİLİR.
    }

    // Fragment değiştirme fonksiyonu
    private fun loadFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        // fragmentContainer içine seçilen fragmenti yerleştir
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()
    }


    private fun uploadInitialStores() {
        val db = FirebaseFirestore.getInstance()
        val storesCollection = db.collection("stores")

        val stores = ArrayList<Store>()


        stores.add(Store(1, "Active Design", "Mobilya Takımları", "https://framerusercontent.com/images/I3w11TSAUWAQ6IBUDyoScCqqzg.png?scale-down-to=1024&width=4500&height=4501", "A Etap 1. Kat No: 28"))
        stores.add(Store(2, "Adessun", "Mobilya Takımları", "https://framerusercontent.com/images/mXQtgqPJpqNosqToeojAstGfRG8.jpg?scale-down-to=512&width=1528&height=1055", "B Etap 2. Kat No: 35"))
        stores.add(Store(3, "Akmoy Sofa", "Koltuk & Yemek Takımları", "https://framerusercontent.com/images/EWyKZ8QxvsWvS6ifXl5kw9v7cM.png?width=239&height=235", "B Etap 1. Kat No: 02-03"))
        stores.add(Store(4, "Aksay Koltuk", "Modern Koltuk Takımları", "https://framerusercontent.com/images/L5EJoiOFuimgtnXQrehYKsK3XM8.jpg?scale-down-to=512&width=8534&height=8534", "B Etap 2. Kat No: 20"))
        stores.add(Store(5, "Akyol Life", "Yatak Odası", "https://framerusercontent.com/images/mpwlimJbwQQvgSJsWNvwSP0GRs.png?width=368&height=126", "A Etap 2. Kat No: 23"))
        stores.add(Store(6, "Alinset", "Modern Mobilya Takımları", "https://framerusercontent.com/images/uUFWwzAlgAWnU6Sy9tj5t1HqoeM.png?width=330&height=160", "B Etap 1. Kat No: 29"))
        stores.add(Store(7, "Almila", "Bebek & Genç", "https://framerusercontent.com/images/hDMt9hBFFAvFsYXC3WOFrunas.png?scale-down-to=512&width=1000&height=208", "A Etap Giriş Kat No: 44"))
        stores.add(Store(8, "Alpino", "Mobilya Takımları", "https://framerusercontent.com/images/WOGmPTgvjXFdYoVyGLDbIPs2ks.png?width=500&height=156", "B Etap Giriş Kat No: 23"))
        stores.add(Store(9, "Anje", "Home Concept", "https://framerusercontent.com/images/uN9dRSUV31jV1nQPovg3vGRiS0.png?width=225&height=225", "A Etap 2. Kat No: 11"))
        stores.add(Store(10, "AR Mobilya", "Mobilya Takımları", "https://framerusercontent.com/images/IGtXZYUx7kt8QGHIA3B0xWLLaA.png?width=355&height=337", "B Etap 2. Kat No: 27"))
        stores.add(Store(11, "Aracıkan", "Luxury Mobilya Takımları", "https://framerusercontent.com/images/zbbapThyDNcJYIWwcmNJEukUe7g.png?scale-down-to=512&width=1000&height=1000", "A Etap Giriş Kat No: 41"))
        stores.add(Store(12, "Area", "Mobilya Takımları", "https://framerusercontent.com/images/nBskr1b8jwUAM2XvLuj0S9mrP7I.jpg?scale-down-to=512&width=1350&height=1080", "A Etap Giriş Kat No: 1"))
        stores.add(Store(13, "Armesse", "Mobilya Takımları", "https://framerusercontent.com/images/jwANJQcAn0l3uBJKTQBHV8Lgbzk.png?width=220&height=40", "B Etap 1. Kat No: 11-12"))
        stores.add(Store(14, "Artelux", "Mobilya Takımları", "https://framerusercontent.com/images/gGt1pErHKB2SoF1KTq5uymdGk4.png?width=480&height=108", "A Etap 2. Kat No: 39"))
        stores.add(Store(15, "Artev", "Koltuk Takımları", "https://framerusercontent.com/images/azWBIMcLb5pRc7u725IedRjx04.png?scale-down-to=512&width=772&height=194", "B Etap Giriş Kat No: 19"))
        stores.add(Store(16, "Arwen", "Koltuk Takımları", "https://framerusercontent.com/images/MXW0x9PWWsI4xLwh46lvXgE.png?width=215&height=60", "A Etap Giriş Kat No: 6"))
        stores.add(Store(17, "As Oda", "Mobilya Takımları", "https://framerusercontent.com/images/sAjwoMMsXhcXyGeng1cb1fZ9Y.jpg?scale-down-to=512&width=2221&height=746", "A Etap Giriş Kat No: 28"))
        stores.add(Store(18, "Aslan Şaşmaz", "Modern Koltuk Takımları", "https://framerusercontent.com/images/BJ8V39zqucROmoSNCWmiV4z7LQ.png?width=294&height=151", "B Etap 2. Kat No: 36"))
        stores.add(Store(19, "Asrra Group", "Mobilya Takımları", "https://framerusercontent.com/images/XfMjeQn5v9J3gCV6kYMkKvxIPg.jpg?scale-down-to=512&width=1240&height=1240", "A Etap 2. Kat No: 42"))
        stores.add(Store(20, "Ayhan Home", "Mobilya Takımları", "https://framerusercontent.com/images/xhV2xqh6HD0ZF9IUDqIuQka3aQU.png?scale-down-to=512&width=2036&height=1438", "A Etap Giriş Kat No: 54"))
        stores.add(Store(21, "Azemo", "Masa & Sandalye", "https://framerusercontent.com/images/Oom089DbepByN63nBdiGdyys.png?width=159&height=59", "B Etap 2. Kat No: 04-05"))
        stores.add(Store(22, "Balalaica", "Koltuk Takımları", "https://framerusercontent.com/images/DUE3k1gbWDAq7JLoYmOG0OmhkU.png?width=225&height=225", "A Etap 2. Kat No: 36"))
        stores.add(Store(23, "Balhome Mobilya", "Mobilya Takımları", "https://framerusercontent.com/images/1WIyUusm0LLbKI63vjlaW27mc.png?width=180&height=99", "B Etap Giriş Kat No: 26"))
        stores.add(Store(24, "Bedingart Yatak", "Yatak Odası", "https://framerusercontent.com/images/Mfwen94rgrrxnwlb8XerqJNcAo.png?width=282&height=77", "B Etap Giriş Kat No: 13"))
        stores.add(Store(25, "Bellacaza", "Mobilya Takımları", "https://framerusercontent.com/images/0oFaftNIwzWllb1tFgXpdNbQk.png?scale-down-to=1024&width=4500&height=4501","B Etap Giriş Kat No: 10"))
        stores.add(Store(26,"Benimodam","Bebek & Genç","https://framerusercontent.com/images/QLeHhUm3XrbTdaDvGZaFazVhGEw.png?width=300&height=300","A Etap Giriş Kat No: 31"))
        stores.add(Store(27,"Blesk","Mobilya Takımları","https://framerusercontent.com/images/8M8p6QoshtF0CvHwfB5kmqLM7lI.png?width=328&height=140","A Etap Giriş Kat No: 17"))
        stores.add(Store(28,"Bomonti Life","Mobilya Takımları","https://framerusercontent.com/images/QnI6zY24M50yuXAPmCHZQmyDZ0.jpg?scale-down-to=512&width=860&height=647","A Etap Giriş Kat No: 46"))
        stores.add(Store(29,"Bozok","Mobilya Takımları","https://framerusercontent.com/images/ooTVW4pICrqRj8AauFRYMvnbo9M.png?width=130&height=81","A Etap 2. Kat No: 06"))
        stores.add(Store(30,"Boztaş Premium","Mobilya Takımları","https://framerusercontent.com/images/rhOMROhLXh4hC1mW66rP4drBHbs.png?scale-down-to=512&width=1654&height=1654","A Etap Giriş Kat No: 18"))
        stores.add(Store(31,"Brezza","Mobilya Takımları","https://framerusercontent.com/images/OKGupCx1dP6jZLPzo3svIIbrXIo.png?scale-down-to=512&width=4500&height=4500","B Etap 2. Kat No: 23"))
        stores.add(Store(32,"By Stilist","Mobilya Takımları","https://framerusercontent.com/images/4JYgaJsvieDxIGcrkntIQGCrNBA.jpg?width=300&height=212","B Etap 2. Kat No: 32"))
        stores.add(Store(33,"Bürolife","Mobilya Takımları","https://framerusercontent.com/images/iVCfRa5PUvCZckmfUza1B3RiTkU.jpeg?width=324&height=155","A Etap 2. Kat No: 50"))
        stores.add(Store(34,"Caliform","Mobilya Takımları","https://framerusercontent.com/images/Sr6eqIP85bsv9h8VmTxc4eBh6YU.png?width=97&height=19","A Etap Giriş Kat No: 40"))
        stores.add(Store(35,"Calvin","Mobilya Takımları","https://framerusercontent.com/images/TeShSSqfCokB6HJyzOh7H7Te1w4.jpeg?scale-down-to=512&width=1294&height=853","A Etap 2. Kat No: 12"))
        stores.add(Store(36,"Canape","Mobilya Takımları","https://framerusercontent.com/images/NUPMf3Ctk3Sz8XafXXN38yXqdXM.jpg?scale-down-to=512&width=1350&height=1080","B Etap 2. Kat No: 15"))
        stores.add(Store(37,"Candess Mobilya","Mobilya Takımları","https://framerusercontent.com/images/RSvX3zBO1Zfh2SgH8DK86KA0g.png?width=300&height=300","B Etap Giriş Kat No: 25"))
        stores.add(Store(38,"Casa Brava","Mobilya Takımları","https://framerusercontent.com/images/IqGqzlJcDIPmNvdzvm4nw20ySCc.png?width=248&height=61","A Etap 2. Kat No: 02"))
        stores.add(Store(39,"Casa Mia","Mobilya Takımları","https://framerusercontent.com/images/kwsMo06PIU1WwkcbT0gUNexFcv4.png?width=272&height=66","B Etap 2. Kat No: 37"))
        stores.add(Store(40,"Cascade","Mobilya Takımları","https://framerusercontent.com/images/VVKzzUSIUd8MAwICpcbupuGFCSU.png?width=500&height=87","A Etap Giriş Kat No: 35"))
        stores.add(Store(41,"Casetto","Mobilya Takımları","https://framerusercontent.com/images/vmOzESzx1AsCbgSGZLdTtY8Kds.png?scale-down-to=512&width=2362&height=2362","B Etap Giriş Kat No: 18"))
        stores.add(Store(42,"Çağlayan","Mobilya Takımları","https://framerusercontent.com/images/dzs4rCLGheScMxuJCBkJ3kJXtw.jpg?width=184&height=195","A Etap Giriş Kat No: 36"))
        stores.add(Store(43,"Çelmo Mobilya","Mobilya Takımları","https://framerusercontent.com/images/gL3aQqjphGSVb5QssEqHSCUDIb4.png?width=300&height=300","A Etap 2. Kat No: 29"))
        stores.add(Store(44,"Cemal Can","Mobilya Takımları","https://framerusercontent.com/images/fd3gdRFAmi5QYto1RMr8JHjqgKA.jpg?width=300&height=300","A Etap 2. Kat No: 20"))
        stores.add(Store(45,"Çilek","Mobilya Takımları","https://framerusercontent.com/images/xwYl5Ts0pDUGKM70kNdkhrMzEQI.png?width=300&height=300","A Etap Giriş Kat No: 12"))
        stores.add(Store(46,"Coco Luxury","Mobilya Takımları","https://framerusercontent.com/images/tF51lRekb3GDqxscErSi6fBNw.jpg?width=295&height=176","A Etap Giriş Kat No: 47-50"))
        stores.add(Store(47,"Curizon","Mobilya Takımları","https://framerusercontent.com/images/o10l5jhq5xWUqKaj0BxFhwQO4.png?width=300&height=300","A Etap Giriş Kat No: 48"))
        stores.add(Store(48,"D.G.N Sofa","Mobilya Takımları","https://framerusercontent.com/images/oPWe8D4mZSdkD7r1Rrf984nYk6M.png?width=250&height=85","A Etap 2. Kat No: 25"))
        stores.add(Store(49,"Davenza","Mobilya Takımları","https://framerusercontent.com/images/eLnT4lp7BqQ8qj0EJR2HGxDk.jpg?scale-down-to=512&width=726&height=726","B Etap Giriş Kat No: 28"))
        stores.add(Store(50,"Ddlogi","Mobilya Takımları","https://framerusercontent.com/images/TTITEzzMQwFg9zkxDhDVTuLIDag.png?scale-down-to=512&width=522&height=154","A Etap 2. Kat No: 43"))
        stores.add(Store(51,"Decohill","Mobilya Takımları","https://framerusercontent.com/images/VEr7fGwiS9fD44dlvSTxjB70.png?width=225&height=225","B Etap Giriş Kat No: 8-9"))
        stores.add(Store(52,"Derler Design","Mobilya Takımları","https://framerusercontent.com/images/wKpo0N1JZPvoWOCTedk0CmZPU.jpg?width=320&height=320","B Etap 2. Kat No: 14"))
        stores.add(Store(53,"Didvani Home Style","Mobilya Takımları","https://framerusercontent.com/images/e4IOEW23r2IJ5xcdcX9tPPbLVQ.jpg?scale-down-to=512&width=684&height=644","B Etap Giriş Kat No: 34"))
        stores.add(Store(54,"Doconcept","Mobilya Takımları","https://framerusercontent.com/images/Kngfl4GABkttQN1BjcDwwuDfwg.png?width=225&height=225","B Etap 2. Kat No: 16"))
        stores.add(Store(55,"Ecole Living","Mobilya Takımları","https://framerusercontent.com/images/tTC1II2E8JHcDBAJoX5fEiHW14.png?width=309&height=300","A Etap 2. Kat No: 36"))
        stores.add(Store(56,"Ekhlas Company","Mobilya Takımları","https://framerusercontent.com/images/MjuTOYhQo6T1OArmK8gXFqHYs.png?scale-down-to=512&width=1000&height=1000","A Etap 2. Kat No: 45"))
        stores.add(Store(57,"El Massa","Mobilya Takımları","https://framerusercontent.com/images/u5TO9EvnQiyMk9sgOanEOuruo.jpg?scale-down-to=512&width=3508&height=2480","A Etap 2. Kat No: 40"))
        stores.add(Store(58,"Ela Koltuk","Koltuk Takımları","https://framerusercontent.com/images/abuD8pWdUHFNcmkjBNKueAy9c0.jpg?width=512&height=321","B Etap 2. Kat No: 38"))
        stores.add(Store(59,"Elegance Furniture","Mobilya Takımları","https://framerusercontent.com/images/Ak6nTcHAvg73X4TbPIIeNY5E.png?scale-down-to=512&width=1000&height=1000","A Etap 2. Kat No: 41"))
        stores.add(Store(60,"Elegancia","Mobilya Takımları","https://framerusercontent.com/images/EBOQStfZEsyD32xem7NwxloTUs.png?width=173&height=38","A Etap Giriş Kat No: 7"))
        stores.add(Store(61,"Enmobi","Mobilya Takımları","https://framerusercontent.com/images/6kpJ9Ko8nUzwnsGyXsNKgCn7I.png?scale-down-to=512&width=695&height=372","A Etap Giriş Kat No: 07"))
        stores.add(Store(62,"Eral Mobilya","Mobilya Takımları","https://framerusercontent.com/images/CbSRDdls01HyUlSc0SgRP4Uzojw.png?width=300&height=300","A Etap 2. Kat No: 32"))
        stores.add(Store(63,"Erike Home","Mobilya Takımları","https://framerusercontent.com/images/yEFl8A1u9pZtyLWVJhOVh0vUqQ.jpg?scale-down-to=512&width=1350&height=1080","B Etap 2. Kat No: 24"))
        stores.add(Store(64,"Eta Concept","Mobilya Takımları","https://framerusercontent.com/images/x71TUHn8KYzpa7zMaiEKTjQbdak.png?width=289&height=50","B Etap 2. Kat No: 23"))
        stores.add(Store(65,"Ewa Home","Mobilya Takımları","https://framerusercontent.com/images/kw1gxcWZCZ1tH5PlV887mV0xg54.png?width=300&height=300","A Etap Giriş Kat No: 38"))
        stores.add(Store(66,"Family Group","Mobilya Takımları","https://framerusercontent.com/images/lQbiwpb4zCxn6wvCAVMPjlZ6M.jpg?scale-down-to=512&width=2916&height=1129","A Etap Giriş Kat No: 21"))
        stores.add(Store(67,"Gala Mobilya","Mobilya Takımları","https://framerusercontent.com/images/kwLuWXmQ0aWkPeuOEHpnqnqvXo.jpg?width=300&height=300","A Etap Giriş Kat No: 13"))
        stores.add(Store(68,"Galata Home","Mobilya Takımları","https://framerusercontent.com/images/4vwSJR2It2FD8htIaIBu0duKRG4.png?scale-down-to=512&width=2362&height=2362","A Etap 2. Kat No: 3"))
        stores.add(Store(69,"Gencecix","Bebek & Genç","https://framerusercontent.com/images/F4QrWm5HakjoUL8goUxUcNnFELE.png?width=290&height=300","A Etap 2. Kat No: 33"))
        stores.add(Store(70,"Greensons","Mobilya Takımları","https://framerusercontent.com/images/ahY4ArYrUWSztOeKPchZHKfj9XM.png?width=225&height=225","A Etap 2. Kat No: 51"))
        stores.add(Store(71,"Gül Wood","Mobilya Takımları","https://framerusercontent.com/images/dzcpKtYnXhfJLCDh3rXuW8Ulckk.png?width=240&height=123","B Etap 2. Kat No: 02-03"))
        stores.add(Store(72,"Halıca","Halı","https://framerusercontent.com/images/f9LS9rjdIC8TEYWT8MEzQMboT8.webp?scale-down-to=512&width=1860&height=355","A Etap 2. Kat No: 35"))
        stores.add(Store(73,"Heritage","Mobilya Takımları","https://framerusercontent.com/images/Ht8eZ6CigLJ2ttkBHMmN3VSgGGY.png?scale-down-to=512&width=2125&height=289","A Etap Giriş Kat No: 51"))
        stores.add(Store(74,"Hilsa","Mobilya Takımları","https://framerusercontent.com/images/tI05UlMfgousIGVikZEaGV04GE.webp?width=114&height=104","A Etap 2. Kat No: 26"))
        stores.add(Store(75,"Holywood Deco","Mobilya Takımları","https://framerusercontent.com/images/IpkVfGIK9iRaZnaCulS98PaE.jpeg?scale-down-to=512&width=1024&height=475","B Etap 2. Kat No: 26"))
        stores.add(Store(76,"Hunters Luxury","Mobilya Takımları","https://framerusercontent.com/images/KGhVuAIkKotKoPzgdLgwK2HWgQ.jpg?scale-down-to=512&width=3838&height=2480","B Etap Giriş Kat No: 39"))
        stores.add(Store(77,"İnarte Mobilya","Mobilya Takımları","https://framerusercontent.com/images/cx9SyORkRvueeb2o0rnJk1w5PE.png?scale-down-to=512&width=2501&height=872","A Etap 2. Kat No: 12"))
        stores.add(Store(78,"Inci Design Koltuk","Koltuk Takımları","https://framerusercontent.com/images/hvoXf9a5oWiTBLZlOV6CMlRg4.jpg?scale-down-to=512&width=771&height=771","A Etap Giriş Kat No: 31"))
        stores.add(Store(79,"Indivani Yatak","Yatak Odası","https://framerusercontent.com/images/CLidGAIvegwESoRP66sBYNSDzw.jpg?width=300&height=300","A Etap Giriş Kat No: 52"))
        stores.add(Store(80,"İnegöl Sofa","Mobilya Takımları","https://framerusercontent.com/images/i7r6UmpjFofwJuouqDKyUm4nVI.png?width=243&height=76","B Etap 2. Kat No: 25"))
        stores.add(Store(81,"Infavori Mobilya","Mobilya Takımları","https://framerusercontent.com/images/NHzUgW5aVD3lzhT23n9xKRrhKI.jpeg?scale-down-to=512&width=1080&height=1080","A Etap Giriş Kat No: 11"))
        stores.add(Store(82,"Insleep","Yatak Odası","https://framerusercontent.com/images/duXtVbhfXAWyeiB4GDJPEzrly8A.jpg?width=297&height=121","B Etap 2. Kat No: 43"))
        stores.add(Store(83,"İssalon Mobilya","Mobilya Takımları","https://framerusercontent.com/images/I38Bwtd9x73u6I4ifj2yRV2kCI.png?width=150&height=147","A Etap Giriş Kat No: 26"))
        stores.add(Store(84,"İzz","Mobilya Takımları","https://framerusercontent.com/images/WA8IGq9i0frxMvD5dTUg0pfRE.webp?width=250&height=99","A Etap 2. Kat No: 18"))
        stores.add(Store(85,"Karyolachi","Yatak Odası","https://framerusercontent.com/images/1tI6oCSd2ZQuUQD49w2LHVHo.png?width=259&height=72","A Etap 2. Kat No: 34"))
        stores.add(Store(86,"Keyf-i Dizayn","Mobilya Takımları","https://framerusercontent.com/images/sIWwc5W2ahfKYshyvXSn1Cotc.png?width=320&height=215","A Etap 2. Kat No: 33"))
        stores.add(Store(87,"Kirpi Kids","Bebek & Genç","https://framerusercontent.com/images/7cqHatCU02LLzuFCowscx0Zo.jpg?width=300&height=300","A Etap Giriş Kat No: 53"))
        stores.add(Store(88,"Kosova Koltuk","Mobilya Takımları","https://framerusercontent.com/images/jACSWjwSKwGAe2ZJc7RZ8Y7BfE.png?width=300&height=101","B Etap 2. Kat No: 11-12"))
        stores.add(Store(89,"Kristal","Mobilya Takımları","https://framerusercontent.com/images/DxChf3Rw4emSIN4fYIuP9oNwI6U.png?width=290&height=100","B Etap Giriş Kat No: 42"))
        stores.add(Store(90,"Kuka Home","Mobilya Takımları","https://framerusercontent.com/images/b9zkk4b6Br8H6DA3hyJQesvxZE.png?width=350&height=185","A Etap Giriş Kat No: 24"))
        stores.add(Store(91,"Kupa","Bebek & Genç","https://framerusercontent.com/images/SZkiqz7KOo4PXibpuCtTPfi6Z3s.webp?width=500&height=142","A Etap 2. Kat No: 13"))
        stores.add(Store(92,"L'ayla","Mobilya Takımları","https://framerusercontent.com/images/hIFVh7z9TPVoUe7pCK2xV8lQU3Y.png?width=176&height=67","B Etap Giriş Kat No: 21"))
        stores.add(Store(93,"Larex Design","Mobilya Takımları","https://framerusercontent.com/images/l8IvtNQJpvGPviBxpk4gDWx2g.png?width=311&height=257","B Etap 2. Kat No: 33"))
        stores.add(Store(94,"Lavita Sofa Design","Koltuk Takımları","https://framerusercontent.com/images/WLxnHYhp0JuVP2AFdYDcbfUGo.jpg?scale-down-to=512&width=1155&height=773","A Etap Giriş Kat No: 23"))
        stores.add(Store(95,"Lenova Mobilya","Mobilya Takımları","https://framerusercontent.com/images/i2hwTZbIcAAj8tqm8vfa19rHwo.jpg?scale-down-to=512&width=960&height=960","A Etap Giriş Kat No: 34 & B Etap Giriş Kat No: 41"))
        stores.add(Store(96,"Lerisa Mobilya","Mobilya Takımları","https://framerusercontent.com/images/DSlxTUNgR3iKBUMwldqFwIqk.png?width=200&height=76","B Etap 2. Kat No: 10"))
        stores.add(Store(97,"Libercan Mobilya","Mobilya Takımları","https://framerusercontent.com/images/INmD2u9E1S7SZZOutZUHagu6cg.jpg?width=212&height=69","B Etap 2. Kat No: 18"))
        stores.add(Store(98,"Lifem Mobilya","Mobilya Takımları","https://framerusercontent.com/images/4AHrnDrt7crY2UW6rkSAkqfeKlE.png?width=399&height=118","B Etap Giriş Kat No: 27"))
        stores.add(Store(99,"Litani Mobilya","Mobilya Takımları","https://framerusercontent.com/images/89mcgtVHNNRP6RgjHYOdXFABbwA.png?width=360&height=330","A Etap 2. Kat No: 20"))
        stores.add(Store(100,"Livello Mobilya","Mobilya Takımları","https://framerusercontent.com/images/SP7lpXNluse5XMUWNn0YOQ2hO4.png?scale-down-to=512&width=1086&height=303","B Etap Giriş Kat No: 18"))
        stores.add(Store(101,"Liwgar","Mobilya Takımları","https://framerusercontent.com/images/83WSM09Ner7aoZKlPKTWKJwc6w.png?width=180&height=62","B Etap Giriş Kat No: 31"))
        stores.add(Store(102,"Liz Aksesuar","Mobilya Takımları","https://framerusercontent.com/images/oIilDgZtvSAnCBblfmwsBYr7864.png?width=200&height=200","B Etap 2. Kat No: 28"))
        stores.add(Store(103,"Lorenzi Furniture","Mobilya Takımları","https://framerusercontent.com/images/Xi6BaljLvkQWWgMzCtPGET3QME.jpg?scale-down-to=512&width=1080&height=1080","B Etap Giriş Kat No: 32"))
        stores.add(Store(104,"Luca Ahşap","Mobilya Takımları","https://framerusercontent.com/images/gMxw0VLLdDpzQLeyoQTufi2vpo.jpg?scale-down-to=512&width=738&height=717","B Etap 2. Kat No: 21"))
        stores.add(Store(105,"Lucetta Concept","Mobilya Takımları","https://framerusercontent.com/images/gKLnvthxcyY6VqzSNsZ8XAAzAU.jpg?width=317&height=159","A Etap 2. Kat No: 4"))
        stores.add(Store(106,"Luxdekor","Mobilya Takımları","https://framerusercontent.com/images/bkWEBo5rD9R3nq2K5WL4NmGs87M.webp?scale-down-to=512&width=3000&height=1276","B Etap Giriş Kat No: 16"))
        stores.add(Store(107,"Luxe Life","Mobilya Takımları","https://framerusercontent.com/images/G015EJCqu8pd1QLDSCPITnc14Jc.png?scale-down-to=512&width=1601&height=782","A Etap 2. Kat No: 5"))
        stores.add(Store(108,"Merli Salon","Mobilya Takımları","https://framerusercontent.com/images/nlpQnYK5HzdNpD7JJpOfdzfRiw.webp?scale-down-to=512&width=1024&height=273","A Etap 2. Kat No: 14"))
        stores.add(Store(109,"Mert Mobilya","Mobilya Takımları","https://framerusercontent.com/images/jWWgcBcimUwtb9l3s85iqpRcJHw.jpeg?width=584&height=585","B Etap Giriş Kat No: 4-5"))
        stores.add(Store(110,"Mobella","Mobilya Takımları","https://framerusercontent.com/images/XwpvUKkmRR0M4Y80XvBLN8q4Ek.png?scale-down-to=512&width=1446&height=220","A Etap 2. Kat No: 24"))
        stores.add(Store(111,"Mobiant Furniture","Mobilya Takımları","https://framerusercontent.com/images/Oda5SnmFKHINfCSl7iAcLI5Bk.png?scale-down-to=512&width=1024&height=253","A Etap 2. Kat No: 16"))
        stores.add(Store(112,"Mobimexx","Mobilya Takımları","https://framerusercontent.com/images/2Jdc4xKhlyYuaNCUwEOnjvpk.jpg?width=300&height=300","B Etap Giriş Kat No: 10"))
        stores.add(Store(113,"Mobinya","Mobilya Takımları","https://framerusercontent.com/images/I4BYniT5TR9jYKNV6CW5iHUlM.webp?width=350&height=97","B Etap 2. Kat No: 22"))
        stores.add(Store(114,"Mobiwoods","Mobilya Takımları","https://framerusercontent.com/images/TB3NHpflYSjVbSJG1y3ejrDs.png?scale-down-to=512&width=800&height=800","B Etap 2. Kat No: 6-7"))
        stores.add(Store(115,"Modamassa","Mobilya Takımları","https://framerusercontent.com/images/T4QsmLnwUwfoM589pAvl0rj4.webp?width=274&height=46","B Etap 2. Kat No: 13"))
        stores.add(Store(116,"Modis Interiors","Mobilya Takımları","https://framerusercontent.com/images/aZuARxRVjRTs5L7tXxSJOcB7AAY.png?scale-down-to=512&width=1960&height=690","B Etap Giriş Kat No: 6-7"))
        stores.add(Store(117,"Montro Mobilya","Mobilya Takımları","https://framerusercontent.com/images/sG7HKF6TYuZ7eTlZb7D8BBVXeNs.jpg?width=150&height=150","A Etap 2. Kat No: 22"))
        stores.add(Store(118,"Mr. Boss Mobilya","Mobilya Takımları","https://framerusercontent.com/images/anUy6MBz4n1scXXmceR4hfVWkxI.jpg?width=300&height=300","A Etap Giriş Kat No: 6"))
        stores.add(Store(119,"Mr. Classic","Mobilya Takımları","https://framerusercontent.com/images/EFoe3nEIvk1VJP70y63iyzAZg.jpg?width=150&height=93","A Etap 2. Kat No: 37"))
        stores.add(Store(120,"Mudimo Home","Mobilya Takımları","https://framerusercontent.com/images/7E0tJCB5LCk564AO6lE4smSPI.jpeg?scale-down-to=512&width=1080&height=1080","B Etap Giriş Kat No: 38"))
        stores.add(Store(121,"Muwa","Mobilya Takımları","https://framerusercontent.com/images/OO6xVvm8ryLNfgMRn7wceA0zxBo.jpg?scale-down-to=512&width=828&height=828","A Etap 2. Kat No: 10"))
        stores.add(Store(122,"Muzaffer Mobilya","Mobilya Takımları","https://framerusercontent.com/images/eWAUR6HDrdy3xuz14AH3Hc5wnkE.jpg?scale-down-to=512&width=1638&height=1638","A Etap Giriş Kat No: 10"))
        stores.add(Store(123,"Natty Mobilya","Bebek & Genç","https://framerusercontent.com/images/r9jc1FEbsQUwl7OdKdGD9uTLM.png?width=225&height=225","B Etap 2. Kat No: 30"))
        stores.add(Store(124,"NC Lion","Mobilya Takımları","https://framerusercontent.com/images/PcRzFpijXIiZmCzL6vE9iheicE.png?width=500&height=605","B Etap 2. Kat No: 41"))
        stores.add(Store(125,"Need Mobilya","Mobilya Takımları","https://framerusercontent.com/images/vpgAWSB2PCcLmDKcHEKiAplWYEE.jpg?scale-down-to=512&width=1638&height=1638","A Etap Giriş Kat No: 3"))
        stores.add(Store(126,"Nell Mobilya","Mobilya Takımları","https://framerusercontent.com/images/wETpWDM7Ny86tlE4UnlEBPfFjdw.jpg?scale-down-to=512&width=1504&height=957","A Etap Giriş Kat No: 19"))
        stores.add(Store(127,"Nero Luxury","Mobilya Takımları","https://framerusercontent.com/images/bE5QizOxIpBFc2Hr1LgCETz32c.jpg?scale-down-to=512&width=4500&height=4500","A Etap Giriş Kat No: 22"))
        stores.add(Store(128,"Nest Collection","Mobilya Takımları","https://framerusercontent.com/images/rIgbZZvZxZe7Ux0yich8sI2ffY8.png?scale-down-to=512&width=800&height=800","A Etap Giriş Kat No: 02"))
        stores.add(Store(129,"New Home","Mobilya Takımları","https://framerusercontent.com/images/L8O0Edd29nISM6QnoFrOA3kE1OQ.webp?scale-down-to=512&width=1024&height=253","B Etap 2. Kat No: 34"))
        stores.add(Store(130,"Newland Mobilya","Mobilya Takımları","https://framerusercontent.com/images/8p2JP791pc6FP0XPyV0y9H4H4Q.png?width=300&height=300","B Etap Giriş Kat No: 22"))
        stores.add(Store(131,"Newmood","Mobilya Takımları","https://framerusercontent.com/images/4AqCwU6QSsaurNFouxIL3NpeZo.png?width=260&height=70","B Etap Giriş Kat No: 37"))
        stores.add(Store(132,"Nills Mobilya","Mobilya Takımları","https://framerusercontent.com/images/sqb4SQuImDK9gHeogTU6kYwT74.png?scale-down-to=512&width=678&height=300","A Etap Giriş Kat No: 09"))
        stores.add(Store(133,"Ninety Chairs","Mobilya Takımları","https://framerusercontent.com/images/LoyZjH8QgukWLSkXShu6TORBxg.png?width=314&height=48","B Etap 2. Kat No: 14"))
        stores.add(Store(134,"Nirvana Premium","Mobilya Takımları","https://framerusercontent.com/images/GFMDTbQDypg6fRYys48UMJQgFC0.png?scale-down-to=512&width=1080&height=483","B Etap Giriş Kat No: 14"))
        stores.add(Store(135,"Noga Mobilya","Mobilya Takımları","https://framerusercontent.com/images/3gn8W541HVmeasjqvw1VhZOjCYA.png?width=330&height=80","A Etap Giriş Kat No: 25"))
        stores.add(Store(136,"Odam Mobilya","Mobilya Takımları","https://framerusercontent.com/images/TgJLdJxnpVDe5Xt5qknifkolA6w.png?width=300&height=300","B Etap Giriş Kat No: 1"))
        stores.add(Store(137,"Okan Koltuk","Koltuk Takımları","https://framerusercontent.com/images/CRwzyHGO8gvLIASrKsAvIOWDVis.png?width=300&height=300","A Etap Giriş Kat No: 49"))
        stores.add(Store(138,"Onesto Concept","Mobilya Takımları","https://framerusercontent.com/images/5Ln5WWkxRcerTVLpAQ8kHcBzEOE.png?width=242&height=100","A Etap Giriş Kat No: 55"))
        stores.add(Store(139,"Onss","Mobilya Takımları","https://framerusercontent.com/images/2WiU1qqkv4kvUR5kg9eJAIO6og.jpg?scale-down-to=512&width=682&height=441","A Etap Giriş Kat No: 42"))
        stores.add(Store(140,"Onzo Home","Mobilya Takımları","https://framerusercontent.com/images/i9N2qQ1CscNrFildFIRIrijCawk.png?width=225&height=225","B Etap Giriş Kat No: 24"))
        stores.add(Store(141,"Orhan Portmanto","Mobilya Takımları","https://framerusercontent.com/images/UxeD6CsmsWsAX3xLuvZk58oz8.png?width=302&height=91","B Etap 2. Kat No: 31"))
        stores.add(Store(142,"Orix","Mobilya Takımları","https://framerusercontent.com/images/cgHzwu421UwT6CTsm7Npar9Bo.jpg?width=255&height=81","A Etap Giriş Kat No: 32"))
        stores.add(Store(143,"Ossi","Mobilya Takımları","https://framerusercontent.com/images/OCQwYa2sejnOKKuDIHVySFR1zQo.png?scale-down-to=512&width=608&height=152","B Etap 2. Kat No: 40"))
        stores.add(Store(144,"Oxto Design","Mobilya Takımları","https://framerusercontent.com/images/U9hvN50HYJ9PxOBIaBLaT022Is.jpg?width=492&height=365","A Etap 2. Kat No: 9"))
        stores.add(Store(145,"Palma","Mobilya Takımları","https://framerusercontent.com/images/mEEPMSrlr1ijRqsapUsMYq8L3BA.png?width=248&height=70","A Etap 2. Kat No: 15"))
        stores.add(Store(146,"Pianno Mobilya","Mobilya Takımları","https://framerusercontent.com/images/FTik1Q7kF1OZyVGru8VNTv1jFsQ.gif?width=300&height=214","A Etap Giriş Kat No: 16"))
        stores.add(Store(147,"Primos Home Store","Mobilya Takımları","https://framerusercontent.com/images/6ssA4x4xQwdjesE2VFKLpyFeMw.jpg?width=300&height=300","B Etap Giriş Kat No: 36"))
        stores.add(Store(148,"Pumo/Pullu","Mobilya Takımları","https://framerusercontent.com/images/2CG87RRyQ0FIBbBE4fcsNTohe90.png?width=350&height=193","A Etap 2. Kat No: 49"))
        stores.add(Store(149,"Qasas Proje","Mobilya Takımları","https://framerusercontent.com/images/QyAoB7WY7NNoMOIk7kdNL2CSY3E.jpg?width=320&height=320","A Etap 2. Kat No: 46"))
        stores.add(Store(150,"Raudi","Mobilya Takımları","https://framerusercontent.com/images/sKGbpsrSnlcHGLel6yuiMeRkNhU.png?width=500&height=244","A Etap 2. Kat No: 32"))
        stores.add(Store(151,"Razgat Classic","Mobilya Takımları","https://framerusercontent.com/images/vwO1lduKembxdDDV2SRN0PVtPw.png?width=300&height=300","A Etap Giriş Kat No: 4"))
        stores.add(Store(152,"Restore Mobilya","Mobilya Takımları","https://framerusercontent.com/images/lnoZUhA2nrOTDdU4hVcV1LEAgdo.png?width=500&height=237","A Etap Giriş Kat No: 15"))
        stores.add(Store(153,"Rivista","Mobilya Takımları","https://framerusercontent.com/images/29szMBIdHggKCIuUjCK6wTkjQ.webp?width=149&height=85","B Etap 2. Kat No: 29"))
        stores.add(Store(154,"Rixos","Mobilya Takımları","https://framerusercontent.com/images/BgEuRwWDFcW5OhQPKpFwJwJIlTw.jpg?width=320&height=320","A Etap 2. Kat No: 27"))
        stores.add(Store(155,"Rois","Bebek & Genç","https://framerusercontent.com/images/UadQvGA5ZCm1JhbHC3ZOfWQxSrA.png?width=330&height=80","A Etap 2. Kat No: 7"))
        stores.add(Store(156,"Roll Mobilya","Mobilya Takımları","https://framerusercontent.com/images/PSXigyJZMgq3SVCOBYJfb7aVWJ0.png?width=180&height=300","B Etap Giriş Kat No: 30"))
        stores.add(Store(157,"Romel Koltuk","Mobilya Takımları","https://framerusercontent.com/images/GEyX6Zy2r2p8dTLdDlAU4c9SiyM.png?width=152&height=64","B Etap Giriş Kat No: 31"))
        stores.add(Store(158,"Sabrino Mobilya","Mobilya Takımları","https://framerusercontent.com/images/2sLZbosdfptwf5T2mYiUq64pug.png?width=300&height=300","A Etap Giriş Kat No: 27"))
        stores.add(Store(159,"Şahin Mobilya","Mobilya Takımları","https://framerusercontent.com/images/IXMsvoTDgZm6UDdr7s5tixDXxHU.jpeg?scale-down-to=512&width=1600&height=1171","A Etap Giriş Kat No: 11"))
        stores.add(Store(160,"Saka Mobilya","Mobilya Takımları","https://framerusercontent.com/images/vPCgAUDmK5ExaLjZqQGhGsyY.png?scale-down-to=512&width=800&height=800","A Etap Giriş Kat No: 30"))
        stores.add(Store(161,"Salda Home","Mobilya Takımları","https://framerusercontent.com/images/tf9vTZXC8sb7XIOJ5PdogHfI53o.png?scale-down-to=512&width=1495&height=237","A Etap 2. Kat No: 7"))
        stores.add(Store(162,"Saloni Mobilya","Mobilya Takımları","https://framerusercontent.com/images/mSKz3cK45L0YPhlz4Pr7fwgDw20.png?width=300&height=300","A Etap Giriş Kat No: 39"))
        stores.add(Store(163,"Sancrea","Mobilya Takımları","https://framerusercontent.com/images/DdDiWYUOeRPv7rn5pVOANn0kOtE.png?width=240&height=50","A Etap 2. Kat No: 44"))
        stores.add(Store(164,"Sandalyecix","Masa & Sandalye","https://framerusercontent.com/images/6itkrFSpUOYOdWAhvH7ZKivvq2E.png?width=194&height=39","B Etap 2. Kat No: 12"))
        stores.add(Store(165,"Sardunya","Mobilya Takımları","https://framerusercontent.com/images/egzfCFi7ik94TojzMRadS588.png?width=286&height=78","A Etap Giriş Kat No: 43"))
        stores.add(Store(166,"SDesign","Mobilya Takımları","https://framerusercontent.com/images/0kWq8zRoSPBoyMrR4XtM7lhMHf0.jpg?width=512&height=138","A Etap 2. Kat No: 30"))
        stores.add(Store(167,"Selenga","Mobilya Takımları","https://framerusercontent.com/images/EHuUDt62L1RlPUy4YDII4mLGM9Q.png?scale-down-to=512&width=2703&height=943","A Etap Giriş Kat No: 20"))
        stores.add(Store(168,"Selimoğlu Koltuk","Koltuk Takımları","https://framerusercontent.com/images/6d1qh1D7bRbRxErZ09nqGayEDwE.png?scale-down-to=512&width=4246&height=1294","A Etap Giriş Kat No: 8"))
        stores.add(Store(169,"Selis Home Concept","Mobilya Takımları","https://framerusercontent.com/images/CGZiCXNNxeXtbhOkWBl6eFUqIzg.png?width=415&height=119","A Etap Giriş Kat No: 37"))
        stores.add(Store(170,"Serbest Mobilya","Mobilya Takımları","https://framerusercontent.com/images/o28G6GCFhIJgXSjlIpT2FtY.webp?width=500&height=200","A Etap Giriş Kat No: 35"))
        stores.add(Store(171,"Sett Mobilya","Mobilya Takımları","https://framerusercontent.com/images/gfx00D7ycw0UPqxGLGasLE5V28.jpg?width=320&height=320","B Etap Giriş Kat No: 24"))
        stores.add(Store(172,"Seyran Koltuk","Mobilya Takımları","https://framerusercontent.com/images/TnjhEIIZ6tYCLiYlNvwiwXETM94.png?width=300&height=92","A Etap Giriş Kat No: 14"))
        stores.add(Store(173,"Şiptar","Mobilya Takımları","https://framerusercontent.com/images/tRm1ziVo1hpIxfz7mkvEJ701Fw.jpg?width=500&height=500","B Etap Giriş Kat No: 35"))
        stores.add(Store(174,"Sunny","Bebek & Genç","https://framerusercontent.com/images/eVOH9SDb8XmK1LMnVAM1gV44M.jpg?scale-down-to=512&width=1080&height=1080","B Etap Giriş Kat No: 15"))
        stores.add(Store(175,"Sural Mobilya","Mobilya Takımları","https://framerusercontent.com/images/VhqjsgbJdcYZm3rtaJNl5j5ZKO0.jpg?scale-down-to=512&width=827&height=827","A Etap 2. Kat No: 28"))
        stores.add(Store(176,"Swenza","Mobilya Takımları","https://framerusercontent.com/images/hDVEtHd22NKUotGIQoy87iAIi4.png?width=240&height=74","B Etap 2. Kat No: 1"))
        stores.add(Store(177,"Tabbure Concept","Mobilya Takımları","https://framerusercontent.com/images/uDS1PaSrSRVCacaip5GN4DeE4Q.png?width=383&height=121","B Etap 2. Kat No: 9"))
        stores.add(Store(178,"Tarık Chair","Masa & Sandalye","https://framerusercontent.com/images/iNco80X7CptHQT7prYG3wrdFBk.png?scale-down-to=512&width=2362&height=2362","A Etap 2. Kat No: 1"))
        stores.add(Store(179,"Tarz-ı Hayal","Mobilya Takımları","https://framerusercontent.com/images/ryOEGQkKMcZwwNw0cJNt8Dn064.png?width=300&height=300","A Etap 2. Kat No: 48"))
        stores.add(Store(180,"Tetri Home","Mobilya Takımları","https://framerusercontent.com/images/NzqYXRa2SeeHBfJfXLv60BNWpKM.jpg?width=150&height=115","A Etap Giriş Kat No: 45"))
        stores.add(Store(181,"TİTİ Baby-Kids-Young","Bebek & Genç","https://framerusercontent.com/images/nZol4yG0yIFjSfLRyWRnQkUV4LY.jpg?scale-down-to=512&width=768&height=768","A Etap 2. Kat No: 21"))
        stores.add(Store(182,"Trigon","Mobilya Takımları","https://framerusercontent.com/images/GQ9nJ9QX9fWCAGpnGrj3earnQ6U.svg?width=224&height=122","B Etap 2. Kat No: 17"))
        stores.add(Store(183,"Twins","Mobilya Takımları","https://framerusercontent.com/images/ZyldvMtj5bgWTwRUYhQFy4jdAo.png?width=188&height=120","A Etap Giriş Kat No: 29"))
        stores.add(Store(184,"Umut Concept","Mobilya Takımları","https://framerusercontent.com/images/eHp6iCNoraEcj3IzNu9626YbuU.png?width=252&height=166","A Etap 2. Kat No: 19"))
        stores.add(Store(185,"Verte Mobilya","Mobilya Takımları","https://framerusercontent.com/images/CZky0MVHLIbFw8tGc0b3M4YeXFQ.jpeg?scale-down-to=512&width=1046&height=511","B Etap 2. Kat No: 36"))
        stores.add(Store(186,"Vito","Mobilya Takımları","https://framerusercontent.com/images/MCdPyuJNJr0DIHLFXvaYabwdCRk.jpeg?width=225&height=225","B Etap 2. Kat No: 34"))
        stores.add(Store(187,"Vivaldi","Mobilya Takımları","https://framerusercontent.com/images/mD23PzeErW4bGPPxckAFRaCBYa8.png?scale-down-to=512&width=667&height=531","A Etap Giriş Kat No: 33"))
        stores.add(Store(188,"Vivanza Mobilya","Mobilya Takımları","https://framerusercontent.com/images/sQ047MFACIwha1ZKEI1Gs8M8c.jpg?scale-down-to=512&width=827&height=827","A Etap 2. Kat No: 17"))
        stores.add(Store(189,"Voiz Mobilya","Mobilya Takımları","https://framerusercontent.com/images/LBng7GQFUbOD0Ox0M7Hg3ZzpS34.png?width=240&height=160","B Etap Giriş Kat No: 33"))
        stores.add(Store(190,"VRL Mobilya","Mobilya Takımları","https://framerusercontent.com/images/LLthV1IFJHYx1wfgUvplVbjoZc.png?width=228&height=78","A Etap 2. Kat No: 47"))
        stores.add(Store(191,"Wobilya Home","Mobilya Takımları","https://framerusercontent.com/images/NN0B2oiarKGWYEcwlAnAOCDn4Kw.png?width=400&height=111","A Etap 2. Kat No: 3"))
        stores.add(Store(192,"Woox Sofa","Koltuk Takımları","https://framerusercontent.com/images/JAAAokhLTJcUOOAZX0CVf26nTY.webp?width=350&height=117","A Etap Giriş Kat No: 5"))
        stores.add(Store(193,"Yol Mobilya","Mobilya Takımları","https://framerusercontent.com/images/0ZqVTszIC77eXGiKkTVUPy8s.png?scale-down-to=512&width=5584&height=3018","B Etap Giriş Kat No: 40"))
        stores.add(Store(194,"Zanotti","Mobilya Takımları","https://framerusercontent.com/images/DSPikIh9G7ywlkavjg5MYLuhTE.webp?scale-down-to=512&width=962&height=682","A Etap Giriş Kat No: 47"))
        stores.add(Store(195,"Zebrano","Mobilya Takımları","https://framerusercontent.com/images/MYYSnYylkt0KtYPDGYCzFzC6vU.jpg?width=150&height=72","B Etap Giriş Kat No: 20"))
        stores.add(Store(196,"Zümer","Mobilya Takımları","https://framerusercontent.com/images/3i9WHiu8tn7WdwmqLcTqPXFy4.png?width=126&height=100","B Etap 2. Kat No: 22"))

        // Toplu yükleme işlemi
        var successCount = 0
        for (store in stores) {
            storesCollection.document(store.id.toString()).set(store)
                .addOnSuccessListener {
                    successCount++
                    if (successCount == stores.size) {
                        Toast.makeText(this, "Tüm mağazalar yüklendi!", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}