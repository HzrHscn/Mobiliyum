package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

// --- 1. ANA FRAGMENT ---
class FavoritesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoritesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val favoriteUiList = ArrayList<FavoriteUiItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stores, container, false)
        view.findViewById<TextView>(R.id.tvTitle)?.text = "Favorilerim & Fiyat Takibi"
        view.findViewById<View>(R.id.cardSearch)?.visibility = View.GONE
        recyclerView = view.findViewById(R.id.rvStores)
        recyclerView.layoutManager = LinearLayoutManager(context)
        loadFavorites()
        return view
    }

    private fun loadFavorites() {
        val user = UserManager.getCurrentUser() ?: return
        db.collection("users").document(user.id).collection("favorites").get()
            .addOnSuccessListener { favDocs ->
                if (favDocs.isEmpty) {
                    Toast.makeText(context, "Henüz favoriniz yok.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val alertMap = HashMap<String, Boolean>()
                for (doc in favDocs) {
                    val pid = doc.getString("productId")
                    val alert = doc.getBoolean("priceAlert") ?: true
                    if (pid != null) alertMap[pid] = alert
                }
                val productIds = alertMap.keys.mapNotNull { it.toIntOrNull() }
                if (productIds.isNotEmpty()) {
                    db.collection("products").whereIn("id", productIds).get()
                        .addOnSuccessListener { productDocs ->
                            favoriteUiList.clear()
                            for (doc in productDocs) {
                                val product = doc.toObject(Product::class.java)
                                val isAlertOn = alertMap[product.id.toString()] ?: false
                                favoriteUiList.add(FavoriteUiItem(product, isAlertOn))
                            }
                            adapter = FavoritesAdapter(favoriteUiList,
                                onDetailClick = { openDetail(it) },
                                onRemoveClick = { item, pos -> confirmRemoveFavorite(item, pos) },
                                onGraphClick = { showAnalysisDialog(it) },
                                onAlertChange = { id, check -> FavoritesManager.updatePriceAlert(id, check) }
                            )
                            recyclerView.adapter = adapter
                        }
                }
            }
    }

    private fun openDetail(product: Product) {
        val detailFragment = ProductDetailFragment()
        val bundle = Bundle()
        bundle.putSerializable("product_data", product)
        detailFragment.arguments = bundle
        parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, detailFragment).addToBackStack(null).commit()
    }

    private fun confirmRemoveFavorite(item: FavoriteUiItem, position: Int) {
        AlertDialog.Builder(context)
            .setTitle("Favorilerden Çıkar")
            .setMessage("${item.product.name} favorilerinizden çıkarılacak.")
            .setPositiveButton("Çıkar") { _, _ ->
                FavoritesManager.toggleFavorite(item.product) {
                    favoriteUiList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, favoriteUiList.size)
                }
            }
            .setNegativeButton("İptal", null).show()
    }

    private fun showAnalysisDialog(product: Product) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_price_analysis, null)
        val container = dialogView.findViewById<FrameLayout>(R.id.graphContainer)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseGraph)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupPeriod)

        dialogView.findViewById<TextView>(R.id.tvGraphTitle).text = "${product.name} - Fiyat Analizi"

        val graphView = InternalGraphView(requireContext())
        graphView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container.addView(graphView)

        // GERÇEK VERİ YÖNETİMİ
        val realHistory = HashMap<Long, Double>()
        val currentPrice = PriceUtils.parsePrice(product.price)
        val now = System.currentTimeMillis()
        realHistory[now] = currentPrice // Bugünü ekle

        // Veritabanındaki geçmişi (String: "yyyy-MM-dd") Long'a çevir
        if (product.priceHistory.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for ((dateStr, price) in product.priceHistory) {
                try {
                    val date = sdf.parse(dateStr)
                    if (date != null) realHistory[date.time] = price
                } catch (e: Exception) {}
            }
        }

        fun updateGraph(mode: Int) {
            val dataToShow = HashMap<Long, Double>()
            // Kesin filtreleme zamanı
            val cutoff = when(mode) {
                0 -> now - (7 * 86400000L) // 7 Gün
                1 -> now - (30 * 86400000L) // 30 Gün
                else -> now - (365 * 86400000L) // 1 Yıl
            }

            // Filtrele
            val filtered = realHistory.filterKeys { it >= cutoff }

            // Eğer veri boşsa veya tekse, grafiğin düzgün görünmesi için başa aynı değeri koy
            if (filtered.size <= 1) {
                dataToShow[now] = currentPrice
                dataToShow[cutoff] = currentPrice // Düz çizgi için başlangıç
            } else {
                dataToShow.putAll(filtered)
            }

            val format = if(mode == 2) "MMM" else "dd MMM"
            graphView.setData(dataToShow, format)
        }

        updateGraph(1)
        toggleGroup.check(R.id.btnMonth)
        toggleGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                when(id) {
                    R.id.btnWeek -> updateGraph(0)
                    R.id.btnMonth -> updateGraph(1)
                    R.id.btnYear -> updateGraph(2)
                }
            }
        }

        val alert = AlertDialog.Builder(context).setView(dialogView).create()
        btnClose.setOnClickListener { alert.dismiss() }
        alert.show()
    }
}

