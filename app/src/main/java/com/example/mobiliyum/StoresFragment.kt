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

    // Tüm listeyi burada tutuyoruz, adapter'a filtreli kopyasını göndereceğiz
    private var allStores = ArrayList<Store>()
    private val db = FirebaseFirestore.getInstance()

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

        // Adapter Kurulumu (ListAdapter olduğu için liste vermiyoruz)
        storeAdapter = StoreAdapter { selectedStore ->
            // İstatistik Kaydı
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val updates = mapOf(
                "clickCount" to FieldValue.increment(1),
                "clickHistory.$today" to FieldValue.increment(1)
            )
            db.collection("stores").document(selectedStore.id.toString()).update(updates)

            // Detay sayfasına geçiş
            val detailFragment = StoreDetailFragment()
            val bundle = Bundle()
            bundle.putInt("id", selectedStore.id)
            bundle.putString("name", selectedStore.name)
            bundle.putString("image", selectedStore.imageUrl)
            bundle.putString("location", selectedStore.location)

            detailFragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null)
                .commit()
        }
        binding.rvStores.adapter = storeAdapter

        setupSearchView()
        fetchStoresFromFirestore()
    }

    private fun setupSearchView() {
        binding.searchViewStore.setOnClickListener { binding.searchViewStore.onActionViewExpanded() }
        binding.searchViewStore.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })
    }

    private fun filterList(query: String?) {
        val listToSend = if (query.isNullOrEmpty()) {
            ArrayList(allStores)
        } else {
            val searchText = query.lowercase(Locale.getDefault())
            val filtered = allStores.filter { store ->
                store.name.lowercase(Locale.getDefault()).contains(searchText) ||
                        store.category.lowercase(Locale.getDefault()).contains(searchText)
            }
            ArrayList(filtered)
        }
        // DiffUtil değişimi algılayıp animasyonla güncelleyecek
        storeAdapter.submitList(listToSend)
    }

    private fun fetchStoresFromFirestore() {
        db.collection("stores")
            .orderBy("id", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                allStores.clear()
                for (document in documents) {
                    val store = document.toObject(Store::class.java)
                    allStores.add(store)
                }
                // İlk yüklemede hepsini göster
                storeAdapter.submitList(ArrayList(allStores))
            }
            .addOnFailureListener { exception ->
                context?.let {
                    Toast.makeText(it, "Hata: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}