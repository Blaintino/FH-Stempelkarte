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

    fun originalFile(ctx: Context) = File(ctx.filesDir, "original")
    fun customFile(ctx: Context, state: StempelState) = File(ctx.filesDir, "bild_${state.name.lowercase()}")
    fun hasOriginal(ctx: Context) = originalFile(ctx).length() > 0

    /** Bilddatei des Zustands: `NEUTRAL` = Original, sonst eigenes Bild statt Farbe. */
    fun imageFile(ctx: Context, state: StempelState) =
        if (state == StempelState.NEUTRAL) originalFile(ctx) else customFile(ctx, state)

    /** Kleine Vorschau (Höhe [height] px): gewähltes Bild oder erzeugtes Farbbild; null ohne Normal-Bild. */
    fun preview(ctx: Context, state: StempelState, height: Int): Bitmap? {
        val f = imageFile(ctx, state)
        if (f.length() > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= height) sample *= 2
            return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (state == StempelState.NEUTRAL) return null
        val cfg = Store(ctx).config
        val (w, h) = screenSize(ctx)
        val pw = (height * w.toFloat() / h).toInt().coerceAtLeast(1)
        val out = state == StempelState.NICHT_EINGELOGGT
        return render(pw, height, if (out) cfg.colorOut else cfg.colorIn, if (out) TEXT_OUT else TEXT_IN)
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

    private fun flags(cfg: Config): Int =
        (if (cfg.onHome) WallpaperManager.FLAG_SYSTEM else 0) or
            (if (cfg.onLock) WallpaperManager.FLAG_LOCK else 0)

    /** Setzt den Hintergrund für [state]. Blockiert ~1 s – nicht im Hauptthread. */
    fun apply(ctx: Context, state: StempelState, clearIfNoOriginal: Boolean = false): Result {
        val cfg = Store(ctx).config
        val which = flags(cfg)
        if (which == 0) return Result.OK
        val wm = WallpaperManager.getInstance(ctx)
        return runCatching {
            when (state) {
                StempelState.NEUTRAL -> {
                    val f = originalFile(ctx)
                    when {
                        f.length() > 0 -> f.inputStream().use { wm.setStream(it, null, true, which) }
                        clearIfNoOriginal -> wm.clear(which)
                        else -> return Result.NO_ORIGINAL
                    }
                }
                else -> {
                    val custom = customFile(ctx, state)
                    if (custom.length() > 0) {
                        custom.inputStream().use { wm.setStream(it, null, true, which) }
                    } else {
                        val (w, h) = screenSize(ctx)
                        val out = state == StempelState.NICHT_EINGELOGGT
                        val bmp = render(w, h, if (out) cfg.colorOut else cfg.colorIn, if (out) TEXT_OUT else TEXT_IN)
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
