package de.fhstempelkarte

/** SSID-Vergleich ohne Android – testbar in `SsidTest`. */
object Ssid {
    const val UNKNOWN = "<unknown ssid>"

    /** Entfernt Android-Anführungszeichen; unbekannte/leere SSID → null. */
    fun normalize(raw: String?): String? {
        var s = raw?.trim() ?: return null
        if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s = s.substring(1, s.length - 1)
        if (s.isEmpty() || s.equals(UNKNOWN, ignoreCase = true) || s == "0x") return null
        return s
    }

    /** Eingabe aus den Einstellungen: durch Komma, Semikolon oder Zeilenumbruch getrennt. */
    fun parseList(text: String): List<String> =
        text.split(',', ';', '\n').mapNotNull(::normalize).distinctBy { it.lowercase() }

    fun matches(current: String?, configured: Collection<String>): Boolean {
        val c = normalize(current) ?: return false
        return configured.any { normalize(it)?.equals(c, ignoreCase = true) == true }
    }
}
