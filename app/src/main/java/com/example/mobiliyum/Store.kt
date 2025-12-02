package com.example.mobiliyum
import java.io.Serializable

data class Store(
    val id: Int = 0,
    val name: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val location: String = "",
    val clickCount: Int = 0,
    // YENİ: Tarih bazlı tıklanma geçmişi (Format: "yyyy-MM-dd" -> Tık Sayısı)
    val clickHistory: HashMap<String, Int> = hashMapOf(),
    // YENİ: Mağazanın Seçimi (Vitrin) için seçilen ürün ID'leri
    val featuredProductIds: List<Int> = listOf()
) : Serializable