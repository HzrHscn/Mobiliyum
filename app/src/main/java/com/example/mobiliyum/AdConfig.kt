package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
@Parcelize
data class AdConfig(
    val isActive: Boolean = false,
    val imageUrl: String = "",
    val title: String = "",
    val endDate: Long = 0L,
    val orientation: String = "VERTICAL", // VERTICAL / HORIZONTAL
    val type: String = "",                // PRODUCT / STORE
    val targetProductId: String = "",
    val targetStoreId: String = ""
) : Parcelable
