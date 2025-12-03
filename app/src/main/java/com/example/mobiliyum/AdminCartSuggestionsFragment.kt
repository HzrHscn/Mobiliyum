package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.ItemProductSelectionBinding // YENİ BINDING
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class AdminCartSuggestionsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSave: MaterialButton
    private val db = FirebaseFirestore.getInstance()
    private val selectedIds = ArrayList<Int>()
    private lateinit var adapter: SuggestionSelectionAdapter

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
            text = "Sepet Önerileri (Admin)"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setPadding(32, 32, 32, 8)
        }
        layout.addView(title)

        val subTitle = TextView(context).apply {
            text = "Sepeti boş olan kullanıcılara gösterilecek 4 ürünü seçiniz."
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(32, 0, 32, 24)
        }
        layout.addView(subTitle)

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        layout.addView(recyclerView)

        btnSave = MaterialButton(context).apply {
            text = "Seçimi Kaydet"
            setBackgroundColor(android.graphics.Color.parseColor("#FF6F00"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(32, 16, 32, 32)
            }
        }
        layout.addView(btnSave)

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = SuggestionSelectionAdapter { product, isChecked ->
            toggleSelection(product.id, isChecked)
        }
        recyclerView.adapter = adapter

        loadCurrentSelectionAndProducts()

        btnSave.setOnClickListener { saveSelection() }

        return layout
    }

    private fun loadCurrentSelectionAndProducts() {
        db.collection("app_settings").document("cart_suggestions").get()
            .addOnSuccessListener { doc ->
                val savedIds = doc.get("productIds") as? List<Long>
                selectedIds.clear()
                savedIds?.forEach { selectedIds.add(it.toInt()) }
                adapter.setSelectedIds(selectedIds)

                db.collection("products").get().addOnSuccessListener { querySnapshot ->
                    val products = querySnapshot.toObjects(Product::class.java)
                    adapter.submitList(products)
                }
            }
    }

    private fun toggleSelection(id: Int, isChecked: Boolean) {
        if (isChecked) {
            if (selectedIds.size >= 4) {
                Toast.makeText(context, "En fazla 4 ürün seçebilirsiniz.", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            } else {
                if (!selectedIds.contains(id)) selectedIds.add(id)
            }
        } else {
            selectedIds.remove(id)
        }
    }

    private fun saveSelection() {
        if (selectedIds.size != 4) {
            Toast.makeText(context, "Lütfen tam olarak 4 ürün seçiniz.", Toast.LENGTH_SHORT).show()
            return
        }

        val data = mapOf("productIds" to selectedIds)
        db.collection("app_settings").document("cart_suggestions")
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(context, "Sepet önerileri güncellendi!", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
    }

    // Adapter - Güncellendi
    class SuggestionSelectionAdapter(private val onCheckChanged: (Product, Boolean) -> Unit) :
        ListAdapter<Product, SuggestionSelectionAdapter.VH>(DiffCallback()) {

        private var selectedIds: List<Int> = emptyList()

        fun setSelectedIds(ids: List<Int>) { this.selectedIds = ids }

        class DiffCallback : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
        }

        // BINDING DEĞİŞTİ: ItemProductSelectionBinding
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

            holder.binding.cbSelectProduct.setOnCheckedChangeListener(null)
            holder.binding.cbSelectProduct.isChecked = selectedIds.contains(item.id)

            holder.binding.cbSelectProduct.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(item, isChecked)
            }
            holder.itemView.setOnClickListener { holder.binding.cbSelectProduct.toggle() }
        }
    }
}