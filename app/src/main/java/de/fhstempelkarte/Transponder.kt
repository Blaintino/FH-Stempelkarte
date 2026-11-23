package de.fhstempelkarte

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Zweiter, unabhängiger Hinweis: „Transponder aufladen“ (Wunsch des Nutzers, 2026-11-23).
 * Eigene Uhrzeit/Wochentage, eigener Alarm, eigener Kanal und eigene Meldung. Berührt weder
 * den Stempel-Zustand noch Hintergrund oder Erkennung.
 */
object Transponder {

    const val CHANNEL = "transponder"
    const val ACTION = "de.fhstempelkarte.TRANSPONDER"
    private const val ID = 2
    private val LOG_TIME = DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", Locale.GERMAN)

    private fun alarmPi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 10, Intent(ctx, TransponderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun next(cfg: Config, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? =
        if (cfg.transponderOn) TransponderPlan.next(now, cfg.transponderMinute, cfg.transponderDays) else null

    /** Idempotent; aus [Scheduler.planAll] und nach jeder Erinnerung. */
    fun plan(ctx: Context) {
        val store = Store(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmPi(ctx)
        val at = next(store.config)
        if (at == null) {
            am.cancel(pi)
        } else {
            val ms = at.toInstant().toEpochMilli()
            if (Scheduler.canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        }
        val ms = at?.toInstant()?.toEpochMilli() ?: 0L
        if (ms != store.plannedTransponder) {
            store.plannedTransponder = ms
            EventLog.add(
                ctx,
                if (at == null) "Keine Transponder-Erinnerung geplant" else "Transponder-Erinnerung geplant: ${at.format(LOG_TIME)}",
            )
        }
    }

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "Transponder aufladen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Erinnerung, den FH-Transponder aufzuladen"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Antippen schließt die Meldung und öffnet die Einstellungen. `cancel` + `notify` wie [Notifier]. */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) {
            EventLog.add(ctx, "Transponder-Meldung NICHT gezeigt: Benachrichtigungen aus")
            return
        }
        val open = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stempel)
            .setContentTitle("Transponder aufladen")
            .setContentText("Denk dran, deinen Transponder aufzuladen.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.cancel(ID)
        nm.notify(ID, n)
        EventLog.add(ctx, "Transponder-Meldung gezeigt")
    }
}

/** Alarm der Transponder-Erinnerung: Meldung zeigen und die nächste planen. */
class TransponderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Transponder.ACTION) return
        val pending = goAsync()
        thread {
            try {
                if (Store(ctx).config.transponderOn) runCatching { Transponder.show(ctx) }
                Transponder.plan(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
