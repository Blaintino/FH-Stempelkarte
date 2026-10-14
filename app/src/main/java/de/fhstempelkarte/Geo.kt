package de.fhstempelkarte

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper

object Geo {
    /** Für „Aktuellen Standort übernehmen“: frischer letzter Standort, sonst eine neue Messung. Ruft [done] im Hauptthread. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun currentLocation(ctx: Context, done: (Location?) -> Unit) {
        Detector.lastFreshLocation(ctx, maxAgeMs = 60_000L)?.let { return done(it) }
        if (!Detector.hasFineLocation(ctx)) return done(null)
        val lm = ctx.getSystemService(LocationManager::class.java)
        // Für einen festen Ort lieber GPS, sonst der beste vorhandene Anbieter.
        val provider = LocationManager.GPS_PROVIDER.takeIf { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: Detector.bestProvider(lm) ?: return done(null)
        if (Build.VERSION.SDK_INT >= 30) {
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { done(it) }
        } else {
            lm.requestSingleUpdate(provider, LocationListener { done(it) }, Looper.getMainLooper())
        }
    }
}
