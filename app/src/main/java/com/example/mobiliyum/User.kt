package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class UserRole {
    ADMIN, SRV, MANAGER, EDITOR, CUSTOMER
}

@Parcelize // Bu anotasyonu ekleyin
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
    // YENİ EKLENEN ALAN: Son doğrulama maili zamanı
    var lastVerificationMailSent: Long = 0,
    // YENİ: Editör ise bildirim atabilir mi? (Manager verir bu yetkiyi)
    val canSendNotifications: Boolean = false
) : Parcelable