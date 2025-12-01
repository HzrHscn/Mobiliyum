package com.example.mobiliyum

import java.io.Serializable

enum class UserRole {
    ADMIN, SRV, MANAGER, EDITOR, CUSTOMER
}

data class User(
    val id: String = "",
    val username: String = "",
    var fullName: String = "",
    val role: UserRole = UserRole.CUSTOMER,
    val storeId: Int? = null, // Çalıştığı mağaza
    val email: String = "",
    var lastPasswordUpdate: Long = 0,
    var lastProfileUpdate: Long = 0,
    var isBanned: Boolean = false,
    // YENİ: Takip edilen mağaza ID'leri
    val followedStores: ArrayList<Int> = arrayListOf(),
    // YENİ: Editör ise bildirim atabilir mi? (Manager verir bu yetkiyi)
    val canSendNotifications: Boolean = false
) : Serializable