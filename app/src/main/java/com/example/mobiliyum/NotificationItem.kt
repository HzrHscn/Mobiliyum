package com.example.mobiliyum
import java.util.Date

data class NotificationItem(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val date: Date = Date(),
    val type: String = "general", // "general", "price_alert", "store_update"
    val relatedId: String = "",   // StoreID veya ProductID
    val imageUrl: String = "",    // Bildirim resmi (Mağaza logosu veya ürün resmi)
    val senderName: String = ""   // Gönderen (Mağaza adı veya Sistem)
)