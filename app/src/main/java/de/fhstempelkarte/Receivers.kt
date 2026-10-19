package de.fhstempelkarte

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.IntentCompat
import kotlin.concurrent.thread

/** Arbeit im Hintergrund-Thread; goAsync hält den Receiver bis zu ~10 s am Leben. */
private fun BroadcastReceiver.async(block: () -> Unit) {
    val pending = goAsync()
    thread {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fensterbeginn/-ende: neu planen, Erkennung starten oder stoppen. */
class WindowReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = async {
        val source = when (intent.action) {
            Scheduler.ACTION_START -> "Alarm Fensterbeginn"
            Scheduler.ACTION_END -> "Alarm Fensterende"
            else -> "Alarm 5-Min-Prüfung"
        }
        if (intent.action != Scheduler.ACTION_POLL) EventLog.add(ctx, source)
        if (Scheduler.planAll(ctx)) Detector.checkNow(ctx, source)
    }
}

/** Treffer von Geofence, Standort-Update oder WLAN. */
class DetectReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = async {
        when (intent.action) {
            Detector.ACTION_PROXIMITY -> {
                val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
                EventLog.add(ctx, "Geofence: " + if (entering) "betreten" else "verlassen")
                if (entering && Store(ctx).config.locationActive) Stempel.maybeTrigger(ctx, "Geofence")
            }
            Detector.ACTION_LOCATION ->
                IntentCompat.getParcelableExtra(intent, LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
                    ?.let { loc ->
                        val d = Detector.checkLocation(ctx, loc)
                        EventLog.add(ctx, "Standort-Update: ${d ?: "?"} m (±${loc.accuracy.toInt()} m, ${loc.provider})")
                    }
            Detector.ACTION_WIFI -> {
                val net = IntentCompat.getParcelableExtra(intent, ConnectivityManager.EXTRA_NETWORK, Network::class.java)
                val ssid = Detector.currentSsid(ctx, net)
                EventLog.add(ctx, "WLAN-Meldung: " + (ssid?.let { "„$it“" } ?: "Name NICHT lesbar"))
                val cfg = Store(ctx).config
                if (cfg.wifiActive && Ssid.matches(ssid, cfg.ssids)) Stempel.maybeTrigger(ctx, "WLAN $ssid")
            }
        }
    }
}

/** Plant nach Neustart, App-Update, Zeitänderung oder neuer Alarm-Berechtigung neu. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Exportiert (für die System-Broadcasts) – fremde Intents ignorieren.
        if (intent.action !in HANDLED) return
        async {
            EventLog.add(ctx, "System: ${intent.action?.substringAfterLast('.')}")
            if (Scheduler.planAll(ctx)) Detector.checkNow(ctx)
            runCatching { StempelWidget.updateAll(ctx) }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
