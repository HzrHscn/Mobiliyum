package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
// Tam ekran detay sayfasını import et
import com.example.mobiliyum.ProductDetailFragment

class StoreDetailFragment : Fragment() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var categoryAdapter: CategoryAdapter
    private var categorySectionList = ArrayList<CategorySection>()

    // Gelen Veriler
    private var storeId: Int = 0 // --- ARTIK ID VAR ---
    private var storeName: String? = null
    private var storeImage: String? = null
    private var storeLocation: String? = null

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Paketten ID'yi çıkarıyoruz. (Varsayılan 0)
            storeId = it.getInt("id", 0)
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

        val imgLogo = view.findViewById<ImageView>(R.id.imgDetailLogo)
        val tvName = view.findViewById<TextView>(R.id.tvDetailName)
        val tvLocation = view.findViewById<TextView>(R.id.tvDetailLocation)
        rvCategories = view.findViewById(R.id.rvProducts)

        tvName.text = storeName
        tvLocation.text = storeLocation
        if (storeImage != null && storeImage!!.isNotEmpty()) {
            Glide.with(this).load(storeImage).into(imgLogo)
        }

        rvCategories.layoutManager = LinearLayoutManager(context)

        // Verileri Çek
        fetchProductsAndGroup()

        return view
    }

    private fun fetchProductsAndGroup() {
        // ID kontrolü
        if (storeId == 0) {
            Toast.makeText(context, "Mağaza ID'si alınamadı!", Toast.LENGTH_SHORT).show()
            return
        }

        // --- KRİTİK SORGULAMA ---
        // Artık veritabanına "StoreId'si X olan ürünleri getir" diyoruz.
        // CSV'de girdiğin 6 numaralı ürünler, sen 6 numaralı mağazaya tıklayınca gelecek.
        db.collection("products")
            .whereEqualTo("storeId", storeId)
            .get()
            .addOnSuccessListener { documents ->
                val allProducts = ArrayList<Product>()
                for (doc in documents) {
                    allProducts.add(doc.toObject(Product::class.java))
                }

                if (allProducts.isEmpty()) {
                    // Eğer ürün yoksa boş liste göster (Hata vermesin)
                    Toast.makeText(context, "Bu mağazada henüz ürün yok.", Toast.LENGTH_SHORT).show()
                } else {
                    // Ürünleri grupla ve göster
                    groupProductsBycategory(allProducts)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Ürünler yüklenemedi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun groupProductsBycategory(products: List<Product>) {
        categorySectionList.clear()

        // Kategori ismine göre grupla
        val groupedMap = products.groupBy { it.category }

        for ((categoryName, productList) in groupedMap) {
            // Kategori sayısına göre otomatik açma mantığı
            var expanded = false
            if (groupedMap.size <= 2) { // Az kategori varsa açık gelsin
                expanded = true
            }

            categorySectionList.add(CategorySection(categoryName, productList, expanded))
        }

        categoryAdapter = CategoryAdapter(requireContext(), categorySectionList) { clickedProduct ->
            // Ürüne tıklanınca Detay Sayfasına Git (Tam Ekran)
            val detailFragment = ProductDetailFragment()
            val bundle = Bundle()
            bundle.putSerializable("product_data", clickedProduct)
            detailFragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null)
                .commit()
        }
        rvCategories.adapter = categoryAdapter
    }
}