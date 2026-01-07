package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobiliyum.databinding.FragmentStoresBinding
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoresFragment : Fragment() {

    private var _binding: FragmentStoresBinding? = null
    private val binding get() = _binding!!

    private lateinit var storeAdapter: StoreAdapter
    private var allStores = ArrayList<Store>()
    //private val db by lazy { FirebaseFirestore.getInstance() }
    private val db by lazy { DataManager.getDb() }
    // Adminin belirlediği özel sıralama listesi (Store ID'leri)
    private var customSortOrder = ArrayList<Long>()

    // Filtre Durumu
    private var currentFilter = FilterType.POPULAR // Varsayılan Popüler
    private var currentSort = SortType.WALKING_ORDER

    enum class FilterType { POPULAR, ETAP_A, ETAP_B }
    enum class SortType { WALKING_ORDER, ALPHABETICAL, POPULARITY }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoresBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!NetworkMonitor.isOnline()) {
            Toast.makeText(context, "Çevrimdışı mod - Eski veriler gösteriliyor", Toast.LENGTH_SHORT).show()
        }

        binding.rvStores.layoutManager = LinearLayoutManager(context)
        binding.rvStores.setHasFixedSize(true)

        storeAdapter = StoreAdapter { selectedStore ->
            /*recordClick(selectedStore) aşağıdaki ile değiştirildi sil
            openStoreDetail(selectedStore)*/
            savedInstanceState ?: recordClick(selectedStore)
            openStoreDetail(selectedStore)
        }
        binding.rvStores.adapter = storeAdapter

        setupListeners()

        // Önce admin sıralamasını çek, sonra mağazaları yükle
        fetchCustomSortOrder {
            fetchStoresFromFirestore()
        }
    }

    private fun setupListeners() {
        binding.searchViewStore.setOnClickListener { binding.searchViewStore.onActionViewExpanded() }
        binding.searchViewStore.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilterAndSort(newText ?: "")
                return true
            }
        })

        // Chip Seçimleri
        binding.chipGroupEtap.setOnCheckedChangeListener { group, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipPopular -> FilterType.POPULAR
                R.id.chipEtapA -> FilterType.ETAP_A
                R.id.chipEtapB -> FilterType.ETAP_B
                else -> FilterType.POPULAR
            }

            // Popüler seçiliyse sıralama butonunu gizle (Admin yönetiyor)
            // Diğerlerinde göster
            if (currentFilter == FilterType.POPULAR) {
                binding.btnSortStores.visibility = View.GONE
                binding.btnSortStores.isEnabled = false
            } else {
                binding.btnSortStores.visibility = View.VISIBLE
                binding.btnSortStores.isEnabled = true
                // Etaplara geçince varsayılan yürüyüş sırasını ayarla
                currentSort = SortType.WALKING_ORDER
            }

            applyFilterAndSort(binding.searchViewStore.query.toString())
        }

        binding.btnSortStores.setOnClickListener { showSortDialog() }
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
            .addOnFailureListener { onComplete() }
    }

    private fun showSortDialog() {
        val options = arrayOf("Gezinti Sırası (Giriş -> Üst Kat)", "Alfabetik (A-Z)", "Popülerlik (Tık)")
        AlertDialog.Builder(context)
            .setTitle("Sıralama")
            .setItems(options) { _, which ->
                currentSort = when (which) {
                    0 -> SortType.WALKING_ORDER
                    1 -> SortType.ALPHABETICAL
                    2 -> SortType.POPULARITY
                    else -> SortType.WALKING_ORDER
                }
                applyFilterAndSort(binding.searchViewStore.query.toString())
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchStoresFromFirestore() {
        // Önce DataManager cache'inden al
        if (DataManager.cachedStores.isNotEmpty()) {
            allStores.clear()
            allStores.addAll(DataManager.cachedStores.filter { it.isActive })
            applyFilterAndSort("")
            return
        }

        // Cache boşsa Firestore'dan çek
        db.collection("stores").get()
            .addOnSuccessListener { documents ->
                allStores.clear()
                for (document in documents) {
                    val store = document.toObject(Store::class.java)
                    if (store.isActive) {
                        allStores.add(store)
                    }
                }
                applyFilterAndSort("")
            }
    }

    private fun applyFilterAndSort(query: String) {
        val searchLower = query.lowercase(Locale.getDefault())

        // 1. ADIM: ARAMA FİLTRESİ
        // Sequence kullanarak büyük listelerde performansı artırıyoruz
        // Eğer query boşsa tüm listeyi, doluysa filtrelenmiş halini alıyoruz
        val filteredList = if (query.isEmpty()) {
            allStores
        } else {
            allStores.filter {
                it.name.lowercase().contains(searchLower) ||
                        it.category.lowercase().contains(searchLower)
            }
        }

        // 2. ADIM: KATEGORİ/ETAP FİLTRESİ VE SIRALAMA
        val finalResult: List<Store> = if (currentFilter == FilterType.POPULAR) {
            // --- POPÜLER (ADMİN VİTRİNİ) ---
            binding.tvSortInfo.text = "Sıralama: Mobiliyum Vitrini"

            // Sadece customSortOrder listesinde olanları al
            // Sıralama performansını artırmak için ID'leri ve sıralarını bir Map'e alabiliriz
            // ama mağaza sayısı az olduğu için sortedBy yeterli ve temizdir.
            filteredList
                .filter { customSortOrder.contains(it.id.toLong()) }
                .sortedBy { customSortOrder.indexOf(it.id.toLong()) }
        } else {
            // --- ETAPLAR VE DİĞER SIRALAMALAR ---

            // A. Etap Filtreleme
            val etapFiltered = when (currentFilter) {
                FilterType.ETAP_A -> filteredList.filter { it.etap.equals("A", ignoreCase = true) }
                FilterType.ETAP_B -> filteredList.filter { it.etap.equals("B", ignoreCase = true) }
                else -> filteredList // TÜMÜ seçiliyse filtreleme yapma
            }

            // B. Sıralama Yöntemi
            when (currentSort) {
                SortType.WALKING_ORDER -> {
                    binding.tvSortInfo.text = "Sıralama: Kat Sırası"
                    etapFiltered.sortedBy { StoreSortHelper.calculateLocationWeight(it.location) }
                }
                SortType.ALPHABETICAL -> {
                    binding.tvSortInfo.text = "Sıralama: Alfabetik"
                    etapFiltered.sortedBy { it.name }
                }
                SortType.POPULARITY -> {
                    binding.tvSortInfo.text = "Sıralama: En Çok Ziyaret Edilenler"
                    etapFiltered.sortedByDescending { it.clickCount }
                }
            }
        }
        // ListAdapter List<T> kabul eder, ArrayList zorunlu değildir.
        storeAdapter.submitList(finalResult)
    }

//    private fun recordClick(store: Store) {
//        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
//        db.collection("stores").document(store.id.toString()).update(
//            mapOf(
//                "clickCount" to FieldValue.increment(1),
//                "clickHistory.$today" to FieldValue.increment(1)
//            )
//        )
//    }

    private val lastClickTimes = HashMap<Int, Long>()

    private fun recordClick(store: Store) {
        val now = System.currentTimeMillis()
        val lastClick = lastClickTimes[store.id] ?: 0L

        // 1 dakika içinde aynı mağazaya tıklama sayılmasın
        if (now - lastClick < 60000) {
            android.util.Log.d("StoresFragment", "⏭️ Click throttled: ${store.name}")
            openStoreDetail(store)
            return
        }

        lastClickTimes[store.id] = now

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("stores").document(store.id.toString()).update(
            mapOf(
                "clickCount" to FieldValue.increment(1),
                "clickHistory.$today" to FieldValue.increment(1)
            )
        )

        openStoreDetail(store)
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
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}