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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.DialogPriceAnalysisBinding
import com.example.mobiliyum.databinding.FragmentFavoritesBinding
import com.example.mobiliyum.databinding.ItemFavoriteBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FavoritesAdapter
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
    private val favoriteUiList = ArrayList<FavoriteUiItem>()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = "Favorilerim & Fiyat Takibi"
        binding.cardSearch.visibility = View.GONE

        binding.rvFavorites.layoutManager = LinearLayoutManager(context)

        adapter = FavoritesAdapter(
            onDetailClick = { product -> openDetail(product) },
            onRemoveClick = { item, _ -> confirmRemoveFavorite(item) },
            onGraphClick = { product -> showAnalysisDialog(product) },
            onAlertChange = { id, check -> FavoritesManager.updatePriceAlert(id, check) }
        )
        binding.rvFavorites.adapter = adapter

        // Ürünleri Keşfet butonu (Boş ekranda çıkar)
        binding.btnDiscoverProducts.setOnClickListener {
            // Ana aktivitedeki bottom navigation'dan 'Mağazalar' veya 'Ürünler' sekmesine yönlendir
            (activity as? MainActivity)?.switchToTab(R.id.nav_products)
        }
        loadFavorites()
    }

    private fun loadFavorites() {
        val user = UserManager.getCurrentUser() ?: return
        db.collection("users").document(user.id).collection("favorites").get()
            .addOnSuccessListener { favDocs ->
                if (favDocs.isEmpty) {
                    showEmptyState(true)
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

                            if (favoriteUiList.isEmpty()) {
                                showEmptyState(true)
                            } else {
                                showEmptyState(false)
                                adapter.submitList(ArrayList(favoriteUiList))
                            }
                        }
                } else {
                    showEmptyState(true)
                }
            }
    }

    private fun showEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.rvFavorites.visibility = View.GONE
            binding.layoutEmptyFavorites.visibility = View.VISIBLE
        } else {
            binding.rvFavorites.visibility = View.VISIBLE
            binding.layoutEmptyFavorites.visibility = View.GONE
        }
    }

    private fun openDetail(product: Product) {
        val detailFragment = ProductDetailFragment()
        val bundle = Bundle()
        bundle.putParcelable("product_data", product)
        detailFragment.arguments = bundle

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun confirmRemoveFavorite(item: FavoriteUiItem) {
        AlertDialog.Builder(context).setTitle("Favorilerden Çıkar")
            .setMessage("${item.product.name} silinecek.")
            .setPositiveButton("Çıkar") { _, _ ->
                FavoritesManager.toggleFavorite(
                    product = item.product,
                    context = requireContext()
                ) { isFav ->
                    if (!isFav) {
                        favoriteUiList.remove(item)
                        adapter.submitList(ArrayList(favoriteUiList))
                        if (favoriteUiList.isEmpty()) showEmptyState(true)
                    }
                }
            }.setNegativeButton("İptal", null).show()
    }

    private fun showAnalysisDialog(product: Product) {
        val dialogBinding = DialogPriceAnalysisBinding.inflate(LayoutInflater.from(context))
        dialogBinding.tvGraphTitle.text = "${product.name}"

        val graphView = InternalGraphView(requireContext())
        graphView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialogBinding.graphContainer.addView(graphView)

        val historyMap = HashMap<Long, Double>()
        val currentPrice = PriceUtils.parsePrice(product.price)
        val now = System.currentTimeMillis()

        // Geçmiş verisi yoksa en azından bugünü ekle
        historyMap[now] = currentPrice

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
            // Sadece seçilen aralıktaki verileri filtrele
            val filteredData = historyMap.filterKeys { it >= startTime }.toMutableMap()

            // Eğer aralığın başında veri yoksa, grafiğin başı boş kalmasın diye en eski veriyi başa koy
            if (!filteredData.containsKey(startTime)) {
                val oldestAvailableKey = historyMap.keys.minOrNull()
                val oldestAvailableVal = if(oldestAvailableKey != null) historyMap[oldestAvailableKey] else currentPrice
                filteredData[startTime] = oldestAvailableVal!!
            }
            // Bugünü de ekle ki grafik ucu açık kalmasın
            filteredData[now] = currentPrice

            graphView.setData(filteredData, startTime, now)
        }

        // İlk açılışta 1 Aylık göster
        updateGraph(30)
        dialogBinding.toggleGroupPeriod.check(R.id.btnMonth)

        dialogBinding.toggleGroupPeriod.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                when(id) {
                    R.id.btnWeek -> updateGraph(7)
                    R.id.btnMonth -> updateGraph(30)
                    R.id.btnYear -> updateGraph(365)
                }
            }
        }

        val alert = AlertDialog.Builder(context).setView(dialogBinding.root).create()
        dialogBinding.btnCloseGraph.setOnClickListener { alert.dismiss() }
        alert.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// --- YARDIMCI SINIFLAR ---

