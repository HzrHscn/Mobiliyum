package com.example.mobiliyum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "price_alert_channel"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Fiyat Alarmları"
            val descriptionText = "Favori ürünlerinizdeki indirim bildirimleri"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendPriceDropNotification(context: Context, productName: String, oldPrice: Double, newPrice: Double) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("İndirim Alarmı! 🔥")
                .setContentText("$productName fiyatı düştü! ${oldPrice.toInt()}₺ -> ${newPrice.toInt()}₺")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) { }
    }
}