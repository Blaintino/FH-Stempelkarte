package de.fhstempelkarte

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Zweite Meldung „Transponder aufladen“ (Wunsch des Nutzers, 2026-11-23): kommt zusammen mit
 * der Einloggen-Erinnerung (gleiche Auslösung, [Stempel.maybeTrigger]), ist aber eine eigene
 * Meldung mit eigener ID und eigenem Kanal – sie bleibt beim Einloggen stehen, bis der Nutzer
 * sie wegwischt oder antippt. Der Transponder wird an der Gebäudeaußenseite aufgeladen.
 */
object Transponder {

    const val CHANNEL = "transponder"
    const val ACTION_DONE = "de.fhstempelkarte.TRANSPONDER_ERLEDIGT"
    private const val ID = 2

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "Transponder aufladen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Zweite Meldung beim Auslösen; bleibt beim Einloggen stehen"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showIfOn(ctx: Context) {
        if (Store(ctx).config.transponderOn) runCatching { show(ctx) }
    }

    /** `cancel` + `notify` wie [Notifier] (Huawei Health). Antippen schließt sie nur. */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) {
            EventLog.add(ctx, "Transponder-Meldung NICHT gezeigt: Benachrichtigungen aus")
            return
        }
        val done = PendingIntent.getBroadcast(
            ctx, 10, Intent(ctx, TransponderReceiver::class.java).setAction(ACTION_DONE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stempel)
            .setContentTitle("Transponder aufladen")
            .setContentText("Am Gebäude den Transponder aufladen. Antippen, wenn erledigt.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(done)
            .build()
        nm.cancel(ID)
        nm.notify(ID, n)
        EventLog.add(ctx, "Transponder-Meldung gezeigt")
    }
}

/** Antippen der Transponder-Meldung: nur schließen (auch falls autoCancel nicht greift) und vermerken. */
class TransponderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Transponder.ACTION_DONE) return
        NotificationManagerCompat.from(ctx).cancel(2)
        EventLog.add(ctx, "Transponder: erledigt")
    }
}
