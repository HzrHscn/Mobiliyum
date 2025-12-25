package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.FragmentStoreDetailBinding
import com.example.mobiliyum.databinding.ItemCategoryGroupBinding
import com.google.firebase.firestore.FirebaseFirestore

class StoreDetailFragment : Fragment() {

    private var _binding: FragmentStoreDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var userChoiceAdapter: ProductAdapter
    private lateinit var storeChoiceAdapter: ProductAdapter
    private var currentAnnouncement: NotificationItem? = null
    private var categorySectionList = ArrayList<CategorySection>()
    private var storeId: Int = 0
    private var storeName: String? = null
    private var storeImage: String? = null
    private var storeLocation: String? = null
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            storeId = it.getInt("id", 0)
            storeName = it.getString("name")
            storeImage = it.getString("image")
            storeLocation = it.getString("location")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDetailName.text = storeName
        binding.tvDetailLocation.text = storeLocation

        if (!storeImage.isNullOrEmpty()) {
            Glide.with(this)
                .load(storeImage)
                .into(binding.imgDetailLogo)
        }

        binding.rvProducts.layoutManager = LinearLayoutManager(context)
        binding.rvUserChoice.layoutManager = GridLayoutManager(context, 2)
        binding.rvStoreChoice.layoutManager = GridLayoutManager(context, 2)

        binding.rvProducts.isNestedScrollingEnabled = false
        binding.rvUserChoice.isNestedScrollingEnabled = false
        binding.rvStoreChoice.isNestedScrollingEnabled = false

        setupFollowButton()

        if (storeId != 0) {
            loadStoreProductsFromCache()
            fetchLatestAnnouncement()
        }

        binding.cardStoreAnnouncement.setOnClickListener {
            if (currentAnnouncement != null) {
                AlertDialog.Builder(context)
                    .setTitle(currentAnnouncement!!.title)
                    .setMessage(currentAnnouncement!!.message)
                    .setPositiveButton("Kapat", null)
                    .show()
            }
        }

