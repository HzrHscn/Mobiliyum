package com.example.mobiliyum

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagementFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var tvPendingCount: TextView? = null

    // Personel Yönetimi Bileşenleri
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

        view.findViewById<TextView>(R.id.tvAdminWelcome).text = "Hoşgeldiniz, ${user?.fullName ?: "Yönetici"}"
        view.findViewById<TextView>(R.id.tvAdminRole).text = "Yetki: ${role.name}"

        // Ana Butonlar
        val btnAnnouncements = view.findViewById<LinearLayout>(R.id.btnAnnouncements)
        val btnPendingRequests = view.findViewById<LinearLayout>(R.id.btnPendingStores)
        val btnUsers = view.findViewById<LinearLayout>(R.id.btnUsers)
        val cardUsersBox = view.findViewById<CardView>(R.id.cardUsersBox) // Kullanıcı kutusu
        val btnReports = view.findViewById<LinearLayout>(R.id.btnReports)

        // Personel Yönetimi UI
        cardStaff = view.findViewById(R.id.cardStaffManagement)
        etStaffEmail = view.findViewById(R.id.etStaffEmail)
        btnSearchUser = view.findViewById(R.id.btnSearchUser)
        layoutResult = view.findViewById(R.id.layoutUserResult)
        tvResultInfo = view.findViewById(R.id.tvResultUserInfo)
        btnMakeEditor = view.findViewById(R.id.btnMakeEditor)

        val tvPendingTitle = btnPendingRequests.getChildAt(1) as? TextView
        tvPendingCount = btnPendingRequests.getChildAt(2) as? TextView

        // --- ROL BAZLI GÖRÜNÜM AYARLARI ---
        if (role == UserRole.ADMIN || role == UserRole.SRV) {
            // ADMIN: Her şeyi görür
            tvPendingTitle?.text = "Satın Alma Onayları"
            updatePendingCount()
            cardUsersBox.visibility = View.VISIBLE
            cardStaff.visibility = View.GONE // Admin personel eklemez, Müdür ekler
        }
        else if (role == UserRole.MANAGER) {
            // MÜDÜR: Kendi mağazasını yönetir
            tvPendingTitle?.text = "Onay Bekleyenler"
            updatePendingCount()

            cardUsersBox.visibility = View.GONE // Genel kullanıcıları göremez
            cardStaff.visibility = View.VISIBLE // Kendi personelini ekler
        }
        else {
            // EDİTÖR: Kısıtlı erişim
            cardUsersBox.visibility = View.GONE
            cardStaff.visibility = View.GONE
        }

        // --- TIKLAMA OLAYLARI ---

        btnAnnouncements.setOnClickListener { showAnnouncementDialog() }
        btnPendingRequests.setOnClickListener { showPendingRequestsDialog() }
        btnReports.setOnClickListener { showReportsDialog() }

        btnUsers.setOnClickListener {
            // Kullanıcı Yönetimi için ReportsFragment'taki Kullanıcı sekmesine yönlendir
            val fragment = ReportsFragment()
            // İsteğe bağlı: Sekme indexi gönderilebilir
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        // --- PERSONEL ARAMA VE ATAMA ---
        btnSearchUser.setOnClickListener {
            val email = etStaffEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                searchUserByEmail(email)
            }
        }

        btnMakeEditor.setOnClickListener {
            if (foundUser != null) {
                assignEditorRole(foundUser!!)
            }
        }

        return view
    }

    // --- MÜDÜR FONKSİYONLARI ---

    private fun searchUserByEmail(email: String) {
        layoutResult.visibility = View.GONE // Önce gizle

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val doc = docs.documents[0]
                    foundUser = doc.toObject(User::class.java)

                    if (foundUser != null) {
                        // Kendi kendini ekleyemez
                        if (foundUser!!.id == UserManager.getCurrentUser()?.id) {
                            Toast.makeText(context, "Kendinizi atayamazsınız.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        // Zaten yetkili mi?
                        if (foundUser!!.role == UserRole.MANAGER || foundUser!!.role == UserRole.ADMIN) {
                            Toast.makeText(context, "Bu kullanıcı zaten yönetici.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        layoutResult.visibility = View.VISIBLE
                        tvResultInfo.text = "Bulunan Kişi:\n${foundUser!!.fullName}\n(${foundUser!!.role.name})"

                        if (foundUser!!.role == UserRole.EDITOR && foundUser!!.storeId == UserManager.getCurrentUser()?.storeId) {
                            btnMakeEditor.isEnabled = false
                            btnMakeEditor.text = "Zaten Sizin Editörünüz"
                        } else {
                            btnMakeEditor.isEnabled = true
                            btnMakeEditor.text = "Editör Olarak Ata"
                        }
                    }
                } else {
                    Toast.makeText(context, "Bu e-posta ile kayıtlı kullanıcı bulunamadı.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Arama hatası: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun assignEditorRole(targetUser: User) {
        val manager = UserManager.getCurrentUser() ?: return

        if (manager.storeId == null || manager.storeId == 0) {
            Toast.makeText(context, "Hata: Sizin mağaza kaydınız yok.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Yetki Onayı")
            .setMessage("${targetUser.fullName} adlı kullanıcıyı Mağaza ID: ${manager.storeId} için EDİTÖR yapmak istiyor musunuz?")
            .setPositiveButton("Evet, Ata") { _, _ ->

                // Kullanıcıyı güncelle
                db.collection("users").document(targetUser.id)
                    .update(mapOf(
                        "role" to "EDITOR",
                        "storeId" to manager.storeId,
                        "canSendNotifications" to true // Varsayılan olarak bildirim atabilsin
                    ))
                    .addOnSuccessListener {
                        Toast.makeText(context, "Kullanıcı Editör yapıldı!", Toast.LENGTH_LONG).show()
                        layoutResult.visibility = View.GONE
                        etStaffEmail.text?.clear()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun updatePendingCount() {
        db.collection("purchase_requests")
            .whereEqualTo("status", "PENDING")
            .get()
            .addOnSuccessListener { docs ->
                tvPendingCount?.text = "Bekleyen: ${docs.size()}"
            }
    }

    // --- RAPORLAR (DÜZELTİLDİ: FİLTRELEME ve TAM EKRAN GEÇİŞ) ---
    private fun showReportsDialog() {
        // Popup yerine Tam Ekran ReportsFragment'a geçiş
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ReportsFragment())
            .addToBackStack(null)
            .commit()
    }

    // --- DİĞER FONKSİYONLAR ---
    private fun showPendingRequestsDialog() {
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        val title = TextView(context)
        title.text = "Yükleniyor..."
        title.textSize = 18f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        title.setPadding(0, 0, 0, 16)
        layout.addView(title)

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        layout.addView(recyclerView)

        val dialog = AlertDialog.Builder(context).setView(layout).setPositiveButton("Kapat", null).create()

        ReviewManager.getPendingRequests(
            onSuccess = { requests ->
                if (requests.isEmpty()) {
                    title.text = "Bekleyen talep yok"
                    Toast.makeText(context, "Talep yok.", Toast.LENGTH_SHORT).show()
                } else {
                    title.text = "Bekleyen Doğrulamalar"
                    recyclerView.adapter = RequestAdapter(requests) { request, approved ->
                        ReviewManager.processRequest(request, approved) { success ->
                            if (success) {
                                Toast.makeText(context, "İşlem başarılı", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                updatePendingCount()
                            }
                        }
                    }
                }
            },
            onFailure = { title.text = "Hata" }
        )
        dialog.show()
    }

    // --- ADAPTER (REQUEST) ---
    inner class RequestAdapter(
        private val items: List<PurchaseRequest>,
        private val onAction: (PurchaseRequest, Boolean) -> Unit
    ) : RecyclerView.Adapter<RequestAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvUser: TextView = v.findViewById(R.id.tvReqUserName)
            val tvProduct: TextView = v.findViewById(R.id.tvReqProductName)
            val tvOrder: TextView = v.findViewById(R.id.tvReqOrderInfo)
            val tvDate: TextView = v.findViewById(R.id.tvReqDate)
            val btnApprove: View = v.findViewById(R.id.btnApprove)
            val btnReject: View = v.findViewById(R.id.btnReject)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_purchase_request, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvUser.text = item.userName
            holder.tvProduct.text = item.productName
            holder.tvOrder.text = item.orderNumber
            holder.tvDate.text = SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(item.requestDate)
            holder.btnApprove.setOnClickListener { onAction(item, true) }
            holder.btnReject.setOnClickListener { onAction(item, false) }
        }
        override fun getItemCount() = items.size
    }

    private fun showAnnouncementDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_send_announcement, null)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etAnnounceTitle)
        val etMessage = dialogView.findViewById<TextInputEditText>(R.id.etAnnounceMessage)

        AlertDialog.Builder(context)
            .setTitle("Duyuru Yayınla")
            .setView(dialogView)
            .setPositiveButton("Gönder") { _, _ ->
                val title = etTitle.text.toString().trim()
                val message = etMessage.text.toString().trim()
                if (title.isNotEmpty() && message.isNotEmpty()) {
                    sendAnnouncementToFirebase(title, message)
                } else {
                    Toast.makeText(context, "Alanlar boş olamaz.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun sendAnnouncementToFirebase(title: String, message: String) {
        if (message.length > 200) {
            Toast.makeText(context, "Mesaj çok uzun! (Max 200)", Toast.LENGTH_LONG).show()
            return
        }
        val user = UserManager.getCurrentUser() ?: return
        val role = UserManager.getUserRole()

        var type = "general"
        var relatedId = ""
        var senderName = user.fullName

        if (role == UserRole.ADMIN || role == UserRole.SRV) {
            type = "store_update"
            relatedId = "6" // Test için Allinset
            senderName = "Allinset (Yönetici)"
        } else if (role == UserRole.MANAGER || role == UserRole.EDITOR) {
            type = "store_update"
            relatedId = user.storeId?.toString() ?: ""
        }

        if (type == "store_update" && relatedId.isEmpty()) {
            Toast.makeText(context, "Mağaza ID bulunamadı.", Toast.LENGTH_SHORT).show()
            return
        }

        val announcement = hashMapOf(
            "title" to title,
            "message" to message,
            "date" to Date(),
            "author" to senderName,
            "type" to type,
            "relatedId" to relatedId
        )

        db.collection("announcements").add(announcement)
            .addOnSuccessListener { Toast.makeText(context, "Duyuru Yayınlandı!", Toast.LENGTH_LONG).show() }
    }
}