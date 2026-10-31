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
    class Cat(val id: String, val art: Node, val silhouette: Node, val bg: Pair<Int, Int>)

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
            draw(canvas, if (silhouette) cat.silhouette else cat.art, t + phase)
            canvas.restore()
        }
    }

    private val tmp = Matrix()

    private fun draw(canvas: Canvas, n: Node, t: Float) {
        var alpha = n.opacity
        val a = n.anim
        val save = canvas.save()
        n.matrix?.let { canvas.concat(it) }
        if (a != null) {
            val v = Motion.at(a.kind, t)
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
                n.children.forEach { draw(canvas, it, t) }
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
    class V(var r: Float = 0f, var tx: Float = 0f, var ty: Float = 0f, var sx: Float = 1f, var sy: Float = 1f, var o: Float = 1f)

    private fun ease(u: Float): Float = if (u < 0.5f) 2 * u * u else 1 - (-2 * u + 2) * (-2 * u + 2) / 2

    /** Piecewise eased interpolation over keyframes (position 0..1 to value). */
    private fun seg(t: Float, vararg keys: Pair<Float, Float>): Float {
        for (i in 0 until keys.size - 1) {
            val (p0, v0) = keys[i]
            val (p1, v1) = keys[i + 1]
            if (t >= p0 && t <= p1) {
                val span = p1 - p0
                val u = if (span <= 0f) 1f else ease((t - p0) / span)
                return v0 + (v1 - v0) * u
            }
        }
        return keys.last().second
    }

    private fun frac(t: Float, period: Float): Float {
        val x = (t / period) % 1f
        return if (x < 0) x + 1 else x
    }

    fun at(kind: String, t: Float): V = when (kind) {
        "bob" -> V(r = seg(frac(t, 3.4f), 0f to -4f, .5f to 4f, 1f to -4f))
        "eyes" -> V(sy = seg(frac(t, 4.8f), 0f to 1f, .88f to 1f, .91f to .08f, .94f to 1f, 1f to 1f))
        "earR" -> V(r = seg(frac(t, 5.5f), 0f to 0f, .78f to 0f, .81f to 14f, .84f to -6f, .87f to 0f, 1f to 0f))
        "whisk" -> V(sy = seg(frac(t, 2.6f), 0f to 1f, .5f to 1.08f, 1f to 1f))
        "tail" -> V(r = seg(frac(t, 2.2f), 0f to -8f, .5f to 10f, 1f to -8f))
        "breathe" -> frac(t, 3f).let { V(sx = seg(it, 0f to 1f, .5f to 1.015f, 1f to 1f), sy = seg(it, 0f to 1f, .5f to 1.035f, 1f to 1f)) }
        "lick" -> frac(t, 1.1f).let { V(r = seg(it, 0f to 0f, .5f to -14f, 1f to 0f), ty = seg(it, 0f to 0f, .5f to -2f, 1f to 0f)) }
        "wave" -> V(r = seg(frac(t, .9f), 0f to -16f, .5f to 14f, 1f to -16f))
        "zz" -> frac(t, 2.6f).let { V(ty = seg(it, 0f to 6f, 1f to -8f), o = seg(it, 0f to 0f, .3f to 1f, 1f to 0f)) }
        "peek" -> V(ty = seg(frac(t, 3.2f), 0f to 4f, .4f to -3f, .7f to -3f, 1f to 4f))
        "wig" -> frac(t, .5f).let { V(tx = seg(it, 0f to -1.6f, .5f to 1.6f, 1f to -1.6f), r = seg(it, 0f to -2f, .5f to 2f, 1f to -2f)) }
        "sway" -> V(r = seg(frac(t, 3f), 0f to -3f, .5f to 3f, 1f to -3f))
        "shiver" -> V(tx = seg(frac(t, .25f), 0f to 0f, .25f to -.5f, .75f to .5f, 1f to 0f))
        "tw" -> V(o = seg(frac(t, 1.6f), 0f to 1f, .5f to .25f, 1f to 1f))
        else -> V()
    }
}
