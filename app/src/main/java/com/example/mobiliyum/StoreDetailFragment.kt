package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
// Tam ekran ürün detayları için doğru import
import com.example.mobiliyum.ProductDetailFragment

class StoreDetailFragment : Fragment() {

    private lateinit var rvProducts: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private lateinit var productList: ArrayList<Product>

    // UI Bileşenleri
    private lateinit var imgLogo: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvLocation: TextView

    // Gelen Verileri Tutacak Değişkenler
    private var storeName: String? = null
    private var storeImage: String? = null
    private var storeLocation: String? = null

    // Fragment oluşturulurken argümanları alıyoruz
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            storeName = it.getString("name")
            storeImage = it.getString("image")
            storeLocation = it.getString("location")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_store_detail, container, false)

        // Bileşenleri Bağla
        imgLogo = view.findViewById(R.id.imgDetailLogo)
        tvName = view.findViewById(R.id.tvDetailName)
        tvLocation = view.findViewById(R.id.tvDetailLocation)
        rvProducts = view.findViewById(R.id.rvProducts)

        // Verileri Yazdır
        tvName.text = storeName
        tvLocation.text = storeLocation

        // Resmi Yükle (Glide)
        if (storeImage != null) {
            Glide.with(this).load(storeImage).into(imgLogo)
        }

        // Ürün Listesini Ayarla (Yan yana 2'li Izgara Görünümü)
        rvProducts.layoutManager = GridLayoutManager(context, 2)

        // Mağazaya Özel Ürünleri Hazırla
        prepareStoreProducts()

        return view
    }

    private fun prepareStoreProducts() {
        productList = ArrayList()

        // Mağaza ID'leri: 1=Akyol, 2=Çilek, 3=Nills, 4=Saloni, 5=İnegöl Sofa, 6=Büro Life
        val nameCheck = storeName?.lowercase() ?: ""

        if (nameCheck.contains("çilek") || nameCheck.contains("bebek")) {
            // Çilek Odası (Store ID: 2)
            val storeId = 2
            productList.add(Product(
                1, storeId, "Araba Yatak", "GTE Araba Yatak (Kırmızı)", "59.750 ₺",
                "https://cilek.com/cdn/shop/files/20.02.1370.001_1800x1800.jpg?v=1726485007",
                "https://cilek.com/products/gte-araba-yatak-kirmizi-90x195-cm"
            ))
            productList.add(Product(
                2, storeId, "Genç Odası", "Romantic Çekirdek Oda", "84.250 ₺",
                "https://cilek.com/cdn/shop/files/Kampanya_0021_Romantic_3b4ae0db-df90-46e7-b106-3dc567106717_1800x1800.jpg?v=1733205443",
                "https://cilek.com/products/romantic-genc-odasi-takimi"
            ))
            productList.add(Product(
                3, storeId, "Karyola", "Daybed Çatısız Karyola (Montes Beyaz)", "14.355 ₺",
                "https://cilek.com/cdn/shop/files/20.77.1321.00-01_1800x1800.jpg?v=1734696576",
                "https://cilek.com/products/montes-beyaz-karyola-100x200-cm"
            ))
            productList.add(Product(
                4, storeId, "Masa", "Dark Metal Gaming Masa", "26.000 ₺",
                "https://cilek.com/cdn/shop/files/20.52.1110.00-03_1800x1800.jpg?v=1760444569",
                "https://cilek.com/products/dark-metal-calisma-masasi"
            ))
        }
        else if (nameCheck.contains("nills") || nameCheck.contains("yatak")) {
            // Nill's Mobilya (Store ID: 3)
            val storeId = 3
            productList.add(Product(
                5, storeId, "Yatak Odası", "Nevia Yatak Odası", "98.000 ₺",
                "https://nills.com/wp-content/uploads/2025/10/nevia-yatak-odasi-1.jpg",
                "https://nills.com/tr/urun-detay/nevia-yatak-odasi"
            ))
            productList.add(Product(
                6, storeId, "Yatak Odası", "Lea Yatak Odası", "104.500 ₺",
                "https://nills.com/wp-content/uploads/2025/03/lea-yatak-odasi-250325-15.jpg",
                "https://nills.com/tr/urun-detay/lea-yatak-odasi"
            ))
        }
        else if (nameCheck.contains("ofis") || nameCheck.contains("büro")) {
            // Büro Life (Store ID: 6)
            val storeId = 6
            productList.add(Product(
                7, storeId, "Makam Takımları", "Point Makam Takımı", "75.000 ₺",
                "https://burolife.com.tr/wp-content/uploads/pointman.jpg",
                "https://burolife.com.tr/urun/point-makam-takimi"
            ))
            productList.add(Product(
                8, storeId, "Ofis Koltukları", "Ergonomik Vox Siyah Sandalye", "14.500 ₺",
                "https://burolife.com.tr/wp-content/uploads/b%C3%BCrolife_0000s_0067_Katman-45-300x231.jpg",
                "https://burolife.com.tr/urun/vox-yonetici-koltugu"
            ))
        }
        else if (nameCheck.contains("saloni")) {
            // Saloni Mobilya (Store ID: 4)
            val storeId = 4
            productList.add(Product(
                9, storeId, "Koltuk Takımları", "Da Vinci Koltuk Takımı", "105.000 ₺",
                "https://saloni.furniture/wp-content/uploads/da-vinci-modular-sofa-p3-1-2048x1262.jpg",
                "https://saloni.furniture/urun/da-vinci-koltuk-takimi"
            ))
            productList.add(Product(
                10, storeId, "Köşe Takımları", "Orion Köşe Takımı Lüks", "102.500 ₺",
                "https://saloni.furniture/wp-content/uploads/orion-modular-sofa-p22-2048x1567.jpg",
                "https://saloni.furniture/urun/orion-kose-takimi"
            ))
        }
        else if (nameCheck.contains("akyol")) {
            // Akyol Life (Store ID: 1)
            val storeId = 1
            productList.add(Product(
                11, storeId, "Yatak Odası", "Kapadokya Yatak Odası", "69.360 ₺",
                "https://www.akyollife.com.tr/upload/urunler/kapadokya-yatak-odasi-takimi-207_1.jpg",
                "https://www.akyollife.com.tr/yatak-odasi/kapadokya-yatak-odasi-takimi-207"
            ))
            productList.add(Product(
                12, storeId, "Yatak Odası", "Dolunay Yatak Odası", "72.500 ₺",
                "https://www.akyollife.com.tr/upload/urunler/dolunay-yatak-odasi-takimi-210_1.jpg",
                "https://www.akyollife.com.tr/yatak-odasi/dolunay-yatak-odasi-takimi-210"
            ))
        }
        else {
            // Diğer Mağazalar (Genel ID: 5 - İnegöl Sofa vb.)
            val storeId = 5
            productList.add(Product(
                13, storeId, "Tv Üniteleri", "TV Ünitesi Linda", "18.900 ₺",
                "https://www.inegolmobilyadukkani.com/images/urunler/linda-tv-unitesi-resim-2131.jpg",
                "https://www.inegolmobilyadukkani.com/urun/linda-tv-unitesi"
            ))
            productList.add(Product(
                14, storeId, "Yemek Takımları", "Klas Beyaz Yemek Odası", "58.950 ₺",
                "https://www.inegolmobilyadukkani.com/images/urunler/Klas-Beyaz-Yemek-Odasi-resim-881.jpg",
                "https://www.inegolmobilyadukkani.com/urun/klas-yemek-odasi"
            ))
            productList.add(Product(
                15, storeId, "Berjerler", "Valencia Berjer Koltuk", "17.000 ₺",
                "https://storage.googleapis.com/mobilya/model/47/m-mobilya-82472-628787e96e117.jpg",
                "https://www.inegolmobilyadukkani.com/kategori/berjerler"
            ))
        }

        productAdapter = ProductAdapter(productList) { clickedProduct ->

            // 1. Detay Fragment'ı hazırla (Tam ekran detay)
            val detailFragment = ProductDetailFragment()

            // 2. Tıklanan ürünü pakete koy
            val bundle = Bundle()
            bundle.putSerializable("product_data", clickedProduct)
            detailFragment.arguments = bundle

            // 3. Sayfayı aç
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null)
                .commit()
        }

        rvProducts.adapter = productAdapter
    }
}