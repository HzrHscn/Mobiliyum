package com.example.mobiliyum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class ProductDetailFragment : Fragment() {
    private var currentProduct: Product? = null
    private lateinit var btnGoToStore: MaterialButton
    // Favori butonu (şimdilik sadece tanımlıyoruz)
    // private lateinit var btnFavorite: ImageView

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

        if (currentProduct != null) {
            // Ürün Görüntülenme Sayısını Artır
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("products").document(currentProduct!!.id.toString())
                .update("clickCount", com.google.firebase.firestore.FieldValue.increment(1))
        }

        val imgProduct = view.findViewById<ImageView>(R.id.imgProductDetail)
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvCategory = view.findViewById<TextView>(R.id.tvProductCategory)
        val tvPrice = view.findViewById<TextView>(R.id.tvProductPrice)
        val btnAddToCart = view.findViewById<MaterialButton>(R.id.btnAddToCart)
        btnGoToStore = view.findViewById<MaterialButton>(R.id.btnGoToStore)

        tvName.text = currentProduct!!.name
        tvCategory.text = currentProduct!!.category

        // DÜZELTME: PriceUtils kullanarak süslü fiyat yazdırma
        tvPrice.text = PriceUtils.formatPriceStyled(currentProduct!!.price)

        Glide.with(this)
            .load(currentProduct!!.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgProduct)

        // Sepete Ekle
        btnAddToCart.setOnClickListener {
            CartManager.addToCart(currentProduct!!)
            Toast.makeText(context, "${currentProduct!!.name} sepete eklendi!", Toast.LENGTH_SHORT).show()
        }

        // Firmaya Git
        btnGoToStore.setOnClickListener {
            val url = currentProduct!!.productUrl
            if (url.isNotEmpty()) openWebsite(url)
            else Toast.makeText(context, "Mağaza linki bulunamadı.", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun openWebsite(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Tarayıcı açılamadı", Toast.LENGTH_SHORT).show()
        }
    }
}