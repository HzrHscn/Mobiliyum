package com.example.mobiliyum

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class UserManagementFragment : Fragment() {

    private lateinit var rvUsers: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var toggleRoles: MaterialButtonToggleGroup
    private lateinit var tvCount: TextView

    private val db = FirebaseFirestore.getInstance()
    private val allUsers = ArrayList<User>()
    private var adapter: UserManagementAdapter? = null

    // Filtre Durumları
    private var currentRoleFilter: UserRole? = null // Null = Hepsi
    private var currentSearchText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_management, container, false)

        rvUsers = view.findViewById(R.id.rvUserList)
        etSearch = view.findViewById(R.id.etUserSearch)
        toggleRoles = view.findViewById(R.id.toggleUserRoles)
        tvCount = view.findViewById(R.id.tvUserCount)

        rvUsers.layoutManager = LinearLayoutManager(context)

        // Arama Dinleyicisi
        etSearch.addTextChangedListener {
            currentSearchText = it.toString()
            applyFilters()
        }

        // Rol Filtre Dinleyicisi
        toggleRoles.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentRoleFilter = when(checkedId) {
                    R.id.btnRoleAdmin -> UserRole.ADMIN
                    R.id.btnRoleManager -> UserRole.MANAGER
                    R.id.btnRoleEditor -> UserRole.EDITOR
                    R.id.btnRoleCustomer -> UserRole.CUSTOMER
                    else -> null // Tümü
                }
                applyFilters()
            }
        }

        loadUsers()
        return view
    }

    private fun loadUsers() {
        db.collection("users").get().addOnSuccessListener { docs ->
            allUsers.clear()
            for (doc in docs) {
                val user = doc.toObject(User::class.java)
                allUsers.add(user)
            }
            applyFilters()
        }
    }

    private fun applyFilters() {
        val filtered = allUsers.filter { user ->
            // 1. Rol Filtresi
            val roleMatch = currentRoleFilter == null || user.role == currentRoleFilter

            // 2. Arama Filtresi (İsim, Mail, Mağaza ID)
            val searchLower = currentSearchText.lowercase(Locale.getDefault())
            val searchMatch = if (currentSearchText.isEmpty()) true else {
                user.fullName.lowercase().contains(searchLower) ||
                        user.email.lowercase().contains(searchLower) ||
                        (user.storeId?.toString() ?: "").contains(searchLower)
            }

            roleMatch && searchMatch
        }

        tvCount.text = "Listelenen: ${filtered.size} Kullanıcı"

        if (adapter == null) {
            adapter = UserManagementAdapter(filtered)
            rvUsers.adapter = adapter
        } else {
            adapter!!.updateList(filtered)
        }
    }

    // --- ROL ATAMA ---
    private fun showRoleDialog(user: User) {
        val roles = arrayOf("MÜŞTERİ", "EDİTÖR", "MÜDÜR", "SERVİS", "ADMİN")
        val roleEnum = arrayOf(UserRole.CUSTOMER, UserRole.EDITOR, UserRole.MANAGER, UserRole.SRV, UserRole.ADMIN)

        AlertDialog.Builder(context)
            .setTitle("${user.fullName} - Rol Seç")
            .setItems(roles) { _, which ->
                val newRole = roleEnum[which]

                // Eğer Müdür veya Editör ise Mağaza ID sor
                if (newRole == UserRole.MANAGER || newRole == UserRole.EDITOR) {
                    showStoreIdDialog(user, newRole)
                } else {
                    updateUserRole(user, newRole, null)
                }
            }
            .show()
    }

    private fun showStoreIdDialog(user: User, newRole: UserRole) {
        val input = EditText(context)
        input.hint = "Mağaza ID Giriniz (Örn: 5)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(context)
            .setTitle("Mağaza Atama")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val storeId = input.text.toString().toIntOrNull()
                if (storeId != null) {
                    updateUserRole(user, newRole, storeId)
                } else {
                    Toast.makeText(context, "Geçersiz Mağaza ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun updateUserRole(user: User, role: UserRole, storeId: Int?) {
        val updates = hashMapOf<String, Any>(
            "role" to role,
            "storeId" to (storeId ?: 0)
        )
        // Eğer müşteri/admin oluyorsa storeId'yi sıfırla/sil
        if (role == UserRole.CUSTOMER || role == UserRole.ADMIN) {
            updates["storeId"] = 0
        }

        db.collection("users").document(user.id).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Rol Güncellendi", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
    }

    // --- BANLAMA ---
    private fun toggleBan(user: User) {
        val newState = !user.isBanned
        db.collection("users").document(user.id).update("isBanned", newState)
            .addOnSuccessListener {
                val msg = if (newState) "Kullanıcı Banlandı" else "Ban Kaldırıldı"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                loadUsers()
            }
    }

    // --- ADAPTER ---
    inner class UserManagementAdapter(private var items: List<User>) : RecyclerView.Adapter<UserManagementAdapter.VH>() {

        fun updateList(newList: List<User>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvUserName)
            val email: TextView = v.findViewById(R.id.tvUserEmail)
            val role: TextView = v.findViewById(R.id.tvUserRole)
            val btnBan: MaterialButton = v.findViewById(R.id.btnBanUser)
            // DİKKAT: Burada XML dosyasındaki ID ile eşleştiğinden emin oluyoruz (btnChangeRole)
            val btnRole: MaterialButton = v.findViewById(R.id.btnChangeRole)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_report_user, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = items[position]
            holder.name.text = user.fullName
            holder.email.text = "${user.email} ${if(user.storeId != 0 && user.storeId != null) "(Mağaza: ${user.storeId})" else ""}"
            holder.role.text = user.role.name

            if (user.isBanned) {
                holder.btnBan.text = "Banı Aç"
                holder.btnBan.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
            } else {
                holder.btnBan.text = "Banla"
                holder.btnBan.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            }
            holder.btnBan.setOnClickListener { toggleBan(user) }

            holder.btnRole.setOnClickListener { showRoleDialog(user) }
        }

        override fun getItemCount() = items.size
    }
}