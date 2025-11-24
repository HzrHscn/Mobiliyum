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

        // Burada mağaza ismine göre farklı ürünler gösteriyormuş gibi yapıyoruz.
        // contains komutu ile ismin içinde geçen kelimeye bakıyoruz.

        val nameCheck = storeName?.lowercase() ?: ""

        if (nameCheck.contains("çilek") || nameCheck.contains("bebek")) {
            // Çilek Odası ise Bebek/Çocuk ürünleri göster
            productList.add(Product(1, "Araba Yatak", "GTE Araba Yatak (Kırmızı)",   "59.750 ₺", "https://cilek.com/cdn/shop/files/20.02.1370.001_1800x1800.jpg?v=1726485007"))
            productList.add(Product(2, "Romantic", "Romantic Çekirdek Oda",   "84.250 ₺", "https://cilek.com/cdn/shop/files/Kampanya_0021_Romantic_3b4ae0db-df90-46e7-b106-3dc567106717_1800x1800.jpg?v=1733205443"))
            productList.add(Product(3, "Çatısız Karyolalar", "Daybed Çatısız Karyola (Montes Beyaz) (100x200 cm)",   "14.355 ₺", "https://cilek.com/cdn/shop/files/20.77.1321.00-01_1800x1800.jpg?v=1734696576"))
            productList.add(Product(4, "Masa", "Dark Metal Gaming Masa", "26.000 ₺", "https://cilek.com/cdn/shop/files/20.52.1110.00-03_1800x1800.jpg?v=1760444569"))
        }
        else if (nameCheck.contains("yatak") || nameCheck.contains("nills")) {
            // Yatak firması ise
            productList.add(Product(1, "Yatak Odası", "Nevia Yatak Odası", "98.000 ₺", "https://nills.com/wp-content/uploads/2025/10/nevia-yatak-odasi-1.jpg"))
            productList.add(Product(2, "Yatak Odası", "Lea Yatak Odası", "104.500 ₺", "https://nills.com/wp-content/uploads/2025/03/lea-yatak-odasi-250325-15.jpg"))
        }
        else if (nameCheck.contains("ofis") || nameCheck.contains("büro")) {
            // Ofis firması ise
            productList.add(Product(1, "Makam Takımları", "Point Makam Takımı", "75.000 ₺", "https://burolife.com.tr/wp-content/uploads/pointman.jpg"))
            productList.add(Product(2, "Ofis Koltukları", "Ergonomik Vox Siyah Sandalye", "14.500 ₺", "https://burolife.com.tr/wp-content/uploads/b%C3%BCrolife_0000s_0067_Katman-45-300x231.jpg"))
        }
        else {
            // Diğer mobilyacılar için genel koltuk/salon takımları
            productList.add(Product(1, "Koltuk Takımları", "Da Vinci Koltuk Takımı", "105.000 ₺", "https://saloni.furniture/wp-content/uploads/da-vinci-modular-sofa-p3-1-2048x1262.jpg"))
            productList.add(Product(2, "Koltuk Takımları", "Orion Köşe Takımı Lüks", "102.500 ₺", "https://saloni.furniture/wp-content/uploads/orion-modular-sofa-p22-2048x1567.jpg"))
            productList.add(Product(3, "Tv Üniteleri", "TV Ünitesi Linda", "18.900 ₺", "https://www.inegolmobilyadukkani.com/images/urunler/linda-tv-unitesi-resim-2131.jpg"))
            productList.add(Product(4, "Yemek Takımları", "Klas Beyaz Yemek Odası", "58.950 ₺", "https://www.inegolmobilyadukkani.com/images/urunler/Klas-Beyaz-Yemek-Odasi-resim-881.jpg"))
            productList.add(Product(5, "Berjerler", "Valencia Berjer Koltuk", "17.000 ₺", "https://storage.googleapis.com/mobilya/model/47/m-mobilya-82472-628787e96e117.jpg"))
        }

        productAdapter = ProductAdapter(productList) { clickedProduct ->

            // 1. Detay Fragment'ı hazırla
            val detailFragment = ProductDetailFragment()

            // 2. Tıklanan ürünü pakete koy
            val bundle = Bundle()
            bundle.putSerializable("product_data", clickedProduct)
            detailFragment.arguments = bundle

            // 3. Sayfayı aç
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null) // Geri tuşu ile listeye dönebilsin
                .commit()
        }

        rvProducts.adapter = productAdapter
    }
}