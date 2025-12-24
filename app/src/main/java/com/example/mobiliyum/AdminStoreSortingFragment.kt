package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Collections

class AdminStoreSortingFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSave: MaterialButton
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
    private val storeList = ArrayList<Store>()
    private lateinit var adapter: SortingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(context).apply {
            text = "Mağaza Sıralaması Düzenle"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#333333"))
            setPadding(32, 32, 32, 8)
        }
        layout.addView(title)

        val subTitle = TextView(context).apply {
            text = "Mağazaların yerini değiştirmek için basılı tutup sürükleyin."
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(32, 0, 32, 24)
        }
        layout.addView(subTitle)

        recyclerView = RecyclerView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        layout.addView(recyclerView)

        btnSave = MaterialButton(context).apply {
            text = "Sıralamayı Kaydet"
            setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(32, 16, 32, 32)
            }
        }
        layout.addView(btnSave)

        setupRecyclerView()
        loadStores()

        btnSave.setOnClickListener { saveOrder() }

        return layout
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = SortingAdapter(storeList)
        recyclerView.adapter = adapter

        // Sürükle Bırak Yardımcısı
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                Collections.swap(storeList, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Kaydırma yok
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun loadStores() {
        // 1. Önce kayıtlı sıralamayı çek
        db.collection("app_settings").document("store_sorting").get()
            .addOnSuccessListener { sortDoc ->
                val sortedIds = sortDoc.get("sortedIds") as? List<Long> ?: emptyList()

                // 2. Mağazaları çek
                db.collection("stores").get().addOnSuccessListener { docs ->
                    storeList.clear()
                    val allFetched = ArrayList<Store>()
                    for (doc in docs) {
                        allFetched.add(doc.toObject(Store::class.java))
                    }

                    // 3. Sıralamaya göre listeyi düzenle
                    storeList.addAll(allFetched.sortedBy { store ->
                        val index = sortedIds.indexOf(store.id.toLong())
                        if (index == -1) Int.MAX_VALUE else index
                    })

                    adapter.notifyDataSetChanged()
                }
            }
    }

    private fun saveOrder() {
        val idList = storeList.map { it.id }
        db.collection("app_settings").document("store_sorting")
            .set(mapOf("sortedIds" to idList))
            .addOnSuccessListener {
                Toast.makeText(context, "Sıralama Kaydedildi!", Toast.LENGTH_SHORT).show()
                DataManager.triggerServerVersionUpdate(
                    updatedStoreId = "SORTING"
                )  //silebiliriz çalışmazsa
                parentFragmentManager.popBackStack()
            }
    }

    // Basit Adapter (Sadece isim gösterir, sürükleme için)
    class SortingAdapter(private val items: List<Store>) : RecyclerView.Adapter<SortingAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(android.R.id.text1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.text.text = "${position + 1}. ${items[position].name}"
            holder.text.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_sort_by_size, 0)
        }
        override fun getItemCount() = items.size
    }
}