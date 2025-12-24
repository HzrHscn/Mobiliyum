package com.example.mobiliyum

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mobiliyum.databinding.FragmentUserManagementBinding
import com.example.mobiliyum.databinding.ItemReportUserBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class UserManagementFragment : Fragment() {

    private var _binding: FragmentUserManagementBinding? = null
    private val binding get() = _binding!!
    //private val db = FirebaseFirestore.getInstance()
    private val db by lazy { DataManager.getDb() }
    private val allUsers = ArrayList<User>()
    private lateinit var adapter: UserManagementAdapter
    private var currentRoleFilter: UserRole? = null
    private var currentSearchText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvUserList.layoutManager = LinearLayoutManager(context)

        adapter = UserManagementAdapter(
            onBanClick = { user -> toggleBan(user) },
            onRoleClick = { user -> showRoleDialog(user) }
        )
        binding.rvUserList.adapter = adapter

        binding.etUserSearch.addTextChangedListener {
            currentSearchText = it.toString()
            applyFilters()
        }

        binding.toggleUserRoles.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentRoleFilter = when(checkedId) {
                    R.id.btnRoleAdmin -> UserRole.ADMIN
                    R.id.btnRoleManager -> UserRole.MANAGER
                    R.id.btnRoleEditor -> UserRole.EDITOR
                    R.id.btnRoleCustomer -> UserRole.CUSTOMER
                    else -> null
                }
                applyFilters()
            } else {
                // Eğer seçim kaldırıldıysa filtreyi temizle (Opsiyonel, genelde ToggleGroup tek seçimli olur)
                // currentRoleFilter = null
                // applyFilters()
            }
        }

        loadUsers()
    }

    private fun loadUsers() {
        // Sadece ilk açılışta veya refresh istendiğinde çalışmalı
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
            val roleMatch = currentRoleFilter == null || user.role == currentRoleFilter
            val searchLower = currentSearchText.lowercase(Locale.getDefault())

            val searchMatch = if (currentSearchText.isEmpty()) true else {
                val storeIdStr = user.storeId?.toString() ?: ""
                user.fullName.lowercase().contains(searchLower) ||
                        user.email.lowercase().contains(searchLower) ||
                        storeIdStr.contains(searchLower)
            }
            roleMatch && searchMatch
        }

        binding.tvUserCount.text = "Listelenen: ${filtered.size} Kullanıcı"

        // ListAdapter'a YENİ bir liste referansı gönderiyoruz
        adapter.submitList(ArrayList(filtered))
    }

    private fun showRoleDialog(user: User) {
        val roles = arrayOf("MÜŞTERİ", "EDİTÖR", "MÜDÜR", "SERVİS", "ADMİN")
        val roleEnum = arrayOf(UserRole.CUSTOMER, UserRole.EDITOR, UserRole.MANAGER, UserRole.SRV, UserRole.ADMIN)

        AlertDialog.Builder(context)
            .setTitle("${user.fullName} - Rol Seç")
            .setItems(roles) { _, which ->
                val newRole = roleEnum[which]
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
        // Eğer müşteri veya admin ise storeId sıfırlanmalı
        if (role == UserRole.CUSTOMER || role == UserRole.ADMIN) {
            updates["storeId"] = 0
        }

        db.collection("users").document(user.id).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Rol Güncellendi", Toast.LENGTH_SHORT).show()

                // OPTİMİZASYON: Tekrar loadUsers() yapma, yerel listeyi güncelle
                val index = allUsers.indexOfFirst { it.id == user.id }
                if (index != -1) {
                    // Kullanıcının kopyasını oluşturup güncelle (Immutable yapı için)
                    val updatedUser = user.copy(
                        role = role,
                        storeId = if (role == UserRole.CUSTOMER || role == UserRole.ADMIN) 0 else (storeId ?: 0)
                    )
                    allUsers[index] = updatedUser
                    applyFilters() // Listeyi yenile
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleBan(user: User) {
        val newState = !user.isBanned
        db.collection("users").document(user.id).update("isBanned", newState)
            .addOnSuccessListener {
                val msg = if (newState) "Kullanıcı Banlandı" else "Ban Kaldırıldı"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                // OPTİMİZASYON: Tekrar loadUsers() yapma, yerel listeyi güncelle
                val index = allUsers.indexOfFirst { it.id == user.id }
                if (index != -1) {
                    val updatedUser = user.copy(isBanned = newState)
                    allUsers[index] = updatedUser
                    applyFilters()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Hata: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class UserManagementAdapter(
        private val onBanClick: (User) -> Unit,
        private val onRoleClick: (User) -> Unit
    ) : ListAdapter<User, UserManagementAdapter.VH>(UserDiffCallback()) {

        class UserDiffCallback : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
        }

        inner class VH(val binding: ItemReportUserBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemReportUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = getItem(position)

            holder.binding.tvUserName.text = user.fullName

            // Mağaza bilgisini gösterirken null kontrolü
            val storeText = if (user.storeId != null && user.storeId != 0) "(Mağaza: ${user.storeId})" else ""
            holder.binding.tvUserEmail.text = "${user.email} $storeText"

            holder.binding.tvUserRole.text = user.role.name

            if (user.isBanned) {
                holder.binding.btnBanUser.text = "Banı Aç"
                holder.binding.btnBanUser.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            } else {
                holder.binding.btnBanUser.text = "Banla"
                holder.binding.btnBanUser.backgroundTintList = ColorStateList.valueOf(Color.RED)
            }

            holder.binding.btnBanUser.setOnClickListener { onBanClick(user) }
            holder.binding.btnChangeRole.setOnClickListener { onRoleClick(user) }
        }
    }
}