package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.mobiliyum.databinding.FragmentAdminProductEditBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class AdminProductEditFragment : Fragment() {

    private var _binding: FragmentAdminProductEditBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private var currentProduct: Product? = null
    // Spinner için Mağaza listesi
    private var storesList = ArrayList<Store>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Sürüm kontrolü ile güvenli Parcelable alımı
            currentProduct = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable("product_data", Product::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable("product_data")
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminProductEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mağazaları yükle (Spinner için gerekli)
        loadStoresForSpinner()

        // "Sil" butonu tamamen kaldırıldı. Sadece Aktif/Pasif ayarı var.

        if (currentProduct != null) {
            binding.tvTitle.text = "Ürünü Düzenle"

            // Mevcut verileri doldur
            binding.etProductName.setText(currentProduct!!.name)
            binding.etProductPrice.setText(currentProduct!!.price)
            binding.etProductCategory.setText(currentProduct!!.category)
            binding.etProductImage.setText(currentProduct!!.imageUrl)
            binding.etProductUrl.setText(currentProduct!!.productUrl)
            binding.etProductDesc.setText(currentProduct!!.description)

            // Ürün aktif mi pasif mi? Switch'i ona göre ayarla
            binding.switchProductActive.isChecked = currentProduct!!.isActive

        } else {
            binding.tvTitle.text = "Yeni Ürün Ekle"
            // Yeni ürün eklerken varsayılan olarak Aktif gelsin
            binding.switchProductActive.isChecked = true
        }

        binding.btnSaveProduct.setOnClickListener { saveProduct() }

        // btnDeleteProduct ile ilgili tüm kodlar silindi.
        // Layout dosyasından da (xml) btnDeleteProduct butonunu kaldırmayı unutmayın.
        // Eğer kaldırmadıysanız bile burada kodunu sildiğimiz için çalışmayacaktır,
        // sadece visibility = GONE yaparak gizleyebilirsiniz:
        binding.btnDeleteProduct.visibility = View.GONE
    }

    private fun loadStoresForSpinner() {
        db.collection("stores").get().addOnSuccessListener { result ->
            storesList.clear()
            val storeNames = ArrayList<String>()

            for (doc in result) {
                val store = doc.toObject(Store::class.java)
                storesList.add(store)
                // Spinner'da görünecek format: "101 - Mobiliyum AVM"
                storeNames.add("${store.id} - ${store.name}")
            }

            if (context != null) {
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, storeNames)
                binding.spinnerStores.adapter = adapter

                // Düzenleme modundaysak, ürünün mağazasını seçili getir
                if (currentProduct != null) {
                    val index = storesList.indexOfFirst { it.id == currentProduct!!.storeId }
                    if (index != -1) {
                        binding.spinnerStores.setSelection(index)
                    }
                }
            }
        }
    }

    private fun saveProduct() {
        val name = binding.etProductName.text.toString().trim()
        val price = binding.etProductPrice.text.toString().trim()
        val category = binding.etProductCategory.text.toString().trim()
        val imageUrl = binding.etProductImage.text.toString().trim()
        val productUrl = binding.etProductUrl.text.toString().trim()
        val description = binding.etProductDesc.text.toString().trim()

        // Ürünün görünürlüğü bu switch'e bağlı
        val isActive = binding.switchProductActive.isChecked

        if (name.isEmpty() || price.isEmpty()) {
            Toast.makeText(context, "Ürün adı ve fiyat zorunludur.", Toast.LENGTH_SHORT).show()
            return
        }

        // Mağaza seçimi kontrolü
        val selectedStorePosition = binding.spinnerStores.selectedItemPosition
        if (selectedStorePosition == -1 || storesList.isEmpty()) {
            Toast.makeText(context, "Lütfen bir mağaza seçin.", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedStore = storesList[selectedStorePosition]

        // ID YÖNETİMİ:
        // ID'miz Int tipinde. Eğer yeni ürünse rastgele sayı üret, eskisiyse mevcut ID'yi kullan.
        val productIdInt: Int = if (currentProduct != null) {
            currentProduct!!.id
        } else {
            Random.nextInt(100000, 999999)
        }

        // Veri haritasını oluştur
        val productData = hashMapOf(
            "id" to productIdInt, // Int olarak kaydediyoruz
            "name" to name,
            "price" to price,
            "category" to category,
            "imageUrl" to imageUrl,
            "productUrl" to productUrl,
            "description" to description,
            "storeId" to selectedStore.id,
            "isActive" to isActive, // Kritik alan: Pasif ise kullanıcı göremez
            "lastUpdated" to System.currentTimeMillis(), // YENİ: Şu anki zamanı kaydet

            // Eski verileri kaybetmemek için koruyoruz
            "rating" to (currentProduct?.rating ?: 0f),
            "reviewCount" to (currentProduct?.reviewCount ?: 0),
            "totalRating" to (currentProduct?.totalRating ?: 0.0),
            "favoriteCount" to (currentProduct?.favoriteCount ?: 0),
            "clickCount" to (currentProduct?.clickCount ?: 0),
            "priceHistory" to (currentProduct?.priceHistory ?: hashMapOf<String, Double>()),
            "quantity" to (currentProduct?.quantity ?: 1)
        )

        // Firestore'a kaydet
        // ÖNEMLİ: document() içine String path vermek zorundayız, o yüzden productIdInt.toString() yapıyoruz.
        db.collection("products").document(productIdInt.toString())
            .set(productData)
            .addOnSuccessListener {
                // 1. Ürünü oluştur (Product nesnesi olarak)
                val updatedProduct = Product(
                    id = productIdInt,
                    name = name,
                    price = price,
                    category = category,
                    imageUrl = imageUrl,
                    productUrl = productUrl,
                    description = description,
                    storeId = selectedStore.id,
                    isActive = isActive,
                    // Eski değerleri koru
                    rating = (currentProduct?.rating ?: 0f),
                    reviewCount = (currentProduct?.reviewCount ?: 0),
                    totalRating = (currentProduct?.totalRating ?: 0.0),
                    favoriteCount = (currentProduct?.favoriteCount ?: 0),
                    clickCount = (currentProduct?.clickCount ?: 0),
                    priceHistory = (currentProduct?.priceHistory ?: hashMapOf()),
                    quantity = (currentProduct?.quantity ?: 1)
                )

                // 2. YEREL ÖNBELLEĞİ GÜNCELLE (0 READ MALİYETİ)
                DataManager.updateProductInCache(updatedProduct)

                // 3. SUNUCUDAKİ VERSİYONU ARTIR (DİĞER KULLANICILAR İÇİN)
                // Bu sayede diğer kullanıcılar uygulamayı açtığında yeni veriyi çeker.
                DataManager.triggerServerVersionUpdate()

                Toast.makeText(context, "Ürün başarıyla kaydedildi.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}