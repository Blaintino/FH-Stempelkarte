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
        if (Scheduler.planAll(ctx)) Detector.checkNow(ctx)
    }
}

/** Treffer von Geofence, Standort-Update oder WLAN. */
class DetectReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = async {
        when (intent.action) {
            Detector.ACTION_PROXIMITY ->
                if (intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false) &&
                    Store(ctx).config.locationActive
                ) {
                    Stempel.maybeTrigger(ctx, "Geofence")
                }
            Detector.ACTION_LOCATION ->
                IntentCompat.getParcelableExtra(intent, LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
                    ?.let { Detector.checkLocation(ctx, it) }
            Detector.ACTION_WIFI -> {
                val net = IntentCompat.getParcelableExtra(intent, ConnectivityManager.EXTRA_NETWORK, Network::class.java)
                val ssid = Detector.currentSsid(ctx, net)
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
            if (Scheduler.planAll(ctx)) Detector.checkNow(ctx)
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
