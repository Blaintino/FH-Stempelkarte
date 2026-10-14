package de.fhstempelkarte

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Einstellungen (vom Nutzer gesetzt). */
data class Config(
    val window: WindowConfig = WindowConfig(),
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusM: Int = 200,
    val ssids: List<String> = emptyList(),
    val useLocation: Boolean = true,
    val useWifi: Boolean = true,
    val zeusPackage: String? = null,
    val colorOut: Int = COLOR_OUT,
    val colorIn: Int = COLOR_IN,
    val onHome: Boolean = true,
    val onLock: Boolean = true,
) {
    val hasLocation get() = lat != null && lon != null

    /** Standort-Erkennung eingeschaltet und FH-Position gesetzt. */
    val locationActive get() = useLocation && hasLocation

    /** WLAN-Erkennung eingeschaltet und mindestens eine SSID eingetragen. */
    val wifiActive get() = useWifi && ssids.isNotEmpty()

    companion object {
        const val COLOR_OUT = 0xFFC62828.toInt()
        const val COLOR_IN = 0xFF2E7D32.toInt()
    }
}

/**
 * Alles in SharedPreferences, geschrieben mit commit() statt apply(): Receiver-Prozesse können
 * direkt danach sterben. Laufzeit-Zustand und Einstellungen werden getrennt geschrieben, damit
 * die Oberfläche mit veralteter Kopie nie einen gerade ausgelösten Zustand überschreibt.
 */
class Store(ctx: Context) {
    private val p: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("stempel", Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref")
    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).commit()
        version.value = version.value + 1
    }

    var state: StempelState
        get() = runCatching { StempelState.valueOf(p.getString(K_STATE, null)!!) }
            .getOrDefault(StempelState.NEUTRAL)
        set(v) = put { putString(K_STATE, v.name) }

    var lastTriggerDay: LocalDate?
        get() = p.getString(K_LAST_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(v) = put { putString(K_LAST_DAY, v?.toString()) }

    /** Für die Anzeige: wann und wodurch zuletzt ausgelöst. */
    var lastTriggerInfo: String?
        get() = p.getString(K_LAST_INFO, null)
        set(v) = put { putString(K_LAST_INFO, v) }

    /** Ende der laufenden Erkennung (0 = keine). */
    var detectingUntil: Long
        get() = p.getLong(K_DETECTING, 0)
        set(v) = put { putLong(K_DETECTING, v) }

    var config: Config
        get() = Config(
            window = WindowConfig(
                p.getInt("start", 7 * 60),
                p.getInt("end", 9 * 60),
                p.getInt("days", WindowConfig.WORKDAYS),
            ),
            lat = p.getString("lat", null)?.toDoubleOrNull(),
            lon = p.getString("lon", null)?.toDoubleOrNull(),
            radiusM = p.getInt("radius", 200),
            ssids = p.getString("ssids", "")!!.split('\n').filter { it.isNotBlank() },
            useLocation = p.getBoolean("useLocation", true),
            useWifi = p.getBoolean("useWifi", true),
            zeusPackage = p.getString("zeus", null),
            colorOut = p.getInt("colorOut", Config.COLOR_OUT),
            colorIn = p.getInt("colorIn", Config.COLOR_IN),
            onHome = p.getBoolean("onHome", true),
            onLock = p.getBoolean("onLock", true),
        )
        set(c) = put {
            putInt("start", c.window.startMinute)
            putInt("end", c.window.endMinute)
            putInt("days", c.window.weekdayMask)
            putString("lat", c.lat?.toString())
            putString("lon", c.lon?.toString())
            putInt("radius", c.radiusM)
            putString("ssids", c.ssids.joinToString("\n"))
            putBoolean("useLocation", c.useLocation)
            putBoolean("useWifi", c.useWifi)
            putString("zeus", c.zeusPackage)
            putInt("colorOut", c.colorOut)
            putInt("colorIn", c.colorIn)
            putBoolean("onHome", c.onHome)
            putBoolean("onLock", c.onLock)
        }

    fun update(change: (Config) -> Config) {
        config = change(config)
    }

    companion object {
        private const val K_STATE = "state"
        private const val K_LAST_DAY = "lastTriggerDay"
        private const val K_LAST_INFO = "lastTriggerInfo"
        private const val K_DETECTING = "detectingUntil"

        /** Zählt jede Änderung hoch, damit die Oberfläche neu liest. */
        val version = MutableStateFlow(0)
    }
}
