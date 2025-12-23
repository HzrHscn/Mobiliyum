package com.example.mobiliyum

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {

    // Kanal ID'leri
    private const val CHANNEL_PRICE = "price_alerts"
    private const val CHANNEL_STORE = "store_updates"
    private const val CHANNEL_GENERAL = "general_notifications"
    private const val GROUP_ID = "mobiliyum_group"

    private val notificationId = AtomicInteger(0)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Grup Oluştur
            val group = NotificationChannelGroup(GROUP_ID, "Mobiliyum Bildirimleri")
            manager.createNotificationChannelGroup(group)

            // 1. Fiyat Bildirimleri (Yüksek Önem)
            val priceChannel = NotificationChannel(
                CHANNEL_PRICE,
                "Fiyat Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Favori ürünlerin fiyat düşüşleri"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
                setGroup(GROUP_ID)
            }

            // 2. Mağaza Duyuruları (Orta Önem)
            val storeChannel = NotificationChannel(
                CHANNEL_STORE,
                "Mağaza Duyuruları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Takip ettiğiniz mağazaların duyuruları"
                enableLights(true)
                lightColor = Color.BLUE
                setShowBadge(true)
                setGroup(GROUP_ID)
            }

            // 3. Genel Bildirimler (Düşük Önem)
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "Genel Bildirimler",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sistem bildirimleri ve duyurular"
                setShowBadge(false)
                setGroup(GROUP_ID)
            }

            manager.createNotificationChannels(listOf(priceChannel, storeChannel, generalChannel))
        }
    }

    fun sendNotification(
        context: Context,
        title: String,
        message: String,
        type: String = "general",
        relatedId: String? = null
    ) {
        // İzin Kontrolü (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val channelId = when (type) {
            "price_alert" -> CHANNEL_PRICE
            "store_update" -> CHANNEL_STORE
            else -> CHANNEL_GENERAL
        }

        // Tıklanınca Açılacak Ekran
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_fragment", "notifications")
            putExtra("notification_type", type)
            relatedId?.let { putExtra("related_id", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // İkon Seçimi
        val icon = when (type) {
            "price_alert" -> android.R.drawable.star_big_on
            "store_update" -> android.R.drawable.ic_dialog_map
            else -> android.R.drawable.ic_dialog_info
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                if (type == "price_alert") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_ID)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(notificationId.get(), builder.build())

            // Gruplama (4 bildirimde bir özet göster)
            if (notificationId.get() % 4 == 0) {
                sendSummaryNotification(context, channelId)
            }
        } catch (e: SecurityException) {
            // İzin yoksa sessizce geç
        }
    }

    private fun sendSummaryNotification(context: Context, channelId: String) {
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Mobiliyum")
            .setContentText("Yeni bildirimleriniz var")
            .setGroup(GROUP_ID)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(0, summary)
        } catch (e: SecurityException) {}
    }

    fun clearAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}