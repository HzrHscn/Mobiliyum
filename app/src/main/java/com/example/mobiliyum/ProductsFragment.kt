package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mobiliyum.databinding.FragmentProductsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var productAdapter: ProductAdapter
    private var allProducts = listOf<Product>() // List olarak değiştirdim
    private val db by lazy { DataManager.getDb() }
    private var searchQuery = ""
    private var selectedCategories = ArrayList<String>()
    private var selectedStoreIds = ArrayList<Int>()
    private var minPriceFilter: Double? = null
    private var maxPriceFilter: Double? = null
    private var minRatingFilter: Int = 0
    private var currentSortMode = SortMode.DEFAULT

    enum class SortMode {
        DEFAULT, PRICE_LOW_HIGH, PRICE_HIGH_LOW, MOST_CLICKED, MOST_FAVORITED
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!NetworkMonitor.isOnline()) {
            Toast.makeText(context, "Çevrimdışı mod - Eski veriler gösteriliyor", Toast.LENGTH_SHORT).show()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            val prefs = requireContext().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().remove("productsVersion").apply()
            fetchProducts()
            binding.swipeRefreshLayout.isRefreshing = false
        }

        setupRecyclerView()
        setupListeners()
        fetchProducts()
    }

    private fun setupRecyclerView() {
        binding.rvProducts.layoutManager = GridLayoutManager(context, 2)
        productAdapter = ProductAdapter { product -> openProductDetail(product) }
        binding.rvProducts.adapter = productAdapter
    }

    private fun setupListeners() {
        binding.searchViewProducts.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                applyFiltersAndSort()
                return true
            }
        })
        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.btnCategories.setOnClickListener { showMultiCategoryDialog() }
        binding.btnFilter.setOnClickListener { showAdvancedFilterDialog() }
        binding.chipResetFilters.setOnClickListener { resetAllFilters() }
    }

    private fun fetchProducts() {
        binding.progressBarProducts.visibility = View.VISIBLE

        DataManager.fetchProductsSmart(
            requireContext(),
            onSuccess = { products ->
                allProducts = products // ArrayList, List'e atanabilir
                applyFiltersAndSort()
                binding.progressBarProducts.visibility = View.GONE
            },
            onError = {
                binding.progressBarProducts.visibility = View.GONE
                Toast.makeText(context, "Ürünler yüklenemedi", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun applyFiltersAndSort() {
        // HATA GİDERİLDİ: List kullanımı ve gereksiz cast işlemleri kaldırıldı
        var result: List<Product> = ArrayList(allProducts)

        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase(Locale.getDefault())
            result = result.filter {
                it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
            }
        }
        if (selectedCategories.isNotEmpty()) {
            result = result.filter { selectedCategories.contains(it.category) }
        }
        if (selectedStoreIds.isNotEmpty()) {
            result = result.filter { selectedStoreIds.contains(it.storeId) }
        }
        if (minPriceFilter != null) {
            result = result.filter { PriceUtils.parsePrice(it.price) >= minPriceFilter!! }
        }
        if (maxPriceFilter != null) {
            result = result.filter { PriceUtils.parsePrice(it.price) <= maxPriceFilter!! }
        }
        if (minRatingFilter > 0) {
            result = result.filter { it.rating >= minRatingFilter.toFloat() }
        }

        result = when (currentSortMode) {
            SortMode.PRICE_LOW_HIGH -> result.sortedBy { PriceUtils.parsePrice(it.price) }
            SortMode.PRICE_HIGH_LOW -> result.sortedByDescending { PriceUtils.parsePrice(it.price) }
            SortMode.MOST_CLICKED -> result.sortedByDescending { it.clickCount }
            SortMode.MOST_FAVORITED -> result.sortedByDescending { it.favoriteCount }
            else -> result
        }

        // ListAdapter List<T> kabul eder
        productAdapter.submitList(result)
        checkResetButtonVisibility()
    }

    // --- UI DIALOGLARI ---
    private fun showSortDialog() {
        val options = arrayOf("Varsayılan", "Fiyat Artan", "Fiyat Azalan", "Çok Tıklanan", "Çok Beğenilen")
        AlertDialog.Builder(context).setItems(options) { _, w ->
            currentSortMode = when (w) { 1 -> SortMode.PRICE_LOW_HIGH; 2 -> SortMode.PRICE_HIGH_LOW; 3 -> SortMode.MOST_CLICKED; 4 -> SortMode.MOST_FAVORITED; else -> SortMode.DEFAULT }
            applyFiltersAndSort()
        }.show()
    }

    private fun showMultiCategoryDialog() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.dialog_multi_category)
        val container = dialog.findViewById<LinearLayout>(R.id.containerCategories)
        val btnApply = dialog.findViewById<MaterialButton>(R.id.btnApplyCategories)
        val uniqueCategories = allProducts.map { it.category }.distinct().sorted()
        val tempSelected = ArrayList<String>(selectedCategories)

        uniqueCategories.forEach { category ->
            val cb = CheckBox(context)
            cb.text = category
            cb.isChecked = tempSelected.contains(category)
            cb.setOnCheckedChangeListener { _, isCh -> if (isCh) tempSelected.add(category) else tempSelected.remove(category) }
            container?.addView(cb)
        }
        btnApply?.setOnClickListener { selectedCategories = ArrayList(tempSelected); applyFiltersAndSort(); dialog.dismiss() }
        dialog.show()
    }

    private fun showAdvancedFilterDialog() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.behavior.peekHeight = resources.displayMetrics.heightPixels
        dialog.setContentView(R.layout.dialog_advanced_filter)

        val etMin = dialog.findViewById<TextView>(R.id.etMinPrice)
        val etMax = dialog.findViewById<TextView>(R.id.etMaxPrice)
        val etSearchStore = dialog.findViewById<TextView>(R.id.etSearchStore)
        val btnSelectAll = dialog.findViewById<TextView>(R.id.btnSelectAllStores)
        val btnDeselectAll = dialog.findViewById<TextView>(R.id.btnDeselectAllStores)
        val containerStores = dialog.findViewById<LinearLayout>(R.id.containerStores)
        val btnApply = dialog.findViewById<MaterialButton>(R.id.btnApplyFilters)
        val progress = dialog.findViewById<android.widget.ProgressBar>(R.id.progressStores)

        val stars = listOf(
            dialog.findViewById<ImageView>(R.id.star1), dialog.findViewById<ImageView>(R.id.star2),
            dialog.findViewById<ImageView>(R.id.star3), dialog.findViewById<ImageView>(R.id.star4),
            dialog.findViewById<ImageView>(R.id.star5)
        )
        val tvRatingInfo = dialog.findViewById<TextView>(R.id.tvRatingInfo)
        val btnResetRating = dialog.findViewById<TextView>(R.id.btnResetRating)

        if (minPriceFilter != null) etMin?.text = minPriceFilter?.toInt().toString()
        if (maxPriceFilter != null) etMax?.text = maxPriceFilter?.toInt().toString()

        var tempRating = minRatingFilter

        fun updateStars(rating: Int) {
            tempRating = rating
            tvRatingInfo?.text = if(rating > 0) "$rating puan ve üzeri" else "Filtre yok"
            stars.forEachIndexed { index, imageView ->
                if (index < rating) {
                    imageView?.setColorFilter(Color.parseColor("#FFC107"))
                    imageView?.setImageResource(R.drawable.ic_heart_filled)
                } else {
                    imageView?.setColorFilter(Color.LTGRAY)
                    imageView?.setImageResource(R.drawable.ic_heart_outline)
                }
            }
        }
        updateStars(tempRating)

        stars.forEachIndexed { index, imageView -> imageView?.setOnClickListener { updateStars(index + 1) } }
        btnResetRating?.setOnClickListener { updateStars(0) }

        val tempSelectedStores = ArrayList<Int>(selectedStoreIds)
        val allStoreCheckBoxes = ArrayList<CheckBox>()

        progress?.visibility = View.VISIBLE
        db.collection("stores").get()
            .addOnSuccessListener { documents ->
                progress?.visibility = View.GONE

                val stores = documents.map { it.toObject(Store::class.java) }
                val sortedStores = stores.sortedBy { it.id }

                sortedStores.forEach { store ->
                    val cb = CheckBox(context)
                    cb.text = "${store.id} - ${store.name}"
                    cb.isChecked = tempSelectedStores.contains(store.id)
                    cb.textSize = 14f
                    cb.setPadding(8, 8, 8, 8)
                    cb.tag = store

                    cb.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            tempSelectedStores.add(store.id)
                        } else {
                            tempSelectedStores.remove(store.id)
                        }
                    }

                    containerStores?.addView(cb)
                    allStoreCheckBoxes.add(cb)
                }
            }
            .addOnFailureListener {
                progress?.visibility = View.GONE
            }

        etSearchStore?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase(Locale.getDefault())
                allStoreCheckBoxes.forEach { cb ->
                    val text = cb.text.toString().lowercase(Locale.getDefault())
                    cb.visibility = if (text.contains(query)) View.VISIBLE else View.GONE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnSelectAll?.setOnClickListener { allStoreCheckBoxes.filter { it.visibility == View.VISIBLE }.forEach { it.isChecked = true } }
        btnDeselectAll?.setOnClickListener { allStoreCheckBoxes.filter { it.visibility == View.VISIBLE }.forEach { it.isChecked = false } }

        btnApply?.setOnClickListener {
            val minStr = etMin?.text.toString()
            val maxStr = etMax?.text.toString()
            minPriceFilter = if (minStr.isNotEmpty()) minStr.toDouble() else null
            maxPriceFilter = if (maxStr.isNotEmpty()) maxStr.toDouble() else null
            minRatingFilter = tempRating
            selectedStoreIds = ArrayList(tempSelectedStores)

            applyFiltersAndSort()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun resetAllFilters() {
        searchQuery = ""; binding.searchViewProducts.setQuery("", false); selectedCategories.clear(); selectedStoreIds.clear()
        minPriceFilter = null; maxPriceFilter = null; minRatingFilter = 0; currentSortMode = SortMode.DEFAULT
        applyFiltersAndSort()
    }

    private fun checkResetButtonVisibility() {
        val isFiltered = searchQuery.isNotEmpty() || selectedCategories.isNotEmpty() || selectedStoreIds.isNotEmpty() || minPriceFilter != null || maxPriceFilter != null || minRatingFilter > 0 || currentSortMode != SortMode.DEFAULT
        binding.chipResetFilters.visibility = if (isFiltered) View.VISIBLE else View.GONE
    }

    private fun openProductDetail(product: Product) {
        val detailFragment = ProductDetailFragment()
        val bundle = Bundle()
        bundle.putParcelable("product_data", product)
        detailFragment.arguments = bundle
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}