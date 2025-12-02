package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentNotificationsBinding
import com.example.mobiliyum.databinding.ItemNotificationBinding // Adapter için
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val allNotifications = ArrayList<NotificationItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvNotifications.layoutManager = LinearLayoutManager(context)

        binding.tabLayoutNotif.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterList(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadAllNotifications()
    }

    private fun loadAllNotifications() {
        val user = UserManager.getCurrentUser() ?: return
        allNotifications.clear()

        // 1. Kişisel Bildirimler
        db.collection("users").document(user.id).collection("notifications").get()
            .addOnSuccessListener { userDocs ->
                for (doc in userDocs) {
                    allNotifications.add(doc.toObject(NotificationItem::class.java))
                }

                // 2. Genel Duyurular
                db.collection("announcements").orderBy("date", Query.Direction.DESCENDING).get()
                    .addOnSuccessListener { globalDocs ->
                        for (doc in globalDocs) {
                            val item = doc.toObject(NotificationItem::class.java)
                            if (item.type == "store_update") {
                                val storeId = item.relatedId.toIntOrNull()
                                if (storeId != null && FavoritesManager.isFollowing(storeId)) {
                                    allNotifications.add(item)
                                }
                            } else {
                                allNotifications.add(item)
                            }
                        }
                        filterList(binding.tabLayoutNotif.selectedTabPosition)
                    }
            }
    }

    private fun filterList(tabIndex: Int) {
        val filtered = when(tabIndex) {
            0 -> allNotifications.filter { it.type == "store_update" }
            1 -> allNotifications.filter { it.type == "price_alert" }
            else -> allNotifications.filter { it.type == "general" }
        }.sortedByDescending { it.date }

        binding.rvNotifications.adapter = NotifAdapter(filtered)
    }

    private fun handleNotificationClick(item: NotificationItem) {
        if (item.type == "price_alert" && item.relatedId.isNotEmpty()) {
            db.collection("products").document(item.relatedId).get()
                .addOnSuccessListener { document ->
                    val product = document.toObject(Product::class.java)
                    if (product != null) {
                        val fragment = ProductDetailFragment()
                        val bundle = Bundle()
                        // ADIM 1: Parcelable kullanımı
                        bundle.putParcelable("product_data", product)
                        fragment.arguments = bundle

                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
        }
        else if (item.type == "store_update" && item.relatedId.isNotEmpty()) {
            db.collection("stores").document(item.relatedId).get()
                .addOnSuccessListener { document ->
                    val store = document.toObject(Store::class.java)
                    if (store != null) {
                        val fragment = StoreDetailFragment()
                        val bundle = Bundle()
                        bundle.putInt("id", store.id)
                        bundle.putString("name", store.name)
                        bundle.putString("image", store.imageUrl)
                        bundle.putString("location", store.location)
                        fragment.arguments = bundle

                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
        }
        else {
            showDetailPopup(item)
        }
    }

    private fun showDetailPopup(item: NotificationItem) {
        // Dialog layoutları için ViewBinding zorunlu değil ama temizlik için layout inflater kullanıyoruz
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_notification_detail, null)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = item.title
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = item.message
        dialogView.findViewById<TextView>(R.id.tvDialogDate).text =
            SimpleDateFormat("dd MMMM yyyy - HH:mm", Locale("tr")).format(item.date)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnDialogClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ADAPTER (ViewBinding'li)
    inner class NotifAdapter(private val items: List<NotificationItem>) : RecyclerView.Adapter<NotifAdapter.VH>() {

        inner class VH(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.tvNotifTitle.text = item.title
            holder.binding.tvNotifMessage.text = item.message
            holder.binding.tvNotifDate.text = SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(item.date)

            when (item.type) {
                "price_alert" -> {
                    holder.binding.imgNotifIcon.setImageResource(android.R.drawable.ic_dialog_info)
                    holder.binding.imgNotifIcon.setColorFilter(Color.RED)
                }
                "store_update" -> {
                    holder.binding.imgNotifIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
                    holder.binding.imgNotifIcon.setColorFilter(Color.BLUE)
                }
                else -> {
                    holder.binding.imgNotifIcon.setImageResource(android.R.drawable.ic_popup_reminder)
                    holder.binding.imgNotifIcon.setColorFilter(Color.parseColor("#FF6F00"))
                }
            }

            holder.itemView.setOnClickListener {
                handleNotificationClick(item)
            }
        }
        override fun getItemCount() = items.size
    }
}