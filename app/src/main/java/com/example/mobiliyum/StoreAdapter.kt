package com.example.mobiliyum

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemStoreBinding // OTOMATİK OLUŞAN SINIF (item_store.xml'den)
import java.util.Locale

class StoreAdapter(
    private var storeList: ArrayList<Store>,
    private val onItemClick: (Store) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    private var filteredList: ArrayList<Store> = ArrayList(storeList)

    // ViewHolder artık View değil, Binding alıyor
    class StoreViewHolder(val binding: ItemStoreBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        // Binding inflate işlemi
        val binding = ItemStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = filteredList[position]

        // findViewById yok! holder.binding ile doğrudan erişim
        holder.binding.txtStoreName.text = currentStore.name
        holder.binding.txtStoreCategory.text = currentStore.category
        holder.binding.txtStoreLocation.text = currentStore.location

        Glide.with(holder.itemView.context)
            .load(currentStore.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.stat_notify_error)
            .into(holder.binding.imgStoreLogo)

        holder.itemView.setOnClickListener {
            onItemClick(currentStore)
        }
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: ArrayList<Store>) {
        storeList = ArrayList(newList)
        filteredList = ArrayList(newList)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        val searchText = query.lowercase(Locale.getDefault())
        filteredList.clear()
        if (searchText.isEmpty()) {
            filteredList.addAll(storeList)
        } else {
            for (store in storeList) {
                if (store.name.lowercase(Locale.getDefault()).contains(searchText) ||
                    store.category.lowercase(Locale.getDefault()).contains(searchText)) {
                    filteredList.add(store)
                }
            }
        }
        notifyDataSetChanged()
    }
}