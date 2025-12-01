package com.example.mobiliyum

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class StoreDetailFragment : Fragment() {

    // RecyclerViews
    private lateinit var rvCategories: RecyclerView
    private lateinit var rvUserChoice: RecyclerView
    private lateinit var rvStoreChoice: RecyclerView

    // Adapters
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var userChoiceAdapter: ProductAdapter
    private lateinit var storeChoiceAdapter: ProductAdapter

    // Layouts
    private lateinit var layoutUserChoice: LinearLayout
    private lateinit var layoutStoreChoice: LinearLayout

    // Duyuru
    private lateinit var cardAnnouncement: CardView
    private lateinit var tvAnnouncement: TextView
    private lateinit var btnSeeAllAnnouncements: TextView
    private lateinit var btnAnnouncementsPage: com.google.android.material.button.MaterialButton

    private var categorySectionList = ArrayList<CategorySection>()

    private var storeId: Int = 0
    private var storeName: String? = null
    private var storeImage: String? = null
    private var storeLocation: String? = null

    private val db = FirebaseFirestore.getInstance()

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
    ): View? {
        val view = inflater.inflate(R.layout.fragment_store_detail, container, false)

        val imgLogo = view.findViewById<ImageView>(R.id.imgDetailLogo)
        val tvName = view.findViewById<TextView>(R.id.tvDetailName)
        val tvLocation = view.findViewById<TextView>(R.id.tvDetailLocation)

        rvCategories = view.findViewById(R.id.rvProducts)
        rvUserChoice = view.findViewById(R.id.rvUserChoice)
        rvStoreChoice = view.findViewById(R.id.rvStoreChoice)

        layoutUserChoice = view.findViewById(R.id.layoutUserChoice)
        layoutStoreChoice = view.findViewById(R.id.layoutStoreChoice)

        cardAnnouncement = view.findViewById(R.id.cardStoreAnnouncement)
        tvAnnouncement = view.findViewById(R.id.tvStoreAnnouncement)
        btnSeeAllAnnouncements = view.findViewById(R.id.btnSeeAllAnnouncements)
        btnAnnouncementsPage = view.findViewById(R.id.btnStoreAnnouncementsPage)

        tvName.text = storeName
        tvLocation.text = storeLocation
        if (storeImage != null && storeImage!!.isNotEmpty()) {
            Glide.with(this).load(storeImage).into(imgLogo)
        }

        rvCategories.layoutManager = LinearLayoutManager(context)
        rvUserChoice.layoutManager = GridLayoutManager(context, 2)
        rvStoreChoice.layoutManager = GridLayoutManager(context, 2)

        rvCategories.isNestedScrollingEnabled = false
        rvUserChoice.isNestedScrollingEnabled = false
        rvStoreChoice.isNestedScrollingEnabled = false

        setupFollowButton(view)

        if (storeId != 0) {
            fetchStoreProducts()
            fetchLatestAnnouncement()
        }

        btnAnnouncementsPage.setOnClickListener { openAnnouncementPage() }
        btnSeeAllAnnouncements.setOnClickListener { openAnnouncementPage() }

        return view
    }

    private fun openAnnouncementPage() {
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

    private fun setupFollowButton(view: View) {
        val btnFollow = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFollowStore)
        fun updateFollowButton() {
            if (FavoritesManager.isFollowing(storeId)) {
                btnFollow.text = "Takip Ediliyor"
                btnFollow.setIconResource(R.drawable.ic_heart_filled)
                btnFollow.setBackgroundColor(Color.GRAY)
            } else {
                btnFollow.text = "Takip Et"
                btnFollow.setIconResource(android.R.drawable.ic_input_add)
                btnFollow.setBackgroundColor(Color.parseColor("#FF6F00"))
            }
        }
        updateFollowButton()

        btnFollow.setOnClickListener {
            if (!UserManager.isLoggedIn()) {
                Toast.makeText(context, "Giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (FavoritesManager.isFollowing(storeId)) {
                FavoritesManager.unfollowStore(storeId) { updateFollowButton() }
            } else {
                FavoritesManager.followStore(storeId) { updateFollowButton() }
            }
        }
    }

    private fun fetchStoreProducts() {
        db.collection("products")
            .whereEqualTo("storeId", storeId)
            .get()
            .addOnSuccessListener { documents ->
                val allProducts = ArrayList<Product>()
                for (doc in documents) {
                    allProducts.add(doc.toObject(Product::class.java))
                }
                if (allProducts.isNotEmpty()) {
                    groupProductsByCategory(allProducts)
                    setupFeaturedProducts(allProducts)
                }
            }
    }

    // --- DUYURU ÇEKME (DÜZELTİLDİ: Index hatasını önlemek için orderBy kaldırıldı) ---
    private fun fetchLatestAnnouncement() {
        db.collection("announcements")
            .whereEqualTo("type", "store_update")
            .whereEqualTo("relatedId", storeId.toString())
            .get() // Sadece filtrele, sıralamayı burada yapma
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    // Kod tarafında tarihe göre sırala ve ilkini al
                    val latestDoc = docs.map { it.toObject(NotificationItem::class.java) }
                        .sortedByDescending { it.date }
                        .firstOrNull()

                    if (latestDoc != null && latestDoc.message.isNotEmpty()) {
                        tvAnnouncement.text = latestDoc.message
                        cardAnnouncement.visibility = View.VISIBLE
                    } else {
                        cardAnnouncement.visibility = View.GONE
                    }
                } else {
                    cardAnnouncement.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                cardAnnouncement.visibility = View.GONE
            }
    }

    private fun setupFeaturedProducts(products: List<Product>) {
        val userChoiceList = products.sortedWith(compareByDescending<Product> { it.favoriteCount }.thenByDescending { it.clickCount }).take(2)
        if (userChoiceList.isNotEmpty()) {
            layoutUserChoice.visibility = View.VISIBLE
            userChoiceAdapter = ProductAdapter(userChoiceList) { product -> openProductDetail(product) }
            rvUserChoice.adapter = userChoiceAdapter
        }

        val storeChoiceList = products.takeLast(4).filter { !userChoiceList.contains(it) }.take(2)
        if (storeChoiceList.isNotEmpty()) {
            layoutStoreChoice.visibility = View.VISIBLE
            storeChoiceAdapter = ProductAdapter(storeChoiceList) { product -> openProductDetail(product) }
            rvStoreChoice.adapter = storeChoiceAdapter
        }
    }

    private fun openProductDetail(product: Product) {
        val detailFragment = ProductDetailFragment()
        val bundle = Bundle()
        bundle.putSerializable("product_data", product)
        detailFragment.arguments = bundle
        parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, detailFragment).addToBackStack(null).commit()
    }

    private fun groupProductsByCategory(products: List<Product>) {
        categorySectionList.clear()
        val groupedMap = products.groupBy { it.category }
        for ((categoryName, productList) in groupedMap) {
            val isExpanded = groupedMap.size <= 2
            categorySectionList.add(CategorySection(categoryName, productList, isExpanded))
        }
        categoryAdapter = CategoryAdapter(requireContext(), categorySectionList) { clickedProduct ->
            openProductDetail(clickedProduct)
        }
        rvCategories.adapter = categoryAdapter
    }
}

