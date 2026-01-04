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

    private const val CHANNEL_PRICE = "price_alerts"
    private const val CHANNEL_STORE = "store_updates"
    private const val CHANNEL_GENERAL = "general_notifications"
    private const val GROUP_ID = "mobiliyum_group"

    private val notificationId = AtomicInteger(0)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.util.Log.d("NotificationHelper", "📺 Bildirim kanalları oluşturuluyor...")

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Grup Oluştur
            val group = NotificationChannelGroup(GROUP_ID, "Mobiliyum Bildirimleri")
            manager.createNotificationChannelGroup(group)

            // 1. Fiyat Bildirimleri
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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setGroup(GROUP_ID)
            }

            // 2. Mağaza Duyuruları
            val storeChannel = NotificationChannel(
                CHANNEL_STORE,
                "Mağaza Duyuruları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Takip ettiğiniz mağazaların duyuruları"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setGroup(GROUP_ID)
            }

            // 3. Genel Bildirimler
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "Genel Bildirimler",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sistem bildirimleri ve duyurular"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setGroup(GROUP_ID)
            }

            manager.createNotificationChannels(listOf(priceChannel, storeChannel, generalChannel))

            android.util.Log.d("NotificationHelper", "✅ 3 Kanal oluşturuldu")

            // Kanal durumlarını kontrol et
            listOf(CHANNEL_PRICE, CHANNEL_STORE, CHANNEL_GENERAL).forEach { channelId ->
                val channel = manager.getNotificationChannel(channelId)
                android.util.Log.d("NotificationHelper", "  📺 $channelId - Önem: ${channel?.importance}")
            }
        }
    }

    fun sendNotification(
        context: Context,
        title: String,
        message: String,
        type: String = "general",
        relatedId: String? = null
    ) {
        android.util.Log.d("NotificationHelper", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("NotificationHelper", "🔔 sendNotification ÇAĞRILDI")
        android.util.Log.d("NotificationHelper", "  📝 Başlık: $title")
        android.util.Log.d("NotificationHelper", "  📝 Mesaj: $message")
        android.util.Log.d("NotificationHelper", "  📝 Tip: $type")
        android.util.Log.d("NotificationHelper", "  📝 İlişkili ID: $relatedId")

        // İzin Kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("NotificationHelper", "❌ BİLDİRİM İZNİ YOK!")
                android.util.Log.e("NotificationHelper", "   Ayarlar → Uygulamalar → Mobiliyum → Bildirimler")
                return
            } else {
                android.util.Log.d("NotificationHelper", "✅ Bildirim izni VAR")
            }
        }

        val channelId = when (type) {
            "price_alert" -> CHANNEL_PRICE
            "store_update" -> CHANNEL_STORE
            else -> CHANNEL_GENERAL
        }

        android.util.Log.d("NotificationHelper", "  📺 Kanal: $channelId")

        // Intent
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

        // İkon
        val icon = when (type) {
            "price_alert" -> android.R.drawable.star_big_on
            "store_update" -> android.R.drawable.ic_dialog_map
            else -> android.R.drawable.ic_dialog_info
        }

        // Bildirim
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
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (type == "price_alert") {
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
            builder.setLights(Color.GREEN, 1000, 1000)
        }

        try {
            val manager = NotificationManagerCompat.from(context)
            val notifId = notificationId.get()

            android.util.Log.d("NotificationHelper", "  🚀 Bildirim gönderiliyor (ID: $notifId)")

            manager.notify(notifId, builder.build())

            android.util.Log.d("NotificationHelper", "  ✅ BİLDİRİM GÖNDERİLDİ!")
            android.util.Log.d("NotificationHelper", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "❌ SecurityException: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "❌ Genel Hata: ${e.message}")
            e.printStackTrace()
        }
    }

    fun clearAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}