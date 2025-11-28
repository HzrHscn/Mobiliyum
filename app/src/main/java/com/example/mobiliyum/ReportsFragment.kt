package com.example.mobiliyum

import android.os.Bundle
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
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore

class ReportsFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnSort: MaterialButton
    private lateinit var tvTotalCount: TextView

    private val db = FirebaseFirestore.getInstance()

    // Aktif Rapor Tipi (0: Mağaza, 1: Ürün, 2: Favori, 3: Kullanıcı)
    private var currentTab = 0

    // Sıralama Modu
    // 0: Tıklama (Çoktan Aza) - Varsayılan
    // 1: Tıklama (Azdan Çoğa)
    // 2: Fiyat (Pahalıdan Ucuza)
    // 3: Fiyat (Ucuzdan Pahalıya)
    private var sortMode = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reports_main, container, false)

        tabLayout = view.findViewById(R.id.tabLayoutReports)
        recyclerView = view.findViewById(R.id.rvReports)
        etSearch = view.findViewById(R.id.etSearchReport)
        btnSort = view.findViewById(R.id.btnSortReport)
        tvTotalCount = view.findViewById(R.id.tvReportCount)

        recyclerView.layoutManager = LinearLayoutManager(context)

        tabLayout.addTab(tabLayout.newTab().setText("Mağazalar"))
        tabLayout.addTab(tabLayout.newTab().setText("Ürünler"))
        tabLayout.addTab(tabLayout.newTab().setText("Favoriler"))
        tabLayout.addTab(tabLayout.newTab().setText("Kullanıcılar"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                loadData(etSearch.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnSort.setOnClickListener {
            showSortDialog()
        }

        etSearch.addTextChangedListener {
            loadData(it.toString())
        }

        loadData()

        return view
    }

    private fun loadData(searchQuery: String = "") {
        tvTotalCount.text = "Yükleniyor..."

        when (currentTab) {
            0 -> loadStores(searchQuery)
            1 -> loadProducts(searchQuery, false)
            2 -> loadProducts(searchQuery, true)
            3 -> loadUsers(searchQuery)
        }
    }

    // --- 1. MAĞAZA RAPORLARI ---
    private fun loadStores(query: String) {
        // Tüm mağazaları çekip hafızada sıralayacağız (0 tıklamalılar da gelsin diye)
        db.collection("stores").get().addOnSuccessListener { docs ->
            val list = ArrayList<Store>()
            for (doc in docs) {
                val item = doc.toObject(Store::class.java)
                // Arama Filtresi
                if (query.isEmpty() || item.name.contains(query, true)) {
                    list.add(item)
                }
            }

            // Kotlin Tarafında Sıralama
            if (sortMode == 1) {
                list.sortBy { it.clickCount } // Azdan Çoğa
            } else {
                list.sortByDescending { it.clickCount } // Çoktan Aza (Varsayılan)
            }

            tvTotalCount.text = "Toplam: ${list.size} Mağaza"
            recyclerView.adapter = ReportStoreAdapter(list)
        }
    }

    // --- 2 & 3. ÜRÜN ve FAVORİ RAPORLARI ---
    private fun loadProducts(query: String, isFavoriteMode: Boolean) {
        // Tüm ürünleri çekiyoruz (Firestore limitine takılmamak için sıralamayı burada yapmıyoruz)
        db.collection("products").get().addOnSuccessListener { docs ->
            val list = ArrayList<Product>()
            for (doc in docs) {
                val item = doc.toObject(Product::class.java)
                // Arama Filtresi
                if (query.isEmpty() || item.name.contains(query, true)) {
                    if (!isFavoriteMode || item.favoriteCount > 0) {
                        list.add(item)
                    }
                }
            }

            // --- GELİŞMİŞ SIRALAMA MANTIĞI ---
            when (sortMode) {
                0 -> { // Tıklama / Favori (Çoktan Aza)
                    if (isFavoriteMode) list.sortByDescending { it.favoriteCount }
                    else list.sortByDescending { it.clickCount }
                }
                1 -> { // Tıklama / Favori (Azdan Çoğa)
                    if (isFavoriteMode) list.sortBy { it.favoriteCount }
                    else list.sortBy { it.clickCount }
                }
                2 -> { // Fiyat (Pahalıdan Ucuza)
                    list.sortByDescending { parsePrice(it.price) }
                }
                3 -> { // Fiyat (Ucuzdan Pahalıya)
                    list.sortBy { parsePrice(it.price) }
                }
            }

            tvTotalCount.text = "Toplam: ${list.size} Ürün"
            recyclerView.adapter = ReportProductAdapter(list, isFavoriteMode)
        }
    }

    // --- Fiyat Ayrıştırıcı Yardımcı Fonksiyon ---
    private fun parsePrice(priceStr: String): Double {
        try {
            // "19.210,00 ₺" -> "19210.00" formatına çeviriyoruz
            var clean = priceStr.replace("[^\\d.,]".toRegex(), "").trim()
            if (clean.contains(",")) {
                clean = clean.replace(".", "").replace(",", ".")
            } else {
                clean = clean.replace(".", "")
            }
            return clean.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            return 0.0
        }
    }

    // --- 4. KULLANICI RAPORLARI ---
    private fun loadUsers(query: String) {
        db.collection("users").get().addOnSuccessListener { docs ->
            val list = ArrayList<User>()
            for (doc in docs) {
                val item = doc.toObject(User::class.java)
                if (query.isEmpty() || item.fullName.contains(query, true) || item.email.contains(query, true)) {
                    list.add(item)
                }
            }
            tvTotalCount.text = "Kayıtlı Kullanıcı: ${list.size}"
            recyclerView.adapter = ReportUserAdapter(list) { user, isBan ->
                toggleUserBan(user, isBan)
            }
        }
    }

    private fun toggleUserBan(user: User, ban: Boolean) {
        db.collection("users").document(user.id)
            .update("isBanned", ban)
            .addOnSuccessListener {
                Toast.makeText(context, if(ban) "Kullanıcı Engellendi" else "Engel Kaldırıldı", Toast.LENGTH_SHORT).show()
                loadData()
            }
    }

    private fun showSortDialog() {
        val options = arrayOf("Tıklama/Favori (Çoktan Aza)", "Tıklama/Favori (Azdan Çoğa)", "Fiyat (Pahalıdan Ucuza)", "Fiyat (Ucuzdan Pahalıya)")
        android.app.AlertDialog.Builder(context)
            .setTitle("Sıralama Seçin")
            .setItems(options) { _, which ->
                sortMode = which
                loadData(etSearch.text.toString())
            }
            .show()
    }

    // --- ADAPTERLAR ---

    inner class ReportStoreAdapter(private val items: List<Store>) : RecyclerView.Adapter<ReportStoreAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvStoreName)
            val rank: TextView = v.findViewById(R.id.tvRank)
            val click: TextView = v.findViewById(R.id.tvClickCount)
            val cat: TextView = v.findViewById(R.id.tvCategory)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_report_store, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.rank.text = "${position + 1}"
            holder.name.text = item.name
            holder.cat.text = item.category
            holder.click.text = "${item.clickCount} Tık"
        }
        override fun getItemCount() = items.size
    }

    inner class ReportProductAdapter(private val items: List<Product>, private val isFavMode: Boolean) : RecyclerView.Adapter<ReportProductAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgProduct)
            val name: TextView = v.findViewById(R.id.tvProductName)
            val price: TextView = v.findViewById(R.id.tvPrice)
            val stat: TextView = v.findViewById(R.id.tvStatCount)
            val label: TextView = v.findViewById(R.id.tvStatLabel)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_report_product, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.price.text = PriceUtils.formatPriceStyled(item.price) // Güzel fiyat

            if (isFavMode) {
                holder.stat.text = "${item.favoriteCount} ❤️"
                holder.label.text = "Favorilenme"
            } else {
                holder.stat.text = "${item.clickCount} 👆"
                holder.label.text = "Görüntülenme"
            }
            Glide.with(holder.itemView).load(item.imageUrl).into(holder.img)
        }
        override fun getItemCount() = items.size
    }

    inner class ReportUserAdapter(private val items: List<User>, private val onBanClick: (User, Boolean) -> Unit) : RecyclerView.Adapter<ReportUserAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvUserName)
            val email: TextView = v.findViewById(R.id.tvUserEmail)
            val role: TextView = v.findViewById(R.id.tvUserRole)
            val btn: MaterialButton = v.findViewById(R.id.btnBanUser)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_report_user, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.fullName
            holder.email.text = item.email
            holder.role.text = item.role.name

            if (item.isBanned) {
                holder.btn.text = "Banı Aç"
                holder.btn.setBackgroundColor(android.graphics.Color.GRAY)
                holder.btn.setOnClickListener { onBanClick(item, false) }
            } else {
                holder.btn.text = "Banla"
                holder.btn.setBackgroundColor(android.graphics.Color.RED)
                holder.btn.setOnClickListener { onBanClick(item, true) }
            }
        }
        override fun getItemCount() = items.size
    }
}