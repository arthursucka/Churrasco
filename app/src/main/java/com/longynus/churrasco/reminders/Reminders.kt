package com.longynus.churrasco.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.longynus.churrasco.ActiveChurrascoDetailsActivity
import com.longynus.churrasco.AuthSession
import com.longynus.churrasco.R
import com.longynus.churrasco.data.ChurrascoRepository
import com.longynus.churrasco.data.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lembretes locais 24 h e 2 h antes de cada churrasco em que a pessoa organizou ou
 * confirmou presença. Ficam no próprio celular, então funcionam mesmo se o servidor
 * estiver dormindo. Reagendados ao abrir as listas, ao editar e ao ligar o aparelho.
 */
object ReminderScheduler {
    private const val PREFS = "reminders"
    private const val KEY_SCHEDULED = "scheduled"
    const val CHANNEL_ID = "churrasco_reminders"

    /** Antecedências em minutos. */
    val OFFSETS = listOf(24 * 60, 2 * 60)

    /** Momentos (epoch ms) em que os avisos devem tocar; só os que ainda estão no futuro. */
    fun triggerTimes(startsAt: Long, now: Long): List<Pair<Int, Long>> =
        OFFSETS.map { it to startsAt - it * 60_000L }.filter { it.second > now }

    fun sync(context: Context, events: List<Event>) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val wanted = mutableSetOf<String>()

        for (event in events) {
            val startsAt = event.eventStartsAt ?: continue
            if (!event.isMember) continue
            for ((offset, at) in triggerTimes(startsAt, now)) {
                val key = "${event.id}:$offset"
                wanted += key
                val intent = pendingIntent(app, event, offset)
                // Inexato de propósito: não exige a permissão de alarme exato e respeita a bateria.
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        for (stale in previous - wanted) {
            val (id, offset) = stale.split(":").let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
            alarm.cancel(pendingIntent(app, Event(id = id), offset))
        }
        prefs.edit { putStringSet(KEY_SCHEDULED, wanted) }
    }

    /** Busca os churrascos ativos e reagenda (usado ao ligar o celular). */
    fun refresh(context: Context) {
        if (!AuthSession.isReady(context)) return
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ChurrascoRepository.events("active").onSuccess { sync(app, it) }
        }
    }

    fun clear(context: Context) = sync(context, emptyList())

    private fun pendingIntent(context: Context, event: Event, offset: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.longynus.churrasco.REMINDER"
            putExtra("churrascoId", event.id)
            putExtra("offset", offset)
            putExtra("local", event.local)
            putExtra("hora", event.hora)
        }
        return PendingIntent.getBroadcast(
            context, "${event.id}:$offset".hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("churrascoId") ?: return
        val offset = intent.getIntExtra("offset", 0)
        val local = intent.getStringExtra("local").orEmpty()
        val hora = intent.getStringExtra("hora").orEmpty()
        ReminderScheduler.ensureChannel(context)

        val title = context.getString(if (offset >= 24 * 60) R.string.reminder_title_tomorrow else R.string.reminder_title_soon)
        val body = context.getString(R.string.reminder_body, hora, local)
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, ActiveChurrascoDetailsActivity::class.java).putExtra("churrascoId", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify("$id:$offset".hashCode(), notification)
        }
    }
}

/** Alarmes somem quando o celular reinicia; este receiver os recria. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.refresh(context)
        }
    }
}
