package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemCartProductBinding // ViewBinding

class CartAdapter(
    private val cartItems: List<Product>,
    private val onItemClick: (Product) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val selectedItems = mutableSetOf<Product>()

    class CartViewHolder(val binding: ItemCartProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val product = cartItems[position]

        holder.binding.tvProductName.text = product.name
        holder.binding.tvProductPrice.text = PriceUtils.formatPriceStyled(product.price)

        holder.binding.tvProductUrl.text = product.productUrl
            .replace("https://", "")
            .replace("http://", "")
            .replace("www.", "")

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .into(holder.binding.imgProduct)

        // Checkbox dinleyicisini geçici olarak durdur (Recycler hatası önlemek için)
        holder.binding.cbSelectProduct.setOnCheckedChangeListener(null)
        holder.binding.cbSelectProduct.isChecked = selectedItems.contains(product)

        holder.binding.cbSelectProduct.setOnCheckedChangeListener { _, isChecked ->
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
}