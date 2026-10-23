package com.longynus.churrasco

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.longynus.churrasco.friends.FriendsActivity
import com.longynus.churrasco.reminders.ReminderScheduler

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private companion object {
        const val CHANNEL_ID = "churrasco_channel"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val type = data["type"].orEmpty()
        val churrascoId = data["churrascoId"]?.takeIf { it.isNotBlank() }
        val title = remoteMessage.notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body ?: data["body"].orEmpty()

        // Horário ou local mudou, ou o churrasco foi cancelado: os lembretes acompanham.
        if (type == "event_updated" || type == "event_cancelled") ReminderScheduler.refresh(this)

        val target = when {
            type == "friend_request" -> Intent(this, FriendsActivity::class.java)
            type == "event_cancelled" || churrascoId == null -> Intent(this, MainActivity::class.java)
            else -> Intent(this, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", churrascoId)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = PendingIntent.getActivity(
            this, (churrascoId ?: type).hashCode(), target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            // Mesma conversa ou evento substitui a notificação anterior em vez de empilhar.
            getSystemService(NotificationManager::class.java).notify("$type:${churrascoId.orEmpty()}".hashCode(), notification)
        }
    }

    override fun onNewToken(token: String) = FcmTokenManager.syncToken(this, token)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.push_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = getString(R.string.push_channel_description)
                }
            )
        }
    }
}
