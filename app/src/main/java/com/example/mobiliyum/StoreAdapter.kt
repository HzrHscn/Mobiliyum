package com.example.mobiliyum

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Locale

class StoreAdapter(
    private var storeList: ArrayList<Store>, // Orijinal liste (Veritabanından gelen)
    private val onItemClick: (Store) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    // Ekranda gösterilecek filtrelenmiş liste
    private var filteredList: ArrayList<Store> = ArrayList(storeList)

    class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgLogo: ImageView = itemView.findViewById(R.id.imgStoreLogo)
        val txtName: TextView = itemView.findViewById(R.id.txtStoreName)
        val txtCategory: TextView = itemView.findViewById(R.id.txtStoreCategory)
        val txtLocation: TextView = itemView.findViewById(R.id.txtStoreLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_store, parent, false)
        return StoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = filteredList[position] // Filtrelenmiş listeden alıyoruz

        holder.txtName.text = currentStore.name
        holder.txtCategory.text = currentStore.category
        holder.txtLocation.text = currentStore.location

        Glide.with(holder.itemView.context)
            .load(currentStore.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.stat_notify_error)
            .into(holder.imgLogo)

        holder.itemView.setOnClickListener {
            onItemClick(currentStore)
        }
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    // --- YENİ: Veri Güncelleme Fonksiyonu ---
    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: ArrayList<Store>) {
        storeList = ArrayList(newList) // Orijinal listeyi güncelle
        filteredList = ArrayList(newList) // Filtreli listeyi de sıfırla
        notifyDataSetChanged()
    }

    // --- YENİ: Arama Filtreleme Fonksiyonu ---
    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        val searchText = query.lowercase(Locale.getDefault())

        filteredList.clear()

        if (searchText.isEmpty()) {
            // Arama boşsa hepsini göster
            filteredList.addAll(storeList)
        } else {
            // Arama doluysa filtrele
            for (store in storeList) {
                // Hem mağaza isminde hem de kategoride arama yapar
                if (store.name.lowercase(Locale.getDefault()).contains(searchText) ||
                    store.category.lowercase(Locale.getDefault()).contains(searchText)) {
                    filteredList.add(store)
                }
            }
        }
        notifyDataSetChanged()
    }
}