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
    // Tarih (Long) -> Fiyat (Double) haritası
    val priceHistory: HashMap<String, Double> = hashMapOf()
) : Serializable