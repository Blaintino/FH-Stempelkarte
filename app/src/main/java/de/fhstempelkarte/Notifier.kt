package de.fhstempelkarte

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {

    const val CHANNEL = "einstempeln"
    private const val ID = 1

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(CHANNEL, "Einstempeln", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Erinnerung, wenn du an der FH bist und noch nicht eingestempelt hast"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Antippen führt zur Auswahl Anmelden/Abmelden, danach ZEUS. */
    private fun choiceIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 1,
        Intent(ctx, ZeusActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Eine Meldung, keine Wiederholung (der Hintergrund ist die eigentliche Erinnerung).
     * Nicht `ongoing` und `cancel` + `notify`: Huawei Health spiegelt sonst oft nicht.
     * Kein Vollbild-Intent, keine Knöpfe: Antippen öffnet die Auswahl.
     */
    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stempel)
            .setContentTitle("Einstempeln nicht vergessen")
            .setContentText("Du bist an der FH und noch nicht in ZEUS eingeloggt.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(choiceIntent(ctx))
            .build()
        nm.cancel(ID)
        nm.notify(ID, n)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}
