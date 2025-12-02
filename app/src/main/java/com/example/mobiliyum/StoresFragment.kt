package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobiliyum.databinding.FragmentStoresBinding // OTOMATİK OLUŞAN SINIF
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoresFragment : Fragment() {

    // 1. Binding Tanımlamaları (Standart Kalıp)
    private var _binding: FragmentStoresBinding? = null
    // Bu 'binding' değişkeni, view elemanlarına (buton, textview vs.) erişmemizi sağlar.
    private val binding get() = _binding!!

    private lateinit var storeAdapter: StoreAdapter
    private var storeList = ArrayList<Store>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 2. Layout'u Binding ile şişiriyoruz (inflate)
        _binding = FragmentStoresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 3. ARTIK findViewById YOK! Doğrudan 'binding.idAdi' ile erişiyoruz.

        binding.rvStores.layoutManager = LinearLayoutManager(context)
        binding.rvStores.setHasFixedSize(true)

        storeAdapter = StoreAdapter(storeList) { selectedStore ->
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
            // ADIM 1'de Parcelable yaptığımız için artık nesneyi doğrudan atabiliriz!
            // Tek tek id, name, image atmaya gerek yok.
            // bundle.putParcelable("store_data", selectedStore)
            // Ancak StoreDetailFragment henüz güncellenmediği için eski usul devam ediyoruz şimdilik:
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

        fetchStoresFromFirestore()
        setupSearchView()
    }

    private fun setupSearchView() {
        // binding.searchViewStore diyerek XML'deki ID'ye erişiyoruz
        binding.searchViewStore.setOnClickListener { binding.searchViewStore.onActionViewExpanded() }
        binding.searchViewStore.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                storeAdapter.filter(newText ?: "")
                return true
            }
        })
    }

    private fun fetchStoresFromFirestore() {
        db.collection("stores")
            .orderBy("id", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val tempList = ArrayList<Store>()
                for (document in documents) {
                    val store = document.toObject(Store::class.java)
                    tempList.add(store)
                }
                storeList = tempList
                storeAdapter.updateList(storeList)
            }
            .addOnFailureListener { exception ->
                // Context null olabilir, güvenli erişim
                context?.let {
                    Toast.makeText(it, "Hata: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 4. Memory Leak (Hafıza Sızıntısı) Önleme
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}