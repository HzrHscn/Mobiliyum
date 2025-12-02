package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Review(
    val id: String = "",
    val productId: Int = 0,
    val userId: String = "",
    val userName: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    val date: Date = Date(),
    val isVerified: Boolean = true,

    // YENİ EKLENEN ALANLAR (Listeleme için gerekli)
    val productName: String = "",
    val productImageUrl: String = ""
) : Parcelable