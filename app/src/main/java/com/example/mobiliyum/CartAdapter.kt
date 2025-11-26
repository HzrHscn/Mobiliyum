package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class CartAdapter(
    private val cartItems: List<Product>,
    private val onItemClick: (Product) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val selectedItems = mutableSetOf<Product>()

    class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        val tvUrl: TextView = itemView.findViewById(R.id.tvProductUrl)
        val cbSelect: CheckBox = itemView.findViewById(R.id.cbSelectProduct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_product, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val product = cartItems[position]

        holder.tvName.text = product.name
        // DÜZELTİLDİ: Yeni formatlama fonksiyonu
        holder.tvPrice.text = formatPrice(product.price)

        holder.tvUrl.text = product.productUrl
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .into(holder.imgProduct)

        holder.cbSelect.isChecked = selectedItems.contains(product)

        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedItems.add(product) else selectedItems.remove(product)
        }

        holder.itemView.setOnClickListener {
            onItemClick(product)
        }
    }

    override fun getItemCount(): Int = cartItems.size

    fun getSelectedProducts(): Set<Product> {
        return selectedItems
    }

    // --- DÜZELTİLMİŞ FİYAT FORMATLAMA FONKSİYONU ---
    private fun formatPrice(rawPrice: String): String {
        try {
            var cleanString = rawPrice.replace("[^\\d.,]".toRegex(), "").trim()
            if (cleanString.isEmpty()) return rawPrice

            if (cleanString.contains(",")) {
                cleanString = cleanString.replace(".", "")
                cleanString = cleanString.replace(",", ".")
            } else {
                cleanString = cleanString.replace(".", "")
            }

            val priceValue = cleanString.toDouble()
            val symbols = DecimalFormatSymbols(Locale.getDefault())
            symbols.groupingSeparator = '.'
            symbols.decimalSeparator = ','
            val decimalFormat = DecimalFormat("#,###.##", symbols)
            return "${decimalFormat.format(priceValue)} ₺"
        } catch (e: Exception) {
            return if (rawPrice.contains("₺")) rawPrice else "$rawPrice ₺"
        }
    }
}