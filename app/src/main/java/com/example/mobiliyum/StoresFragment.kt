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
    private lateinit var searchView: SearchView
    private lateinit var storeAdapter: StoreAdapter
    private var storeList = ArrayList<Store>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stores, container, false)

        recyclerView = view.findViewById(R.id.rvStores)
        searchView = view.findViewById(R.id.searchViewStore)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)

        storeAdapter = StoreAdapter(storeList) { selectedStore ->
            // 1. Firestore'da Tıklanma Sayısını Artır (Arka planda)
            db.collection("stores").document(selectedStore.id.toString())
                .update("clickCount", com.google.firebase.firestore.FieldValue.increment(1))

            // 2. Detay sayfasına git
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
        recyclerView.adapter = storeAdapter

        fetchStoresFromFirestore()
        setupSearchView()

        return view
    }

    // ... (Diğer fonksiyonlar aynı kalabilir: setupSearchView, fetchStoresFromFirestore)

    private fun setupSearchView() {
        searchView.setOnClickListener { searchView.onActionViewExpanded() }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
                Toast.makeText(context, "Hata: ${exception.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}