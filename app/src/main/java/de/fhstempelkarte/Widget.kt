package de.fhstempelkarte

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

/**
 * Startbildschirm-Widget (klassische RemoteViews, kein Glance): Farbe zeigt den Zustand,
 * „Einloggen“ und „Ausloggen“ gehen direkt über die Alias [Interop.EXTERN_ALIAS] von [ZeusActivity] (ohne Auswahl-Dialog) und
 * öffnen ZEUS; Tipp auf den Zustand öffnet die normale Auswahl.
 */
class StempelWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) = update(ctx, manager, ids)

    companion object {
        /** Aus [Stempel.setState] und nach Boot/Update: alle Widgets neu zeichnen. */
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, StempelWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, manager, ids)
        }

        /**
         * [action] null = normale Auswahl; sonst [InteropContract.ACTION_LOGIN]/`ACTION_LOGOUT`
         * über die geschützte Alias [Interop.EXTERN_ALIAS] – derselbe Weg wie für Chip, denn
         * `ZeusActivity` nimmt Direkt-Aktionen nur noch von dort an.
         */
        private fun launch(ctx: Context, requestCode: Int, action: String?): PendingIntent = PendingIntent.getActivity(
            ctx, requestCode,
            (if (action == null) Intent(ctx, ZeusActivity::class.java) else Intent().setClassName(ctx, Interop.EXTERN_ALIAS))
                // Eigene Action je Knopf, sonst fasst Android die PendingIntents zusammen.
                .setAction(action ?: Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val state = Store(ctx).state
            val (bg, fg) = when (state) {
                StempelState.NEUTRAL -> R.drawable.widget_bg_neutral to ctx.getColor(R.color.widget_fg)
                StempelState.NICHT_EINGELOGGT -> R.drawable.widget_bg_out to Color.WHITE
                StempelState.EINGELOGGT -> R.drawable.widget_bg_in to Color.WHITE
            }
            val views = RemoteViews(ctx.packageName, R.layout.widget_stempel).apply {
                setInt(R.id.widget_root, "setBackgroundResource", bg)
                setTextViewText(R.id.widget_state, state.label)
                listOf(R.id.widget_state, R.id.widget_btn_in, R.id.widget_btn_out).forEach { setTextColor(it, fg) }
                setOnClickPendingIntent(R.id.widget_root, launch(ctx, 0, null))
                setOnClickPendingIntent(R.id.widget_btn_in, launch(ctx, 1, InteropContract.ACTION_LOGIN))
                setOnClickPendingIntent(R.id.widget_btn_out, launch(ctx, 2, InteropContract.ACTION_LOGOUT))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
