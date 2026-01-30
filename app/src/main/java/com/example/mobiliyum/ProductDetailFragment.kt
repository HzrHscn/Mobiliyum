package com.example.mobiliyum

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var reviewAdapter: ReviewAdapter
    //private val db by lazy { FirebaseFirestore.getInstance() }
    private val db by lazy { DataManager.getDb() }
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

        //incrementClickCount() çalışıyordu ancak aşağıdaki gibi değiştirdim sil

        //savedInstanceState ?: incrementClickCount()

        if (savedInstanceState == null) {
            incrementClickCount()
        }

        binding.rvReviews.layoutManager = LinearLayoutManager(context)
        reviewAdapter = ReviewAdapter()
        binding.rvReviews.adapter = reviewAdapter

        // UI Doldur
        updateUI(currentProduct!!)

        binding.imgProductDetail.setOnClickListener { showZoomImageDialog(currentProduct!!.imageUrl) }

        // SWIPE REFRESH: Sadece bu ürünü yenile (1 Read)
        binding.swipeRefreshProduct.setOnRefreshListener {
            refreshProductData()
        }

        // --- YETKİ KONTROLÜ (Manager / Editor / Admin) ---
        if (UserManager.canEditProduct(currentProduct!!)) {
            binding.btnAddToCart.text = "Ürünü Düzenle"
            binding.btnAddToCart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
            binding.btnAddToCart.setOnClickListener { showEditOptionsDialog() }
        } else {
            binding.btnAddToCart.text = "Sepete Ekle"
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
            FavoritesManager.toggleFavorite(
                product = currentProduct!!,
                context = requireContext()
            ) { isFav ->
                updateFavoriteUI(isFav)
            }
        }

        binding.btnGoToStore.setOnClickListener {
            try {
                // Mağazaya gitmek için cache'den mağazayı bul (Query atma)
                val storeId = currentProduct!!.storeId

                android.util.Log.d("ProductDetail", "🏪 Mağazaya git tıklandı - Store ID: $storeId")

                if (storeId <= 0) {
                    Toast.makeText(context, "Geçersiz mağaza bilgisi", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val store = DataManager.cachedStores?.find { it.id == storeId }

                if (store != null) {
                    android.util.Log.d("ProductDetail", "✅ Mağaza bulundu: ${store.name}")

                    val fragment = StoreDetailFragment()
                    val bundle = Bundle()
                    // Tek tek gönderiyoruz (StoreDetailFragment yapısına uygun)
                    bundle.putInt("id", store.id)
                    bundle.putString("name", store.name)
                    bundle.putString("image", store.imageUrl)
                    bundle.putString("location", store.location)
                    fragment.arguments = bundle

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit()
                } else {
                    android.util.Log.e("ProductDetail", "❌ Mağaza cache'de bulunamadı, ID ile yönlendiriliyor")

                    // Cache'de yoksa sadece ID ile gönder, fragment kendi çeksin
                    val fragment = StoreDetailFragment()
                    val bundle = Bundle()
                    bundle.putInt("id", storeId)
                    fragment.arguments = bundle

                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, fragment)
                        .addToBackStack(null)
                        .commit()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProductDetail", "❌ Mağazaya git hatası", e)
                Toast.makeText(context, "Bir hata oluştu: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddReview.setOnClickListener { handleReviewClick() }

        binding.btnSeeAllReviews.setOnClickListener {
            reviewAdapter.submitList(allReviewsList)
            binding.btnSeeAllReviews.visibility = View.GONE
            binding.btnHideReviews.visibility = View.VISIBLE
            binding.tvBestReviewTitle.visibility = View.GONE
        }

        binding.btnHideReviews.setOnClickListener {
            setupReviews()
            binding.btnHideReviews.visibility = View.GONE
        }

        setupArButton()
    }

    private fun updateUI(product: Product) {
        binding.tvProductName.text = product.name
        binding.tvProductPrice.text = PriceUtils.formatPriceStyled(product.price)
        Glide.with(this).load(product.imageUrl).into(binding.imgProductDetail)
        setupCategoryIcon(product.category)
        setupDescription(product.description)

        binding.tvAvgRating.text = String.format("%.1f", product.rating)
        binding.rbProductAvg.rating = product.rating
        binding.tvRatingCount.text = "(${product.reviewCount} Değerlendirme)"

        setupReviews()
        setupArButton() // ✅ AR butonu her güncelleme sonrası kontrol edilsin
    }

    private fun refreshProductData() {
        // Sadece 1 Belge Okuma
        db.collection("products").document(currentProduct!!.id.toString()).get()
            .addOnSuccessListener { document ->
                val freshProduct = document.toObject(Product::class.java)
                if (freshProduct != null) {
                    currentProduct = freshProduct
                    updateUI(freshProduct)

                    if (context != null) {
                        DataManager.updateProductInCache(requireContext(), freshProduct)
                    }
                    Toast.makeText(context, "Bilgiler güncellendi", Toast.LENGTH_SHORT).show()
                }
                binding.swipeRefreshProduct.isRefreshing = false
            }
            .addOnFailureListener {
                Toast.makeText(context, "Güncelleme başarısız", Toast.LENGTH_SHORT).show()
                binding.swipeRefreshProduct.isRefreshing = false
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
                    reviewAdapter.submitList(featuredList)
                } else {
                    binding.btnSeeAllReviews.visibility = View.GONE
                    binding.btnHideReviews.visibility = View.GONE
                    binding.tvBestReviewTitle.visibility = View.GONE
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

        AlertDialog.Builder(context).setView(dialogView).setPositiveButton("Yayınla") { _, _ ->
            val rating = ratingBar.rating
            val comment = etComment.text.toString()
            if (rating > 0) {
                ReviewManager.addReview(currentProduct!!, rating, comment) { success ->
                    if (success) {
                        Toast.makeText(context, "Yorumunuz yayınlandı!", Toast.LENGTH_SHORT).show()
                        refreshProductData()
                    } else Toast.makeText(context, "Hata oluştu.", Toast.LENGTH_SHORT).show()
                }
            } else Toast.makeText(context, "Lütfen puan veriniz.", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Vazgeç", null).show()
    }

    private fun showEditOptionsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_options, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialogView.findViewById<View>(R.id.cardOptionPrice).setOnClickListener { dialog.dismiss(); showUpdatePriceDialog() }
        dialogView.findViewById<View>(R.id.cardOptionInfo).setOnClickListener { dialog.dismiss(); showUpdateInfoDialog() }
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
            refreshProductData()
        }
    }

    private fun showUpdateInfoDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        val etName = EditText(context).apply { hint = "Ürün Adı"; setText(currentProduct?.name); setPadding(0, 0, 0, 32) }
        val etDesc = EditText(context).apply { hint = "Açıklama"; setText(currentProduct?.description); minLines = 4; gravity = Gravity.TOP; filters = arrayOf(InputFilter.LengthFilter(500)) }
        layout.addView(etName); layout.addView(etDesc)

        AlertDialog.Builder(context).setTitle("Bilgileri Güncelle").setView(layout).setPositiveButton("Kaydet") { _, _ ->
            val newName = etName.text.toString(); val newDesc = etDesc.text.toString()
            if (newName.isNotEmpty()) {
                db.collection("products").document(currentProduct!!.id.toString())
                    .update(mapOf("name" to newName, "description" to newDesc))
                    .addOnSuccessListener {
                        Toast.makeText(context, "Güncellendi!", Toast.LENGTH_SHORT).show()
                        refreshProductData()
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

    private fun openWebsite(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { } }

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
        binding.imgCategoryIcon.imageTintList = null // TINT SIFIRLAMA (ÖNEMLİ)
    }

    private fun setupArButton() {
        val product = currentProduct ?: return

        Log.d("ProductDetail", "🔍 AR Kontrolü - hasArModel: ${product.hasArModel}, arModelUrl: '${product.arModelUrl}'")

        // AR modeli var mı kontrol et
        if (product.hasArModel && product.arModelUrl.isNotEmpty()) {
            Log.d("ProductDetail", "✅ AR modeli var: ${product.arModelUrl}")

            binding.btnViewInAr.visibility = View.VISIBLE

            // ARCore kurulu mu?
            if (ArHelper.isArCoreInstalled(requireContext())) {
                binding.btnViewInAr.text = "📱 Evinizde Görün (AR)"
                binding.btnViewInAr.setOnClickListener {
                    openArView()
                }
            } else {
                binding.btnViewInAr.text = "🔥 ARCore Gerekli"
                binding.btnViewInAr.setOnClickListener {
                    showArCoreRequiredDialog()
                }
            }
        } else {
            Log.d("ProductDetail", "❌ AR modeli yok - hasArModel: ${product.hasArModel}, arModelUrl isEmpty: ${product.arModelUrl.isEmpty()}")
            binding.btnViewInAr.visibility = View.GONE
        }
    }

    // ⚠️ YENİ METOD
    private fun openArView() {
        val product = currentProduct ?: return

        // Kamera izni kontrolü
        if (!ArHelper.hasCameraPermission(requireContext())) {
            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA),
                100
            )
            return
        }

        // ArActivity'yi başlat
        val intent = Intent(requireContext(), ArActivity::class.java)
        intent.putExtra("product_id", product.id)
        intent.putExtra("product_name", product.name)
        intent.putExtra("model_path", product.arModelUrl)
        intent.putExtra("model_scale", product.modelScale)

        startActivity(intent)
    }

    // ⚠️ YENİ METOD
    private fun showArCoreRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ARCore Gerekli")
            .setMessage("AR özelliğini kullanmak için Google ARCore uygulamasını yüklemelisiniz.")
            .setPositiveButton("Yükle") { _, _ ->
                // Play Store'a yönlendir
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("market://details?id=com.google.ar.core")
                startActivity(intent)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // ⚠️ YENİ: İzin sonucu
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openArView()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    class ReviewAdapter : ListAdapter<Review, ReviewAdapter.VH>(ReviewDiffCallback()) {
        class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(o: Review, n: Review) = o.id == n.id
            override fun areContentsTheSame(o: Review, n: Review) = o == n
        }
        inner class VH(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemReviewBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val i = getItem(pos)
            h.binding.tvReviewerName.text = i.userName
            h.binding.tvReviewComment.text = i.comment
            h.binding.rbReview.rating = i.rating
            h.binding.imgVerified.visibility = if(i.isVerified) View.VISIBLE else View.GONE
        }
    }
}