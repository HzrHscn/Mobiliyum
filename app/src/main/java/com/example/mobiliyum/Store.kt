package com.example.mobiliyum

data class Store(
    val id: Int,
    val name: String,       // Mağaza Adı (Örn: Çilek Mobilya)
    val category: String,   // Kategori (Örn: Genç Odası, Ofis)
    val imageUrl: String,   // Logo URL'si
    val location: String    // Mağaza No / Konum
)