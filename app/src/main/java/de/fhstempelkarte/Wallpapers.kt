package de.fhstempelkarte

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.DisplayMetrics
import android.view.WindowManager
import android.os.Build
import java.io.File

/**
 * Hintergrundbilder. Das Original wird nicht vom System gelesen (bräuchte ab Android 13
 * Speicherrechte), sondern vom Nutzer einmal gewählt und nach `files/` kopiert.
 */
object Wallpapers {

    enum class Result { OK, NO_ORIGINAL, FAILED }

    const val TEXT_OUT = "NICHT\nEINGELOGGT"
    const val TEXT_IN = "EINGELOGGT"

    /** Bilddateien: Startbildschirm unter den alten Namen, Sperrbildschirm mit Endung `_sperre`. */
    private fun suffix(screen: Screen) = if (screen == Screen.LOCK) "_sperre" else ""

    fun originalFile(ctx: Context, screen: Screen = Screen.HOME) = File(ctx.filesDir, "original" + suffix(screen))

    /** Bilddatei für (Zustand, Bildschirm): `NEUTRAL` = Original, sonst eigenes Bild statt Farbe. */
    fun imageFile(ctx: Context, state: StempelState, screen: Screen): File =
        if (state == StempelState.NEUTRAL) {
            originalFile(ctx, screen)
        } else {
            File(ctx.filesDir, "bild_${state.name.lowercase()}" + suffix(screen))
        }

    fun hasImage(ctx: Context, state: StempelState, screen: Screen) = imageFile(ctx, state, screen).length() > 0

    fun source(ctx: Context, cfg: Config, state: StempelState, screen: Screen): WallpaperSource =
        WallpaperPlan.source(cfg, state, screen) { hasImage(ctx, state, it) }

    private fun enabledScreens(cfg: Config) =
        listOfNotNull(Screen.HOME.takeIf { cfg.onHome }, Screen.LOCK.takeIf { cfg.onLock })

    /** Kann Ausloggen für alle eingeschalteten Bildschirme den normalen Hintergrund wiederherstellen? */
    fun hasOriginal(ctx: Context): Boolean {
        val cfg = Store(ctx).config
        return enabledScreens(cfg).none { source(ctx, cfg, StempelState.NEUTRAL, it) == WallpaperSource.Missing }
    }

    /** Kleine Vorschau (Höhe [height] px): gewähltes Bild oder erzeugtes Farbbild; null ohne Normal-Bild. */
    fun preview(ctx: Context, state: StempelState, screen: Screen, height: Int): Bitmap? {
        val cfg = Store(ctx).config
        return when (val src = source(ctx, cfg, state, screen)) {
            WallpaperSource.Missing -> null
            is WallpaperSource.Image -> {
                val f = imageFile(ctx, state, src.screen)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                var sample = 1
                while (bounds.outHeight / (sample * 2) >= height) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            is WallpaperSource.Generated -> {
                val (w, h) = screenSize(ctx)
                val pw = (height * w.toFloat() / h).toInt().coerceAtLeast(1)
                render(pw, height, src.color, text(state))
            }
        }
    }

    /** Kopiert ein gewähltes Bild (Photo Picker) in den internen Speicher. */
    fun import(ctx: Context, uri: Uri, target: File): Boolean = runCatching {
        val tmp = File(target.path + ".tmp")
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        check(tmp.length() > 0)
        tmp.renameTo(target)
    }.getOrDefault(false)

    private fun text(state: StempelState) = if (state == StempelState.EINGELOGGT) TEXT_IN else TEXT_OUT

    private fun flag(screen: Screen) =
        if (screen == Screen.HOME) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK

    /**
     * Setzt den Hintergrund für [state] auf Start- und/oder Sperrbildschirm. Blockiert ~1 s –
     * nicht im Hauptthread. Gleiche Quelle für beide: ein Aufruf mit beiden Flags. Sonst erst
     * Start, dann Sperre (Start allein setzen kann eine geteilte Sperre mitändern).
     */
    fun apply(ctx: Context, state: StempelState, clearIfNoOriginal: Boolean = false): Result {
        val cfg = Store(ctx).config
        val screens = enabledScreens(cfg)
        if (screens.isEmpty()) return Result.OK
        val sources = screens.associateWith { source(ctx, cfg, state, it) }
        if (!clearIfNoOriginal && WallpaperSource.Missing in sources.values) return Result.NO_ORIGINAL
        val wm = WallpaperManager.getInstance(ctx)
        val groups: List<Pair<WallpaperSource, Int>> =
            if (sources.values.distinct().size == 1) {
                listOf(sources.values.first() to screens.fold(0) { acc, s -> acc or flag(s) })
            } else {
                screens.map { sources.getValue(it) to flag(it) }
            }
        return runCatching {
            for ((src, which) in groups) {
                when (src) {
                    WallpaperSource.Missing -> wm.clear(which)
                    is WallpaperSource.Image ->
                        imageFile(ctx, state, src.screen).inputStream().use { wm.setStream(it, null, true, which) }
                    is WallpaperSource.Generated -> {
                        val (w, h) = screenSize(ctx)
                        val bmp = render(w, h, src.color, text(state))
                        wm.setBitmap(bmp, null, true, which)
                        bmp.recycle()
                    }
                }
            }
            Result.OK
        }.getOrDefault(Result.FAILED)
    }

    /** Hochkant-Bildschirmgröße in Pixeln. */
    @Suppress("DEPRECATION")
    fun screenSize(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return minOf(w, h).coerceAtLeast(320) to maxOf(w, h).coerceAtLeast(480)
    }

    /** Einfarbiges Bild mit großem Text; Anordnung aus [WallpaperLayout]. */
    fun render(width: Int, height: Int, color: Int, text: String): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(color)
        val layout = WallpaperLayout.compute(width, height, text)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = layout.textSize
        }
        // Schätzung aus WallpaperLayout nachmessen, damit nichts über den Rand läuft.
        val widest = layout.lines.maxOf { paint.measureText(it) }
        if (widest > width * 0.9f) paint.textSize *= width * 0.9f / widest
        layout.lines.forEachIndexed { i, line -> canvas.drawText(line, layout.centerX, layout.baselines[i], paint) }
        return bmp
    }
}
