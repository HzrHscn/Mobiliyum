package com.example.mobiliyum
import java.io.Serializable

data class Product(
    val id: Int = 0,
    val storeId: Int = 0,
    val category: String = "",
    val name: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val productUrl: String = "",
    val clickCount: Int = 0,    // YENİ: Ürün Detayına bakılma sayısı
    val favoriteCount: Int = 0  // YENİ: Favorilenme sayısı
) : Serializable