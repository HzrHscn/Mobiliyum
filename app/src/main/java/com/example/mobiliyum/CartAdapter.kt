package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemCartProductBinding

class CartAdapter(
    private val onItemClick: (Product) -> Unit
) : ListAdapter<Product, CartAdapter.CartViewHolder>(CartDiffCallback()) {

    private val selectedItems = mutableSetOf<Product>()

    class CartDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem == newItem
        }
    }

    class CartViewHolder(val binding: ItemCartProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val product = getItem(position)

        holder.binding.tvProductName.text = product.name
        holder.binding.tvProductPrice.text = PriceUtils.formatPriceStyled(product.price)

        holder.binding.tvProductUrl.text = product.productUrl
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .into(holder.binding.imgProduct)

        // Dinleyiciyi geçici olarak kaldır
        holder.binding.cbSelectProduct.setOnCheckedChangeListener(null)
        // Durumu set et
        holder.binding.cbSelectProduct.isChecked = selectedItems.contains(product)

        // Dinleyiciyi tekrar ekle
        holder.binding.cbSelectProduct.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedItems.add(product) else selectedItems.remove(product)
        }

        holder.itemView.setOnClickListener {
            onItemClick(product)
        }
    }

    fun getSelectedProducts(): Set<Product> {
        return selectedItems
    }
}