// --- YARDIMCI SINIFLAR ---
data class CategorySection(val categoryName: String, val products: List<Product>, var isExpanded: Boolean = false)

class CategoryAdapter(private val context: Context, private val categoryList: List<CategorySection>, private val onProductClick: (Product) -> Unit) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val btnExpand: ConstraintLayout = itemView.findViewById(R.id.layoutCategoryHeader)
        val imgArrow: ImageView = itemView.findViewById(R.id.imgExpandIcon)
        val rvProducts: RecyclerView = itemView.findViewById(R.id.rvInnerProducts)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CategoryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_category_group, parent, false))
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val section = categoryList[position]
        holder.tvTitle.text = "${section.categoryName} (${section.products.size})"
        holder.rvProducts.visibility = if (section.isExpanded) View.VISIBLE else View.GONE
        holder.imgArrow.rotation = if (section.isExpanded) 180f else 0f
        holder.rvProducts.layoutManager = GridLayoutManager(context, 2)
        holder.rvProducts.adapter = ProductAdapter(section.products, onProductClick)
        holder.rvProducts.isNestedScrollingEnabled = false
        holder.btnExpand.setOnClickListener { section.isExpanded = !section.isExpanded; notifyItemChanged(position) }
    }
    override fun getItemCount() = categoryList.size
}