package com.example.mobiliyum

import java.util.Date

data class PurchaseRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val productId: Int = 0,
    val productName: String = "",
    val storeId: Int = 0,
    val orderNumber: String = "", // Kullanıcının girdiği kanıt/sipariş no
    val status: String = "PENDING", // PENDING (Bekliyor), APPROVED (Onaylandı), REJECTED (Red)
    val requestDate: Date = Date()
)