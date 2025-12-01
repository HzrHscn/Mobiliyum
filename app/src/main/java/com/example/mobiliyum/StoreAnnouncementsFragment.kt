package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StoreAnnouncementsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private val db = FirebaseFirestore.getInstance()

    // Tüm duyuruları hafızada tutacağız, filtrelemeyi burada yapacağız (Daha hızlı)
    private val allList = ArrayList<NotificationItem>()
    private val filteredList = ArrayList<NotificationItem>()

    private var storeId: String = ""
    private var storeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            storeId = it.getString("storeId", "")
            storeName = it.getString("storeName", "Mağaza")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_store_announcements, container, false)

        recyclerView = view.findViewById(R.id.rvStoreAnnouncements)
        tvTitle = view.findViewById(R.id.tvPageTitle)
        toggleGroup = view.findViewById(R.id.toggleFilter)

        tvTitle.text = "$storeName - Duyurular"
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Filtre Butonları
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn1Month -> filterList(30)
                    R.id.btn6Months -> filterList(180)
                    R.id.btn1Year -> filterList(365)
                    R.id.btnAllTime -> filterList(-1) // Tümü
                }
            }
        }

        loadAllAnnouncements()

        return view
    }

    private fun loadAllAnnouncements() {
        if (storeId.isEmpty()) return

        // Sadece bu mağazaya ait duyuruları çek (En yeniden en eskiye)
        db.collection("announcements")
            .whereEqualTo("type", "store_update")
            .whereEqualTo("relatedId", storeId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                allList.clear()
                for (doc in documents) {
                    allList.add(doc.toObject(NotificationItem::class.java))
                }

                // Varsayılan Filtre: 1 AY
                toggleGroup.check(R.id.btn1Month)
                filterList(30)
            }
    }

    private fun filterList(days: Int) {
        filteredList.clear()

        if (days == -1) {
            // Tümü
            filteredList.addAll(allList)
        } else {
            // Zaman hesabı (Şimdiki zaman - gün sayısı)
            val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)

            for (item in allList) {
                if (item.date.time >= cutoffTime) {
                    filteredList.add(item)
                }
            }
        }

        // Listeyi güncelle
        recyclerView.adapter = AnnouncementAdapter(filteredList)
    }

    inner class AnnouncementAdapter(private val items: List<NotificationItem>) : RecyclerView.Adapter<AnnouncementAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvNotifTitle)
            val msg: TextView = v.findViewById(R.id.tvNotifMessage)
            val date: TextView = v.findViewById(R.id.tvNotifDate)
            val icon: ImageView = v.findViewById(R.id.imgNotifIcon)
            val container: View = v
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.msg.text = item.message
            holder.date.text = SimpleDateFormat("dd MMM yyyy", Locale("tr")).format(item.date)

            // İkon (Mağaza güncellemesi olduğu için sabit ikon)
            holder.icon.setImageResource(android.R.drawable.ic_menu_myplaces)
            holder.icon.setColorFilter(android.graphics.Color.parseColor("#1976D2")) // Mavi

            holder.container.setOnClickListener {
                // Popup Detay
                AlertDialog.Builder(context)
                    .setTitle(item.title)
                    .setMessage(item.message)
                    .setPositiveButton("Kapat", null)
                    .show()
            }
        }
        override fun getItemCount() = items.size
    }
}