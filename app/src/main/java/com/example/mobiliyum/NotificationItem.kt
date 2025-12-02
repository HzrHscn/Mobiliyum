package com.example.mobiliyum

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class NotificationItem(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val date: Date = Date(),
    val type: String = "general", // "general", "price_alert", "store_update"
    val relatedId: String = "",   // StoreID veya ProductID
    val imageUrl: String = "",    // Bildirim resmi
    val senderName: String = "",   // Gönderen
    val isRead: Boolean = false   // Ekstra: Okundu bilgisi
) : Parcelable