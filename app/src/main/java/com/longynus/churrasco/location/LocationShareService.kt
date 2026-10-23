package com.longynus.churrasco.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.longynus.churrasco.ActiveChurrascoDetailsActivity
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ApiError
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.LocationUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que manda a localização enquanto a pessoa está "a caminho",
 * mesmo com a tela bloqueada ou outro app aberto. Começa sempre a partir da tela do
 * churrasco (app visível), por isso só precisa da permissão "durante o uso".
 * Para sozinho quando a sessão do servidor vence, quando o servidor encerra (410/403)
 * ou pelo botão "Parar" da notificação.
 */
class LocationShareService : Service() {

    data class State(val churrascoId: String? = null, val expiresAt: Long = 0, val endedMessage: String? = null) {
        val active get() = churrascoId != null
    }

    companion object {
        private const val CHANNEL_ID = "churrasco_location"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_STOP = "com.longynus.churrasco.STOP_LOCATION"
        private const val EXTRA_ID = "churrascoId"
        private const val EXTRA_SESSION = "sessionId"
        private const val EXTRA_EXPIRES = "expiresAt"
        private const val EXTRA_PLACE = "place"

        private val mutableState = MutableStateFlow(State())
        val state: StateFlow<State> = mutableState.asStateFlow()

        fun start(context: Context, churrascoId: String, sessionId: String, expiresAt: Long, place: String) {
            val intent = Intent(context, LocationShareService::class.java)
                .putExtra(EXTRA_ID, churrascoId)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_EXPIRES, expiresAt)
                .putExtra(EXTRA_PLACE, place)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LocationShareService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null
    private var expiryJob: Job? = null
    private var churrascoId: String? = null
    private var sessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Escopo próprio: o do serviço é cancelado no onDestroy e cortaria o pedido
            // no meio, deixando a localização visível para os outros.
            churrascoId?.let { id -> CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ChurrascoRepository.stopLocation(id) } }
            finish(getString(R.string.location_stopped))
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_ID)
        val session = intent?.getStringExtra(EXTRA_SESSION)
        val expiresAt = intent?.getLongExtra(EXTRA_EXPIRES, 0L) ?: 0L
        if (id == null || session == null || expiresAt <= System.currentTimeMillis()) {
            finish(null)
            return START_NOT_STICKY
        }

        churrascoId = id
        sessionId = session
        val notification = buildNotification(id, intent.getStringExtra(EXTRA_PLACE).orEmpty(), expiresAt)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        mutableState.value = State(id, expiresAt)
        startUpdates()
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(expiresAt - System.currentTimeMillis())
            finish(getString(R.string.location_session_over))
        }
        // Se o sistema matar o serviço, não volta sozinho: a sessão do servidor tem prazo.
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        callback?.let { fused.removeLocationUpdates(it) }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMinUpdateDistanceMeters(20f)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { send(it.latitude, it.longitude) }
            }
        }
        callback = cb
        runCatching { fused.requestLocationUpdates(request, cb, Looper.getMainLooper()) }
            .onFailure { finish(getString(R.string.location_permission_needed)) }
        runCatching { fused.lastLocation.addOnSuccessListener { it?.let { l -> send(l.latitude, l.longitude) } } }
    }

    private fun send(latitude: Double, longitude: Double) {
        val id = churrascoId ?: return
        val session = sessionId ?: return
        scope.launch {
            ChurrascoRepository.shareLocation(id, LocationUpdateRequest(latitude, longitude, session)).onFailure { error ->
                val api = error as? ApiError
                // 410: sessão encerrada (por exemplo, em outro aparelho); 403: a janela do evento fechou.
                if (api?.status == 410 || api?.status == 403) {
                    finish(api.serverMessage ?: getString(R.string.location_session_over))
                }
            }
        }
    }

    private fun finish(message: String?) {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        expiryJob?.cancel()
        mutableState.value = State(endedMessage = message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        callback?.let { fused.removeLocationUpdates(it) }
        if (mutableState.value.active) mutableState.value = State()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(id: String, place: String, expiresAt: Long): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.location_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, LocationShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val until = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date(expiresAt))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.location_notification_title))
            .setContentText(getString(R.string.location_notification_body, place, until))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.location_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
