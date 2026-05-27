package com.longynus.churrasco

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "churrasco_channel"
        private const val CHANNEL_NAME = "Convites de Churrasco"
        private var notificationCounter = 0
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isEmpty() && remoteMessage.notification == null) {
            return
        }

        val data = remoteMessage.data
        val churrascoId = data["churrascoId"]?.takeIf { it.isNotBlank() }
            ?: data["id"]?.takeIf { it.isNotBlank() }
            ?: return

        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "Churrasco"
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: "Você foi convidado!"

        val intent = Intent(this, ChurrascoDetailsActivity::class.java).apply {
            putExtra("churrascoId", churrascoId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            churrascoId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        createNotificationChannelIfNeeded()

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSound)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(++notificationCounter, notification)
    }

    override fun onNewToken(token: String) {
        FcmTokenManager.syncToken(this, token)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Canal para convites e atualizações de churrasco"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
