package com.example.mobiliyum

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bundle içinden Serializable nesneyi alıyoruz
        arguments?.let {
            currentProduct = it.getSerializable("product_data") as Product?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_product_detail, container, false)

        // Eğer ürün verisi gelmediyse işlemi durdur (Hata önleyici)
        if (currentProduct == null) return view

        val imgProduct = view.findViewById<ImageView>(R.id.imgProductDetail)
        val tvName = view.findViewById<TextView>(R.id.tvProductName)
        val tvCategory = view.findViewById<TextView>(R.id.tvProductCategory)
        val tvPrice = view.findViewById<TextView>(R.id.tvProductPrice)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddToCart)

        // Verileri Yazdır
        tvName.text = currentProduct!!.name
        tvCategory.text = currentProduct!!.category
        tvPrice.text = currentProduct!!.price

        Glide.with(this)
            .load(currentProduct!!.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgProduct)

        // SEPETE EKLE BUTONU
        btnAdd.setOnClickListener {
            // 1. Ürünü Singleton Sepet Yöneticisine ekle
            CartManager.addToCart(currentProduct!!)

            // 2. Kullanıcıya bilgi ver
            Toast.makeText(context, "${currentProduct!!.name} sepete eklendi!", Toast.LENGTH_SHORT).show()

            // 3. İsteğe bağlı: Sepete gitmek ister mi? Şimdilik sadece bildirim verip sayfada kalıyoruz.
        }

        return view
    }
}