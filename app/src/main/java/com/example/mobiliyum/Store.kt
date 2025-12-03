package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Store(
    val id: Int = 0,
    val name: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val location: String = "",
    val clickCount: Int = 0,
    // YENİ: Tarih bazlı tıklanma geçmişi (Format: "yyyy-MM-dd" -> Tık Sayısı)
    val clickHistory: HashMap<String, Int> = hashMapOf(),
    // YENİ ALAN: Etap Bilgisi ("A" veya "B")
    val etap: String = "",
    // YENİ: Mağazanın Seçimi (Vitrin) için seçilen ürün ID'leri
    val featuredProductIds: List<Int> = listOf()
) : Parcelable