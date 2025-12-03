package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentManagementBinding
import com.example.mobiliyum.databinding.ItemPurchaseRequestBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class ManagementFragment : Fragment() {

    private var _binding: FragmentManagementBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private var foundUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = UserManager.getCurrentUser()
        val role = UserManager.getUserRole()

        binding.tvAdminWelcome.text = "Hoşgeldiniz, ${user?.fullName ?: "Yönetici"}"
        binding.tvAdminRole.text = "Yetki: ${role.name}"

        // --- ROL YÖNETİMİ ---
        if (role == UserRole.ADMIN || role == UserRole.SRV) {
            // --- ADMIN MODU ---
            binding.tvAdminTitle.text = "Sistem Admin Paneli"
            binding.tvPendingTitle.text = "Satın Alma Onayları"
            updatePendingCount(null)

            binding.tvUsersTitle.text = "Kullanıcı Yönetimi"

            binding.cardStaffManagement.visibility = View.GONE
            binding.cardEditorRequests.visibility = View.GONE

            binding.btnPendingStores.setOnClickListener { showPurchaseRequestsDialog(null) }
            binding.btnReports.setOnClickListener { showReportsDialog(-1) }
            binding.btnUsers.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, UserManagementFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
        else if (role == UserRole.MANAGER) {
            // --- MANAGER MODU ---
            binding.tvAdminTitle.text = "Mağaza Yönetim Paneli"
            binding.tvAdminTitle.setBackgroundColor(Color.parseColor("#FF6F00"))

            val myStoreId = user?.storeId

            if (myStoreId != null) {
                binding.tvPendingTitle.text = "Müşteri Onayları"
                updatePendingCount(myStoreId)
                binding.btnPendingStores.setOnClickListener { showPurchaseRequestsDialog(myStoreId) }

                binding.tvReportsTitle.text = "Mağaza İstatistikleri"
                binding.btnReports.setOnClickListener { showReportsDialog(myStoreId) }

                binding.tvUsersTitle.text = "Vitrin Yönetimi"
                binding.imgUsersIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                binding.btnUsers.setOnClickListener {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, StoreShowcaseFragment())
                        .addToBackStack(null)
                        .commit()
                }

                binding.cardEditorRequests.visibility = View.VISIBLE
                updateEditorRequestCount(myStoreId)
                binding.btnEditorRequests.setOnClickListener {
                    showEditorRequestsDialog(myStoreId)
                }
            } else {
                binding.tvPendingCount.text = "Mağaza kaydı yok"
            }
            binding.cardStaffManagement.visibility = View.VISIBLE
        }
        else if (role == UserRole.EDITOR) {
            // --- EDITOR MODU ---
            binding.tvAdminTitle.text = "Editör Paneli"
            binding.tvAdminTitle.setBackgroundColor(Color.parseColor("#43A047"))

            binding.cardReportsBox.visibility = View.GONE
            binding.cardStaffManagement.visibility = View.GONE
            binding.cardEditorRequests.visibility = View.GONE

            binding.cardPendingBox.visibility = View.VISIBLE
            binding.tvPendingTitle.text = "Vitrin Düzenle"
            binding.tvPendingCount.text = "Seçim Yap"
            binding.imgPendingIcon.setImageResource(android.R.drawable.ic_menu_gallery)

            binding.btnPendingStores.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, StoreShowcaseFragment())
                    .addToBackStack(null)
                    .commit()
            }

            binding.cardUsersBox.visibility = View.VISIBLE
            binding.tvUsersTitle.text = "Ürün Yönetimi"
            binding.imgUsersIcon.setImageResource(android.R.drawable.ic_menu_edit)

            binding.btnUsers.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, EditorProductsFragment())
                    .addToBackStack(null)
                    .commit()
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

    private fun updateEditorRequestCount(storeId: Int) {
        EditorManager.getPendingRequests(storeId) { list ->
            if (_binding == null) return@getPendingRequests
            if (list.isNotEmpty()) {
                binding.tvEditorReqCount.text = "${list.size} Yeni Talep Bekliyor!"
                binding.tvEditorReqCount.setTextColor(Color.RED)
            } else {
                binding.tvEditorReqCount.text = "Bekleyen talep yok."
                binding.tvEditorReqCount.setTextColor(Color.GRAY)
            }
        }
    }

    private fun showEditorRequestsDialog(storeId: Int) {
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

        val title = TextView(context).apply {
            text = "Onay Bekleyen İşlemler"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
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

    private fun updatePendingCount(storeId: Int?) {
        var query: com.google.firebase.firestore.Query = db.collection("purchase_requests").whereEqualTo("status", "PENDING")
        if (storeId != null) query = query.whereEqualTo("storeId", storeId)
        query.get().addOnSuccessListener { docs ->
            if (_binding != null) binding.tvPendingCount.text = "${docs.size()} Bekleyen"
        }
    }

    private fun showPurchaseRequestsDialog(storeId: Int?) {
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
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
                    // DÜZELTME: ListAdapter kurulumu
                    val adapter = RequestAdapter { request, approved ->
                        ReviewManager.processRequest(request, approved) { success ->
                            if (success) {
                                Toast.makeText(context, "İşlem başarılı", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                updatePendingCount(storeId)
                            }
                        }
                    }
                    recyclerView.adapter = adapter
                    adapter.submitList(requests) // Veriyi gönder
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
            .addOnSuccessListener {
                Toast.makeText(context, "Atandı!", Toast.LENGTH_SHORT).show()
                binding.layoutUserResult.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- MODERN ADAPTER (ListAdapter + DiffUtil) ---
    class RequestAdapter(
        private val onAction: (PurchaseRequest, Boolean) -> Unit
    ) : ListAdapter<PurchaseRequest, RequestAdapter.VH>(RequestDiffCallback()) {

        class RequestDiffCallback : DiffUtil.ItemCallback<PurchaseRequest>() {
            override fun areItemsTheSame(oldItem: PurchaseRequest, newItem: PurchaseRequest) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: PurchaseRequest, newItem: PurchaseRequest) = oldItem == newItem
        }

        inner class VH(val binding: ItemPurchaseRequestBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemPurchaseRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.binding.tvReqUserName.text = item.userName
            holder.binding.tvReqProductName.text = item.productName
            holder.binding.tvReqOrderInfo.text = item.orderNumber
            holder.binding.btnApprove.setOnClickListener { onAction(item, true) }
            holder.binding.btnReject.setOnClickListener { onAction(item, false) }
        }
    }
}