data class FavoriteUiItem(val product: Product, var isAlertOn: Boolean)

class FavoritesAdapter(
    private val onDetailClick: (Product) -> Unit,
    private val onRemoveClick: (FavoriteUiItem, Int) -> Unit,
    private val onGraphClick: (Product) -> Unit,
    private val onAlertChange: (Int, Boolean) -> Unit
) : ListAdapter<FavoriteUiItem, FavoritesAdapter.VH>(FavoriteDiffCallback()) {

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteUiItem>() {
        override fun areItemsTheSame(oldItem: FavoriteUiItem, newItem: FavoriteUiItem) = oldItem.product.id == newItem.product.id
        override fun areContentsTheSame(oldItem: FavoriteUiItem, newItem: FavoriteUiItem) = oldItem == newItem
    }

    inner class VH(val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvFavName.text = item.product.name
        holder.binding.tvFavPrice.text = PriceUtils.formatPriceStyled(item.product.price)
        Glide.with(holder.itemView.context).load(item.product.imageUrl).into(holder.binding.imgFavProduct)

        // Listener çakışmasını önlemek için önce null yap
        holder.binding.switchPriceAlert.setOnCheckedChangeListener(null)
        holder.binding.switchPriceAlert.isChecked = item.isAlertOn

        holder.binding.infoLayout.setOnClickListener { onDetailClick(item.product) }
        holder.binding.imgFavProduct.setOnClickListener { onDetailClick(item.product) }
        holder.binding.btnRemoveFav.setOnClickListener { onRemoveClick(item, holder.adapterPosition) }
        holder.binding.btnShowGraph.setOnClickListener { onGraphClick(item.product) }

        holder.binding.switchPriceAlert.setOnCheckedChangeListener { _, isChecked ->
            item.isAlertOn = isChecked
            onAlertChange(item.product.id, isChecked)
        }
    }
}

// Basit Grafik Çizici
class InternalGraphView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val pLine = Paint().apply { color = Color.parseColor("#4CAF50"); strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val pDot = Paint().apply { color = Color.parseColor("#FF6F00"); style = Paint.Style.FILL; isAntiAlias = true }
    private val pGrid = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
    private val pText = Paint().apply { color = Color.DKGRAY; textSize = 24f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

    private var dataMap: Map<Long, Double> = emptyMap()
    private var minTime: Long = 0
    private var maxTime: Long = 0

    fun setData(data: Map<Long, Double>, start: Long, end: Long) {
        dataMap = data; minTime = start; maxTime = end; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataMap.isEmpty()) return

        val padLeft = 80f; val padBottom = 60f; val padTop = 40f; val padRight = 40f
        val w = width - padLeft - padRight; val h = height - padBottom - padTop

        val maxP = dataMap.values.maxOrNull() ?: 100.0; val minP = dataMap.values.minOrNull() ?: 0.0
        // Grafiğin çok düz görünmemesi için aralığı biraz açalım
        val rangeP = max((maxP - minP) * 1.2, 1.0)
        val baseP = minP - (rangeP * 0.1) // Biraz alt boşluk
        val rangeT = max(maxTime - minTime, 1L)

        val sdf = SimpleDateFormat("dd MMM", Locale("tr")); val labelCount = 5

        // Dikey ızgara çizgileri ve tarih etiketleri
        for (i in 0 until labelCount) {
            val fraction = i.toFloat() / (labelCount - 1)
            val x = padLeft + (fraction * w)
            val time = minTime + (fraction * rangeT).toLong()
            canvas.drawLine(x, padTop, x, height - padBottom, pGrid)
            canvas.drawText(sdf.format(Date(time)), x, height - 10f, pText)
        }

        val path = Path()
        val sortedPoints = dataMap.toList().sortedBy { it.first }
        var firstPoint = true

        for ((t, p) in sortedPoints) {
            val fractionX = (t - minTime).toFloat() / rangeT.toFloat()
            val x = padLeft + (fractionX * w)

            val fractionY = ((p - baseP) / rangeP).toFloat()
            val y = (height - padBottom) - (fractionY * h)

            if (firstPoint) { path.moveTo(x, y); firstPoint = false } else { path.lineTo(x, y) }

            canvas.drawCircle(x, y, 8f, pDot)
            // Fiyat etiketini sadece nokta üzerine yaz
            canvas.drawText("${p.toInt()}", x, y - 15f, pText)
        }
        canvas.drawPath(path, pLine)
    }
}