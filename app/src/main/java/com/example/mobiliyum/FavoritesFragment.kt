package com.example.mobiliyum

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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

class FavoritesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoritesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val favoriteUiList = ArrayList<FavoriteUiItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        AlertDialog.Builder(context).setTitle("Favorilerden Çıkar")
            .setMessage("${item.product.name} silinecek.")
            .setPositiveButton("Çıkar") { _, _ ->
                FavoritesManager.toggleFavorite(item.product) {
                    favoriteUiList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, favoriteUiList.size)
                }
            }.setNegativeButton("İptal", null).show()
    }

    private fun showAnalysisDialog(product: Product) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_price_analysis, null)
        val container = dialogView.findViewById<FrameLayout>(R.id.graphContainer)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseGraph)
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupPeriod)

        dialogView.findViewById<TextView>(R.id.tvGraphTitle).text = "${product.name}"

        // YENİ GRAFİK MOTORU
        val graphView = InternalGraphView(requireContext())
        graphView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container.addView(graphView)

        // Veriyi Hazırla
        val historyMap = HashMap<Long, Double>()
        val currentPrice = PriceUtils.parsePrice(product.price)
        val now = System.currentTimeMillis()
        historyMap[now] = currentPrice // Bugünü ekle

        if (product.priceHistory.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for ((k, v) in product.priceHistory) {
                try {
                    val t = sdf.parse(k.toString())?.time
                    if (t != null) historyMap[t] = v
                } catch (e: Exception) {
                    if (k.toString().toLongOrNull() != null) historyMap[k.toString().toLong()] = v
                }
            }
        }

        fun updateGraph(days: Int) {
            val startTime = now - (days * 24 * 60 * 60 * 1000L)

            // Seçilen aralıktaki verileri al
            val filteredData = historyMap.filterKeys { it >= startTime }.toMutableMap()

            // Eğer aralığın başında veri yoksa, grafiğin düzgün başlaması için
            // o tarihe en yakın önceki fiyatı veya şimdiki fiyatı başlangıç noktası yap
            if (!filteredData.containsKey(startTime)) {
                // Bu tarihten önceki en son fiyatı bulmaya çalış, yoksa currentPrice
                // Basitlik için currentPrice veya listedeki en eski fiyatı alıyoruz
                val oldestAvailable = historyMap.keys.minOrNull() ?: now
                val startPrice = historyMap[oldestAvailable] ?: currentPrice
                filteredData[startTime] = startPrice
            }

            // Bitiş noktası (Bugün) kesin olsun
            filteredData[now] = currentPrice

            // Grafiğe Min/Max zaman aralığını ve veriyi gönder
            graphView.setData(filteredData, startTime, now)
        }

        updateGraph(30) // Varsayılan: Aylık
        toggleGroup.check(R.id.btnMonth)

        toggleGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                when(id) {
                    R.id.btnWeek -> updateGraph(7)
                    R.id.btnMonth -> updateGraph(30)
                    R.id.btnYear -> updateGraph(365)
                }
            }
        }

        val alert = AlertDialog.Builder(context).setView(dialogView).create()
        btnClose.setOnClickListener { alert.dismiss() }
        alert.show()
    }
}

// --- VERİ MODELİ ---
data class FavoriteUiItem(val product: Product, var isAlertOn: Boolean)

// --- ADAPTER ---
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

// --- ZAMAN EKSENLİ GRAFİK MOTORU (HATASIZ) ---
class InternalGraphView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val pLine = Paint().apply { color = Color.parseColor("#4CAF50"); strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val pDot = Paint().apply { color = Color.parseColor("#FF6F00"); style = Paint.Style.FILL; isAntiAlias = true }
    private val pGrid = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
    private val pText = Paint().apply { color = Color.DKGRAY; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

    private var dataMap: Map<Long, Double> = emptyMap()
    private var minTime: Long = 0
    private var maxTime: Long = 0

    fun setData(data: Map<Long, Double>, start: Long, end: Long) {
        dataMap = data
        minTime = start
        maxTime = end
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataMap.isEmpty()) return

        val padLeft = 80f
        val padBottom = 60f
        val padTop = 40f
        val padRight = 40f

        val w = width - padLeft - padRight
        val h = height - padBottom - padTop

        // Y EKSENİ (FİYAT) HESABI
        val maxP = dataMap.values.maxOrNull() ?: 100.0
        val minP = dataMap.values.minOrNull() ?: 0.0
        // Grafik üstte ve altta yapışmasın diye %10 pay
        val rangeP = max((maxP - minP) * 1.2, 1.0)
        val baseP = minP - (rangeP * 0.1)

        // X EKSENİ (ZAMAN) HESABI
        val rangeT = max(maxTime - minTime, 1L)

        // 1. Grid Çizgileri ve Tarih Etiketleri (Sabit 5 nokta)
        val sdf = SimpleDateFormat("dd MMM", Locale("tr"))
        val labelCount = 5
        for (i in 0 until labelCount) {
            val fraction = i.toFloat() / (labelCount - 1)
            val x = padLeft + (fraction * w)
            val time = minTime + (fraction * rangeT).toLong()

            // Dikey Grid
            canvas.drawLine(x, padTop, x, height - padBottom, pGrid)
            // Tarih
            canvas.drawText(sdf.format(Date(time)), x, height - 10f, pText)
        }

        // 2. Grafiği Çiz (Veri Noktaları)
        val path = Path()
        val sortedPoints = dataMap.toList().sortedBy { it.first }
        var firstPoint = true

        for ((t, p) in sortedPoints) {
            // Zamanın grafikteki X konumu
            val fractionX = (t - minTime).toFloat() / rangeT.toFloat()
            val x = padLeft + (fractionX * w)

            // Fiyatın grafikteki Y konumu
            val fractionY = ((p - baseP) / rangeP).toFloat()
            val y = (height - padBottom) - (fractionY * h)

            // Çizgi
            if (firstPoint) {
                path.moveTo(x, y)
                firstPoint = false
            } else {
                path.lineTo(x, y)
            }

            // Nokta
            canvas.drawCircle(x, y, 8f, pDot)

            // Fiyat Etiketi (Sadece değişim noktalarında veya hepsinde)
            canvas.drawText("${p.toInt()}", x, y - 15f, pText)
        }
        canvas.drawPath(path, pLine)
    }
}