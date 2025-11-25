package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class StoresFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView // Arama çubuğu referansı
    private lateinit var storeAdapter: StoreAdapter
    private var storeList = ArrayList<Store>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stores, container, false)

        // Bileşenleri Bağla
        recyclerView = view.findViewById(R.id.rvStores)
        searchView = view.findViewById(R.id.searchViewStore) // XML ID'si

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)

        // Adaptörü Başlat
        storeAdapter = StoreAdapter(storeList) { selectedStore ->
            // Detay sayfasına geçiş (Aynı kalıyor)
            val detailFragment = StoreDetailFragment()
            val bundle = Bundle()
            bundle.putString("name", selectedStore.name)
            bundle.putString("image", selectedStore.imageUrl)
            bundle.putString("location", selectedStore.location)
            detailFragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = storeAdapter

        // Verileri Çek
        fetchStoresFromFirestore()

        // --- ARAMA DİNLEYİCİSİ ---
        setupSearchView()

        return view
    }

    private fun setupSearchView() {
        // Arama çubuğuna tıklayınca klavyeyi açmak için tüm alana tıklama özelliği veriyoruz
        searchView.setOnClickListener { searchView.onActionViewExpanded() }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // Kullanıcı "Enter"a basınca çalışır (Gerek yok)
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            // Kullanıcı her harfe bastığında çalışır (Anlık filtreleme)
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
                // Veriler gelince listeyi güncelle
                storeList = tempList
                storeAdapter.updateList(storeList)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Hata: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}