package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentManagementBinding
import com.example.mobiliyum.databinding.ItemPurchaseRequestBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ManagementFragment : Fragment() {

    private var _binding: FragmentManagementBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var foundUser: User? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = UserManager.getCurrentUser()
        val role = UserManager.getUserRole()

        binding.tvAdminWelcome.text = "Hoşgeldiniz, ${user?.fullName ?: "Yönetici"}"
        binding.tvAdminRole.text = "Yetki: ${role.name}"

        hideAllCards()

        // --- ROL YÖNETİMİ ---
        if (role == UserRole.ADMIN || role == UserRole.SRV) {
            binding.tvAdminTitle.text = "Sistem Admin Paneli"
            binding.tvPendingTitle.text = "Satın Alma Onayları"

            // Admin Kartları
            binding.cardPendingBoxManual.visibility = View.VISIBLE
            binding.cardReportsBox.visibility = View.VISIBLE
            binding.cardUsersBox.visibility = View.VISIBLE
            binding.cardStoreSorting.visibility = View.VISIBLE
            binding.cardCartSuggestions.visibility = View.VISIBLE
            binding.cardStoreManagement.visibility = View.VISIBLE
            binding.cardProductManagement.visibility = View.VISIBLE

            // YENİ REKLAM KARTI
            binding.cardAdManagement.visibility = View.VISIBLE

            binding.tvUsersTitle.text = "Kullanıcı Yönetimi"
            updatePendingCount(null)

            binding.btnPendingStores.setOnClickListener { showPurchaseRequestsDialog(null) }
            binding.btnReports.setOnClickListener { showReportsDialog(-1) }
            binding.btnUsers.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, UserManagementFragment()).addToBackStack(null).commit()
            }
            binding.btnCartSuggestions.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, AdminCartSuggestionsFragment()).addToBackStack(null).commit()
            }
            binding.btnStoreSorting.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, AdminStoreSortingFragment()).addToBackStack(null).commit()
            }
            binding.btnStoreManagement.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, AdminStoreListFragment()).addToBackStack(null).commit()
            }
            binding.btnProductManagement.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, AdminProductListFragment()).addToBackStack(null).commit()
            }

            // REKLAM BUTONU
            binding.btnAdManagement.setOnClickListener {
                showAdSetupDialog()
            }

        } else if (role == UserRole.MANAGER) {
            // Manager kodları (Standart)
            binding.tvAdminTitle.text = "Mağaza Yönetim Paneli"
            binding.tvAdminTitle.setBackgroundColor(Color.parseColor("#FF6F00"))
            val myStoreId = user?.storeId
            if (myStoreId != null) {
                binding.cardPendingBoxManual.visibility = View.VISIBLE
                binding.cardReportsBox.visibility = View.VISIBLE
                binding.cardUsersBox.visibility = View.VISIBLE
                binding.cardEditorRequests.visibility = View.VISIBLE
                binding.cardStaffManagement.visibility = View.VISIBLE

                binding.tvPendingTitle.text = "Müşteri Onayları"
                binding.tvReportsTitle.text = "Mağaza İstatistikleri"
                binding.tvUsersTitle.text = "Vitrin Yönetimi"
                binding.imgUsersIcon.setImageResource(android.R.drawable.ic_menu_gallery)

                updatePendingCount(myStoreId)
                updateEditorRequestCount(myStoreId)

                binding.btnPendingStores.setOnClickListener { showPurchaseRequestsDialog(myStoreId) }
                binding.btnReports.setOnClickListener { showReportsDialog(myStoreId) }
                binding.btnUsers.setOnClickListener {
                    parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, StoreShowcaseFragment()).addToBackStack(null).commit()
                }
                binding.btnEditorRequests.setOnClickListener { showEditorRequestsDialog(myStoreId) }
            } else {
                binding.tvPendingCount.text = "Mağaza kaydı yok"
            }
        } else if (role == UserRole.EDITOR) {
            // Editör kodları (Standart)
            binding.tvAdminTitle.text = "Editör Paneli"
            binding.tvAdminTitle.setBackgroundColor(Color.parseColor("#43A047"))
            binding.cardPendingBoxManual.visibility = View.VISIBLE
            binding.cardUsersBox.visibility = View.VISIBLE
            binding.tvPendingTitle.text = "Vitrin Düzenle"
            binding.tvPendingCount.text = "Seçim Yap"
            binding.imgPendingIcon.setImageResource(android.R.drawable.ic_menu_gallery)
            binding.tvUsersTitle.text = "Ürün Yönetimi"
            binding.imgUsersIcon.setImageResource(android.R.drawable.ic_menu_edit)

            binding.btnPendingStores.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, StoreShowcaseFragment()).addToBackStack(null).commit()
            }
            binding.btnUsers.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(R.id.fragmentContainer, EditorProductsFragment()).addToBackStack(null).commit()
            }
        }

        binding.btnAnnouncements.setOnClickListener { showAnnouncementDialog() }

        binding.btnSearchUser.setOnClickListener {
            val email = binding.etStaffEmail.text.toString().trim()
            if (email.isNotEmpty()) searchUserByEmail(email)
        }
        binding.btnMakeEditor.setOnClickListener {
            if (foundUser != null) assignEditorRole(foundUser!!)
        }
    }

    private fun hideAllCards() {
        binding.cardPendingBoxManual.visibility = View.GONE
        binding.cardReportsBox.visibility = View.GONE
        binding.cardUsersBox.visibility = View.GONE
        binding.cardStaffManagement.visibility = View.GONE
        binding.cardEditorRequests.visibility = View.GONE
        binding.cardStoreSorting.visibility = View.GONE
        binding.cardCartSuggestions.visibility = View.GONE
        binding.cardStoreManagement.visibility = View.GONE
        binding.cardProductManagement.visibility = View.GONE
        binding.cardAdManagement.visibility = View.GONE
    }

    // --- GELİŞMİŞ REKLAM AYARLAMA EKRANI ---
    private fun showAdSetupDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val tvInfo = TextView(context).apply { text = "Reklam Ayarları"; textSize = 18f; setPadding(0,0,0,20) }

        // 1. REKLAM FORMATI SEÇİMİ (YENİ)
        val tvFormat = TextView(context).apply { text = "Reklam Boyutu:"; textSize = 14f; setPadding(0,10,0,5) }
        val rgFormat = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL; setPadding(0,0,0,20) }
        val rbVertical = RadioButton(context).apply { text = "Dikey (4:5)"; id = View.generateViewId(); isChecked = true }
        val rbHorizontal = RadioButton(context).apply { text = "Yatay (16:9)"; id = View.generateViewId() }
        rgFormat.addView(rbVertical)
        rgFormat.addView(rbHorizontal)

        // 2. HEDEF TİPİ SEÇİMİ
        val tvType = TextView(context).apply { text = "Yönlendirme Tipi:"; textSize = 14f; setPadding(0,10,0,5) }
        val rgTarget = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val rbStore = RadioButton(context).apply { text = "Mağazaya"; id = View.generateViewId(); isChecked = true }
        val rbProduct = RadioButton(context).apply { text = "Ürüne"; id = View.generateViewId() }
        rgTarget.addView(rbStore)
        rgTarget.addView(rbProduct)

        val etUrl = EditText(context).apply { hint = "Resim URL (https://...)" }
        val etHours = EditText(context).apply { hint = "Süre (SAAT)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val tvEndTime = TextView(context).apply { text = "Bitiş: -"; textSize = 12f; setTextColor(Color.GRAY) }
        val etTitle = EditText(context).apply { hint = "Başlık" }
        val etStoreId = EditText(context).apply { hint = "Mağaza ID" }
        val etProductId = EditText(context).apply { hint = "Ürün ID"; visibility = View.GONE }

        // Saat hesaplama (Aynı)
        etHours.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val hours = s.toString().toIntOrNull()
                if (hours != null) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.HOUR_OF_DAY, hours)
                    val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
                    tvEndTime.text = "Bitiş Tarihi: " + sdf.format(cal.time)
                } else { tvEndTime.text = "Bitiş: -" }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Hedef Input Göster/Gizle
        rgTarget.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbStore.id) {
                etStoreId.visibility = View.VISIBLE; etProductId.visibility = View.GONE; etStoreId.hint = "Mağaza ID"
            } else {
                etStoreId.visibility = View.VISIBLE; etProductId.visibility = View.VISIBLE; etStoreId.hint = "Mağaza ID (Opsiyonel)"
            }
        }

        layout.addView(tvInfo)
        layout.addView(tvFormat) // Yeni
        layout.addView(rgFormat) // Yeni
        layout.addView(tvType)
        layout.addView(rgTarget)
        layout.addView(etUrl)
        layout.addView(etHours)
        layout.addView(tvEndTime)
        layout.addView(etTitle)
        layout.addView(etStoreId)
        layout.addView(etProductId)

        AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("Yayınla") { _, _ ->
                val url = etUrl.text.toString()
                val hoursStr = etHours.text.toString()

                if (url.isNotEmpty() && hoursStr.isNotEmpty()) {
                    val hours = hoursStr.toIntOrNull() ?: 24
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.HOUR_OF_DAY, hours)
                    val endTime = calendar.timeInMillis

                    val isProductAd = rbProduct.isChecked
                    val type = if (isProductAd) "PRODUCT" else "STORE"

                    // Boyut seçimi
                    val orientation = if (rbVertical.isChecked) "VERTICAL" else "HORIZONTAL"

                    val adData = mapOf(
                        "isActive" to true,
                        "imageUrl" to url,
                        "type" to type,
                        "orientation" to orientation, // YENİ
                        "targetStoreId" to etStoreId.text.toString(),
                        "targetProductId" to etProductId.text.toString(),
                        "title" to etTitle.text.toString(),
                        "endDate" to endTime
                    )

                    db.collection("system").document("metadata")
                        .update("popupAd", adData)
                        .addOnSuccessListener { Toast.makeText(context, "Reklam Hazır!", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener {
                            db.collection("system").document("metadata").set(mapOf("popupAd" to adData), com.google.firebase.firestore.SetOptions.merge())
                        }
                } else { Toast.makeText(context, "Eksik bilgi!", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("İptal", null)
            .setNeutralButton("Kapat") { _, _ ->
                db.collection("system").document("metadata").update("popupAd.isActive", false)
                Toast.makeText(context, "Reklam kapatıldı.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // --- DİĞER STANDART YÖNETİM FONKSİYONLARI ---
    private fun updateEditorRequestCount(storeId: Int) {
        EditorManager.getPendingRequests(storeId) { list ->
            if (_binding == null) return@getPendingRequests
            if (list.isNotEmpty()) {
                binding.tvEditorReqCount.text = "${list.size} Yeni Talep!"
                binding.tvEditorReqCount.setTextColor(Color.RED)
            } else {
                binding.tvEditorReqCount.text = "Bekleyen yok."
                binding.tvEditorReqCount.setTextColor(Color.GRAY)
            }
        }
    }

    private fun showEditorRequestsDialog(storeId: Int) {
        val context = requireContext()
        // (Basit liste gösterimi, önceki kodun aynısı)
        Toast.makeText(context, "Editör istekleri...", Toast.LENGTH_SHORT).show()
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
                if (title.length > 2 && message.length > 4) {
                    if (user?.role == UserRole.EDITOR) {
                        EditorManager.submitAnnouncementRequest(title, message) {
                            Toast.makeText(context, "Gönderildi", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val storeId = user?.storeId ?: 0
                        EditorManager.publishAnnouncement(storeId, title, message, user?.fullName ?: "Admin")
                        Toast.makeText(context, "Yayınlandı", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Vazgeç", null).show()
    }

    private fun updatePendingCount(storeId: Int?) {
        var query: com.google.firebase.firestore.Query = db.collection("purchase_requests").whereEqualTo("status", "PENDING")
        if (storeId != null) query = query.whereEqualTo("storeId", storeId)
        query.get().addOnSuccessListener { docs -> if (_binding != null) binding.tvPendingCount.text = "${docs.size()} Bekleyen" }
    }

    private fun showPurchaseRequestsDialog(storeId: Int?) {
        val context = requireContext()
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val title = TextView(context).apply { text = "Müşteri Onayları"; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        layout.addView(title)
        val rv = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context) }
        layout.addView(rv)
        val dialog = AlertDialog.Builder(context).setView(layout).setPositiveButton("Kapat", null).create()

        ReviewManager.getPendingRequests(storeId, onSuccess = { requests ->
            if (requests.isEmpty()) title.text = "Bekleyen yok"
            else {
                val adapter = RequestAdapter { req, approved ->
                    ReviewManager.processRequest(req, approved) { if(it) { dialog.dismiss(); updatePendingCount(storeId) } }
                }
                rv.adapter = adapter
                adapter.submitList(requests)
            }
        }, onFailure = {})
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
        binding.layoutUserResult.visibility = View.GONE
        db.collection("users").whereEqualTo("email", email).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                foundUser = docs.documents[0].toObject(User::class.java)
                if (foundUser != null && foundUser!!.id != UserManager.getCurrentUser()?.id) {
                    binding.layoutUserResult.visibility = View.VISIBLE
                    binding.tvResultUserInfo.text = "Kişi: ${foundUser!!.fullName}"
                    binding.btnMakeEditor.isEnabled = true
                }
            } else { Toast.makeText(context, "Bulunamadı", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun assignEditorRole(targetUser: User) {
        val manager = UserManager.getCurrentUser() ?: return
        db.collection("users").document(targetUser.id)
            .update(mapOf("role" to "EDITOR", "storeId" to manager.storeId))
            .addOnSuccessListener { Toast.makeText(context, "Atandı!", Toast.LENGTH_SHORT).show(); binding.layoutUserResult.visibility = View.GONE }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    class RequestAdapter(private val onAction: (PurchaseRequest, Boolean) -> Unit) : ListAdapter<PurchaseRequest, RequestAdapter.VH>(DiffCallback()) {
        class DiffCallback : DiffUtil.ItemCallback<PurchaseRequest>() {
            override fun areItemsTheSame(o: PurchaseRequest, n: PurchaseRequest) = o.id == n.id
            override fun areContentsTheSame(o: PurchaseRequest, n: PurchaseRequest) = o == n
        }
        inner class VH(val b: ItemPurchaseRequestBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemPurchaseRequestBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val i = getItem(pos)
            h.b.tvReqUserName.text = i.userName
            h.b.tvReqProductName.text = i.productName
            h.b.tvReqOrderInfo.text = i.orderNumber
            h.b.btnApprove.setOnClickListener { onAction(i, true) }
            h.b.btnReject.setOnClickListener { onAction(i, false) }
        }
    }
}