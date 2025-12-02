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
import androidx.recyclerview.widget.LinearLayoutManager
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

    // Dinamik oluşturulan View referansları (Genel Bakış ekranı için)
    private var layoutGeneralStats: LinearLayout? = null
    private var toggleTimeFilter: MaterialButtonToggleGroup? = null
    private var rvTopProducts: RecyclerView? = null
    private var tvTopProductsTitle: TextView? = null
    private var layoutCategoryStats: LinearLayout? = null
    private var tvCategoryStatsTitle: TextView? = null
    private var tvStatSummary: TextView? = null

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

        // 1. Programatik Arayüzü (Genel İstatistikler) Kur
        // XML içinde olmayan, kodla eklenen alanı binding.root (LinearLayout) içine ekliyoruz.
        setupGeneralStatsUI(binding.root as LinearLayout)

        binding.rvReports.layoutManager = LinearLayoutManager(context)

        setupTabs()

        binding.tabLayoutReports.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateVisibility()
                // Arama kutusundaki metne göre veriyi yenile
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

        // Filtre verilerini doldur (Kategoriler ve Mağazalar)
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
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Kullanıcılar"))
        } else {
            // Manager Modu
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Genel Bakış"))
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Ürünlerim"))
            binding.tabLayoutReports.addTab(binding.tabLayoutReports.newTab().setText("Favorilenme"))
        }
    }

    // --- PROGRAMATİK UI OLUŞTURMA (ViewBinding ile Entegre) ---
    private fun setupGeneralStatsUI(rootLayout: LinearLayout) {
        val context = requireContext()

        // Ana Container
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

        // 1. Zaman Filtresi (Toggle Group)
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
                if (index == 3) check(btn.id) // Varsayılan "Tümü"
            }

            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) loadGeneralStats(checkedId)
            }
        }
        layoutGeneralStats?.addView(toggleTimeFilter)

        // 2. İstatistik Özeti (Metin)
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

        // --- EN ÇOK İLGİ GÖRENLER ---
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
        }
        layoutGeneralStats?.addView(rvTopProducts)

        // --- KATEGORİ BAZLI İLGİ GRAFİĞİ ---
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

        // Fragment'ın ana layoutuna (SearchView'ın altına) ekle.
        // XML'de index 0: SearchBar, index 1: TabLayout, index 2: RecyclerView.
        // Biz bunu TabLayout ile RecyclerView arasına ekleyebiliriz veya en alta.
        // Orijinal kodda "rootLayout.addView(..., 2)" denmiş.
        // Ancak ViewBinding inflate edilen root LinearLayout olduğu için index kontrolü önemli.
        // SearchBar(0), TabLayout(1), TotalCount(2), RecyclerView(3)
        // Güvenli olması için TabLayout'un hemen altına ekleyelim.
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

    // --- FİLTRE VE VERİ HAZIRLIĞI ---
    private fun preloadFilterData() {
        // Kategorileri Çek
        db.collection("products").get().addOnSuccessListener { docs ->
            val cats = docs.toObjects(Product::class.java).map { it.category }.distinct().sorted()
            allCategories.clear()
            allCategories.add("Tümü")
            allCategories.addAll(cats)
        }
        // Mağazaları Çek
        db.collection("stores").get().addOnSuccessListener { docs ->
            val stores = docs.toObjects(Store::class.java).map { it.name }.sorted()
            allStoreNames.clear()
            allStoreNames.add("Tümü")
            allStoreNames.addAll(stores)
        }
    }

    private fun showFilterDialog() {
        // Dialog layout için ViewBinding kullanmıyoruz, basit inflater yeterli.
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report_filter, null)

        val etMin = dialogView.findViewById<EditText>(R.id.etMinPrice)
        val etMax = dialogView.findViewById<EditText>(R.id.etMaxPrice)
        val spinCat = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinStore = dialogView.findViewById<Spinner>(R.id.spinnerStore)

        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allCategories)
        spinCat.adapter = catAdapter

        val storeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, allStoreNames)
        spinStore.adapter = storeAdapter

        // Mevcut değerleri geri yükle
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
        val options = arrayOf(
            "Etkileşim (Çoktan Aza)",
            "Etkileşim (Azdan Çoğa)",
            "Fiyat (Pahalıdan Ucuza)",
            "Fiyat (Ucuzdan Pahalıya)"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Sıralama")
            .setItems(options) { _, which ->
                sortMode = which
                loadData(binding.etSearchReport.text.toString())
            }
            .show()
    }

    private fun updateVisibility() {
        // Eğer Manager giriş yaptıysa ve 1. Sekme (Genel Bakış) seçiliyse
        if (targetStoreId != -1 && currentTab == 0) {
            layoutGeneralStats?.visibility = View.VISIBLE
            binding.rvReports.visibility = View.GONE
            binding.etSearchReport.visibility = View.GONE
            binding.btnSortReport.visibility = View.GONE
            binding.btnFilterReport.visibility = View.GONE
            binding.tvReportCount.visibility = View.GONE

            // Genel istatistikleri yükle
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

    // --- VERİ YÜKLEME VE ANALİZ ---

    private fun loadGeneralStats(checkedId: Int) {
        if (toggleTimeFilter == null) return

        val button = toggleTimeFilter!!.findViewById<View>(checkedId)
        val index = toggleTimeFilter!!.indexOfChild(button)

        val days = when (index) {
            0 -> 1    // 1 Gün
            1 -> 30   // 1 Ay
            2 -> 180  // 6 Ay
            else -> -1 // Tümü
        }

        // 1. Mağaza Tıklanma Verisi
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
                                if (date != null && date.time >= cutoffTime) {
                                    totalClicks += count
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    val periodText = if(days == -1) "Tüm Zamanlar" else "Son $days Gün"
                    tvStatSummary?.text = "$periodText\nMağaza Ziyareti: $totalClicks"
                }
            }

        // 2. Ürünleri Çek ve Analiz Et
        db.collection("products")
            .whereEqualTo("storeId", targetStoreId)
            .get()
            .addOnSuccessListener { docs ->
                val allProducts = docs.toObjects(Product::class.java)

                // A) En Çok Tıklananları Bul (Top 5)
                val sortedProducts = allProducts.sortedByDescending { it.clickCount }
                val topProducts = sortedProducts.take(5).filter { it.clickCount > 0 }

                if (topProducts.isNotEmpty()) {
                    rvTopProducts?.adapter = ReportProductAdapter(topProducts, false)
                    rvTopProducts?.visibility = View.VISIBLE
                    tvTopProductsTitle?.visibility = View.VISIBLE
                } else {
                    rvTopProducts?.visibility = View.GONE
                    tvTopProductsTitle?.text = "Bu periyotta ürün etkileşimi yok."
                }

                // B) Kategori Grafiğini Çiz
                drawCategoryGraph(allProducts)
            }
    }

    private fun drawCategoryGraph(products: List<Product>) {
        layoutCategoryStats?.removeAllViews()

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
            val emptyTv = TextView(requireContext()).apply {
                text = "Grafik verisi bulunamadı."
                setPadding(16,0,0,0)
                setTextColor(Color.GRAY)
            }
            layoutCategoryStats?.addView(emptyTv)
            return
        }

        // Grafiği Çiz (Çoktan aza sırala)
        val sortedCategories = categoryMap.toList().sortedByDescending { (_, value) -> value }

        for ((category, count) in sortedCategories) {
            val percentage = if (grandTotalClicks > 0) (count * 100) / grandTotalClicks else 0

            // Kapsayıcı Layout
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 24)
                }
            }

            // Metin Satırı (Kategori Adı ve Sayı)
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
                setTextColor(Color.parseColor("#757575"))
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
                progressTintList = ColorStateList.valueOf(getColorForCategory(category))
            }
            itemLayout.addView(progressBar)

            layoutCategoryStats?.addView(itemLayout)
        }
    }

    private fun getColorForCategory(category: String): Int {
        val lower = category.lowercase(Locale.getDefault())
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
        // Manager ve Genel Bakış sekmesindeysek listeyi yükleme
        if (targetStoreId != -1 && currentTab == 0) return

        binding.tvReportCount.text = "Yükleniyor..."

        if (targetStoreId == -1) {
            // ADMIN MODU
            when (currentTab) {
                0 -> loadStores(searchQuery)
                1 -> loadProducts(searchQuery, false)
                2 -> loadUsers(searchQuery) // Admin için Kullanıcılar sekmesi
            }
        } else {
            // MANAGER MODU (0. sekme Genel Bakış, 1. Ürünler, 2. Favorilenme)
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

                // Filtreleme
                val matchSearch = query.isEmpty() || item.name.contains(query, true)
                // Mağaza için fiyat/kategori filtresi uygulanamaz, o yüzden geçiyoruz

                if (matchSearch) list.add(item)
            }

            // Sıralama (Mağazalar için genelde Tıklanma sayısı kullanılır)
            if (sortMode == 1) list.sortBy { it.clickCount } else list.sortByDescending { it.clickCount }

            binding.tvReportCount.text = "Toplam: ${list.size} Mağaza"
            binding.rvReports.adapter = ReportStoreAdapter(list)
        }
    }

    private fun loadUsers(query: String) {
        // Admin için kullanıcı listesi (Son aktifliğe göre)
        db.collection("users").get().addOnSuccessListener { docs ->
            val list = ArrayList<User>()
            for (doc in docs) {
                val user = doc.toObject(User::class.java)
                val matchSearch = query.isEmpty() || user.fullName.contains(query, true) || user.email.contains(query, true)
                if (matchSearch) list.add(user)
            }
            binding.tvReportCount.text = "Toplam: ${list.size} Kullanıcı"
            binding.rvReports.adapter = ReportUserAdapter(list)
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
                val price = PriceUtils.parsePrice(item.price) // PriceUtils kullanıyoruz
                val matchMin = filterMinPrice == null || price >= filterMinPrice!!
                val matchMax = filterMaxPrice == null || price <= filterMaxPrice!!

                // 3. Kategori Filtresi
                val matchCat = filterCategory == "Tümü" || item.category == filterCategory

                // 4. Mağaza İsmi Filtresi (Admin için)
                // Not: Ürün objesinde storeName tutulmuyorsa, bu filtreyi yapmak için storeId lookup gerekir.
                // Basitlik adına client-side bu versiyonda storeName filtresini atlıyoruz veya storeId ile eşleştiriyoruz.
                // Eğer ürün objesinde mağaza adı yoksa, bu filtre verimsiz olabilir.

                if (matchSearch && matchMin && matchMax && matchCat) {
                    // Favori modundaysa, sadece favori sayısı > 0 olanları göster
                    if (!isFavoriteMode || item.favoriteCount > 0) {
                        list.add(item)
                    }
                }
            }

            // Sıralama
            when (sortMode) {
                0 -> if (isFavoriteMode) list.sortByDescending { it.favoriteCount } else list.sortByDescending { it.clickCount }
                1 -> if (isFavoriteMode) list.sortBy { it.favoriteCount } else list.sortBy { it.clickCount }
                2 -> list.sortByDescending { PriceUtils.parsePrice(it.price) }
                3 -> list.sortBy { PriceUtils.parsePrice(it.price) }
            }

            binding.tvReportCount.text = "Listelenen Ürün: ${list.size}"
            binding.rvReports.adapter = ReportProductAdapter(list, isFavoriteMode)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Referansları temizle
        layoutGeneralStats = null
        toggleTimeFilter = null
        rvTopProducts = null
        tvTopProductsTitle = null
        layoutCategoryStats = null
        tvCategoryStatsTitle = null
        tvStatSummary = null
    }

    // --- ADAPTERLAR (ViewBinding ile) ---

    inner class ReportStoreAdapter(private val items: List<Store>) : RecyclerView.Adapter<ReportStoreAdapter.VH>() {
        inner class VH(val binding: ItemReportStoreBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.tvRank.text = "${position + 1}"
            holder.binding.tvStoreName.text = item.name
            holder.binding.tvCategory.text = item.category
            holder.binding.tvClickCount.text = "${item.clickCount} Tık"
        }
        override fun getItemCount() = items.size
    }

    inner class ReportProductAdapter(private val items: List<Product>, private val isFavMode: Boolean) : RecyclerView.Adapter<ReportProductAdapter.VH>() {
        inner class VH(val binding: ItemReportProductBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
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
        override fun getItemCount() = items.size
    }

    inner class ReportUserAdapter(private val items: List<User>) : RecyclerView.Adapter<ReportUserAdapter.VH>() {
        inner class VH(val binding: ItemReportUserBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = items[position]
            holder.binding.tvUserName.text = user.fullName
            holder.binding.tvUserEmail.text = user.email
            holder.binding.tvUserRole.text = user.role.name

            // Eğer butonlarınız varsa (Banla, Rol Değiştir), click listenerları buraya ekleyin.
            // Örnek: holder.binding.btnBanUser.setOnClickListener { ... }
            // Ancak bu bir rapor ekranı olduğu için sadece listeleme yapıyoruz.
            // Yönetim işlemleri UserManagementFragment'da.
            holder.binding.btnChangeRole.visibility = View.GONE
            holder.binding.btnBanUser.visibility = View.GONE
        }
        override fun getItemCount() = items.size
    }
}