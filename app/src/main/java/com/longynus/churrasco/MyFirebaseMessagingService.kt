package com.longynus.churrasco

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Log para depuração
        Log.d("FCM", "Mensagem recebida de: ${remoteMessage.from}")

        // Verificar dados da mensagem
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Dados da mensagem: ${remoteMessage.data}")

            val title = remoteMessage.data["title"] ?: "Sem título"
            val body = remoteMessage.data["body"] ?: "Sem mensagem"
            val data = remoteMessage.data // Inclui todos os dados
            Log.d("FCM", "Dados recebidos: $data")
            Log.d("FCM", "Fornecidos recebidos: ${data["fornecidos"]}")

            // Exibir notificação usando dados
            showNotification(title, body, data)
        }
        // Verificar se a mensagem contém uma notificação
        remoteMessage.notification?.let {
            val title = it.title ?: "Sem título"
            val body = it.body ?: "Sem mensagem"

            Log.d("FCM", "Título da mensagem: $title")
            Log.d("FCM", "Corpo da mensagem: $body")

            // Verificar se há dados adicionais
            val data = remoteMessage.data.ifEmpty { emptyMap() }

            // Exibir notificação usando a notificação recebida
            showNotification(title, body, data)
        }

    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Log do novo token
        Log.d("FCM", "Novo token gerado: $token")

        // Aqui você pode enviar o token para o servidor, se necessário
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, message: String, data: Map<String, String>) {
        val channelId = "default_channel"
        val notificationId = System.currentTimeMillis().toInt()
        val fornecidosList = data["fornecidos"]?.split(",")?.map { it.trim() } ?: emptyList()
        Log.d("FCM", "Itens fornecidos no intent: $fornecidosList")

        // Cria um Intent para abrir a tela de detalhes do churrasco
        val intent = Intent(this, ChurrascoDetailsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("churrascoDate", data["churrascoDate"] ?: "Data não definida")
            putExtra("hora", data["hora"] ?: "Hora não definida")
            putExtra("local", data["local"] ?: "Local não definido")
            putExtra("evento", data["evento"] ?: "Evento não definido")
            putStringArrayListExtra("fornecidos", ArrayList(fornecidosList))
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cria o canal de notificação (para Android 8.0 ou superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificações Padrão",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificações padrão"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Constrói e exibe a notificação
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }
}