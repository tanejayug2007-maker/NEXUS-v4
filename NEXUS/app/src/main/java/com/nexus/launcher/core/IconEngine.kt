package com.nexus.launcher.core

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything that changes how an icon bitmap looks; part of the cache key. */
data class IconSpec(
    val style: IconStyle,
    val shape: IconShape,
    val sizePx: Int,
    val accent: Int,
    val accent2: Int,
    val panel: Int,
    val glow: Float,
    val outline: Float,
    val tint: Boolean,
    val pack: String,
) {
    val cacheKey: String get() = "$style|$shape|$sizePx|$accent|$accent2|$panel|${(glow * 20).toInt()}|${(outline * 10).toInt()}|$tint|$pack"
}

/** Shape geometry shared by bitmap rendering (android.graphics) and Compose clipping. */
object IconGeometry {
    fun path(shape: IconShape, r: RectF): Path = Path().apply {
        val cx = r.centerX()
        val cy = r.centerY()
        val w = r.width()
        val h = r.height()
        when (shape) {
            IconShape.CIRCLE -> addOval(r, Path.Direction.CW)
            IconShape.SQUARE -> addRoundRect(r, w * 0.08f, h * 0.08f, Path.Direction.CW)
            IconShape.SQUIRCLE -> addRoundRect(r, w * 0.34f, h * 0.34f, Path.Direction.CW)
            IconShape.HEXAGON -> polygon(this, 6, cx, cy, min(w, h) / 2f, -90f)
            IconShape.DIAMOND -> polygon(this, 4, cx, cy, min(w, h) / 2f, -90f)
            IconShape.OCTAGON -> {
                val c = w * 0.25f
                moveTo(r.left + c, r.top); lineTo(r.right - c, r.top); lineTo(r.right, r.top + c)
                lineTo(r.right, r.bottom - c); lineTo(r.right - c, r.bottom); lineTo(r.left + c, r.bottom)
                lineTo(r.left, r.bottom - c); lineTo(r.left, r.top + c); close()
            }
        }
    }

    private fun polygon(p: Path, n: Int, cx: Float, cy: Float, radius: Float, startDeg: Float) {
        for (i in 0 until n) {
            val a = Math.toRadians((startDeg + i * 360f / n).toDouble())
            val x = cx + radius * cos(a).toFloat()
            val y = cy + radius * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }
}

/**
 * Renders the NEXUS icon system. Icons are composed once on a background thread into
 * bitmaps and cached (LRU, sized by bytes) so scrolling and animations never decode.
 */
class IconEngine(private val context: Context) {

    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtMost(48 * 1024 * 1024)

    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    @Volatile private var pack: IconPack? = null

    private fun key(app: AppInfo, spec: IconSpec, overrideUri: String?) =
        "${app.key.id}|${spec.cacheKey}|${overrideUri.orEmpty()}"

    fun cached(app: AppInfo, spec: IconSpec, overrideUri: String?): ImageBitmap? = cache.get(key(app, spec, overrideUri))

    /** Renders in progress, so concurrent requests for the same icon share one render. */
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap>>()
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Cached bitmap, or one render shared by every caller asking for the same key at the same time
     * (e.g. the background prewarm and the visible icon on a cold start). A caller that is cancelled
     * doesn't cancel the render: the result still lands in the cache for the next request.
     */
    suspend fun load(app: AppInfo, spec: IconSpec, overrideUri: String?): ImageBitmap {
        val k = key(app, spec, overrideUri)
        cache.get(k)?.let { return it }
        val job = inFlight.computeIfAbsent(k) {
            renderScope.async(start = CoroutineStart.LAZY) {
                try {
                    render(app, spec, overrideUri).also { cache.put(k, it) }
                } finally {
                    inFlight.remove(k)
                }
            }
        }
        job.start()
        return job.await()
    }

