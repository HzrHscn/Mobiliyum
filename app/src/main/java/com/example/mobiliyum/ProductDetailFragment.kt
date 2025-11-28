package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
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

    // YENİ: Fiyat Güncelleme Butonu (Sadece Admin)
    private lateinit var btnAdminUpdatePrice: MaterialButton

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

        // XML Bağlantıları
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

        // --- YENİ BUTON (XML'de ekli olmadığı için kodla ekleyebiliriz veya XML'e eklemen gerekir) ---
        // XML'e dokunmamak için BottomBar'ın hemen üstüne dinamik ekleyebiliriz ama
        // temiz olması için "Sepete Ekle" butonunu Admin ise "Fiyat Güncelle" yapalım.

        if (UserManager.canEditProduct(currentProduct!!)) {
            btnAddToCart.text = "Fiyatı Güncelle (Yönetici)"
            btnAddToCart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F")) // Kırmızı
            btnAddToCart.setOnClickListener {
                showUpdatePriceDialog()
            }
        } else {
            btnAddToCart.setOnClickListener {
                CartManager.addToCart(currentProduct!!)
                Toast.makeText(context, "Sepete eklendi", Toast.LENGTH_SHORT).show()
            }
        }

        // Verileri Yaz
        tvName.text = currentProduct!!.name
        tvCategory.text = currentProduct!!.category
        tvPrice.text = PriceUtils.formatPriceStyled(currentProduct!!.price)

        Glide.with(this).load(currentProduct!!.imageUrl).into(imgProduct)

        // Favori Durumu
        updateFavoriteUI(FavoritesManager.isFavorite(currentProduct!!.id))

        // Listeners
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

        return view
    }

    // --- FİYAT GÜNCELLEME MANTIĞI ---
    private fun showUpdatePriceDialog() {
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Yeni Fiyat (Örn: 14500)"

        AlertDialog.Builder(context)
            .setTitle("Fiyat Güncelle")
            .setMessage("Şu anki fiyat: ${currentProduct!!.price}\n\nEski fiyat geçmişe kaydedilecek.")
            .setView(input)
            .setPositiveButton("Güncelle") { _, _ ->
                val newPriceStr = input.text.toString()
                if (newPriceStr.isNotEmpty()) {
                    updateProductPriceInFirebase(newPriceStr)
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun updateProductPriceInFirebase(newPriceRaw: String) {
        val db = FirebaseFirestore.getInstance()
        val productRef = db.collection("products").document(currentProduct!!.id.toString())

        // 1. Yeni fiyatı formatla (15000 -> 15.000,00 ₺)
        // Kullanıcı düz 15000 girerse biz formatlarız
        val newPriceDouble = newPriceRaw.toDoubleOrNull() ?: return
        val newPriceFormatted = PriceUtils.formatPriceStyled(newPriceDouble).toString()

        // 2. Şu anki fiyatı sayıya çevir
        val currentPriceDouble = PriceUtils.parsePrice(currentProduct!!.price)

        // 3. Geçmiş haritasını hazırla
        // Mevcut geçmişi al (String key, Double value)
        val historyMap = HashMap<String, Double>()
        // Eğer formatı uygunsa mevcutları kopyala
        historyMap.putAll(currentProduct!!.priceHistory)

        // BUGÜNÜN tarihini ve ESKİ fiyatı ekle
        // Format: yyyy-MM-dd
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(Date())
        historyMap[today] = currentPriceDouble

        // 4. Veritabanını Güncelle
        val updates = hashMapOf<String, Any>(
            "price" to newPriceFormatted,
            "oldPrice" to currentProduct!!.price, // Eskiyi buraya sakla
            "priceHistory" to historyMap
        )

        productRef.update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Fiyat güncellendi!", Toast.LENGTH_SHORT).show()
                // Ekranı kapatıp açmaya gerek yok, manuel güncelleme
                // Ancak kullanıcı geri çıkıp girse iyi olur
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
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
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) { }
    }
}