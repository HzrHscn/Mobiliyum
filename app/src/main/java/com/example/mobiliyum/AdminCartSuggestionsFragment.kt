package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentAdminCartSuggestionsBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class AdminCartSuggestionsFragment : Fragment() {

    private var _binding: FragmentAdminCartSuggestionsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private var allProducts = ArrayList<Product>()
    private val selectedIds = HashSet<Int>()
    private lateinit var adapter: SuggestionAdapter

    private val storeNames = ArrayList<String>()
    private val categories = ArrayList<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminCartSuggestionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvSuggestions.layoutManager = LinearLayoutManager(context)
        adapter = SuggestionAdapter()
        binding.rvSuggestions.adapter = adapter

        setupListeners()
        loadData()
    }

    private fun setupListeners() {
        binding.searchViewSuggestion.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilters()
                return true
            }
        })

        binding.btnClearSelection.setOnClickListener {
            if (selectedIds.isNotEmpty()) {
                AlertDialog.Builder(context)
                    .setTitle("Temizle")
                    .setMessage("Tüm seçimler kaldırılacak. Emin misiniz?")
                    .setPositiveButton("Evet") { _, _ ->
                        selectedIds.clear()
                        updateCountText()
                        applyFilters()
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            }
        }

        binding.btnSaveSuggestions.setOnClickListener { saveSuggestions() }

        binding.spinnerFilterStore.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { applyFilters() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerFilterCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { applyFilters() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadData() {
        db.collection("app_settings").document("cart_suggestions").get()
            .addOnSuccessListener { doc ->
                val ids = doc.get("productIds") as? List<Long>
                selectedIds.clear()
                if (ids != null) {
                    ids.forEach { selectedIds.add(it.toInt()) }
                }
                updateCountText()
                loadProducts()
            }
            .addOnFailureListener { loadProducts() }
    }

    private fun loadProducts() {
        db.collection("stores").get().addOnSuccessListener { storeDocs ->
            storeNames.clear()
            storeNames.add("Tüm Mağazalar")

            for (doc in storeDocs) {
                val s = doc.toObject(Store::class.java)
                storeNames.add(s.name)
            }

            val storeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, storeNames)
            binding.spinnerFilterStore.adapter = storeAdapter

            db.collection("products").get().addOnSuccessListener { productDocs ->
                allProducts.clear()
                categories.clear()
                categories.add("Tüm Kategoriler")
                val tempCategories = HashSet<String>()

                for (doc in productDocs) {
                    val p = doc.toObject(Product::class.java)
                    if (p.isActive) {
                        allProducts.add(p)
                        if (p.category.isNotEmpty()) tempCategories.add(p.category)
                    }
                }

                categories.addAll(tempCategories.sorted())
                val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
                binding.spinnerFilterCategory.adapter = catAdapter

                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        val query = binding.searchViewSuggestion.query.toString().lowercase(Locale.getDefault())
        val selectedCategory = binding.spinnerFilterCategory.selectedItem?.toString() ?: "Tüm Kategoriler"

        var filteredList = allProducts.filter { p ->
            val matchesSearch = p.name.lowercase().contains(query)
            val matchesCategory = selectedCategory == "Tüm Kategoriler" || p.category == selectedCategory
            matchesSearch && matchesCategory
        }

        // HATA ÇÖZÜLDÜ: 'p' yerine 'it' kullanıldı
        filteredList = filteredList.sortedWith(
            compareByDescending<Product> { selectedIds.contains(it.id) }
                .thenBy { it.name }
        )

        adapter.submitList(ArrayList(filteredList))
    }

    private fun saveSuggestions() {
        val data = mapOf("productIds" to selectedIds.toList())

        db.collection("app_settings").document("cart_suggestions")
            .set(data)
            .addOnSuccessListener {
                Toast.makeText(context, "Öneriler başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCountText() {
        binding.tvSelectedCount.text = "${selectedIds.size} Ürün Seçili"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- ADAPTER ---
    inner class SuggestionAdapter : RecyclerView.Adapter<SuggestionAdapter.VH>() {

        private var items = ArrayList<Product>()

        fun submitList(newItems: ArrayList<Product>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(android.R.id.text1)
            val cbSelect: CheckBox = itemView.findViewById(android.R.id.checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion_select, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val product = items[position]
            holder.tvName.text = "${product.name} (${product.price})"

            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = selectedIds.contains(product.id)

            if (selectedIds.contains(product.id)) {
                holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
            } else {
                holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedIds.add(product.id) else selectedIds.remove(product.id)
                updateCountText()
                notifyItemChanged(position) // Sadece bu satırı güncelle
            }

            holder.itemView.setOnClickListener {
                holder.cbSelect.isChecked = !holder.cbSelect.isChecked
            }
        }

        override fun getItemCount() = items.size
    }
}