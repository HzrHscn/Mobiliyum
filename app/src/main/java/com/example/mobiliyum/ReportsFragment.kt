package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class ReportsFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnSort: MaterialButton
    private lateinit var btnFilter: MaterialButton // YENİ
    private lateinit var tvTotalCount: TextView
    private lateinit var layoutGeneralStats: LinearLayout
    private lateinit var toggleTimeFilter: MaterialButtonToggleGroup

    // --- YENİ BİLEŞENLER ---
    private lateinit var rvTopProducts: RecyclerView
    private lateinit var tvTopProductsTitle: TextView
    private lateinit var layoutCategoryStats: LinearLayout // Kategori Grafiği İçin
    private lateinit var tvCategoryStatsTitle: TextView

    private val db = FirebaseFirestore.getInstance()
    private var currentTab = 0
    private var sortMode = 0
    private var targetStoreId: Int = -1

    // FİLTRE DEĞİŞKENLERİ
    private var filterMinPrice: Double? = null
    private var filterMaxPrice: Double? = null
    private var filterCategory: String = "Tümü"
    private var filterStoreName: String = "Tümü"
    private val allCategories = mutableListOf("Tümü")
    private val allStoreNames = mutableListOf("Tümü")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { targetStoreId = it.getInt("storeId", -1) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reports_main, container, false)

        tabLayout = view.findViewById(R.id.tabLayoutReports)
        recyclerView = view.findViewById(R.id.rvReports)
        etSearch = view.findViewById(R.id.etSearchReport)
        btnSort = view.findViewById(R.id.btnSortReport)
        btnFilter = view.findViewById(R.id.btnFilterReport) // YENİ
        tvTotalCount = view.findViewById(R.id.tvReportCount)

        setupGeneralStatsUI(view as LinearLayout)

        recyclerView.layoutManager = LinearLayoutManager(context)

        setupTabs()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateVisibility()
                loadData(etSearch.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnSort.setOnClickListener { showSortDialog() }
        btnFilter.setOnClickListener { showFilterDialog() } // YENİ
        etSearch.addTextChangedListener { loadData(it.toString()) }

        // Filtre verilerini doldur (Kategoriler ve Mağazalar)
        preloadFilterData()

        updateVisibility()
        loadData()

        return view
    }

    // Filtre Dialogu İçin Veri Hazırlığı
    private fun preloadFilterData() {
        // Kategorileri Çek
        db.collection("products").get().addOnSuccessListener { docs ->
            val cats = docs.toObjects(Product::class.java).map { it.category }.distinct()
            allCategories.addAll(cats)
        }
        // Mağazaları Çek
        db.collection("stores").get().addOnSuccessListener { docs ->
            val stores = docs.toObjects(Store::class.java).map { it.name }
            allStoreNames.addAll(stores)
        }
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report_filter, null)

        val etMin = dialogView.findViewById<EditText>(R.id.etMinPrice)
        val etMax = dialogView.findViewById<EditText>(R.id.etMaxPrice)
        val spinCat = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinStore = dialogView.findViewById<Spinner>(R.id.spinnerStore)

        // Spinnerları Doldur
        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allCategories)
        spinCat.adapter = catAdapter

        val storeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allStoreNames)
        spinStore.adapter = storeAdapter

        // Mevcut değerleri set et
        if(filterMinPrice != null) etMin.setText(filterMinPrice.toString())
        if(filterMaxPrice != null) etMax.setText(filterMaxPrice.toString())
        spinCat.setSelection(allCategories.indexOf(filterCategory))
        spinStore.setSelection(allStoreNames.indexOf(filterStoreName))

        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Uygula") { _, _ ->
                filterMinPrice = etMin.text.toString().toDoubleOrNull()
                filterMaxPrice = etMax.text.toString().toDoubleOrNull()
                filterCategory = spinCat.selectedItem.toString()
                filterStoreName = spinStore.selectedItem.toString()

                loadData(etSearch.text.toString())
            }
            .setNeutralButton("Temizle") { _, _ ->
                filterMinPrice = null
                filterMaxPrice = null
                filterCategory = "Tümü"
                filterStoreName = "Tümü"
                loadData(etSearch.text.toString())
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun setupTabs() {
        tabLayout.removeAllTabs()
        if (targetStoreId == -1) {
            // Admin (Kullanıcılar ÇIKARILDI)
            tabLayout.addTab(tabLayout.newTab().setText("Mağazalar"))
            tabLayout.addTab(tabLayout.newTab().setText("Ürünler"))
            tabLayout.addTab(tabLayout.newTab().setText("Favoriler"))
        } else {
            // Manager
            tabLayout.addTab(tabLayout.newTab().setText("Genel Bakış"))
            tabLayout.addTab(tabLayout.newTab().setText("Ürünlerim"))
            tabLayout.addTab(tabLayout.newTab().setText("Favorilenme"))
        }
    }

    private fun setupGeneralStatsUI(rootLayout: LinearLayout) {
        layoutGeneralStats = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        // 1. Zaman Filtresi
        toggleTimeFilter = MaterialButtonToggleGroup(requireContext(), null, com.google.android.material.R.attr.materialButtonToggleGroupStyle).apply {
            isSingleSelection = true
            isSelectionRequired = true

            val periods = listOf("1 Gün", "1 Ay", "6 Ay", "Tümü")
            periods.forEachIndexed { index, text ->
                val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    this.text = text
                    id = View.generateViewId()
                }
                addView(btn)
                if (index == 3) check(btn.id)
            }

            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) loadGeneralStats(checkedId)
            }
        }
        layoutGeneralStats.addView(toggleTimeFilter)

        // 2. İstatistik Özeti (Metin)
        val tvStat = TextView(requireContext()).apply {
            id = View.generateViewId()
            text = "Veri Yükleniyor..."
            textSize = 18f
            setPadding(0, 32, 0, 32)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        layoutGeneralStats.addView(tvStat)

        // --- EN ÇOK İLGİ GÖRENLER ---
        addDivider(layoutGeneralStats)
        tvTopProductsTitle = TextView(requireContext()).apply {
            text = "🏆 En Çok İlgi Gören Ürünler"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        layoutGeneralStats.addView(tvTopProductsTitle)

        rvTopProducts = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(context)
        }
        layoutGeneralStats.addView(rvTopProducts)

        // --- KATEGORİ BAZLI İLGİ GRAFİĞİ ---
        addDivider(layoutGeneralStats)
        tvCategoryStatsTitle = TextView(requireContext()).apply {
            text = "📊 Kategori İlgi Dağılımı"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        layoutGeneralStats.addView(tvCategoryStatsTitle)

        layoutCategoryStats = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        layoutGeneralStats.addView(layoutCategoryStats)

        rootLayout.addView(layoutGeneralStats, 2)
    }

    private fun addDivider(layout: LinearLayout) {
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 32, 0, 32) }
            setBackgroundColor(Color.LTGRAY)
        }
        layout.addView(divider)
    }

    private fun updateVisibility() {
        if (targetStoreId != -1 && currentTab == 0) {
            // Manager -> Genel Bakış
            layoutGeneralStats.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            etSearch.visibility = View.GONE
            btnSort.visibility = View.GONE
            btnFilter.visibility = View.GONE
            tvTotalCount.visibility = View.GONE
            loadGeneralStats(toggleTimeFilter.checkedButtonId)
        } else {
            layoutGeneralStats.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            etSearch.visibility = View.VISIBLE
            btnSort.visibility = View.VISIBLE
            btnFilter.visibility = View.VISIBLE
            tvTotalCount.visibility = View.VISIBLE
        }
    }

    private fun loadGeneralStats(checkedId: Int) {
        val days = when (toggleTimeFilter.indexOfChild(toggleTimeFilter.findViewById(checkedId))) {
            0 -> 1
            1 -> 30
            2 -> 180
            else -> -1
        }

        // 1. Mağaza Tıklanma Verisi
        db.collection("stores").document(targetStoreId.toString()).get()
            .addOnSuccessListener { doc ->
                val store = doc.toObject(Store::class.java)
                val statsTv = layoutGeneralStats.getChildAt(1) as TextView

                if (store != null) {
                    var totalClicks = 0
                    if (days == -1) {
                        totalClicks = store.clickCount
                    } else {
                        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                        store.clickHistory.forEach { (dateStr, count) ->
                            try {
                                val date = sdf.parse(dateStr)
                                if (date != null && date.time >= cutoffTime) {
                                    totalClicks += count
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    val periodText = if(days == -1) "Tüm Zamanlar" else "Son $days Gün"
                    statsTv.text = "$periodText\nMağaza Ziyareti: $totalClicks"
                }
            }

        // 2. Ürünleri Çek ve Analiz Et
        db.collection("products")
            .whereEqualTo("storeId", targetStoreId)
            .get()
            .addOnSuccessListener { docs ->
                val allProducts = docs.toObjects(Product::class.java)

                // --- A) En Çok Tıklananları Bul ---
                val sortedProducts = allProducts.sortedByDescending { it.clickCount }
                val topProducts = sortedProducts.take(5).filter { it.clickCount > 0 }

                if (topProducts.isNotEmpty()) {
                    rvTopProducts.adapter = ReportProductAdapter(topProducts, false)
                    rvTopProducts.visibility = View.VISIBLE
                    tvTopProductsTitle.visibility = View.VISIBLE
                } else {
                    rvTopProducts.visibility = View.GONE
                    tvTopProductsTitle.text = "Henüz ürün etkileşimi yok."
                }

                // --- B) Kategori Grafiğini Çiz ---
                drawCategoryGraph(allProducts)
            }
    }

    private fun drawCategoryGraph(products: List<Product>) {
        layoutCategoryStats.removeAllViews()

        // Kategorilere göre tıklamaları topla
        val categoryMap = HashMap<String, Int>()
        var grandTotalClicks = 0

        for (p in products) {
            val cat = p.category
            val clicks = p.clickCount
            if (clicks > 0) {
                categoryMap[cat] = categoryMap.getOrDefault(cat, 0) + clicks
                grandTotalClicks += clicks
            }
        }

        if (categoryMap.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply { text = "Veri yok."; setPadding(16,0,0,0) }
            layoutCategoryStats.addView(emptyTv)
            return
        }

        // Grafiği Çiz (Çoktan aza sırala)
        val sortedCategories = categoryMap.toList().sortedByDescending { (_, value) -> value }

        for ((category, count) in sortedCategories) {
            val percentage = if (grandTotalClicks > 0) (count * 100) / grandTotalClicks else 0

            // Kapsayıcı
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 24)
                }
            }

            // Metin (Kategori Adı ve Sayı)
            val infoLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
            }
            val tvName = TextView(requireContext()).apply {
                text = category
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
                setTextColor(Color.DKGRAY)
                setTypeface(null, Typeface.BOLD)
            }
            val tvCount = TextView(requireContext()).apply {
                text = "$count Tık (%$percentage)"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
                gravity = Gravity.END
                setTextColor(Color.GRAY)
                textSize = 12f
            }
            infoLayout.addView(tvName)
            infoLayout.addView(tvCount)
            itemLayout.addView(infoLayout)

            // Progress Bar (Grafik Çubuğu)
            val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24).apply {
                    setMargins(0, 8, 0, 0)
                }
                max = 100
                progress = percentage
                progressTintList = android.content.res.ColorStateList.valueOf(getColorForCategory(category))
            }
            itemLayout.addView(progressBar)

            layoutCategoryStats.addView(itemLayout)
        }
    }

    private fun getColorForCategory(category: String): Int {
        val lower = category.lowercase()
        return when {
            lower.contains("yatak") -> Color.parseColor("#7E57C2") // Mor
            lower.contains("oturma") || lower.contains("koltuk") -> Color.parseColor("#FF7043") // Turuncu
            lower.contains("yemek") -> Color.parseColor("#66BB6A") // Yeşil
            lower.contains("ofis") -> Color.parseColor("#42A5F5") // Mavi
            lower.contains("çocuk") || lower.contains("genç") -> Color.parseColor("#EC407A") // Pembe
            else -> Color.GRAY
        }
    }

    private fun loadData(searchQuery: String = "") {
        if (targetStoreId != -1 && currentTab == 0) return

        tvTotalCount.text = "Yükleniyor..."

        if (targetStoreId == -1) {
            // ADMIN
            when (currentTab) {
                0 -> loadStores(searchQuery)
                1 -> loadProducts(searchQuery, false)
                2 -> loadProducts(searchQuery, true)
            }
        } else {
            // MANAGER
            when (currentTab) {
                1 -> loadProducts(searchQuery, false)
                2 -> loadProducts(searchQuery, true)
            }
        }
    }

    private fun loadStores(query: String) {
        db.collection("stores").get().addOnSuccessListener { docs ->
            val list = ArrayList<Store>()
            for (doc in docs) {
                val item = doc.toObject(Store::class.java)
                if (query.isEmpty() || item.name.contains(query, true)) list.add(item)
            }
            if (sortMode == 1) list.sortBy { it.clickCount } else list.sortByDescending { it.clickCount }
            tvTotalCount.text = "Toplam: ${list.size} Mağaza"
            recyclerView.adapter = ReportStoreAdapter(list)
        }
    }

    private fun loadProducts(query: String, isFavoriteMode: Boolean) {
        var dbQuery: Query = db.collection("products")
        if (targetStoreId != -1) dbQuery = dbQuery.whereEqualTo("storeId", targetStoreId)

        dbQuery.get().addOnSuccessListener { docs ->
            val list = ArrayList<Product>()
            for (doc in docs) {
                val item = doc.toObject(Product::class.java)

                // 1. Arama Filtresi
                val matchSearch = query.isEmpty() || item.name.contains(query, true)

                // 2. Fiyat Filtresi
                val price = parsePrice(item.price)
                val matchMin = filterMinPrice == null || price >= filterMinPrice!!
                val matchMax = filterMaxPrice == null || price <= filterMaxPrice!!

                // 3. Kategori Filtresi
                val matchCat = filterCategory == "Tümü" || item.category == filterCategory

                if (matchSearch && matchMin && matchMax && matchCat) {
                    if (!isFavoriteMode || item.favoriteCount > 0) {
                        list.add(item)
                    }
                }
            }

            when (sortMode) {
                0 -> if (isFavoriteMode) list.sortByDescending { it.favoriteCount } else list.sortByDescending { it.clickCount }
                1 -> if (isFavoriteMode) list.sortBy { it.favoriteCount } else list.sortBy { it.clickCount }
                2 -> list.sortByDescending { parsePrice(it.price) }
                3 -> list.sortBy { parsePrice(it.price) }
            }

            tvTotalCount.text = "Listelenen Ürün: ${list.size}"
            recyclerView.adapter = ReportProductAdapter(list, isFavoriteMode)
        }
    }

    private fun parsePrice(priceStr: String): Double {
        try {
            var clean = priceStr.replace("[^\\d.,]".toRegex(), "").trim()
            if (clean.contains(",")) clean = clean.replace(".", "").replace(",", ".") else clean = clean.replace(".", "")
            return clean.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) { return 0.0 }
    }

    private fun showSortDialog() {
        val options = arrayOf("Etkileşim (Çoktan Aza)", "Etkileşim (Azdan Çoğa)", "Fiyat (Pahalıdan Ucuza)", "Fiyat (Ucuzdan Pahalıya)")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Sıralama")
            .setItems(options) { _, w -> sortMode = w; loadData(etSearch.text.toString()) }
            .show()
    }

    inner class ReportStoreAdapter(private val items: List<Store>) : RecyclerView.Adapter<ReportStoreAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvStoreName); val rank: TextView = v.findViewById(R.id.tvRank); val click: TextView = v.findViewById(R.id.tvClickCount); val cat: TextView = v.findViewById(R.id.tvCategory)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_report_store, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]; holder.rank.text = "${position + 1}"; holder.name.text = item.name; holder.cat.text = item.category; holder.click.text = "${item.clickCount} Tık"
        }
        override fun getItemCount() = items.size
    }

    inner class ReportProductAdapter(private val items: List<Product>, private val isFavMode: Boolean) : RecyclerView.Adapter<ReportProductAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgProduct); val name: TextView = v.findViewById(R.id.tvProductName); val price: TextView = v.findViewById(R.id.tvPrice); val stat: TextView = v.findViewById(R.id.tvStatCount); val label: TextView = v.findViewById(R.id.tvStatLabel)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_report_product, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]; holder.name.text = item.name; holder.price.text = PriceUtils.formatPriceStyled(item.price)
            if (isFavMode) { holder.stat.text = "${item.favoriteCount} ❤️"; holder.label.text = "Favorilenme" } else { holder.stat.text = "${item.clickCount} 👆"; holder.label.text = "Görüntülenme" }
            Glide.with(holder.itemView).load(item.imageUrl).into(holder.img)
        }
        override fun getItemCount() = items.size
    }
}