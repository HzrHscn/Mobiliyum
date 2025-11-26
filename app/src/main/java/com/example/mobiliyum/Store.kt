package com.example.mobiliyum
import java.io.Serializable

data class Store(
    val id: Int = 0,
    val name: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val location: String = "",
    val clickCount: Int = 0 // YENİ: Tıklanma Sayısı
) : Serializable