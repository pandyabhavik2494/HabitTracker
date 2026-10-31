package com.pandyabhavik.habittracker.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.pandyabhavik.habittracker.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Draws the cat stickers. The art comes from the approved mock as a small draw list
 * (`assets/cats.json`, made by `tools/cats/export.js`); each animated part carries its idle
 * animation (tail swish, blink, ear twitch, breathing, paw wave and so on) and its pivot point.
 * Everything is drawn on a 100 x 100 canvas.
 */
object CatArt {
    class Cat(val id: String, val art: Node, val silhouette: Node, val bg: Pair<Int, Int>) {
        /** Scratch transform for the animated parts; only touched while drawing, under synchronized(this). */
        internal val motion = Motion.V()
    }

    sealed class Node {
        var matrix: Matrix? = null
        var opacity = 1f
        var clip: Path? = null
        var anim: Anim? = null
    }

    class Group(val children: List<Node>) : Node()

    class Shape(val path: Path, val fill: Paint?, val stroke: Paint?) : Node()

    class Text(val text: String, val x: Float, val y: Float, val paint: Paint) : Node()

    class Anim(val kind: String, val ox: Float, val oy: Float)

    @Volatile private var cats: Map<String, Cat>? = null

    fun get(context: Context, id: String): Cat? = load(context)[id]

    fun load(context: Context): Map<String, Cat> = cats ?: synchronized(this) {
        cats ?: parse(context).also { cats = it }
    }

