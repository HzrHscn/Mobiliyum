package com.example.mobiliyum

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val context: Context,
    private val categoryList: List<CategorySection>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val btnExpand: ConstraintLayout = itemView.findViewById(R.id.layoutCategoryHeader)
        val imgArrow: ImageView = itemView.findViewById(R.id.imgExpandIcon)
        val rvProducts: RecyclerView = itemView.findViewById(R.id.rvInnerProducts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_group, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val section = categoryList[position]

        // Başlığı yaz
        holder.tvTitle.text = "${section.categoryName} (${section.products.size})"

        // İçerik görünürlüğünü ayarla
        holder.rvProducts.visibility = if (section.isExpanded) View.VISIBLE else View.GONE

        // Ok işaretini döndür (Görsel efekt)
        holder.imgArrow.rotation = if (section.isExpanded) 180f else 0f

        // İçerdeki RecyclerView'ı ayarla (Ürünler burada listelenecek)
        // GridLayoutManager kullanıyoruz (Yan yana 2 ürün)
        val productLayoutManager = GridLayoutManager(context, 2)
        holder.rvProducts.layoutManager = productLayoutManager

        // Eski ProductAdapter'ımızı burada kullanıyoruz!
        val productAdapter = ProductAdapter(section.products, onProductClick)
        holder.rvProducts.adapter = productAdapter

        // Kaydırma çakışmasını önlemek için
        holder.rvProducts.isNestedScrollingEnabled = false

        // Tıklama Olayı (Aç/Kapa)
        holder.btnExpand.setOnClickListener {
            section.isExpanded = !section.isExpanded
            notifyItemChanged(position) // Sadece bu satırı güncelle
        }
    }

    override fun getItemCount(): Int = categoryList.size
}