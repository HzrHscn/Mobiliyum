package com.example.mobiliyum

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemStoreBinding

class StoreAdapter(
    private val onItemClick: (Store) -> Unit
) : ListAdapter<Store, StoreAdapter.StoreViewHolder>(StoreDiffCallback()) {

    // DiffUtil: Listenin sadece değişen kısımlarını hesaplar
    class StoreDiffCallback : DiffUtil.ItemCallback<Store>() {
        override fun areItemsTheSame(oldItem: Store, newItem: Store): Boolean {
            return oldItem.id == newItem.id // ID Kontrolü
        }

        override fun areContentsTheSame(oldItem: Store, newItem: Store): Boolean {
            return oldItem == newItem // İçerik Kontrolü (Data Class olduğu için equals çalışır)
        }
    }

    class StoreViewHolder(val binding: ItemStoreBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val binding = ItemStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = getItem(position) // ListAdapter metodu

        holder.binding.txtStoreName.text = currentStore.name
        holder.binding.txtStoreCategory.text = currentStore.category
        holder.binding.txtStoreLocation.text = currentStore.location

        Glide.with(holder.itemView.context)
            .load(currentStore.imageUrl)
            .centerCrop() //sonradan ekledim sil
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.stat_notify_error)
            .into(holder.binding.imgStoreLogo)

        holder.itemView.setOnClickListener {
            onItemClick(currentStore)
        }
    }
}