    private fun parse(context: Context): Map<String, Cat> {
        val json = context.assets.open("cats.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val display = runCatching { ResourcesCompat.getFont(context, R.font.bricolage) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val body = runCatching { ResourcesCompat.getFont(context, R.font.figtree) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val out = HashMap<String, Cat>()
        for (id in root.keys()) {
            val c = root.getJSONObject(id)
            val bg = c.getJSONArray("bg")
            out[id] = Cat(
                id = id,
                art = node(c.getJSONObject("art"), display, body),
                silhouette = node(c.getJSONObject("sil"), display, body),
                bg = color(bg.getString(0)) to color(bg.getString(1)),
            )
        }
        return out
    }

    private fun node(o: JSONObject, display: Typeface, body: Typeface): Node {
        val n: Node = when (o.getString("t")) {
            "g" -> {
                val kids = o.optJSONArray("c") ?: JSONArray()
                Group((0 until kids.length()).map { node(kids.getJSONObject(it), display, body) })
            }
            "x" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.textSize = o.getDouble("fs").toFloat()
                p.typeface = if (o.optString("ff").startsWith("Figtree")) body else display
                runCatching { p.fontVariationSettings = "'wght' ${o.optInt("fw", 800)}" }
                p.textAlign = if (o.optString("an") == "middle") Paint.Align.CENTER else Paint.Align.LEFT
                p.color = (o.opt("f") as? String)?.let(::color) ?: 0xFFFFFFFF.toInt()
                Text(o.getString("s"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), p)
            }
            else -> {
                val path = PathParser.createPathFromPathData(o.getString("d"))
                val fill = o.opt("f")?.let { paintFor(it, Paint.Style.FILL) }
                val stroke = o.optJSONObject("k")?.let { k ->
                    paintFor(k.get("c"), Paint.Style.STROKE).apply {
                        strokeWidth = k.getDouble("w").toFloat()
                        strokeCap = when (k.optString("cap")) {
                            "round" -> Paint.Cap.ROUND
                            "square" -> Paint.Cap.SQUARE
                            else -> Paint.Cap.BUTT
                        }
                        strokeJoin = when (k.optString("join")) {
                            "round" -> Paint.Join.ROUND
                            "bevel" -> Paint.Join.BEVEL
                            else -> Paint.Join.MITER
                        }
                        k.optJSONArray("dash")?.let { d ->
                            val arr = FloatArray(if (d.length() % 2 == 0) d.length() else d.length() * 2) { d.getDouble(it % d.length()).toFloat() }
                            pathEffect = android.graphics.DashPathEffect(arr, 0f)
                        }
                    }
                }
                Shape(path, fill, stroke)
            }
        }
        o.optJSONArray("m")?.let { m ->
            // SVG matrix(a b c d e f) -> Android's row-major 3x3.
            n.matrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        m.getDouble(0).toFloat(), m.getDouble(2).toFloat(), m.getDouble(4).toFloat(),
                        m.getDouble(1).toFloat(), m.getDouble(3).toFloat(), m.getDouble(5).toFloat(),
                        0f, 0f, 1f,
                    ),
                )
            }
        }
        if (o.has("o")) n.opacity = o.getDouble("o").toFloat()
        o.optString("clip").takeIf { it.isNotEmpty() }?.let { n.clip = PathParser.createPathFromPathData(it) }
        o.optJSONArray("a")?.let { n.anim = Anim(it.getString(0), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) }
        return n
    }

    private fun paintFor(spec: Any, style: Paint.Style): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = style
        when (spec) {
            is String -> p.color = color(spec)
            is JSONObject -> {
                val st = spec.getJSONArray("st")
                val offsets = FloatArray(st.length()) { st.getJSONArray(it).getDouble(0).toFloat() }
                val colors = IntArray(st.length()) { color(st.getJSONArray(it).getString(1)) }
                spec.optJSONArray("lin")?.let { l ->
                    p.shader = LinearGradient(
                        l.getDouble(0).toFloat(), l.getDouble(1).toFloat(), l.getDouble(2).toFloat(), l.getDouble(3).toFloat(),
                        colors, offsets, Shader.TileMode.CLAMP,
                    )
                }
                spec.optJSONArray("rad")?.let { r ->
                    val shader = RadialGradient(0f, 0f, 1f, colors, offsets, Shader.TileMode.CLAMP)
                    shader.setLocalMatrix(
                        Matrix().apply {
                            setScale(r.getDouble(2).toFloat().coerceAtLeast(0.01f), r.getDouble(3).toFloat().coerceAtLeast(0.01f))
                            postTranslate(r.getDouble(0).toFloat(), r.getDouble(1).toFloat())
                        },
                    )
                    p.shader = shader
                }
            }
        }
        return p
    }

    /** "#rrggbbaa" or "#rrggbb" to ARGB. */
    fun color(s: String): Int {
        val h = s.removePrefix("#")
        val rgb = h.substring(0, 6).toLong(16)
        val a = if (h.length >= 8) h.substring(6, 8).toLong(16) else 0xFF
        return ((a shl 24) or rgb).toInt()
    }

    // ---- Drawing ------------------------------------------------------------------------------------

    /**
     * Draws [cat] into a [size] x [size] area of [canvas] at time [t] seconds. [phase] shifts every
     * animation so a grid of stickers doesn't move in lockstep.
     */
    fun draw(canvas: Canvas, cat: Cat, size: Float, t: Float, phase: Float = 0f, silhouette: Boolean = false) {
        // Paints are shared and their alpha is changed while drawing; widgets draw off the main thread.
        synchronized(cat) {
            canvas.save()
            canvas.scale(size / 100f, size / 100f)
            draw(canvas, if (silhouette) cat.silhouette else cat.art, t + phase, cat.motion)
            canvas.restore()
        }
    }

    private val tmp = Matrix()

    private fun draw(canvas: Canvas, n: Node, t: Float, v: Motion.V) {
        var alpha = n.opacity
        val a = n.anim
        val save = canvas.save()
        n.matrix?.let { canvas.concat(it) }
        if (a != null) {
            Motion.at(a.kind, t, v)
            canvas.translate(a.ox, a.oy)
            if (v.tx != 0f || v.ty != 0f) canvas.translate(v.tx, v.ty)
            if (v.r != 0f) canvas.rotate(v.r)
            if (v.sx != 1f || v.sy != 1f) canvas.scale(v.sx, v.sy)
            canvas.translate(-a.ox, -a.oy)
            alpha *= v.o
        }
        n.clip?.let { canvas.clipPath(it) }
        when (n) {
            is Group -> {
                if (alpha < 0.999f) {
                    if (alpha <= 0.004f) {
                        canvas.restoreToCount(save)
                        return
                    }
                    canvas.saveLayerAlpha(null, (alpha * 255).toInt())
                }
                for (child in n.children) draw(canvas, child, t, v)
            }
            is Shape -> {
                n.fill?.let { drawWithAlpha(canvas, n.path, it, alpha) }
                n.stroke?.let { drawWithAlpha(canvas, n.path, it, alpha) }
            }
            is Text -> {
                val old = n.paint.alpha
                n.paint.alpha = (old * alpha).toInt()
                canvas.drawText(n.text, n.x, n.y, n.paint)
                n.paint.alpha = old
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawWithAlpha(canvas: Canvas, path: Path, paint: Paint, alpha: Float) {
        if (alpha >= 0.999f) {
            canvas.drawPath(path, paint)
            return
        }
        val old = paint.alpha
        paint.alpha = (old * alpha).toInt()
        canvas.drawPath(path, paint)
        paint.alpha = old
    }

    /** A still (or one frame) of a sticker as a bitmap, for widgets and sharing. */
    fun bitmap(context: Context, id: String, sizePx: Int, silhouette: Boolean = false, t: Float = 0f): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cat = get(context, id) ?: return bmp
        draw(Canvas(bmp), cat, sizePx.toFloat(), t, 0f, silhouette)
        return bmp
    }
}

/**
 * The idle animations, the same keyframes as the mock's CSS. Each returns the part's transform
 * around its pivot at time [t] in seconds.
 */
object Motion {
    class V(var r: Float = 0f, var tx: Float = 0f, var ty: Float = 0f, var sx: Float = 1f, var sy: Float = 1f, var o: Float = 1f) {
        fun reset() {
            r = 0f; tx = 0f; ty = 0f; sx = 1f; sy = 1f; o = 1f
        }
    }

    private fun ease(u: Float): Float = if (u < 0.5f) 2 * u * u else 1 - (-2 * u + 2) * (-2 * u + 2) / 2

    /** Piecewise eased interpolation over keyframes, given flat as position, value, position, value... */
    private fun seg(t: Float, keys: FloatArray): Float {
        var i = 0
        while (i + 3 < keys.size) {
            val p0 = keys[i]
            val p1 = keys[i + 2]
            if (t >= p0 && t <= p1) {
                val v0 = keys[i + 1]
                val v1 = keys[i + 3]
                val span = p1 - p0
                val u = if (span <= 0f) 1f else ease((t - p0) / span)
                return v0 + (v1 - v0) * u
            }
            i += 2
        }
        return keys[keys.size - 1]
    }

    private fun frac(t: Float, period: Float): Float {
        val x = (t / period) % 1f
        return if (x < 0) x + 1 else x
    }

    private val BOB = floatArrayOf(0f, -4f, .5f, 4f, 1f, -4f)
    private val EYES = floatArrayOf(0f, 1f, .88f, 1f, .91f, .08f, .94f, 1f, 1f, 1f)
    private val EAR_R = floatArrayOf(0f, 0f, .78f, 0f, .81f, 14f, .84f, -6f, .87f, 0f, 1f, 0f)
    private val WHISK = floatArrayOf(0f, 1f, .5f, 1.08f, 1f, 1f)
    private val TAIL = floatArrayOf(0f, -8f, .5f, 10f, 1f, -8f)
    private val BREATHE_X = floatArrayOf(0f, 1f, .5f, 1.015f, 1f, 1f)
    private val BREATHE_Y = floatArrayOf(0f, 1f, .5f, 1.035f, 1f, 1f)
    private val LICK_R = floatArrayOf(0f, 0f, .5f, -14f, 1f, 0f)
    private val LICK_Y = floatArrayOf(0f, 0f, .5f, -2f, 1f, 0f)
    private val WAVE = floatArrayOf(0f, -16f, .5f, 14f, 1f, -16f)
    private val ZZ_Y = floatArrayOf(0f, 6f, 1f, -8f)
    private val ZZ_O = floatArrayOf(0f, 0f, .3f, 1f, 1f, 0f)
    private val PEEK = floatArrayOf(0f, 4f, .4f, -3f, .7f, -3f, 1f, 4f)
    private val WIG_X = floatArrayOf(0f, -1.6f, .5f, 1.6f, 1f, -1.6f)
    private val WIG_R = floatArrayOf(0f, -2f, .5f, 2f, 1f, -2f)
    private val SWAY = floatArrayOf(0f, -3f, .5f, 3f, 1f, -3f)
    private val SHIVER = floatArrayOf(0f, 0f, .25f, -.5f, .75f, .5f, 1f, 0f)
    private val TW = floatArrayOf(0f, 1f, .5f, .25f, 1f, 1f)

    /** Writes the part's transform around its pivot at time [t] seconds into [out] (no allocation). */
    fun at(kind: String, t: Float, out: V): V {
        out.reset()
        when (kind) {
            "bob" -> out.r = seg(frac(t, 3.4f), BOB)
            "eyes" -> out.sy = seg(frac(t, 4.8f), EYES)
            "earR" -> out.r = seg(frac(t, 5.5f), EAR_R)
            "whisk" -> out.sy = seg(frac(t, 2.6f), WHISK)
            "tail" -> out.r = seg(frac(t, 2.2f), TAIL)
            "breathe" -> frac(t, 3f).let { out.sx = seg(it, BREATHE_X); out.sy = seg(it, BREATHE_Y) }
            "lick" -> frac(t, 1.1f).let { out.r = seg(it, LICK_R); out.ty = seg(it, LICK_Y) }
            "wave" -> out.r = seg(frac(t, .9f), WAVE)
            "zz" -> frac(t, 2.6f).let { out.ty = seg(it, ZZ_Y); out.o = seg(it, ZZ_O) }
            "peek" -> out.ty = seg(frac(t, 3.2f), PEEK)
            "wig" -> frac(t, .5f).let { out.tx = seg(it, WIG_X); out.r = seg(it, WIG_R) }
            "sway" -> out.r = seg(frac(t, 3f), SWAY)
            "shiver" -> out.tx = seg(frac(t, .25f), SHIVER)
            "tw" -> out.o = seg(frac(t, 1.6f), TW)
        }
        return out
    }

    /** Allocating convenience form. */
    fun at(kind: String, t: Float): V = at(kind, t, V())
}
