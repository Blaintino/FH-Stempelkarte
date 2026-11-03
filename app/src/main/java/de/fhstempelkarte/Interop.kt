package de.fhstempelkarte

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.time.ZonedDateTime

/** Android-Seite von [InteropContract]: Änderungen an andere eigene Apps melden. */
object Interop {
    /** Name der exportierten Activity-Alias für Aktionen von außen (siehe Manifest). */
    const val EXTERN_ALIAS = "de.fhstempelkarte.ExternZeus"

    fun statusUri(ctx: Context): Uri = Uri.parse("content://${ctx.packageName}.status/${InteropContract.PATH_STATUS}")

    /** Eigene Berechtigung (Debug-Build hat eine eigene, weil anders signiert). */
    fun permission(ctx: Context) = "${ctx.packageName}.permission.INTEROP"

    /**
     * Aus [Stempel.setState] und nach Boot/Update: Provider-Beobachter benachrichtigen und den
     * expliziten Broadcast an [InteropContract.CONSUMERS] schicken. Fehler nie weiterreichen.
     */
    fun notifyChanged(ctx: Context) {
        runCatching { ctx.contentResolver.notifyChange(statusUri(ctx), null) }
        val store = Store(ctx)
        val state = store.state
        val since = store.stateSince
        val failed = InteropContract.CONSUMERS.filter { pkg ->
            runCatching {
                ctx.sendBroadcast(
                    Intent(InteropContract.ACTION_STATE_CHANGED)
                        .setPackage(pkg)
                        .putExtra(InteropContract.COL_STATE, state.name)
                        .putExtra(InteropContract.COL_SINCE, since),
                    permission(ctx),
                )
            }.isFailure
        }
        // Für die Fehlersuche (Chip-Widget zeigte keinen Wechsel): im Protokoll der Prüfliste.
        EventLog.add(
            ctx,
            "Andere Apps benachrichtigt: ${state.label}" +
                if (failed.isEmpty()) "" else " (fehlgeschlagen: ${failed.joinToString()})",
        )
    }

    /** Nur bei Aufruf über [EXTERN_ALIAS]: Aktion → Direktwert für `ZeusActivity`, sonst null. */
    fun directFromExtern(intent: Intent?): String? =
        intent?.takeIf { it.component?.className == EXTERN_ALIAS }?.let { InteropContract.directFor(it.action) }
}

/**
 * Nur lesbarer Status (eine Zeile, Spalten [InteropContract.COLUMNS]) für andere eigene Apps,
 * z. B. das Chip-Widget. Lesen nur mit der `signature`-Berechtigung (Manifest).
 */
class StatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        if (uri.pathSegments != listOf(InteropContract.PATH_STATUS)) return null
        val store = Store(ctx)
        val now = ZonedDateTime.now()
        val row = InteropContract.statusRow(
            state = store.state,
            since = store.stateSince,
            triggerInfo = store.lastTriggerInfo,
            detectingUntil = store.detectingUntil,
            colorOut = ctx.getColor(R.color.widget_out),
            colorIn = ctx.getColor(R.color.widget_in),
            workClosedMs = WorkTime.closedToday(store.workDay, now),
            workRunningSince = WorkTime.runningSince(store.state, store.stateSince, now),
        )
        // Projektion wird ignoriert: immer alle Spalten, Leser greifen per Spaltenname zu.
        return MatrixCursor(InteropContract.COLUMNS, 1).apply {
            addRow(row)
            setNotificationUri(ctx.contentResolver, uri)
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.de.fhstempelkarte.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("nur lesbar")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("nur lesbar")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("nur lesbar")
}
