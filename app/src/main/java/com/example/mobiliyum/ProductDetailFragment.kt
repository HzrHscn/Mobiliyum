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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class ProductDetailFragment : Fragment() {
    private var currentProduct: Product? = null

    private lateinit var layoutFavorite: LinearLayout
    private lateinit var imgFavoriteIcon: ImageView
    private lateinit var tvFavoriteText: TextView
    private lateinit var btnGoToStore: MaterialButton
    private lateinit var btnAddToCart: MaterialButton
    private lateinit var imgCategoryIcon: ImageView

    // AÇIKLAMA ALANI (Backend Kısmı)
    private lateinit var tvProductDescription: TextView
    private lateinit var tvDescriptionToggle: TextView
    private var isDescriptionExpanded = false

    private lateinit var rvReviews: RecyclerView
    private lateinit var btnAddReview: MaterialButton
    private lateinit var tvAvgRating: TextView
    private lateinit var rbProductAvg: RatingBar
    private lateinit var tvRatingCount: TextView
    private lateinit var tvNoReviews: TextView
    private lateinit var btnSeeAllReviews: TextView
    private lateinit var btnHideReviews: TextView
    private lateinit var tvBestReviewTitle: TextView

    private var allReviewsList = listOf<Review>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentProduct = it.getSerializable("product_data") as Product?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_product_detail, container, false)

        if (currentProduct == null) return view

        incrementClickCount()

        // View Tanımlamaları
        val imgProduct = view.findViewById<ImageView>(R.id.imgProductDetail)
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvPrice = view.findViewById<TextView>(R.id.tvProductPrice)
        val tvCategory = view.findViewById<TextView>(R.id.tvProductCategory)

        // Açıklama Tanımlamaları
        tvProductDescription = view.findViewById(R.id.tvProductDescription)
        tvDescriptionToggle = view.findViewById(R.id.tvDescriptionToggle)

        btnAddToCart = view.findViewById(R.id.btnAddToCart)
        btnGoToStore = view.findViewById(R.id.btnGoToStore)
        imgCategoryIcon = view.findViewById(R.id.imgCategoryIcon)
        layoutFavorite = view.findViewById(R.id.layoutFavorite)
        imgFavoriteIcon = view.findViewById(R.id.imgFavoriteIcon)
        tvFavoriteText = view.findViewById(R.id.tvFavoriteText)

        rvReviews = view.findViewById(R.id.rvReviews)
        btnAddReview = view.findViewById(R.id.btnAddReview)
        tvAvgRating = view.findViewById(R.id.tvAvgRating)
        rbProductAvg = view.findViewById(R.id.rbProductAvg)
        tvRatingCount = view.findViewById(R.id.tvRatingCount)
        tvNoReviews = view.findViewById(R.id.tvNoReviews)
        btnSeeAllReviews = view.findViewById(R.id.btnSeeAllReviews)
        btnHideReviews = view.findViewById(R.id.btnHideReviews)
        tvBestReviewTitle = view.findViewById(R.id.tvBestReviewTitle)

        rvReviews.layoutManager = LinearLayoutManager(context)

        // Verileri Yerleştir
        tvName.text = currentProduct!!.name
        //tvCategory.text = currentProduct!!.category
        tvPrice.text = PriceUtils.formatPriceStyled(currentProduct!!.price)
        Glide.with(this).load(currentProduct!!.imageUrl).into(imgProduct)

        imgProduct.setOnClickListener { showZoomImageDialog(currentProduct!!.imageUrl) }

        setupCategoryIcon(currentProduct!!.category)

        // Açıklamayı Hazırla (Backend Kısmı)
        setupDescription(currentProduct!!.description)

        refreshProductData()

        if (UserManager.canEditProduct(currentProduct!!)) {
            btnAddToCart.text = "Ürünü Düzenle"
            btnAddToCart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
            btnAddToCart.setOnClickListener { showEditOptionsDialog() }
        } else {
            btnAddToCart.setOnClickListener {
                CartManager.addToCart(currentProduct!!)
                Toast.makeText(context, "Sepete eklendi", Toast.LENGTH_SHORT).show()
            }
        }

        updateFavoriteUI(FavoritesManager.isFavorite(currentProduct!!.id))
        layoutFavorite.setOnClickListener {
            if (!UserManager.isLoggedIn()) {
                Toast.makeText(context, "Giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FavoritesManager.toggleFavorite(currentProduct!!) { isFav -> updateFavoriteUI(isFav) }
        }

        btnGoToStore.setOnClickListener {
            val url = currentProduct!!.productUrl
            if (url.isNotEmpty()) openWebsite(url)
        }

        btnAddReview.setOnClickListener { handleReviewClick() }

        btnSeeAllReviews.setOnClickListener {
            rvReviews.adapter = ReviewAdapter(allReviewsList)
            btnSeeAllReviews.visibility = View.GONE
            btnHideReviews.visibility = View.VISIBLE
            tvBestReviewTitle.visibility = View.GONE
        }

        btnHideReviews.setOnClickListener {
            setupReviews()
            btnHideReviews.visibility = View.GONE
        }

        return view
    }

    // --- AÇIKLAMA GİZLE/GÖSTER MANTIĞI ---
    private fun setupDescription(desc: String?) {
        val text = if (desc.isNullOrEmpty()) "Bu ürün için henüz bir açıklama girilmemiş." else desc
        tvProductDescription.text = text

        // Başlangıç ayarları
        isDescriptionExpanded = false
        tvProductDescription.maxLines = 2
        tvDescriptionToggle.visibility = View.GONE

        // Metin yüklendikten sonra satır sayısını kontrol et
        tvProductDescription.post {
            if (tvProductDescription.lineCount > 1) {
                tvDescriptionToggle.visibility = View.VISIBLE
                tvDescriptionToggle.text = "Açıklamanın Tümünü Gör"

                tvDescriptionToggle.setOnClickListener {
                    if (isDescriptionExpanded) {
                        // Kapat
                        tvProductDescription.maxLines = 2
                        tvDescriptionToggle.text = "Açıklamanın Tümünü Gör"
                    } else {
                        // Aç
                        tvProductDescription.maxLines = Int.MAX_VALUE
                        tvDescriptionToggle.text = "Açıklamayı Gizle"
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
                    tvAvgRating.text = String.format("%.1f", rating)
                    rbProductAvg.rating = rating
                    tvRatingCount.text = "($count Değerlendirme)"
                    setupReviews()
                }
            }
    }

    private fun setupReviews() {
        ReviewManager.getReviews(currentProduct!!.id) { reviews ->
            allReviewsList = reviews
            if (reviews.isEmpty()) {
                tvNoReviews.visibility = View.VISIBLE
                rvReviews.visibility = View.GONE
                btnSeeAllReviews.visibility = View.GONE
                btnHideReviews.visibility = View.GONE
                tvBestReviewTitle.visibility = View.GONE
            } else {
                tvNoReviews.visibility = View.GONE
                rvReviews.visibility = View.VISIBLE
                val fiveStarReviews = reviews.filter { it.rating == 5f }
                if (reviews.size > 1) {
                    btnSeeAllReviews.visibility = View.VISIBLE
                    btnSeeAllReviews.text = "Tüm Yorumları Gör (${reviews.size})"
                    btnHideReviews.visibility = View.GONE
                    val featuredList = ArrayList<Review>()
                    if (fiveStarReviews.isNotEmpty()) {
                        featuredList.add(fiveStarReviews.random())
                        tvBestReviewTitle.visibility = View.VISIBLE
                    } else {
                        featuredList.add(reviews[0])
                        tvBestReviewTitle.visibility = View.GONE
                    }
                    rvReviews.adapter = ReviewAdapter(featuredList)
                } else {
                    btnSeeAllReviews.visibility = View.GONE
                    btnHideReviews.visibility = View.GONE
                    tvBestReviewTitle.visibility = View.GONE
                    rvReviews.adapter = ReviewAdapter(reviews)
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
        AlertDialog.Builder(context).setView(dialogView).setPositiveButton("Yayınla") { _, _ ->
            val rating = ratingBar.rating
            val comment = etComment.text.toString()
            if (rating > 0) {
                ReviewManager.addReview(currentProduct!!, rating, comment) { success ->
                    if (success) {
                        Toast.makeText(context, "Teşekkürler!", Toast.LENGTH_SHORT).show()
                        refreshProductData()
                    }
                }
            }
        }.setNegativeButton("Vazgeç", null).show()
    }

    private fun showEditOptionsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_options, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

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
            gravity = android.view.Gravity.TOP
            filters = arrayOf(InputFilter.LengthFilter(500))
        }

        layout.addView(etName)
        layout.addView(etDesc)

        AlertDialog.Builder(context)
            .setTitle("Bilgileri Güncelle")
            .setView(layout)
            .setPositiveButton("Kaydet") { _, _ ->
                val newName = etName.text.toString()
                val newDesc = etDesc.text.toString()

                if (newName.isNotEmpty()) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("products").document(currentProduct!!.id.toString())
                        .update(mapOf(
                            "name" to newName,
                            "description" to newDesc
                        ))
                        .addOnSuccessListener {
                            Toast.makeText(context, "Güncellendi!", Toast.LENGTH_SHORT).show()
                            view?.findViewById<TextView>(R.id.tvProductName)?.text = newName
                            setupDescription(newDesc)
                            currentProduct = currentProduct?.copy(name = newName, description = newDesc)
                        }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun updateFavoriteUI(isFavorite: Boolean) {
        if (isFavorite) {
            imgFavoriteIcon.setImageResource(R.drawable.ic_heart_filled)
            tvFavoriteText.text = "Favorilerden Çıkar"
        } else {
            imgFavoriteIcon.setImageResource(R.drawable.ic_heart_outline)
            tvFavoriteText.text = "Favorilere Ekle"
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
            lower.contains("koltuk") || lower.contains("köşe") || lower.contains("sofa") -> R.drawable.oturmaodalogo
            lower.contains("yemek") -> R.drawable.yemekodalogo
            lower.contains("genç") || lower.contains("bebek") -> R.drawable.cocukodalogo
            lower.contains("ofis") || lower.contains("makam") -> R.drawable.ofislogo
            lower.contains("tv") || lower.contains("ünite") -> R.drawable.tvlogo
            else -> android.R.drawable.ic_menu_sort_by_size
        }
        imgCategoryIcon.setImageResource(iconRes)
        imgCategoryIcon.imageTintList = null
    }

    inner class ReviewAdapter(private val items: List<Review>) : RecyclerView.Adapter<ReviewAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvReviewerName)
            val comment: TextView = v.findViewById(R.id.tvReviewComment)
            val rating: RatingBar = v.findViewById(R.id.rbReview)
            val verified: View = v.findViewById(R.id.imgVerified)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.userName
            holder.comment.text = item.comment
            holder.rating.rating = item.rating
            holder.verified.visibility = if(item.isVerified) View.VISIBLE else View.GONE
        }
        override fun getItemCount() = items.size
    }
}