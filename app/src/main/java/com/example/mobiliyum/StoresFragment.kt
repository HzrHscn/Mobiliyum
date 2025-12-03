package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobiliyum.databinding.FragmentStoresBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoresFragment : Fragment() {

    private var _binding: FragmentStoresBinding? = null
    private val binding get() = _binding!!

    private lateinit var storeAdapter: StoreAdapter
    private var allStores = ArrayList<Store>()
    private val db = FirebaseFirestore.getInstance()

    // Adminin belirlediği sıralama listesi (ID'ler)
    private var customSortOrder = ArrayList<Long>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvStores.layoutManager = LinearLayoutManager(context)
        binding.rvStores.setHasFixedSize(true)

        storeAdapter = StoreAdapter { selectedStore ->
            recordClick(selectedStore)
            openStoreDetail(selectedStore)
        }
        binding.rvStores.adapter = storeAdapter

        setupSearchView()

        // Önce özel sıralamayı çek, sonra mağazaları yükle
        fetchCustomSortOrder {
            fetchStoresFromFirestore()
        }

        // Filtre Butonları Dinleyicisi
        binding.toggleStoreFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                applyFilter(checkedId, binding.searchViewStore.query.toString())
            }
        }
    }

    private fun fetchCustomSortOrder(onComplete: () -> Unit) {
        db.collection("app_settings").document("store_sorting").get()
            .addOnSuccessListener { doc ->
                val ids = doc.get("sortedIds") as? List<Long>
                if (ids != null) {
                    customSortOrder.clear()
                    customSortOrder.addAll(ids)
                }
                onComplete()
            }
            .addOnFailureListener { onComplete() } // Hata olsa da devam et
    }

    private fun fetchStoresFromFirestore() {
        db.collection("stores").get()
            .addOnSuccessListener { documents ->
                allStores.clear()
                for (document in documents) {
                    val store = document.toObject(Store::class.java)
                    allStores.add(store)
                }
                // İlk açılışta seçili olan filtreyi uygula
                applyFilter(binding.toggleStoreFilter.checkedButtonId, "")
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Hata: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter(checkedId: Int, query: String) {
        // 1. Önce Arama Filtresi
        val searchLower = query.lowercase(Locale.getDefault())
        var filteredList = if (query.isEmpty()) {
            ArrayList(allStores)
        } else {
            allStores.filter {
                it.name.lowercase().contains(searchLower) ||
                        it.category.lowercase().contains(searchLower)
            }
        }

        // 2. Sonra Kategori/Etap Filtresi
        filteredList = when (checkedId) {
            R.id.btnFilterEtapA -> filteredList.filter { it.etap == "A" }
            R.id.btnFilterEtapB -> filteredList.filter { it.etap == "B" }
            else -> {
                // POPÜLER (Özel Sıralama)
                // customSortOrder listesindeki ID sırasına göre diz
                filteredList.sortedBy { store ->
                    val index = customSortOrder.indexOf(store.id.toLong())
                    if (index == -1) Int.MAX_VALUE else index
                }
            }
        }

        storeAdapter.submitList(ArrayList(filteredList))
    }

    private fun setupSearchView() {
        binding.searchViewStore.setOnClickListener { binding.searchViewStore.onActionViewExpanded() }
        binding.searchViewStore.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilter(binding.toggleStoreFilter.checkedButtonId, newText ?: "")
                return true
            }
        })
    }

    private fun recordClick(store: Store) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("stores").document(store.id.toString()).update(
            mapOf(
                "clickCount" to FieldValue.increment(1),
                "clickHistory.$today" to FieldValue.increment(1)
            )
        )
    }

    private fun openStoreDetail(store: Store) {
        val detailFragment = StoreDetailFragment()
        val bundle = Bundle()
        bundle.putInt("id", store.id)
        bundle.putString("name", store.name)
        bundle.putString("image", store.imageUrl)
        bundle.putString("location", store.location)
        detailFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}