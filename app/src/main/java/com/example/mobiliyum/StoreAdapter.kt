package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class StoreAdapter(
    private val storeList: List<Store>,
    private val onItemClick: (Store) -> Unit // Tıklama fonksiyonunu dışarıdan alacağız
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    // Tasarım ile kod arasındaki bağlantıyı tutan sınıf
    class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgLogo: ImageView = itemView.findViewById(R.id.imgStoreLogo)
        val txtName: TextView = itemView.findViewById(R.id.txtStoreName)
        val txtCategory: TextView = itemView.findViewById(R.id.txtStoreCategory)
        val txtLocation: TextView = itemView.findViewById(R.id.txtStoreLocation)
    }

    // 1. Tasarım dosyasını (XML) şişirip (inflate) görünüme çevirir
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_store, parent, false)
        return StoreViewHolder(view)
    }

    // 2. Verileri ilgili yerlere yazar
    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = storeList[position]

        holder.txtName.text = currentStore.name
        holder.txtCategory.text = currentStore.category
        holder.txtLocation.text = currentStore.location

        Glide.with(holder.itemView.context)
            .load(currentStore.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.stat_notify_error)
            .into(holder.imgLogo)

        // 2. TIKLAMA OLAYI BURADA TETİKLENİYOR
        holder.itemView.setOnClickListener {
            onItemClick(currentStore) // Tıklanan mağazayı gönder
        }
    }

    // 3. Listedeki eleman sayısını döndürür
    override fun getItemCount(): Int {
        return storeList.size
    }
}