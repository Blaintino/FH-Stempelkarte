package de.fhstempelkarte

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

object Scheduler {

    const val ACTION_START = "de.fhstempelkarte.WINDOW_START"
    const val ACTION_END = "de.fhstempelkarte.WINDOW_END"

    private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, WindowReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Plant Fensterbeginn und -ende neu und startet/stoppt die Erkennung passend zu „jetzt“.
     * Idempotent: nach Neustart, Update, Zeitänderung, jedem Alarm und jedem App-Start.
     * Gibt zurück, ob gerade erkannt wird (dann sollte der Aufrufer [Detector.checkNow]
     * im Hintergrund aufrufen).
     *
     * Fensterbeginn mit `setAlarmClock` (Wecker-Alarm, pünktlich auch im Tiefschlaf; zeigt
     * ein Wecker-Symbol), Fallback ohne Exakt-Berechtigung wie in Quälgeist. Das Ende muss
     * nicht auf die Sekunde stimmen: exakt-und-idle, damit kein zweiter „Wecker“ erscheint.
     */
    fun planAll(ctx: Context): Boolean {
        val store = Store(ctx)
        val cfg = store.config.window
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        val startPi = pi(ctx, ACTION_START, 1)
        val endPi = pi(ctx, ACTION_END, 2)
        val valid = TimeWindow.isValid(cfg)

        val start = if (valid) TimeWindow.nextStart(now, cfg) else null
        if (start == null) {
            am.cancel(startPi)
        } else if (canScheduleExact(ctx)) {
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(start.toInstant().toEpochMilli(), show), startPi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, start.toInstant().toEpochMilli(), startPi)
        }

        val end = if (valid) TimeWindow.nextEnd(now, cfg) else null
        if (end == null) {
            am.cancel(endPi)
        } else if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end.toInstant().toEpochMilli(), endPi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end.toInstant().toEpochMilli(), endPi)
        }

        val currentEnd = if (valid) TimeWindow.currentEnd(now, cfg) else null
        val detect = currentEnd != null &&
            StateLogic.shouldTrigger(store.state, store.lastTriggerDay, now, cfg)
        if (detect) {
            val until = currentEnd!!.toInstant().toEpochMilli()
            Detector.start(ctx, until)
            store.detectingUntil = until
        } else {
            Detector.stop(ctx)
            if (store.detectingUntil != 0L) store.detectingUntil = 0
        }
        return detect
    }
}
