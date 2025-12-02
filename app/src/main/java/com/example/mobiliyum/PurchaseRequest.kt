package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class PurchaseRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val productId: Int = 0,
    val productName: String = "",
    val storeId: Int = 0,
    val orderNumber: String = "",   // Kullanıcının girdiği kanıt/sipariş no
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val requestDate: Date = Date()
) : Parcelable