    /** Plain bitmap for arbitrary drawables (shortcut icons, widget previews, notification apps). */
    suspend fun plain(drawable: Drawable?, sizePx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        drawable ?: return@withContext null
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        drawFull(c, drawable, RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
        b.asImageBitmap()
    }

    fun invalidatePackage(pkg: String) {
        cache.snapshot().keys.filter { it.startsWith("$pkg/") }.forEach { cache.remove(it) }
    }

    fun clear() = cache.evictAll()

    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) cache.evictAll()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) cache.trimToSize(cache.maxSize() / 2)
    }

    private fun packFor(pkg: String): IconPack? {
        if (pkg.isEmpty()) return null
        val current = pack
        if (current != null && current.packageName == pkg) return current
        return IconPack(context, pkg).also { pack = it }
    }

    private fun baseDrawable(app: AppInfo, spec: IconSpec, overrideUri: String?): Drawable? {
        overrideUri?.let { loadOverride(it, spec.sizePx)?.let { d -> return d } }
        packFor(spec.pack)?.drawableFor(app.component)?.let { return it }
        return runCatching { app.info.getIcon(densityDpi) }.getOrNull()
    }

    private fun render(app: AppInfo, spec: IconSpec, overrideUri: String?): ImageBitmap {
        val s = spec.sizePx.toFloat()
        val bmp = Bitmap.createBitmap(spec.sizePx, spec.sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = s * 0.09f
        val frame = RectF(pad, pad, s - pad, s - pad)
        val shapePath = IconGeometry.path(spec.shape, frame)
        val src = baseDrawable(app, spec, overrideUri)
        val fromPackOrOverride = overrideUri != null || (spec.pack.isNotEmpty() && src !is AdaptiveIconDrawable)

        if (src == null) {
            drawFallback(c, app.label, frame, shapePath, spec)
            return bmp.asImageBitmap()
        }

        when (spec.style) {
            IconStyle.ORIGINAL -> {
                c.save(); c.clipPath(shapePath)
                if (src !is AdaptiveIconDrawable) c.drawPath(shapePath, fill(spec.panel))
                drawFull(c, src, if (src is AdaptiveIconDrawable) frame else inset(frame, 0.08f))
                c.restore()
                if (spec.tint) strokeShape(c, shapePath, spec, s, alpha = 160)
            }

            IconStyle.HOLO_FRAME -> {
                c.drawPath(shapePath, plate(spec, frame, 0.92f))
                val inner = inset(frame, 0.19f)
                val innerPath = IconGeometry.path(spec.shape, inner)
                val layer = c.saveLayer(RectF(0f, 0f, s, s), saturationPaint(if (spec.tint) 0.25f else 0.8f))
                c.clipPath(innerPath)
                drawFull(c, src, if (src is AdaptiveIconDrawable || fromPackOrOverride) inner else inset(inner, 0.04f))
                c.restoreToCount(layer)
                if (spec.tint) tintLayer(c, spec.accent, innerPath, 70)
                strokeShape(c, shapePath, spec, s)
                brackets(c, frame, spec, s)
            }

            IconStyle.GLASS -> {
                c.drawPath(shapePath, glassPlate(frame))
                val inner = inset(frame, 0.16f)
                val glyph = glyphLayer(src)
                if (glyph != null) drawLayer(c, glyph, inner)
                else drawFull(c, src, inset(inner, 0.06f))
                val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.016f; color = Color.argb(110, 255, 255, 255) }
                c.drawPath(shapePath, hl)
            }

            IconStyle.GLYPH -> {
                c.drawPath(shapePath, plate(spec, frame, 0.96f))
                val inner = inset(frame, 0.17f)
                val glyph = glyphLayer(src)
                val layer = c.saveLayer(RectF(0f, 0f, s, s), null)
                if (glyph != null) drawLayer(c, glyph, inner) else {
                    val gl = c.saveLayer(RectF(0f, 0f, s, s), saturationPaint(0f))
                    drawFull(c, src, inset(inner, 0.08f)); c.restoreToCount(gl)
                }
                c.drawColor(spec.accent, PorterDuff.Mode.SRC_IN)
                c.restoreToCount(layer)
                strokeShape(c, shapePath, spec, s, alpha = 150)
            }
        }
        return bmp.asImageBitmap()
    }

    // ---- drawing helpers -------------------------------------------------------------------

    private fun inset(r: RectF, f: Float) = RectF(r).apply { inset(r.width() * f, r.height() * f) }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun plate(spec: IconSpec, r: RectF, alpha: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val top = blend(spec.panel, spec.accent, 0.10f)
        shader = LinearGradient(r.left, r.top, r.right, r.bottom, withAlpha(top, alpha), withAlpha(spec.panel, alpha), Shader.TileMode.CLAMP)
    }

    private fun glassPlate(r: RectF) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color.argb(70, 255, 255, 255), Color.argb(18, 255, 255, 255), Shader.TileMode.CLAMP)
    }

    private fun strokeShape(c: Canvas, p: Path, spec: IconSpec, s: Float, alpha: Int = 255) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.02f * spec.outline.coerceIn(0.3f, 2.5f)
            color = withAlpha(spec.accent, alpha / 255f)
            if (spec.glow > 0.01f) setShadowLayer(s * 0.07f * spec.glow, 0f, 0f, spec.accent)
        }
        c.drawPath(p, paint)
    }

    /** Small targeting brackets in the corners — the "cybernetic frame". */
    private fun brackets(c: Canvas, r: RectF, spec: IconSpec, s: Float) {
        val len = r.width() * 0.14f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = s * 0.014f; color = withAlpha(spec.accent2, 0.85f)
            strokeCap = Paint.Cap.SQUARE
        }
        val o = s * 0.035f
        val l = r.left - o; val t = r.top - o; val rr = r.right + o; val b = r.bottom + o
        c.drawLine(l, t, l + len, t, p); c.drawLine(l, t, l, t + len, p)
        c.drawLine(rr, b, rr - len, b, p); c.drawLine(rr, b, rr, b - len, p)
    }

    private fun tintLayer(c: Canvas, color: Int, clip: Path, alpha: Int) {
        c.save(); c.clipPath(clip)
        c.drawColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), PorterDuff.Mode.SRC_ATOP)
        c.restore()
    }

    private fun saturationPaint(sat: Float) = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(sat) })
    }

    /** Monochrome (Android 13 themed icon) layer, or the adaptive foreground, if any. */
    private fun glyphLayer(d: Drawable): Drawable? {
        if (d !is AdaptiveIconDrawable) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) d.monochrome?.let { return it }
        return d.foreground
    }

    /** Draws an adaptive-icon layer (108dp canvas, 72dp visible) into [r]. */
    private fun drawLayer(c: Canvas, layer: Drawable, r: RectF) {
        val extra = r.width() * 0.25f
        layer.bounds = Rect((r.left - extra).toInt(), (r.top - extra).toInt(), (r.right + extra).toInt(), (r.bottom + extra).toInt())
        layer.draw(c)
    }

    private fun drawFull(c: Canvas, d: Drawable, r: RectF) {
        if (d is AdaptiveIconDrawable) {
            d.background?.let { drawLayer(c, it, r) }
            d.foreground?.let { drawLayer(c, it, r) }
        } else {
            d.bounds = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            d.draw(c)
        }
    }

    private fun drawFallback(c: Canvas, label: String, frame: RectF, path: Path, spec: IconSpec) {
        c.drawPath(path, plate(spec, frame, 1f))
        strokeShape(c, path, spec, frame.width())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.accent; textAlign = Paint.Align.CENTER; textSize = frame.height() * 0.42f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val ch = label.trim().take(1).uppercase().ifEmpty { "?" }
        c.drawText(ch, frame.centerX(), frame.centerY() - (p.descent() + p.ascent()) / 2f, p)
    }

    private fun loadOverride(uri: String, size: Int): Drawable? = runCatching {
        val u = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        android.graphics.drawable.BitmapDrawable(context.resources, Bitmap.createScaledBitmap(raw, size, size, true))
    }.getOrNull()

    companion object {
        fun withAlpha(color: Int, a: Float): Int =
            Color.argb((Color.alpha(color) * a.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        fun blend(a: Int, b: Int, t: Float): Int = Color.argb(
            Color.alpha(a),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )
    }
}

/** ADW / Nova-format icon pack reader (appfilter.xml). Supported by nearly every icon pack. */
class IconPack(private val context: Context, val packageName: String) {

    private val res: Resources? = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull()
    private val map: Map<String, String> by lazy { parse() }

    @SuppressLint("DiscouragedApi")
    fun drawableFor(cn: ComponentName): Drawable? {
        val r = res ?: return null
        val name = map["ComponentInfo{${cn.packageName}/${cn.className}}"] ?: return null
        val id = r.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(r, id, null) }.getOrNull()
    }

    @SuppressLint("DiscouragedApi")
    private fun parse(): Map<String, String> {
        val r = res ?: return emptyMap()
        val out = HashMap<String, String>()
        var stream: InputStream? = null
        try {
            val xmlId = r.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) r.getXml(xmlId) else {
                val s = r.assets.open("appfilter.xml")
                stream = s
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(s, "UTF-8") }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) out[comp.trim()] = dr.trim()
                }
                ev = parser.next()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { stream?.close() }
        }
        return out
    }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME",
        )

        @Suppress("DEPRECATION")
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return ACTIONS.flatMap { a ->
                runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            }.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }
    }
}

