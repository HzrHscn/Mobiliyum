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
    private val onQuantityChange: (Product, Int) -> Unit
) : ListAdapter<Product, CartAdapter.CartViewHolder>(CartDiffCallback()) {

    class CartDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            // İçerik (adet, fiyat vb.) değişti mi?
            return oldItem == newItem
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
        holder.binding.tvProductPrice.text = PriceUtils.formatPriceStyled(product.price)
        holder.binding.tvQuantity.text = product.quantity.toString()

        val displayUrl = try {
            product.productUrl
                .replace("https://", "")
                .replace("http://", "")
                .replace("www.", "")
                .substringBefore("/")
        } catch (e: Exception) { product.productUrl }

        holder.binding.tvProductUrl.text = displayUrl

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.binding.imgProduct)

        holder.binding.imgDelete.setOnClickListener { onDeleteClick(product) }

        holder.binding.btnDecrease.setOnClickListener {
            if (product.quantity > 1) onQuantityChange(product, -1)
        }

        holder.binding.btnIncrease.setOnClickListener {
            onQuantityChange(product, 1)
        }

        holder.itemView.setOnClickListener { onItemClick(product) }
    }
}