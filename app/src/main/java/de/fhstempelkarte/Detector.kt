package de.fhstempelkarte

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Erkennung „an der FH“ ohne Google-Dienste, nur im Zeitfenster aktiv:
 * - Geofence über `LocationManager.addProximityAlert` (Framework, kein Play Services),
 *   dazu Standort-Updates alle 2 Min vom besten Anbieter (Fused, sonst Netzwerk, sonst GPS)
 *   mit eigener Entfernungsprüfung – falls der Proximity-Alert auf einem Gerät nicht feuert.
 * - WLAN über `registerNetworkCallback(…, PendingIntent)`, SSID-Prüfung im Receiver.
 * Der erste Treffer gewinnt ([Stempel.maybeTrigger] ist synchronisiert und tagesgesperrt).
 */
object Detector {

    const val ACTION_PROXIMITY = "de.fhstempelkarte.PROXIMITY"
    const val ACTION_LOCATION = "de.fhstempelkarte.LOCATION"
    const val ACTION_WIFI = "de.fhstempelkarte.WIFI"

    private const val LOCATION_INTERVAL_MS = 120_000L

    /** Muss veränderbar sein: das System hängt Extras an (Eintritt, Standort, Netzwerk). */
    private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, DetectReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )

    fun hasFineLocation(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(ctx: Context) =
        Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Bester verfügbarer Standortanbieter, Fused vor Netzwerk vor GPS. */
    fun bestProvider(lm: LocationManager): String? {
        val all = lm.allProviders
        return listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in all && runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context, until: Long) {
        val cfg = Store(ctx).config
        val now = System.currentTimeMillis()
        // Erst alles abmelden: ausgeschaltete Erkennungsarten dürfen nicht weiterlaufen.
        stop(ctx)
        if (cfg.locationActive && hasFineLocation(ctx)) {
            val lm = ctx.getSystemService(LocationManager::class.java)
            val prox = pi(ctx, ACTION_PROXIMITY, 10)
            runCatching {
                lm.addProximityAlert(cfg.lat!!, cfg.lon!!, cfg.radiusM.toFloat(), (until - now).coerceAtLeast(1), prox)
            }
            bestProvider(lm)?.let { p ->
                runCatching { lm.requestLocationUpdates(p, LOCATION_INTERVAL_MS, 0f, pi(ctx, ACTION_LOCATION, 11)) }
            }
        }
        if (cfg.wifiActive) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val wifi = pi(ctx, ACTION_WIFI, 12)
            runCatching {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                    wifi,
                )
            }
        }
    }

    /** Zum Fensterende oder nach dem Auslösen: alles abmelden (Akku). */
    fun stop(ctx: Context) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        runCatching { lm.removeProximityAlert(pi(ctx, ACTION_PROXIMITY, 10)) }
        runCatching { lm.removeUpdates(pi(ctx, ACTION_LOCATION, 11)) }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        runCatching { cm.unregisterNetworkCallback(pi(ctx, ACTION_WIFI, 12)) }
    }

    /** Beim Fensterstart: Schon im FH-WLAN oder (frischer Standort) schon dort? Blockiert. */
    fun checkNow(ctx: Context) {
        val cfg = Store(ctx).config
        val ssid = currentSsid(ctx)
        if (cfg.wifiActive && Ssid.matches(ssid, cfg.ssids)) {
            Stempel.maybeTrigger(ctx, "WLAN $ssid")
            return
        }
        lastFreshLocation(ctx)?.let { checkLocation(ctx, it) }
    }

    fun checkLocation(ctx: Context, loc: Location) {
        val cfg = Store(ctx).config
        if (!cfg.locationActive) return
        val d = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, cfg.lat!!, cfg.lon!!, d)
        if (d[0] <= cfg.radiusM) Stempel.maybeTrigger(ctx, "Standort (${d[0].toInt()} m)")
    }

    @SuppressLint("MissingPermission")
    fun lastFreshLocation(ctx: Context, maxAgeMs: Long = 5 * 60_000L): Location? {
        if (!hasFineLocation(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        val now = System.currentTimeMillis()
        return lm.allProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time in 0..maxAgeMs }
            .minByOrNull { it.accuracy }
    }

    /**
     * Aktuelle SSID oder null. Ab Android 12 schwärzt `getNetworkCapabilities` die SSID oft
     * („<unknown ssid>“), dann über den (veralteten) `WifiManager` – braucht Standort-Recht.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun currentSsid(ctx: Context, network: Network? = null): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= 29) {
            for (n in listOfNotNull(network, cm.activeNetwork)) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                Ssid.normalize((caps.transportInfo as? WifiInfo)?.ssid)?.let { return it }
            }
        }
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return Ssid.normalize(runCatching { wm.connectionInfo?.ssid }.getOrNull())
    }
}
