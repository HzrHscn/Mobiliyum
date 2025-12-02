package com.example.mobiliyum

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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

    // Review
    private lateinit var rvReviews: RecyclerView
    private lateinit var btnAddReview: MaterialButton
    private lateinit var tvAvgRating: TextView
    private lateinit var rbProductAvg: RatingBar
    private lateinit var tvRatingCount: TextView
    private lateinit var tvNoReviews: TextView
    private lateinit var btnSeeAllReviews: TextView
    private lateinit var btnHideReviews: TextView // YENİ
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

        // View Bağlantıları
        val imgProduct = view.findViewById<ImageView>(R.id.imgProductDetail)
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvCategory = view.findViewById<TextView>(R.id.tvProductCategory)
        val tvPrice = view.findViewById<TextView>(R.id.tvProductPrice)

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

        tvName.text = currentProduct!!.name
        //tvCategory.text = currentProduct!!.category
        tvPrice.text = PriceUtils.formatPriceStyled(currentProduct!!.price)
        Glide.with(this).load(currentProduct!!.imageUrl).into(imgProduct)

        // YENİ: RESİM ZOOM (Tıklayınca Açılır)
        imgProduct.setOnClickListener {
            showZoomImageDialog(currentProduct!!.imageUrl)
        }

        setupCategoryIcon(currentProduct!!.category)
        refreshProductData()

        if (UserManager.canEditProduct(currentProduct!!)) {
            btnAddToCart.text = "Fiyatı Güncelle"
            btnAddToCart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
            btnAddToCart.setOnClickListener { showUpdatePriceDialog() }
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
            FavoritesManager.toggleFavorite(currentProduct!!) { isFav ->
                updateFavoriteUI(isFav)
            }
        }

        btnGoToStore.setOnClickListener {
            val url = currentProduct!!.productUrl
            if (url.isNotEmpty()) openWebsite(url)
        }

        btnAddReview.setOnClickListener { handleReviewClick() }

        // "Tümünü Gör" Butonu
        btnSeeAllReviews.setOnClickListener {
            rvReviews.adapter = ReviewAdapter(allReviewsList)
            btnSeeAllReviews.visibility = View.GONE
            btnHideReviews.visibility = View.VISIBLE // Gizle butonu açılır
            tvBestReviewTitle.visibility = View.GONE
        }

        // "Yorumları Gizle" Butonu
        btnHideReviews.setOnClickListener {
            setupReviews() // Başa dön (Sadece en iyiyi göster)
            btnHideReviews.visibility = View.GONE
        }

        return view
    }

    // YENİ: Zoom Dialog Fonksiyonu
    private fun showZoomImageDialog(imageUrl: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // ZoomableImageView'ı kodla oluşturup dialoga ekliyoruz
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

                // Varsayılan Görünüm: Tek Satır (En İyi Yorum)
                val fiveStarReviews = reviews.filter { it.rating == 5f }

                if (reviews.size > 1) {
                    btnSeeAllReviews.visibility = View.VISIBLE
                    btnSeeAllReviews.text = "Tüm Yorumları Gör (${reviews.size})"
                    btnHideReviews.visibility = View.GONE // Başlangıçta gizli

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

        // Kategori ismine göre ikon seçimi
        // R.drawable.yatakodaLogo gibi kendi dosya isimlerini buraya yazmalısın
        val iconRes = when {
            // Yatak Odası
            lower.contains("yatak odası") -> R.drawable.yatakodalogo

            // Yatak
            lower.contains("yatak") -> R.drawable.yataklogo // Dosya adın neyse onu yaz

            // Koltuk veya Köşe Takımları
            lower.contains("koltuk") || lower.contains("köşe") || lower.contains("sofa") -> R.drawable.oturmaodalogo

            // Yemek Odası
            lower.contains("yemek") -> R.drawable.yemekodalogo

            // Genç veya Bebek Odası
            lower.contains("genç") || lower.contains("bebek") -> R.drawable.cocukodalogo

            // Ofis ve Makam
            lower.contains("ofis") || lower.contains("makam") -> R.drawable.ofislogo

            // TV Ünitesi
            lower.contains("tv") || lower.contains("ünite") -> R.drawable.tvlogo

            // Varsayılan (Eşleşme olmazsa)
            else -> android.R.drawable.ic_menu_sort_by_size
        }

        // İkonu ayarla
        imgCategoryIcon.setImageResource(iconRes)

        // İsteğe bağlı: İkon rengini orijinal kalsın istiyorsan tint'i temizle
        // Eğer ikonların renkliyse bu satırı ekle, siyah-beyazsa sil.
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