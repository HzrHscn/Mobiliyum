package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentStoreAnnouncementsBinding
import com.example.mobiliyum.databinding.ItemNotificationBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class StoreAnnouncementsFragment : Fragment() {

    private var _binding: FragmentStoreAnnouncementsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val allList = ArrayList<NotificationItem>()
    private lateinit var adapter: AnnouncementAdapter // Adapter referansı

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
    ): View {
        _binding = FragmentStoreAnnouncementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvPageTitle.text = "$storeName - Duyurular"
        binding.rvStoreAnnouncements.layoutManager = LinearLayoutManager(context)

        // Adapter Başlatma
        adapter = AnnouncementAdapter()
        binding.rvStoreAnnouncements.adapter = adapter

        binding.toggleFilter.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn1Month -> filterList(30)
                    R.id.btn6Months -> filterList(180)
                    R.id.btn1Year -> filterList(365)
                    R.id.btnAllTime -> filterList(-1)
                }
            }
        }

        loadAllAnnouncements()
    }

    private fun loadAllAnnouncements() {
        if (storeId.isEmpty()) return

        db.collection("announcements")
            .whereEqualTo("type", "store_update")
            .whereEqualTo("relatedId", storeId)
            .get()
            .addOnSuccessListener { documents ->
                allList.clear()
                for (doc in documents) {
                    allList.add(doc.toObject(NotificationItem::class.java))
                }

                // Varsayılan Filtre: 1 AY
                binding.toggleFilter.check(R.id.btn1Month)
                filterList(30)
            }
    }

    private fun filterList(days: Int) {
        val filteredList = ArrayList<NotificationItem>()

        if (days == -1) {
            filteredList.addAll(allList)
        } else {
            val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            for (item in allList) {
                if (item.date.time >= cutoffTime) {
                    filteredList.add(item)
                }
            }
        }

        filteredList.sortByDescending { it.date }

        // DÜZELTME: Veriyi submitList ile gönder
        adapter.submitList(ArrayList(filteredList))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- MODERN ADAPTER (ListAdapter + DiffUtil) ---
    class AnnouncementAdapter : ListAdapter<NotificationItem, AnnouncementAdapter.VH>(DiffCallback()) {

        class DiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) = oldItem == newItem
        }

        inner class VH(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)

            holder.binding.tvNotifTitle.text = item.title
            holder.binding.tvNotifMessage.text = item.message
            holder.binding.tvNotifDate.text = SimpleDateFormat("dd MMM yyyy", Locale("tr")).format(item.date)

            holder.binding.imgNotifIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
            holder.binding.imgNotifIcon.setColorFilter(Color.parseColor("#1976D2"))

            holder.itemView.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle(item.title)
                    .setMessage(item.message)
                    .setPositiveButton("Kapat", null)
                    .show()
            }
        }
    }
}