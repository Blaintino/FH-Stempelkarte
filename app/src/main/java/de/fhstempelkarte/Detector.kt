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

    /** Meldet die eingeschalteten Erkennungsarten an; Rückgabe ist eine Zeile fürs Protokoll. */
    @SuppressLint("MissingPermission")
    fun start(ctx: Context, until: Long): String {
        val cfg = Store(ctx).config
        val now = System.currentTimeMillis()
        // Erst alles abmelden: ausgeschaltete Erkennungsarten dürfen nicht weiterlaufen.
        stop(ctx)
        val loc = when {
            !cfg.locationActive -> "Standort aus"
            !hasFineLocation(ctx) -> "Standort: KEIN Recht"
            else -> {
                val lm = ctx.getSystemService(LocationManager::class.java)
                val prox = pi(ctx, ACTION_PROXIMITY, 10)
                val geo = runCatching {
                    lm.addProximityAlert(cfg.lat!!, cfg.lon!!, cfg.radiusM.toFloat(), (until - now).coerceAtLeast(1), prox)
                }
                val provider = bestProvider(lm)
                val upd = provider?.let { p ->
                    runCatching { lm.requestLocationUpdates(p, LOCATION_INTERVAL_MS, 0f, pi(ctx, ACTION_LOCATION, 11)) }
                }
                "Geofence " + (if (geo.isSuccess) "ok" else "FEHLER ${geo.exceptionOrNull()?.javaClass?.simpleName}") +
                    ", Updates über " + (provider ?: "KEINEN Anbieter") +
                    (if (upd?.isFailure == true) " (FEHLER)" else "") +
                    (if (hasBackgroundLocation(ctx)) "" else ", OHNE Hintergrund-Standort")
            }
        }
        val wifi = if (!cfg.wifiActive) {
            "WLAN aus"
        } else {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val r = runCatching {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                    pi(ctx, ACTION_WIFI, 12),
                )
            }
            "WLAN-Meldung " + if (r.isSuccess) "ok" else "FEHLER ${r.exceptionOrNull()?.javaClass?.simpleName}"
        }
        return "$loc; $wifi"
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
    fun checkNow(ctx: Context, source: String = "Prüfung") {
        val cfg = Store(ctx).config
        val parts = mutableListOf<String>()
        if (cfg.wifiActive) {
            val ssid = currentSsid(ctx)
            parts += when {
                ssid != null -> "WLAN „$ssid“"
                wifiConnected(ctx) -> "WLAN verbunden, Name NICHT lesbar"
                else -> "kein WLAN"
            }
            if (Ssid.matches(ssid, cfg.ssids)) {
                EventLog.add(ctx, "$source: ${parts.joinToString()}")
                Stempel.maybeTrigger(ctx, "WLAN $ssid")
                return
            }
        }
        if (cfg.locationActive) {
            val loc = lastFreshLocation(ctx)
            parts += if (loc == null) "kein frischer Standort" else "Standort ${distanceTo(cfg, loc)} m (±${loc.accuracy.toInt()} m)"
            EventLog.add(ctx, "$source: ${parts.joinToString()}")
            loc?.let { checkLocation(ctx, it) }
            return
        }
        EventLog.add(ctx, "$source: ${parts.joinToString()}")
    }

    private fun distanceTo(cfg: Config, loc: Location): Int {
        val d = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, cfg.lat!!, cfg.lon!!, d)
        return d[0].toInt()
    }

    /** Gibt die Entfernung zur FH in Metern zurück (null, wenn Standort-Erkennung aus). */
    fun checkLocation(ctx: Context, loc: Location): Int? {
        val cfg = Store(ctx).config
        if (!cfg.locationActive) return null
        val d = distanceTo(cfg, loc)
        if (d <= cfg.radiusM) Stempel.maybeTrigger(ctx, "Standort ($d m)")
        return d
    }

    fun wifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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
