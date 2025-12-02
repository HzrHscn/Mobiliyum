package com.example.mobiliyum

import java.io.Serializable
import java.util.Date

data class StoreRequest(
    val id: String = "",
    val storeId: Int = 0,
    val requesterId: String = "",     // Editörün ID'si
    val requesterName: String = "",   // Editörün Adı
    val type: String = "",            // "ANNOUNCEMENT" veya "SHOWCASE"
    val status: String = "PENDING",   // PENDING, APPROVED, REJECTED
    val requestDate: Date = Date(),

    // Duyuru için detaylar
    val title: String = "",
    val message: String = "",

    // Vitrin için detaylar
    val selectedProductIds: List<Int> = listOf()
) : Serializable