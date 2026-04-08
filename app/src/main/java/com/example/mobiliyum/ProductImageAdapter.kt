package com.example.mobiliyum

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ProductImageAdapter(
    private val imageUrls: List<String>,
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<ProductImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return ImageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = imageUrls[position]
        Glide.with(holder.imageView.context).load(url).into(holder.imageView)

        // Resme tıklandığında büyütmek (zoom) için tıklandığı URL'yi geri döndürüyoruz
        holder.imageView.setOnClickListener { onImageClick(url) }
    }

    override fun getItemCount(): Int = imageUrls.size
}