package com.example.mobiliyum

import java.io.Serializable

data class Product(
    val id: Int = 0,
    val storeId: Int = 0,
    val category: String = "",
    val name: String = "",
    val price: String = "",
    val oldPrice: String = "", // YENİ: Önceki fiyatı burada tutuyoruz
    val imageUrl: String = "",
    val productUrl: String = "",
    val clickCount: Int = 0,
    val favoriteCount: Int = 0,


    val rating: Float = 0f,       // Ortalama Puan (Görünen)
    val reviewCount: Int = 0,     // Oy Sayısı
    val totalRating: Float = 0f,  // YENİ: Toplam Puan (5+3+4...) - Hesabın kalbi burası

    val priceHistory: HashMap<String, Double> = hashMapOf()
) : Serializable