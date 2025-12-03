package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemCartProductBinding

class CartAdapter(
    private val onItemClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit,
    private val onQuantityChange: (Product, Int) -> Unit // Yeni adet döner
) : ListAdapter<Product, CartAdapter.CartViewHolder>(CartDiffCallback()) {

    class CartDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            // Adet değişirse içerik değişmiş sayılır ve UI güncellenir
            return oldItem == newItem && oldItem.quantity == newItem.quantity
        }
    }

    inner class CartViewHolder(val binding: ItemCartProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val product = getItem(position)

        holder.binding.tvProductName.text = product.name
        // Birim fiyatı gösteriyoruz
        holder.binding.tvProductPrice.text = PriceUtils.formatPriceStyled(product.price)
        holder.binding.tvQuantity.text = product.quantity.toString()

        holder.binding.tvProductUrl.text = product.productUrl
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")
            .substringBefore("/") // Sadece ana domaini göster

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .into(holder.binding.imgProduct)

        // SİLME
        holder.binding.imgDelete.setOnClickListener {
            onDeleteClick(product)
        }

        // AZALT (-)
        // Eğer drawable ikonlarınız yoksa, XML'de ImageView src kısmını
        // @android:drawable/ic_media_rew (veya benzeri) yapın.
        // Kod içinde resim kaynağını değiştirmiyoruz, XML'e güveniyoruz.
        holder.binding.btnDecrease.setOnClickListener {
            if (product.quantity > 1) {
                onQuantityChange(product, -1) // -1: Azalt
            }
        }

        // ARTIR (+)
        holder.binding.btnIncrease.setOnClickListener {
            onQuantityChange(product, 1) // 1: Artır
        }

        // Detay açma
        holder.itemView.setOnClickListener {
            onItemClick(product)
        }
    }
}