        binding.btnSeeAllAnnouncements.setOnClickListener {
            val fragment = StoreAnnouncementsFragment()
            val bundle = Bundle()
            bundle.putString("storeId", storeId.toString())
            bundle.putString("storeName", storeName)
            fragment.arguments = bundle
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadStoreProductsFromCache() {
        // HATA GİDERİLDİ: Lambda parametresi 'allProducts' olarak isimlendirildi
        DataManager.fetchProductsSmart(
            requireContext(),
            onSuccess = { allProducts ->
                // allProducts bir ArrayList<Product>, filter bize List<Product> döner
                val storeProducts = allProducts.filter { it.storeId == storeId }

                if (storeProducts.isNotEmpty()) {
                    groupProductsByCategory(storeProducts)
                    setupFeaturedProducts(storeProducts)
                }
            },
            onError = {
                Toast.makeText(context, "Ürünler yüklenirken hata oluştu.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setupFeaturedProducts(storeProducts: List<Product>) {
        // HATA GİDERİLDİ: Lambda parametresi 'allStores' olarak isimlendirildi
        DataManager.fetchStoresSmart(
            requireContext(),
            onSuccess = { allStores ->
                // find, List üzerinde çalışır ve Store? döner
                val thisStore = allStores.find { it.id == storeId }

                if (thisStore != null) {
                    var storeChoiceList = emptyList<Product>()

                    // featuredProductIds bir List<Int>? olabilir, null check veya empty check
                    if (thisStore.featuredProductIds.isNotEmpty()) {
                        storeChoiceList = storeProducts.filter { thisStore.featuredProductIds.contains(it.id) }
                    }

                    if (storeChoiceList.size < 2) {
                        storeChoiceList = storeProducts.takeLast(4).take(2)
                    }

                    if (storeChoiceList.isNotEmpty()) {
                        binding.layoutStoreChoice.visibility = View.VISIBLE
                        storeChoiceAdapter = ProductAdapter { product -> openProductDetail(product) }
                        binding.rvStoreChoice.adapter = storeChoiceAdapter
                        storeChoiceAdapter.submitList(storeChoiceList) // List kabul eder
                    } else {
                        binding.layoutStoreChoice.visibility = View.GONE
                    }

                    val userChoiceList = storeProducts.sortedWith(
                        compareByDescending<Product> { it.favoriteCount }
                            .thenByDescending { it.clickCount }
                    ).take(2)

                    if (userChoiceList.isNotEmpty()) {
                        binding.layoutUserChoice.visibility = View.VISIBLE
                        userChoiceAdapter = ProductAdapter { product -> openProductDetail(product) }
                        binding.rvUserChoice.adapter = userChoiceAdapter
                        userChoiceAdapter.submitList(userChoiceList) // List kabul eder
                    } else {
                        binding.layoutUserChoice.visibility = View.GONE
                    }
                }
            },
            onError = { }
        )
    }

    private fun fetchLatestAnnouncement() {
        // Bu kısım canlı kalabilir, mağaza başına 1 sorgu çok yük bindirmez.
        db.collection("announcements")
            .whereEqualTo("type", "store_update")
            .whereEqualTo("relatedId", storeId.toString())
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val latestDoc = docs.map { it.toObject(NotificationItem::class.java) }
                        .sortedByDescending { it.date }
                        .firstOrNull()

                    if (latestDoc != null && latestDoc.message.isNotEmpty()) {
                        currentAnnouncement = latestDoc
                        binding.tvStoreAnnouncement.text = latestDoc.message
                        binding.cardStoreAnnouncement.visibility = View.VISIBLE
                    } else {
                        binding.cardStoreAnnouncement.visibility = View.GONE
                    }
                } else {
                    binding.cardStoreAnnouncement.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                binding.cardStoreAnnouncement.visibility = View.GONE
            }
    }

    private fun setupFollowButton() {
        fun updateFollowButtonState() {
            if (FavoritesManager.isFollowing(storeId)) {
                binding.btnFollowStore.text = "Takip Ediliyor"
                binding.btnFollowStore.setIconResource(R.drawable.ic_heart_filled)
                binding.btnFollowStore.setBackgroundColor(Color.GRAY)
            } else {
                binding.btnFollowStore.text = "Takip Et"
                binding.btnFollowStore.setIconResource(android.R.drawable.ic_input_add)
                binding.btnFollowStore.setBackgroundColor(Color.parseColor("#FF6F00"))
            }
        }
        updateFollowButtonState()

        binding.btnFollowStore.setOnClickListener {
            if (!UserManager.isLoggedIn()) {
                Toast.makeText(context, "Giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (FavoritesManager.isFollowing(storeId)) {
                FavoritesManager.unfollowStore(storeId) { updateFollowButtonState() }
            } else {
                FavoritesManager.followStore(storeId) { updateFollowButtonState() }
            }
        }
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

    private fun groupProductsByCategory(products: List<Product>) {
        categorySectionList.clear()
        val groupedMap = products.groupBy { it.category }
        for ((categoryName, productList) in groupedMap) {
            categorySectionList.add(CategorySection(categoryName, productList, false))
        }
        categoryAdapter = CategoryAdapter(requireContext(), categorySectionList) { clickedProduct ->
            openProductDetail(clickedProduct)
        }
        binding.rvProducts.adapter = categoryAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// --- YARDIMCI ADAPTER ---

data class CategorySection(val categoryName: String, val products: List<Product>, var isExpanded: Boolean = false)

class CategoryAdapter(
    private val context: Context,
    private val categoryList: List<CategorySection>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(val binding: ItemCategoryGroupBinding)
        : RecyclerView.ViewHolder(binding.root) {
        val innerAdapter = ProductAdapter(onProductClick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val section = categoryList[position]
        holder.binding.tvCategoryTitle.text = "${section.categoryName} (${section.products.size})"

        holder.binding.rvInnerProducts.visibility = if (section.isExpanded) View.VISIBLE else View.GONE
        holder.binding.imgExpandIcon.rotation = if (section.isExpanded) 180f else 0f

        holder.binding.rvInnerProducts.layoutManager = GridLayoutManager(context, 2)
        holder.binding.rvInnerProducts.adapter = holder.innerAdapter
        // HATA GİDERİLDİ: List kullanımı
        holder.innerAdapter.submitList(section.products)

        holder.binding.rvInnerProducts.isNestedScrollingEnabled = false

        holder.binding.layoutCategoryHeader.setOnClickListener {
            section.isExpanded = !section.isExpanded
            notifyItemChanged(position)
        }
    }
    override fun getItemCount() = categoryList.size
}