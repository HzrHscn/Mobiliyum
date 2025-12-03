package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Product(
    val id: Int = 0,
    val name: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val storeId: Int = 0,
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val totalRating: Double = 0.0,
    val favoriteCount: Int = 0,
    val clickCount: Int = 0,
    val productUrl: String = "",
    // YENİ EKLENEN ALAN:
    val description: String = "",

    val priceHistory: HashMap<String, Double> = hashMapOf(),

    var quantity: Int = 1
) : Parcelable