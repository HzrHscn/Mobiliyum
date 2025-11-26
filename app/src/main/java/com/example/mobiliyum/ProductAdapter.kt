package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class ProductAdapter(
    private val productList: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val tvName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        holder.tvName.text = product.name

        // DÜZELTİLDİ: Yeni formatlama fonksiyonu
        holder.tvPrice.text = formatPrice(product.price)

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(holder.imgProduct)

        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    override fun getItemCount(): Int = productList.size

    // --- DÜZELTİLMİŞ FİYAT FORMATLAMA FONKSİYONU ---
    private fun formatPrice(rawPrice: String): String {
        try {
            // 1. Sadece rakam, nokta ve virgülleri bırak, gerisini temizle
            var cleanString = rawPrice.replace("[^\\d.,]".toRegex(), "").trim()

            if (cleanString.isEmpty()) return rawPrice

            // 2. Türkçe Format Kontrolü (19.210,50 gibi)
            // Eğer virgül varsa, bu ondalık (kuruş) ayracıdır.
            if (cleanString.contains(",")) {
                cleanString = cleanString.replace(".", "") // Binlik noktaları sil (19210,50)
                cleanString = cleanString.replace(",", ".") // Virgülü noktaya çevir (19210.50) - Double için
            } else {
                // Virgül yoksa, noktalar binlik ayracıdır, hepsini sil (1.500 -> 1500)
                cleanString = cleanString.replace(".", "")
            }

            // 3. Sayıya çevir
            val priceValue = cleanString.toDouble()

            // 4. Tekrar Türkçe formatında yazdır
            val symbols = DecimalFormatSymbols(Locale.getDefault())
            symbols.groupingSeparator = '.' // Binlik: Nokta
            symbols.decimalSeparator = ','  // Ondalık: Virgül
            val decimalFormat = DecimalFormat("#,###.##", symbols) // Kuruş varsa göster

            return "${decimalFormat.format(priceValue)} ₺"

        } catch (e: Exception) {
            // Hata olursa (örn: boşsa) olduğu gibi göster
            return if (rawPrice.contains("₺")) rawPrice else "$rawPrice ₺"
        }
    }
}