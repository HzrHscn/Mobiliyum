package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemStoreBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class AdminStoreListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView // Arama Çubuğu
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: AdminStoreAdapter
    private var allStores = ArrayList<Store>() // Tüm mağazaları burada tutuyoruz

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(context).apply {
            text = "Düzenlenecek Mağazayı Seçin"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setPadding(32, 32, 32, 16)
        }
        layout.addView(title)

        // YENİ: Arama Çubuğu
        searchView = SearchView(context).apply {
            queryHint = "Mağaza Adı veya ID ile Ara"
            isIconified = false // Açık olarak gelsin
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#F5F5F5")) // Hafif gri arka plan
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(32, 0, 32, 16)
            }
        }
        layout.addView(searchView)

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        layout.addView(recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = AdminStoreAdapter { store ->
            val fragment = AdminStoreEditFragment()
            val args = Bundle()
            args.putInt("storeId", store.id)
            fragment.arguments = args

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = adapter

        loadStores()
        setupSearchListener() // Arama dinleyicisini başlat

        return layout
    }

    private fun loadStores() {
        db.collection("stores").get().addOnSuccessListener { docs ->
            allStores.clear()
            val list = docs.toObjects(Store::class.java)
            allStores.addAll(list)
            adapter.submitList(list) // Başlangıçta hepsini göster
        }
    }

    private fun setupSearchListener() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterStores(newText)
                return true
            }
        })
    }

    private fun filterStores(query: String?) {
        if (query.isNullOrEmpty()) {
            adapter.submitList(ArrayList(allStores))
        } else {
            val searchText = query.lowercase(Locale.getDefault())
            val filteredList = allStores.filter { store ->
                // İsim veya ID içinde arama yap
                store.name.lowercase(Locale.getDefault()).contains(searchText) ||
                        store.id.toString().contains(searchText)
            }
            adapter.submitList(filteredList)
        }
    }

    class AdminStoreAdapter(private val onClick: (Store) -> Unit) :
        ListAdapter<Store, AdminStoreAdapter.VH>(StoreDiffCallback()) {

        class StoreDiffCallback : DiffUtil.ItemCallback<Store>() {
            override fun areItemsTheSame(oldItem: Store, newItem: Store) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Store, newItem: Store) = oldItem == newItem
        }

        inner class VH(val binding: ItemStoreBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val store = getItem(position)
            holder.binding.txtStoreName.text = store.name
            holder.binding.txtStoreCategory.text = store.category
            holder.binding.txtStoreLocation.text = "ID: ${store.id}"
            Glide.with(holder.itemView).load(store.imageUrl).into(holder.binding.imgStoreLogo)

            holder.itemView.setOnClickListener { onClick(store) }
        }
    }
}