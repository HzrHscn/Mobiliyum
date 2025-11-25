package com.example.mobiliyum

import java.io.Serializable

data class Product(
    val id: Int,
    val storeId: Int,       // ÖNEMLİ: Ürünün hangi mağazaya ait olduğu (Yetki kontrolü için)
    val category: String,
    val name: String,
    val price: String,
    val imageUrl: String,
    val productUrl: String  // YENİ: Sepetten yönlendirme yapılacak web linki
) : Serializable