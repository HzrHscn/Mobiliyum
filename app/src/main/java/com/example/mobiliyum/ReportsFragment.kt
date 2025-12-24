package com.example.mobiliyum

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.setPadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.FragmentReportsMainBinding
import com.example.mobiliyum.databinding.ItemReportProductBinding
import com.example.mobiliyum.databinding.ItemReportStoreBinding
import com.example.mobiliyum.databinding.ItemReportUserBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class ReportsFragment : Fragment() {

    // ViewBinding
    private var _binding: FragmentReportsMainBinding? = null
    private val binding get() = _binding!!

    // Dinamik oluşturulan View referansları
    private var layoutGeneralStats: LinearLayout? = null
    private var toggleTimeFilter: MaterialButtonToggleGroup? = null
    private var rvTopProducts: RecyclerView? = null
    private var tvTopProductsTitle: TextView? = null
    private var layoutCategoryStats: LinearLayout? = null
    private var tvCategoryStatsTitle: TextView? = null
    private var tvStatSummary: TextView? = null

    private val storeAdapter = ReportStoreAdapter()
    // Kullanıcı listesi yerine Favorilenen Ürünler için adapter
    private val productAdapterNormal = ReportProductAdapter(false)
    private val productAdapterFav = ReportProductAdapter(true) // BU ADAPTER'I KULLANACAĞIZ
    private val topProductsAdapter = ReportProductAdapter(false)

    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
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
        arguments?.let {
            targetStoreId = it.getInt("storeId", -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGeneralStatsUI(binding.root as LinearLayout)

        binding.rvReports.layoutManager = LinearLayoutManager(context)
        binding.rvReports.adapter = storeAdapter

        setupTabs()

        binding.tabLayoutReports.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateVisibility()
                loadData(binding.etSearchReport.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnSortReport.setOnClickListener { showSortDialog() }
        binding.btnFilterReport.setOnClickListener { showFilterDialog() }

        binding.etSearchReport.addTextChangedListener {
            loadData(it.toString())
        }

        preloadFilterData()
        updateVisibility()
        loadData()
    }

    private fun setupTabs() {
        binding.tabLayoutReports.removeAllTabs()
        if (targetStoreId == -1) {
            // Admin Modu
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Mağazalar"))
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Ürünler"))
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Favorilenenler")) // DEĞİŞTİ
        } else {
            // Manager Modu
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Genel Bakış"))
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Ürünlerim"))
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Favorilenme"))
        }
    }

    // --- PROGRAMATİK UI OLUŞTURMA (AYNI KALIYOR) ---
    private fun setupGeneralStatsUI(rootLayout: LinearLayout) {
        val context = requireContext()

        layoutGeneralStats = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(32)
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        toggleTimeFilter = MaterialButtonToggleGroup(context, null, com.google.android.material.R.attr.materialButtonToggleGroupStyle).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }

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
        layoutGeneralStats?.addView(toggleTimeFilter)

        tvStatSummary = TextView(context).apply {
            id = View.generateViewId()
            text = "Veri Yükleniyor..."
            textSize = 18f
            setPadding(0, 32, 0, 32)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#333333"))
        }
        layoutGeneralStats?.addView(tvStatSummary)

        addDivider(layoutGeneralStats!!)
        tvTopProductsTitle = TextView(context).apply {
            text = "🏆 En Çok İlgi Gören Ürünler"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        }
        layoutGeneralStats?.addView(tvTopProductsTitle)

        rvTopProducts = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(context)
            isNestedScrollingEnabled = false
            adapter = topProductsAdapter
        }
        layoutGeneralStats?.addView(rvTopProducts)

        addDivider(layoutGeneralStats!!)
        tvCategoryStatsTitle = TextView(context).apply {
            text = "📊 Kategori İlgi Dağılımı"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        }
        layoutGeneralStats?.addView(tvCategoryStatsTitle)

        layoutCategoryStats = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        layoutGeneralStats?.addView(layoutCategoryStats)

        val tabIndex = rootLayout.indexOfChild(binding.tabLayoutReports)
        rootLayout.addView(layoutGeneralStats, tabIndex + 1)
    }

    private fun addDivider(layout: LinearLayout) {
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 32, 0, 32)
            }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        layout.addView(divider)
    }

    private fun preloadFilterData() {
        db.collection("products").get().addOnSuccessListener { docs ->
            val cats = docs.toObjects(Product::class.java).map { it.category }.distinct().sorted()
            allCategories.clear()
            allCategories.add("Tümü")
            allCategories.addAll(cats)
        }
        db.collection("stores").get().addOnSuccessListener { docs ->
            val stores = docs.toObjects(Store::class.java).map { it.name }.sorted()
            allStoreNames.clear()
            allStoreNames.add("Tümü")
            allStoreNames.addAll(stores)
        }
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report_filter, null)
        val etMin = dialogView.findViewById<EditText>(R.id.etMinPrice)
        val etMax = dialogView.findViewById<EditText>(R.id.etMaxPrice)
        val spinCat = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinStore = dialogView.findViewById<Spinner>(R.id.spinnerStore)

        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allCategories)
        spinCat.adapter = catAdapter
        val storeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allStoreNames)
        spinStore.adapter = storeAdapter

        if(filterMinPrice != null) etMin.setText(filterMinPrice.toString())
        if(filterMaxPrice != null) etMax.setText(filterMaxPrice.toString())
        val catIndex = allCategories.indexOf(filterCategory)
        if (catIndex >= 0) spinCat.setSelection(catIndex)
        val storeIndex = allStoreNames.indexOf(filterStoreName)
        if (storeIndex >= 0) spinStore.setSelection(storeIndex)

        AlertDialog.Builder(context)
            .setTitle("Filtrele")
            .setView(dialogView)
            .setPositiveButton("Uygula") { _, _ ->
                filterMinPrice = etMin.text.toString().toDoubleOrNull()
                filterMaxPrice = etMax.text.toString().toDoubleOrNull()
                filterCategory = spinCat.selectedItem?.toString() ?: "Tümü"
                filterStoreName = spinStore.selectedItem?.toString() ?: "Tümü"
                loadData(binding.etSearchReport.text.toString())
            }
            .setNeutralButton("Temizle") { _, _ ->
                filterMinPrice = null
                filterMaxPrice = null
                filterCategory = "Tümü"
                filterStoreName = "Tümü"
                loadData(binding.etSearchReport.text.toString())
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf("Etkileşim (Çoktan Aza)", "Etkileşim (Azdan Çoğa)", "Fiyat (Pahalıdan Ucuza)", "Fiyat (Ucuzdan Pahalıya)")
        AlertDialog.Builder(requireContext())
            .setTitle("Sıralama")
            .setItems(options) { _, which ->
                sortMode = which
                loadData(binding.etSearchReport.text.toString())
            }
            .show()
    }

    private fun updateVisibility() {
        if (targetStoreId != -1 && currentTab == 0) {
            layoutGeneralStats?.visibility = View.VISIBLE
            binding.rvReports.visibility = View.GONE
            binding.etSearchReport.visibility = View.GONE
            binding.btnSortReport.visibility = View.GONE
            binding.btnFilterReport.visibility = View.GONE
            binding.tvReportCount.visibility = View.GONE
            toggleTimeFilter?.checkedButtonId?.let { loadGeneralStats(it) }
        } else {
            layoutGeneralStats?.visibility = View.GONE
            binding.rvReports.visibility = View.VISIBLE
            binding.etSearchReport.visibility = View.VISIBLE
            binding.btnSortReport.visibility = View.VISIBLE
            binding.btnFilterReport.visibility = View.VISIBLE
            binding.tvReportCount.visibility = View.VISIBLE
        }
    }

    private fun loadGeneralStats(checkedId: Int) {
        if (toggleTimeFilter == null) return
        val button = toggleTimeFilter!!.findViewById<View>(checkedId)
        val index = toggleTimeFilter!!.indexOfChild(button)
        val days = when (index) { 0 -> 1; 1 -> 30; 2 -> 180; else -> -1 }

        db.collection("stores").document(targetStoreId.toString()).get()
            .addOnSuccessListener { doc ->
                val store = doc.toObject(Store::class.java)
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
                                if (date != null && date.time >= cutoffTime) totalClicks += count
                            } catch (e: Exception) {}
                        }
                    }
                    val periodText = if(days == -1) "Tüm Zamanlar" else "Son $days Gün"
                    tvStatSummary?.text = "$periodText\nMağaza Ziyareti: $totalClicks"
                }
            }

        db.collection("products")
            .whereEqualTo("storeId", targetStoreId)
            .get()
            .addOnSuccessListener { docs ->
                val allProducts = docs.toObjects(Product::class.java)
                val sortedProducts = allProducts.sortedByDescending { it.clickCount }
                val topProducts = sortedProducts.take(5).filter { it.clickCount > 0 }

                if (topProducts.isNotEmpty()) {
                    topProductsAdapter.submitList(topProducts)
                    rvTopProducts?.visibility = View.VISIBLE
                    tvTopProductsTitle?.visibility = View.VISIBLE
                } else {
                    rvTopProducts?.visibility = View.GONE
                    tvTopProductsTitle?.text = "Bu periyotta ürün etkileşimi yok."
                }
                drawCategoryGraph(allProducts)
            }
    }

    private fun drawCategoryGraph(products: List<Product>) {
        layoutCategoryStats?.removeAllViews()
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
            val emptyTv = TextView(requireContext()).apply {
                text = "Grafik verisi bulunamadı."
                setPadding(16,0,0,0)
                setTextColor(Color.GRAY)
            }
            layoutCategoryStats?.addView(emptyTv)
            return
        }
        val sortedCategories = categoryMap.toList().sortedByDescending { (_, value) -> value }
        for ((category, count) in sortedCategories) {
            val percentage = if (grandTotalClicks > 0) (count * 100) / grandTotalClicks else 0
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
            }
            val infoLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 1f }
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
                setTextColor(Color.parseColor("#757575"))
                textSize = 12f
            }
            infoLayout.addView(tvName)
            infoLayout.addView(tvCount)
            itemLayout.addView(infoLayout)
            val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24).apply { setMargins(0, 8, 0, 0) }
                max = 100
                progress = percentage
                progressTintList = ColorStateList.valueOf(getColorForCategory(category))
            }
            itemLayout.addView(progressBar)
            layoutCategoryStats?.addView(itemLayout)
        }
    }

    private fun getColorForCategory(category: String): Int {
        val lower = category.lowercase(Locale.getDefault())
        return when {
            lower.contains("yatak") -> Color.parseColor("#7E57C2")
            lower.contains("oturma") || lower.contains("koltuk") -> Color.parseColor("#FF7043")
            lower.contains("yemek") -> Color.parseColor("#66BB6A")
            lower.contains("ofis") -> Color.parseColor("#42A5F5")
            lower.contains("çocuk") || lower.contains("genç") -> Color.parseColor("#EC407A")
            else -> Color.GRAY
        }
    }

    private fun loadData(searchQuery: String = "") {
        if (targetStoreId != -1 && currentTab == 0) return
        binding.tvReportCount.text = "Yükleniyor..."

        if (targetStoreId == -1) { // ADMIN
            when (currentTab) {
                0 -> loadStores(searchQuery)
                1 -> loadProducts(searchQuery, false)
                2 -> loadProducts(searchQuery, true) // Favorilenenleri Getir
            }
        } else { // MANAGER
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
                val matchSearch = query.isEmpty() || item.name.contains(query, true)
                if (matchSearch) list.add(item)
            }
            if (sortMode == 1) list.sortBy { it.clickCount } else list.sortByDescending { it.clickCount }
            binding.tvReportCount.text = "Toplam: ${list.size} Mağaza"

            if (binding.rvReports.adapter != storeAdapter) {
                binding.rvReports.adapter = storeAdapter
            }
            storeAdapter.submitList(list)
        }
    }

    private fun loadProducts(query: String, isFavoriteMode: Boolean) {
        var dbQuery: Query = db.collection("products")
        if (targetStoreId != -1) dbQuery = dbQuery.whereEqualTo("storeId", targetStoreId)

        dbQuery.get().addOnSuccessListener { docs ->
            val list = ArrayList<Product>()
            for (doc in docs) {
                val item = doc.toObject(Product::class.java)
                val matchSearch = query.isEmpty() || item.name.contains(query, true)
                val price = PriceUtils.parsePrice(item.price)
                val matchMin = filterMinPrice == null || price >= filterMinPrice!!
                val matchMax = filterMaxPrice == null || price <= filterMaxPrice!!
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
                2 -> list.sortByDescending { PriceUtils.parsePrice(it.price) }
                3 -> list.sortBy { PriceUtils.parsePrice(it.price) }
            }
            binding.tvReportCount.text = "Listelenen Ürün: ${list.size}"

            val targetAdapter = if (isFavoriteMode) productAdapterFav else productAdapterNormal
            if (binding.rvReports.adapter != targetAdapter) {
                binding.rvReports.adapter = targetAdapter
            }
            targetAdapter.submitList(list)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        layoutGeneralStats = null
        toggleTimeFilter = null
        rvTopProducts = null
        tvTopProductsTitle = null
        layoutCategoryStats = null
        tvCategoryStatsTitle = null
        tvStatSummary = null
    }

    // --- ADAPTERLAR ---

    // 1. ReportStoreAdapter (Stores için özel)
    class ReportStoreAdapter : ListAdapter<Store, ReportStoreAdapter.VH>(DiffCallback()) {
        class DiffCallback : DiffUtil.ItemCallback<Store>() {
            override fun areItemsTheSame(oldItem: Store, newItem: Store) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Store, newItem: Store) = oldItem == newItem
        }
        inner class VH(val binding: ItemReportStoreBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.binding.tvRank.text = "${position + 1}"
            holder.binding.tvStoreName.text = item.name
            holder.binding.tvCategory.text = item.category
            holder.binding.tvClickCount.text = "${item.clickCount} Tık"
        }
    }

    // 2. ReportProductAdapter (Ürünler ve Favoriler için ortak)
    class ReportProductAdapter(private val isFavMode: Boolean) : ListAdapter<Product, ReportProductAdapter.VH>(DiffCallback()) {
        class DiffCallback : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
        }
        inner class VH(val binding: ItemReportProductBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.binding.tvProductName.text = item.name
            holder.binding.tvPrice.text = PriceUtils.formatPriceStyled(item.price)
            if (isFavMode) {
                holder.binding.tvStatCount.text = "${item.favoriteCount} ❤️"
                holder.binding.tvStatLabel.text = "Favorilenme"
            } else {
                holder.binding.tvStatCount.text = "${item.clickCount} 👆"
                holder.binding.tvStatLabel.text = "Görüntülenme"
            }
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.binding.imgProduct)
        }
    }

    // 3. ReportUserAdapter SİLİNDİ (Artık kullanılmıyor)
}