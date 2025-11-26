package com.example.mobiliyum

import java.io.Serializable

enum class UserRole {
    ADMIN, SRV, MANAGER, EDITOR, CUSTOMER
}

data class User(
    val id: String = "",
    val username: String = "", // Genelde email başı
    var fullName: String = "",
    val role: UserRole = UserRole.CUSTOMER,
    val storeId: Int? = null,
    val email: String = "",
    // YENİ ALANLAR (Zaman damgaları - Timestamp)
    var lastPasswordUpdate: Long = 0, // System.currentTimeMillis() tutacak
    var lastProfileUpdate: Long = 0   // İsim değiştirme tarihi
) : Serializable