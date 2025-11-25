package com.example.mobiliyum

import java.io.Serializable

enum class UserRole {
    ADMIN,      // Sen: Sistemin tam hakimi
    SRV,        // Mobiliyum Sahibi: Raporları görür
    MANAGER,    // Mağaza Müdürü: Kendi mağazasını yönetir
    EDITOR,     // Mağaza Çalışanı: Ürün düzenler
    CUSTOMER    // Son Kullanıcı
}

data class User(
    val id: Int,
    var username: String,
    var fullName: String,
    val role: UserRole,
    val storeId: Int? = null, // Sadece mağaza çalışanları için dolu olur
    var email: String? = null,
    var password: String, // Demo amaçlı şifre
    val phoneNumber: String? = null // Telefon numarası eklendi
) : Serializable
