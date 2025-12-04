package com.example.mobiliyum

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.FragmentProductDetailBinding
import com.example.mobiliyum.databinding.ItemReviewBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private var currentProduct: Product? = null
    private var isDescriptionExpanded = false
    private var allReviewsList = listOf<Review>()

    // Modern Adapter Tanımlaması
    private lateinit var reviewAdapter: ReviewAdapter

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (currentProduct == null) return

        incrementClickCount()

        binding.rvReviews.layoutManager = LinearLayoutManager(context)

        // Adapter Başlatma (Boş constructor)
        reviewAdapter = ReviewAdapter()
        binding.rvReviews.adapter = reviewAdapter

        // Verileri Yerleştir
        binding.tvProductName.text = currentProduct!!.name
        binding.tvProductPrice.text = PriceUtils.formatPriceStyled(currentProduct!!.price)
        Glide.with(this).load(currentProduct!!.imageUrl).into(binding.imgProductDetail)

        binding.imgProductDetail.setOnClickListener { showZoomImageDialog(currentProduct!!.imageUrl) }

        setupCategoryIcon(currentProduct!!.category)
        setupDescription(currentProduct!!.description)
        refreshProductData()

        if (UserManager.canEditProduct(currentProduct!!)) {
            binding.btnAddToCart.text = "Ürünü Düzenle"
            binding.btnAddToCart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
            binding.btnAddToCart.setOnClickListener { showEditOptionsDialog() }
        } else {
            binding.btnAddToCart.setOnClickListener {
                CartManager.addToCart(currentProduct!!)
                Toast.makeText(context, "Sepete eklendi", Toast.LENGTH_SHORT).show()
            }
        }

        updateFavoriteUI(FavoritesManager.isFavorite(currentProduct!!.id))
        binding.layoutFavorite.setOnClickListener {
            if (!UserManager.isLoggedIn()) {
                Toast.makeText(context, "Giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FavoritesManager.toggleFavorite(currentProduct!!) { isFav -> updateFavoriteUI(isFav) }
        }

        binding.btnGoToStore.setOnClickListener {
            val url = currentProduct!!.productUrl
            if (url.isNotEmpty()) openWebsite(url)
        }

        binding.btnAddReview.setOnClickListener { handleReviewClick() }

        binding.btnSeeAllReviews.setOnClickListener {
            // TÜMÜNÜ GÖSTER
            reviewAdapter.submitList(allReviewsList)
            binding.btnSeeAllReviews.visibility = View.GONE
            binding.btnHideReviews.visibility = View.VISIBLE
            binding.tvBestReviewTitle.visibility = View.GONE
        }

        binding.btnHideReviews.setOnClickListener {
            setupReviews() // ÖZET GÖSTER
            binding.btnHideReviews.visibility = View.GONE
        }
    }

    private fun setupDescription(desc: String?) {
        val text = if (desc.isNullOrEmpty()) "Bu ürün için henüz bir açıklama girilmemiş." else desc
        binding.tvProductDescription.text = text

        isDescriptionExpanded = false
        binding.tvProductDescription.maxLines = 2
        binding.tvDescriptionToggle.visibility = View.GONE

        binding.tvProductDescription.post {
            if (binding.tvProductDescription.lineCount > 1) {
                binding.tvDescriptionToggle.visibility = View.VISIBLE
                binding.tvDescriptionToggle.text = "Açıklamanın Tümünü Gör"

                binding.tvDescriptionToggle.setOnClickListener {
                    if (isDescriptionExpanded) {
                        binding.tvProductDescription.maxLines = 2
                        binding.tvDescriptionToggle.text = "Açıklamanın Tümünü Gör"
                    } else {
                        binding.tvProductDescription.maxLines = Int.MAX_VALUE
                        binding.tvDescriptionToggle.text = "Açıklamayı Gizle"
                    }
                    isDescriptionExpanded = !isDescriptionExpanded
                }
            }
        }
    }

    private fun incrementClickCount() {
        val db = FirebaseFirestore.getInstance()
        db.collection("products").document(currentProduct!!.id.toString())
            .update("clickCount", FieldValue.increment(1))
    }

    private fun showZoomImageDialog(imageUrl: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val zoomView = ZoomableImageView(requireContext())
        zoomView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        Glide.with(this).load(imageUrl).into(zoomView)
        dialog.setContentView(zoomView)
        dialog.show()
    }

    private fun refreshProductData() {
        val db = FirebaseFirestore.getInstance()
        db.collection("products").document(currentProduct!!.id.toString()).get()
            .addOnSuccessListener { document ->
                val freshProduct = document.toObject(Product::class.java)
                if (freshProduct != null) {
                    currentProduct = freshProduct
                    setupDescription(freshProduct.description)
                    val rating = freshProduct.rating
                    val count = freshProduct.reviewCount
                    binding.tvAvgRating.text = String.format("%.1f", rating)
                    binding.rbProductAvg.rating = rating
                    binding.tvRatingCount.text = "($count Değerlendirme)"
                    setupReviews()
                }
            }
    }

    private fun setupReviews() {
        ReviewManager.getReviews(currentProduct!!.id) { reviews ->
            allReviewsList = reviews
            if (reviews.isEmpty()) {
                binding.tvNoReviews.visibility = View.VISIBLE
                binding.rvReviews.visibility = View.GONE
                binding.btnSeeAllReviews.visibility = View.GONE
                binding.btnHideReviews.visibility = View.GONE
                binding.tvBestReviewTitle.visibility = View.GONE
            } else {
                binding.tvNoReviews.visibility = View.GONE
                binding.rvReviews.visibility = View.VISIBLE

                val fiveStarReviews = reviews.filter { it.rating == 5f }

                if (reviews.size > 1) {
                    binding.btnSeeAllReviews.visibility = View.VISIBLE
                    binding.btnSeeAllReviews.text = "Tüm Yorumları Gör (${reviews.size})"
                    binding.btnHideReviews.visibility = View.GONE

                    val featuredList = ArrayList<Review>()
                    if (fiveStarReviews.isNotEmpty()) {
                        featuredList.add(fiveStarReviews.random())
                        binding.tvBestReviewTitle.visibility = View.VISIBLE
                    } else {
                        featuredList.add(reviews[0])
                        binding.tvBestReviewTitle.visibility = View.GONE
                    }
                    // ÖZET GÖSTER
                    reviewAdapter.submitList(featuredList)
                } else {
                    binding.btnSeeAllReviews.visibility = View.GONE
                    binding.btnHideReviews.visibility = View.GONE
                    binding.tvBestReviewTitle.visibility = View.GONE
                    // HEPSİNİ GÖSTER
                    reviewAdapter.submitList(reviews)
                }
            }
        }
    }

    private fun handleReviewClick() {
        if (!UserManager.isLoggedIn()) {
            Toast.makeText(context, "Puan vermek için giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
            return
        }
        ReviewManager.checkReviewPermission(currentProduct!!.id) { hasPermission ->
            if (hasPermission) showAddReviewDialog()
            else showRequestDialog()
        }
    }

    private fun showRequestDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_request_verification, null)
        val etInfo = dialogView.findViewById<TextInputEditText>(R.id.etOrderInfo)
        AlertDialog.Builder(context).setView(dialogView).setPositiveButton("Gönder") { _, _ ->
            val info = etInfo.text.toString()
            if (info.isNotEmpty()) {
                ReviewManager.requestPurchaseVerification(currentProduct!!, info) { success ->
                    if (success) Toast.makeText(context, "Talep gönderildi.", Toast.LENGTH_LONG).show()
                    else Toast.makeText(context, "Hata.", Toast.LENGTH_SHORT).show()
                }
            }
        }.setNegativeButton("İptal", null).show()
    }

    private fun showAddReviewDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.rbUserRating)
        val etComment = dialogView.findViewById<TextInputEditText>(R.id.etUserComment)

        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Yayınla") { _, _ ->
                val rating = ratingBar.rating
                val comment = etComment.text.toString()

                if (rating > 0) {
                    ReviewManager.addReview(currentProduct!!, rating, comment) { success ->
                        if (success) {
                            Toast.makeText(context, "Yorumunuz başarıyla yayınlandı!", Toast.LENGTH_SHORT).show()
                            refreshProductData()
                        } else {
                            Toast.makeText(context, "Hata oluştu.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Lütfen puan veriniz.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun showEditOptionsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_options, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogView.findViewById<View>(R.id.cardOptionPrice).setOnClickListener {
            dialog.dismiss()
            showUpdatePriceDialog()
        }
        dialogView.findViewById<View>(R.id.cardOptionInfo).setOnClickListener {
            dialog.dismiss()
            showUpdateInfoDialog()
        }
        dialog.show()
    }

    private fun showUpdatePriceDialog() {
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Yeni Fiyat"
        AlertDialog.Builder(context).setTitle("Fiyat Güncelle").setView(input).setPositiveButton("Güncelle") { _, _ ->
            val newPriceStr = input.text.toString()
            if (newPriceStr.isNotEmpty()) updateProductPriceInFirebase(newPriceStr)
        }.setNegativeButton("İptal", null).show()
    }

    private fun updateProductPriceInFirebase(newPriceRaw: String) {
        val db = FirebaseFirestore.getInstance()
        val productRef = db.collection("products").document(currentProduct!!.id.toString())
        val newPriceFormatted = PriceUtils.formatPriceStyled(newPriceRaw.toDoubleOrNull() ?: 0.0).toString()
        val currentPriceDouble = PriceUtils.parsePrice(currentProduct!!.price)
        val historyMap = HashMap<String, Double>()
        historyMap.putAll(currentProduct!!.priceHistory)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(Date())
        historyMap[today] = currentPriceDouble
        val updates = hashMapOf<String, Any>("price" to newPriceFormatted, "oldPrice" to currentProduct!!.price, "priceHistory" to historyMap)
        productRef.update(updates).addOnSuccessListener {
            Toast.makeText(context, "Fiyat güncellendi", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun showUpdateInfoDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val etName = EditText(context).apply {
            hint = "Ürün Adı"
            setText(currentProduct?.name)
            setPadding(0, 0, 0, 32)
        }
        val etDesc = EditText(context).apply {
            hint = "Açıklama (Max 500 karakter)"
            setText(currentProduct?.description)
            minLines = 4
            gravity = Gravity.TOP
            filters = arrayOf(InputFilter.LengthFilter(500))
        }
        layout.addView(etName)
        layout.addView(etDesc)

        AlertDialog.Builder(context).setTitle("Bilgileri Güncelle").setView(layout).setPositiveButton("Kaydet") { _, _ ->
            val newName = etName.text.toString()
            val newDesc = etDesc.text.toString()
            if (newName.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                db.collection("products").document(currentProduct!!.id.toString())
                    .update(mapOf("name" to newName, "description" to newDesc))
                    .addOnSuccessListener {
                        Toast.makeText(context, "Güncellendi!", Toast.LENGTH_SHORT).show()
                        binding.tvProductName.text = newName
                        setupDescription(newDesc)
                        currentProduct = currentProduct?.copy(name = newName, description = newDesc)
                    }
            }
        }.setNegativeButton("İptal", null).show()
    }

    private fun updateFavoriteUI(isFavorite: Boolean) {
        if (isFavorite) {
            binding.imgFavoriteIcon.setImageResource(R.drawable.ic_heart_filled)
            binding.tvFavoriteText.text = "Favorilerden Çıkar"
        } else {
            binding.imgFavoriteIcon.setImageResource(R.drawable.ic_heart_outline)
            binding.tvFavoriteText.text = "Favorilere Ekle"
        }
    }

    private fun openWebsite(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    }

    private fun setupCategoryIcon(catName: String) {
        val lower = catName.lowercase()
        val iconRes = when {
            lower.contains("yatak odası") -> R.drawable.yatakodalogo
            lower.contains("yatak") -> R.drawable.yataklogo
            lower.contains("koltuk") || lower.contains("köşe") || lower.contains("sofa") || lower.contains("oturma") -> R.drawable.oturmaodalogo
            lower.contains("yemek") -> R.drawable.yemekodalogo
            lower.contains("genç") || lower.contains("bebek") -> R.drawable.cocukodalogo
            lower.contains("ofis") || lower.contains("makam") -> R.drawable.ofislogo
            lower.contains("tv") || lower.contains("ünite") -> R.drawable.tvlogo
            else -> android.R.drawable.ic_menu_sort_by_size
        }
        binding.imgCategoryIcon.setImageResource(iconRes)
        binding.imgCategoryIcon.imageTintList = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- MODERN ADAPTER (ListAdapter + DiffUtil + ViewBinding) ---
    class ReviewAdapter : ListAdapter<Review, ReviewAdapter.VH>(ReviewDiffCallback()) {

        class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
        }

        inner class VH(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.binding.tvReviewerName.text = item.userName
            holder.binding.tvReviewComment.text = item.comment
            holder.binding.rbReview.rating = item.rating
            holder.binding.imgVerified.visibility = if(item.isVerified) View.VISIBLE else View.GONE
        }
    }
}