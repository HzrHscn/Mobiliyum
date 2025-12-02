package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemMyReviewBinding // ViewBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MyReviewsAdapter(private val reviews: List<Review>) : RecyclerView.Adapter<MyReviewsAdapter.VH>() {

    inner class VH(val binding: ItemMyReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMyReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val review = reviews[position]

        // Ürün adı yoksa (eski veri) "Ürün" yaz
        holder.binding.tvReviewProductName.text = if (review.productName.isNotEmpty()) review.productName else "Ürün #${review.productId}"
        holder.binding.tvMyReviewComment.text = review.comment
        holder.binding.rbMyReview.rating = review.rating

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
        holder.binding.tvMyReviewDate.text = dateFormat.format(review.date)

        if (review.productImageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(review.productImageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.binding.imgReviewProduct)
        } else {
            holder.binding.imgReviewProduct.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun getItemCount() = reviews.size
}