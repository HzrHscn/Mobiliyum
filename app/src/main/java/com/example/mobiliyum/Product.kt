package com.example.mobiliyum

import java.io.Serializable

data class Product(
    val id: Int,
    val category: String,   // Ürün Kategorisi (Örn: Koltuk Takımları)
    val name: String,       // Ürün Adı (Örn: Berlin Köşe Takımı)
    val price: String,      // Fiyatı (Örn: 45.000 TL)
    val imageUrl: String    // Ürün Resmi URL'si
) : Serializable