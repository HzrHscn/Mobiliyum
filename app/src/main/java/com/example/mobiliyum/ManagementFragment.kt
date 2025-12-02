package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class ManagementFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var tvPendingCount: TextView? = null

    // Editör Talepleri UI Referansları
    private lateinit var cardEditorRequests: CardView
    private lateinit var tvEditorReqCount: TextView

    // Personel Yönetimi
    private lateinit var cardStaff: CardView
    private lateinit var etStaffEmail: TextInputEditText
    private lateinit var btnSearchUser: Button
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvResultInfo: TextView
    private lateinit var btnMakeEditor: Button
    private var foundUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_management, container, false)
        val user = UserManager.getCurrentUser()
        val role = UserManager.getUserRole()

        // UI Tanımlamaları
        val tvTitle = view.findViewById<TextView>(R.id.tvAdminTitle)
        view.findViewById<TextView>(R.id.tvAdminWelcome).text = "Hoşgeldiniz, ${user?.fullName ?: "Yönetici"}"
        view.findViewById<TextView>(R.id.tvAdminRole).text = "Yetki: ${role.name}"

        // Duyurular Butonu
        val btnAnnouncements = view.findViewById<LinearLayout>(R.id.btnAnnouncements)

        // 1. Kutu: Onay Bekleyenler (Müşteri) / Vitrin Yönetimi (Editör)
        val cardPendingBox = view.findViewById<CardView>(R.id.cardPendingBox)
        val btnPendingStores = view.findViewById<LinearLayout>(R.id.btnPendingStores)
        val tvPendingTitle = view.findViewById<TextView>(R.id.tvPendingTitle)
        val imgPendingIcon = view.findViewById<ImageView>(R.id.imgPendingIcon)
        tvPendingCount = view.findViewById(R.id.tvPendingCount)

        // 2. Kutu: Kullanıcılar / Ürün Yönetimi / Vitrin Yönetimi (Manager)
        val cardUsersBox = view.findViewById<CardView>(R.id.cardUsersBox)
        val btnUsers = view.findViewById<LinearLayout>(R.id.btnUsers)
        val tvUsersTitle = view.findViewById<TextView>(R.id.tvUsersTitle)
        val imgUsersIcon = view.findViewById<ImageView>(R.id.imgUsersIcon)

        // 3. Kutu: Raporlar
        val cardReportsBox = view.findViewById<CardView>(R.id.cardReportsBox)
        val btnReports = view.findViewById<LinearLayout>(R.id.btnReports)
        val tvReportsTitle = view.findViewById<TextView>(R.id.tvReportsTitle)

        // 4. Kutu: Editör Talepleri (YENİ - XML'de var)
        cardEditorRequests = view.findViewById(R.id.cardEditorRequests)
        val btnEditorRequests = view.findViewById<LinearLayout>(R.id.btnEditorRequests)
        tvEditorReqCount = view.findViewById(R.id.tvEditorReqCount)

        // Personel Yönetimi
        cardStaff = view.findViewById(R.id.cardStaffManagement)
        etStaffEmail = view.findViewById(R.id.etStaffEmail)
        btnSearchUser = view.findViewById(R.id.btnSearchUser)
        layoutResult = view.findViewById(R.id.layoutUserResult)
        tvResultInfo = view.findViewById(R.id.tvResultUserInfo)
        btnMakeEditor = view.findViewById(R.id.btnMakeEditor)

        // --- ROL YÖNETİMİ ---

        if (role == UserRole.ADMIN || role == UserRole.SRV) {
            // --- ADMIN MODU ---
            tvTitle.text = "Sistem Admin Paneli"
            tvPendingTitle.text = "Satın Alma Onayları"
            updatePendingCount(null)

            tvUsersTitle.text = "Kullanıcı Yönetimi"

            // Admin için gizli olanlar
            cardStaff.visibility = View.GONE
            cardEditorRequests.visibility = View.GONE

            btnPendingStores.setOnClickListener { showPurchaseRequestsDialog(null) }
            btnReports.setOnClickListener { showReportsDialog(-1) }
            // YENİ: Kullanıcı Yönetimi Sayfasına Git
            btnUsers.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, UserManagementFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
        else if (role == UserRole.MANAGER) {
            // --- MANAGER MODU ---
            tvTitle.text = "Mağaza Yönetim Paneli"
            tvTitle.setBackgroundColor(Color.parseColor("#FF6F00"))

            val myStoreId = user?.storeId

            if (myStoreId != null) {
                // 1. Müşteri Onayları
                tvPendingTitle.text = "Müşteri Onayları"
                updatePendingCount(myStoreId)
                btnPendingStores.setOnClickListener { showPurchaseRequestsDialog(myStoreId) }

                // 2. Raporlar -> İstatistikler
                tvReportsTitle.text = "Mağaza İstatistikleri"
                btnReports.setOnClickListener { showReportsDialog(myStoreId) }

                // 3. Vitrin Yönetimi
                tvUsersTitle.text = "Vitrin Yönetimi"
                imgUsersIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                btnUsers.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, StoreShowcaseFragment())
                        .addToBackStack(null)
                        .commit()
                }

                // 4. Editör Talepleri (ONAY KUTUSU)
                cardEditorRequests.visibility = View.VISIBLE
                updateEditorRequestCount(myStoreId)
                btnEditorRequests.setOnClickListener {
                    showEditorRequestsDialog(myStoreId)
                }
            } else {
                tvPendingCount?.text = "Mağaza kaydı yok"
            }

            cardStaff.visibility = View.VISIBLE
        }
        else if (role == UserRole.EDITOR) {
            // --- EDITOR MODU ---
            tvTitle.text = "Editör Paneli"
            tvTitle.setBackgroundColor(Color.parseColor("#43A047"))

            // Gizlenecekler
            cardReportsBox.visibility = View.GONE
            cardStaff.visibility = View.GONE
            cardEditorRequests.visibility = View.GONE

            // 1. Vitrin Yönetimi (Yeşil Kutuyu Dönüştür)
            cardPendingBox.visibility = View.VISIBLE
            tvPendingTitle.text = "Vitrin Düzenle"
            tvPendingCount?.text = "Seçim Yap"
            imgPendingIcon.setImageResource(android.R.drawable.ic_menu_gallery)

            btnPendingStores.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, StoreShowcaseFragment())
                    .addToBackStack(null)
                    .commit()
            }

            // 2. Ürün Yönetimi (Turuncu Kutuyu Dönüştür)
            cardUsersBox.visibility = View.VISIBLE
            tvUsersTitle.text = "Ürün Yönetimi"
            imgUsersIcon.setImageResource(android.R.drawable.ic_menu_edit)

            btnUsers.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, EditorProductsFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        btnAnnouncements.setOnClickListener { showAnnouncementDialog() }

        btnSearchUser.setOnClickListener {
            val email = etStaffEmail.text.toString().trim()
            if (email.isNotEmpty()) searchUserByEmail(email)
        }
        btnMakeEditor.setOnClickListener {
            if (foundUser != null) assignEditorRole(foundUser!!)
        }

        return view
    }

    private fun updateEditorRequestCount(storeId: Int) {
        EditorManager.getPendingRequests(storeId) { list ->
            if (list.isNotEmpty()) {
                tvEditorReqCount.text = "${list.size} Yeni Talep Bekliyor!"
                tvEditorReqCount.setTextColor(Color.RED)
            } else {
                tvEditorReqCount.text = "Bekleyen talep yok."
                tvEditorReqCount.setTextColor(Color.GRAY)
            }
        }
    }

    private fun showEditorRequestsDialog(storeId: Int) {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

        val title = TextView(context).apply {
            text = "Onay Bekleyen İşlemler"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        layout.addView(title)

        val listView = ListView(context)
        layout.addView(listView)

        val dialog = AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("Kapat", null)
            .create()

        EditorManager.getPendingRequests(storeId) { list ->
            if (list.isEmpty()) {
                Toast.makeText(context, "Bekleyen talep yok.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@getPendingRequests
            }

            val adapter = object : ArrayAdapter<StoreRequest>(context, android.R.layout.simple_list_item_2, android.R.id.text1, list) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text1 = view.findViewById<TextView>(android.R.id.text1)
                    val text2 = view.findViewById<TextView>(android.R.id.text2)

                    val item = getItem(position)!!
                    val typeLabel = if(item.type == "ANNOUNCEMENT") "[DUYURU]" else "[VİTRİN]"
                    text1.text = "$typeLabel - ${item.requesterName}"
                    text1.setTextColor(Color.parseColor("#333333"))

                    text2.text = if(item.type == "ANNOUNCEMENT") item.title else "Ürün Seçimi Güncellemesi"
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, pos, _ ->
                val req = list[pos]
                val detailMsg = if(req.type=="ANNOUNCEMENT")
                    "Başlık: ${req.title}\nMesaj: ${req.message}"
                else
                    "Editör vitrin ürünlerini güncellemek istiyor."

                AlertDialog.Builder(context)
                    .setTitle("Talebi İşle")
                    .setMessage("$detailMsg\n\nOnaylarsanız yayınlanacaktır.")
                    .setPositiveButton("Onayla ✅") { _, _ ->
                        EditorManager.processRequest(req, true) {
                            Toast.makeText(context, "Onaylandı.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            updateEditorRequestCount(storeId)
                        }
                    }
                    .setNegativeButton("Reddet ❌") { _, _ ->
                        EditorManager.processRequest(req, false) {
                            Toast.makeText(context, "Reddedildi.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            updateEditorRequestCount(storeId)
                        }
                    }
                    .setNeutralButton("İptal", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun showAnnouncementDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_send_announcement, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etAnnounceTitle)
        val etMessage = dialogView.findViewById<TextInputEditText>(R.id.etAnnounceMessage)

        val user = UserManager.getCurrentUser()
        val btnText = if (user?.role == UserRole.EDITOR) "Onaya Gönder" else "Yayınla"

        AlertDialog.Builder(context)
            .setTitle("Duyuru")
            .setView(dialogView)
            .setPositiveButton(btnText) { _, _ ->
                val title = etTitle.text.toString().trim()
                val message = etMessage.text.toString().trim()
                if (title.length < 3 || message.length < 5) {
                    Toast.makeText(context, "Çok kısa.", Toast.LENGTH_LONG).show()
                } else {
                    if (user?.role == UserRole.EDITOR) {
                        EditorManager.submitAnnouncementRequest(title, message) {
                            Toast.makeText(context, "Onaya gönderildi.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val req = StoreRequest(storeId = user?.storeId ?: 0, requesterName = user?.fullName ?: "Yönetici", title = title, message = message, type="ANNOUNCEMENT")
                        EditorManager.processRequest(req, true) {
                            Toast.makeText(context, "Yayınlandı.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // --- Helper Functions ---
    private fun updatePendingCount(storeId: Int?) {
        var query: com.google.firebase.firestore.Query = db.collection("purchase_requests").whereEqualTo("status", "PENDING")
        if (storeId != null) query = query.whereEqualTo("storeId", storeId)
        query.get().addOnSuccessListener { docs -> tvPendingCount?.text = "${docs.size()} Bekleyen" }
    }

    private fun showPurchaseRequestsDialog(storeId: Int?) {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val title = TextView(context).apply { text = "Müşteri Onayları"; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        layout.addView(title)
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        layout.addView(recyclerView)
        val dialog = AlertDialog.Builder(context).setView(layout).setPositiveButton("Kapat", null).create()

        ReviewManager.getPendingRequests(storeId,
            onSuccess = { requests ->
                if (requests.isEmpty()) title.text = "Bekleyen müşteri onayı yok"
                else {
                    recyclerView.adapter = RequestAdapter(requests) { request, approved ->
                        ReviewManager.processRequest(request, approved) { success ->
                            if (success) {
                                Toast.makeText(context, "İşlem başarılı", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                updatePendingCount(storeId)
                            }
                        }
                    }
                }
            },
            onFailure = { title.text = "Hata oluştu" }
        )
        dialog.show()
    }

    private fun showReportsDialog(storeId: Int) {
        val fragment = ReportsFragment()
        val args = Bundle()
        args.putInt("storeId", storeId)
        fragment.arguments = args
        parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).addToBackStack(null).commit()
    }

    private fun searchUserByEmail(email: String) {
        layoutResult.visibility = View.GONE
        db.collection("users").whereEqualTo("email", email).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                foundUser = docs.documents[0].toObject(User::class.java)
                if (foundUser != null && foundUser!!.id != UserManager.getCurrentUser()?.id) {
                    layoutResult.visibility = View.VISIBLE
                    tvResultInfo.text = "Kişi: ${foundUser!!.fullName}"
                    btnMakeEditor.isEnabled = true
                }
            } else { Toast.makeText(context, "Bulunamadı", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun assignEditorRole(targetUser: User) {
        val manager = UserManager.getCurrentUser() ?: return
        db.collection("users").document(targetUser.id)
            .update(mapOf("role" to "EDITOR", "storeId" to manager.storeId))
            .addOnSuccessListener {
                Toast.makeText(context, "Atandı!", Toast.LENGTH_SHORT).show()
                layoutResult.visibility = View.GONE
            }
    }

    inner class RequestAdapter(private val items: List<PurchaseRequest>, private val onAction: (PurchaseRequest, Boolean) -> Unit) : RecyclerView.Adapter<RequestAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvUser: TextView = v.findViewById(R.id.tvReqUserName); val tvProduct: TextView = v.findViewById(R.id.tvReqProductName)
            val tvOrder: TextView = v.findViewById(R.id.tvReqOrderInfo); val btnApprove: View = v.findViewById(R.id.btnApprove); val btnReject: View = v.findViewById(R.id.btnReject)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_purchase_request, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvUser.text = item.userName; holder.tvProduct.text = item.productName; holder.tvOrder.text = item.orderNumber
            holder.btnApprove.setOnClickListener { onAction(item, true) }; holder.btnReject.setOnClickListener { onAction(item, false) }
        }
        override fun getItemCount() = items.size
    }
}