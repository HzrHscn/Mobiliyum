package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class StoreRequest(
    val id: String = "",
    val storeId: Int = 0,
    val requesterId: String = "",     // Editörün ID'si
    val requesterName: String = "",   // Editörün Adı
    val type: String = "",            // "ANNOUNCEMENT" veya "SHOWCASE_UPDATE"
    val status: String = "PENDING",   // PENDING, APPROVED, REJECTED
    val requestDate: Date = Date(),

    // Duyuru için detaylar
    val title: String = "",
    val message: String = "",

    // Vitrin için detaylar (ArrayList, Parcelable ile daha uyumludur)
    val selectedProductIds: ArrayList<Int> = arrayListOf()
) : Parcelable