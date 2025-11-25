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
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class ProductDetailDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_PRODUCT = "product_data"

        fun newInstance(product: Product): ProductDetailDialogFragment {
            val fragment = ProductDetailDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_PRODUCT, product)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // YENİ LAYOUT'U BURADA ÇAĞIRIYORUZ
        val view = inflater.inflate(R.layout.dialog_product_detail, container, false)

        val product = arguments?.getSerializable(ARG_PRODUCT) as Product?

        if (product == null) {
            dismiss()
            return view
        }

        // Yeni XML'deki ID'leri bağlıyoruz
        val imgProduct = view.findViewById<ImageView>(R.id.imgDialogProduct)
        val tvName = view.findViewById<TextView>(R.id.tvDialogName)
        val tvCategory = view.findViewById<TextView>(R.id.tvDialogCategory)
        val tvPrice = view.findViewById<TextView>(R.id.tvDialogPrice)
        val btnGoToStore = view.findViewById<MaterialButton>(R.id.btnDialogGoStore)

        // Verileri yazdır
        tvName.text = product.name
        tvCategory.text = product.category
        tvPrice.text = product.price

        Glide.with(this)
            .load(product.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgProduct)

        // Buton işlemi
        btnGoToStore.setOnClickListener {
            openWebsite(product.productUrl)
            dismiss()
        }

        return view
    }

    // Dialog açıldığında genişliğini ayarlamak için en garanti yöntem onStart'tır.
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // Genişlik: Ekranın %90'ı kadar olsun
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            // Yükseklik: İçeriğe göre otomatik (Wrap Content)
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.setLayout(width, height)

            // Arkaplanı şeffaf yap ki CardView köşeleri düzgün görünsün
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun openWebsite(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Link açılamadı: $url", Toast.LENGTH_SHORT).show()
        }
    }
}