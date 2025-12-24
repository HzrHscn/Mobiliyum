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
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
    private var currentProduct: Product? = null
    private var storesList = ArrayList<Store>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
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
        loadStoresForSpinner()

        if (currentProduct != null) {
            binding.tvTitle.text = "Ürünü Düzenle"
            binding.etProductName.setText(currentProduct!!.name)
            binding.etProductPrice.setText(currentProduct!!.price)
            binding.etProductCategory.setText(currentProduct!!.category)
            binding.etProductImage.setText(currentProduct!!.imageUrl)
            binding.etProductUrl.setText(currentProduct!!.productUrl)
            binding.etProductDesc.setText(currentProduct!!.description)
            binding.switchProductActive.isChecked = currentProduct!!.isActive
        } else {
            binding.tvTitle.text = "Yeni Ürün Ekle"
            binding.switchProductActive.isChecked = true
        }

        binding.btnSaveProduct.setOnClickListener { saveProduct() }
        binding.btnDeleteProduct.visibility = View.GONE
    }

    private fun loadStoresForSpinner() {
        db.collection("stores").get().addOnSuccessListener { result ->
            storesList.clear()
            val storeNames = ArrayList<String>()

            for (doc in result) {
                val store = doc.toObject(Store::class.java)
                storesList.add(store)
                storeNames.add("${store.id} - ${store.name}")
            }

            if (context != null) {
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, storeNames)
                binding.spinnerStores.adapter = adapter
                if (currentProduct != null) {
                    val index = storesList.indexOfFirst { it.id == currentProduct!!.storeId }
                    if (index != -1) binding.spinnerStores.setSelection(index)
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
        val isActive = binding.switchProductActive.isChecked

        if (name.isEmpty() || price.isEmpty()) {
            Toast.makeText(context, "Zorunlu alanları doldurun.", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedStorePosition = binding.spinnerStores.selectedItemPosition
        if (selectedStorePosition == -1 || storesList.isEmpty()) {
            Toast.makeText(context, "Lütfen bir mağaza seçin.", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedStore = storesList[selectedStorePosition]

        val productIdInt: Int = if (currentProduct != null) currentProduct!!.id else Random.nextInt(100000, 999999)

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
            lastUpdated = System.currentTimeMillis(),
            rating = (currentProduct?.rating ?: 0f),
            reviewCount = (currentProduct?.reviewCount ?: 0),
            totalRating = (currentProduct?.totalRating ?: 0.0),
            favoriteCount = (currentProduct?.favoriteCount ?: 0),
            clickCount = (currentProduct?.clickCount ?: 0),
            priceHistory = (currentProduct?.priceHistory ?: hashMapOf()),
            quantity = (currentProduct?.quantity ?: 1)
        )

        db.collection("products").document(productIdInt.toString())
            .set(updatedProduct)
            .addOnSuccessListener {
                // HATA BURADAYDI: requireContext() eklendi
                DataManager.updateProductInCache(requireContext(), updatedProduct)
                DataManager.triggerServerVersionUpdate(
                    updatedProductId = productIdInt.toString()
                )

                Toast.makeText(context, "Ürün kaydedildi.", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}