// --- 2. VERİ MODELİ ---
data class FavoriteUiItem(val product: Product, var isAlertOn: Boolean)

// --- 3. ADAPTER ---
class FavoritesAdapter(
    private val items: List<FavoriteUiItem>,
    private val onDetailClick: (Product) -> Unit,
    private val onRemoveClick: (FavoriteUiItem, Int) -> Unit,
    private val onGraphClick: (Product) -> Unit,
    private val onAlertChange: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvFavName)
        val price: TextView = v.findViewById(R.id.tvFavPrice)
        val img: ImageView = v.findViewById(R.id.imgFavProduct)
        val btnGraph: View = v.findViewById(R.id.btnShowGraph)
        val btnRemove: View = v.findViewById(R.id.btnRemoveFav)
        val switchAlert: SwitchMaterial = v.findViewById(R.id.switchPriceAlert)
        val infoArea: View = v.findViewById(R.id.infoLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.product.name
        holder.price.text = PriceUtils.formatPriceStyled(item.product.price)
        Glide.with(holder.itemView).load(item.product.imageUrl).into(holder.img)
        holder.switchAlert.setOnCheckedChangeListener(null)
        holder.switchAlert.isChecked = item.isAlertOn

        holder.infoArea.setOnClickListener { onDetailClick(item.product) }
        holder.img.setOnClickListener { onDetailClick(item.product) }
        holder.btnRemove.setOnClickListener { onRemoveClick(item, position) }
        holder.btnGraph.setOnClickListener { onGraphClick(item.product) }
        holder.switchAlert.setOnCheckedChangeListener { _, c ->
            item.isAlertOn = c
            onAlertChange(item.product.id, c)
        }
    }
    override fun getItemCount() = items.size
}

// --- 4. GRAFİK MOTORU ---
class InternalGraphView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val pLine = Paint().apply { color = Color.parseColor("#4CAF50"); strokeWidth = 6f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val pDot = Paint().apply { color = Color.parseColor("#FF6F00"); style = Paint.Style.FILL; isAntiAlias = true }
    private val pText = Paint().apply { color = Color.DKGRAY; textSize = 28f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private var pts: List<Pair<Long, Double>> = emptyList()
    private var fmt = "dd MMM"

    fun setData(h: Map<Long, Double>, f: String) { pts = h.toList().sortedBy { it.first }; fmt = f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pts.isEmpty()) return
        val pad = 80f
        val w = width - pad - 40f
        val h = height - pad - 40f
        val maxP = pts.maxOf { it.second }
        val minP = pts.minOf { it.second }
        val diff = maxP - minP
        val vMax = if (diff == 0.0) maxP * 1.1 else maxP + (diff * 0.1)
        val vMin = if (diff == 0.0) maxP * 0.9 else minP - (diff * 0.1)
        val rng = vMax - vMin
        val stepX = if (pts.size > 1) w / (pts.size - 1) else 0f
        val sdf = SimpleDateFormat(fmt, Locale("tr", "TR"))
        val path = Path()

        if (pts.size == 1) {
            val x = pad + w / 2
            val y = h / 2
            canvas.drawCircle(x, y, 12f, pDot)
            canvas.drawText("${pts[0].second.toInt()}₺", x, y - 30, pText)
            canvas.drawText(sdf.format(Date(pts[0].first)), x, height - 20f, pText)
            return
        }

        for (i in pts.indices) {
            val (d, p) = pts[i]
            val x = pad + (i * stepX)
            val frac = (p - vMin) / rng
            val y = (height - pad) - (frac * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            canvas.drawCircle(x, y, 10f, pDot)
            canvas.drawText("${p.toInt()}₺", x, y - 25, pText)
            // Çakışmayı önlemek için basit mantık
            if (pts.size < 8 || i % 2 == 0) canvas.drawText(sdf.format(Date(d)), x, height - 20f, pText)
        }
        canvas.drawPath(path, pLine)
    }
}