package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout
    private val db = FirebaseFirestore.getInstance()
    private val allNotifications = ArrayList<NotificationItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        recyclerView = view.findViewById(R.id.rvNotifications)
        tabLayout = view.findViewById(R.id.tabLayoutNotif)

        recyclerView.layoutManager = LinearLayoutManager(context)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterList(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadAllNotifications()
        return view
    }

    private fun loadAllNotifications() {
        val user = UserManager.getCurrentUser() ?: return
        allNotifications.clear()

        db.collection("users").document(user.id).collection("notifications").get()
            .addOnSuccessListener { userDocs ->
                for (doc in userDocs) {
                    allNotifications.add(doc.toObject(NotificationItem::class.java))
                }

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
                        filterList(0)
                    }
            }
    }

    private fun filterList(tabIndex: Int) {
        val filtered = when(tabIndex) {
            0 -> allNotifications.filter { it.type == "store_update" }
            1 -> allNotifications.filter { it.type == "price_alert" }
            else -> allNotifications.filter { it.type == "general" }
        }.sortedByDescending { it.date }

        recyclerView.adapter = NotifAdapter(filtered)
    }

    // --- TIKLAMA MANTIĞI DÜZELTİLDİ ---
    private fun handleNotificationClick(item: NotificationItem) {
        if (item.type == "price_alert" && item.relatedId.isNotEmpty()) {
            // 1. Ürün ID'sini al
            // 2. Ürünü veritabanından çek
            // 3. Detay sayfasını aç
            db.collection("products").document(item.relatedId).get()
                .addOnSuccessListener { document ->
                    val product = document.toObject(Product::class.java)
                    if (product != null) {
                        val fragment = ProductDetailFragment()
                        val bundle = Bundle()
                        bundle.putSerializable("product_data", product)
                        fragment.arguments = bundle

                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    } else {
                        Toast.makeText(context, "Ürün bulunamadı.", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            // Diğer bildirimler için POPUP aç
            showDetailPopup(item)
        }
    }

    private fun showDetailPopup(item: NotificationItem) {
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

    inner class NotifAdapter(private val items: List<NotificationItem>) : RecyclerView.Adapter<NotifAdapter.VH>() {
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
            holder.date.text = SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(item.date)

            when (item.type) {
                "price_alert" -> {
                    holder.icon.setImageResource(android.R.drawable.ic_dialog_info)
                    holder.icon.setColorFilter(android.graphics.Color.RED)
                }
                "store_update" -> {
                    holder.icon.setImageResource(android.R.drawable.ic_menu_myplaces)
                    holder.icon.setColorFilter(android.graphics.Color.BLUE)
                }
                else -> {
                    holder.icon.setImageResource(android.R.drawable.ic_popup_reminder)
                    holder.icon.setColorFilter(android.graphics.Color.parseColor("#FF6F00"))
                }
            }

            holder.container.setOnClickListener {
                handleNotificationClick(item)
            }
        }
        override fun getItemCount() = items.size
    }
}