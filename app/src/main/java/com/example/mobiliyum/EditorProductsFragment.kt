package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SearchView // Search View eklendi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemProductSelectionBinding
import com.google.firebase.firestore.FirebaseFirestore

class EditorProductsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: EditorProductAdapter
    private var allProducts = listOf<Product>() // Filtreleme için tüm liste

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
            text = "Ürün Yönetimi"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setPadding(32, 32, 32, 16)
        }
        layout.addView(title)

        // --- ARAMA ÇUBUĞU ---
        searchView = SearchView(context).apply {
            queryHint = "Ürün Ara..."
            setIconifiedByDefault(false)
            setPadding(16, 0, 16, 16)
        }
        layout.addView(searchView)

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        layout.addView(recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = EditorProductAdapter { product -> openEdit(product) }
        recyclerView.adapter = adapter

        // Arama Dinleyicisi
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        loadStoreProducts()

        return layout
    }

    private fun loadStoreProducts() {
        val user = UserManager.getCurrentUser() ?: return
        if (user.storeId == null) return

        db.collection("products")
            .whereEqualTo("storeId", user.storeId)
            .get()
            .addOnSuccessListener { docs ->
                allProducts = docs.toObjects(Product::class.java)
                adapter.submitList(allProducts) // İlk başta hepsini göster
            }
    }

    private fun filterList(query: String?) {
        val filtered = if (query.isNullOrEmpty()) {
            allProducts
        } else {
            allProducts.filter { it.name.contains(query, ignoreCase = true) }
        }
        adapter.submitList(filtered)
    }

    private fun openEdit(product: Product) {
        val fragment = ProductDetailFragment()
        val args = Bundle()
        args.putParcelable("product_data", product)
        fragment.arguments = args
        parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).addToBackStack(null).commit()
    }

    class EditorProductAdapter(private val onItemClick: (Product) -> Unit) :
        ListAdapter<Product, EditorProductAdapter.VH>(DiffCallback()) {

        class DiffCallback : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
        }

        inner class VH(val binding: ItemProductSelectionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemProductSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.binding.tvProductName.text = item.name
            holder.binding.tvProductPrice.text = PriceUtils.formatPriceStyled(item.price)
            Glide.with(holder.itemView).load(item.imageUrl).into(holder.binding.imgProduct)

            holder.binding.cbSelectProduct.buttonDrawable = null
            holder.binding.cbSelectProduct.setBackgroundResource(android.R.drawable.ic_menu_edit)
            holder.binding.cbSelectProduct.setOnClickListener { onItemClick(item) }
            holder.itemView.setOnClickListener { onItemClick(item) }
        }
    }
}