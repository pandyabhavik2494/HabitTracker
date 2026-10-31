package com.pandyabhavik.habittracker.art

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Seal: four quarter arcs, one per habit, laid out like the Today tiles: steps top left,
 * calories top right, bath bottom right, weigh-in bottom left. Closed quarters glow. A Perfect Day joins
 * them into one glowing ring, each quarter still in its own colour, with a white shimmer travelling round.
 * Drawn on a 100 x 100 box, scaled to [size].
 */
object SealArt {
    val COLORS = intArrayOf(0xFF3BE38F.toInt(), 0xFFFF8A3D.toInt(), 0xFFA98BFF.toInt(), 0xFF43D3EA.toInt())
    const val RISK = 0xFFFF5A6A.toInt()
    private const val GAP = 22f
    private const val R = 41f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val oval = RectF(50 - R, 50 - R, 50 + R, 50 + R)

    /**
     * Clockwise slot (0 = top left, 1 = top right, 2 = bottom right, 3 = bottom left) for each habit
     * ordinal (steps, calories, weigh-in, bath), so the ring matches the tile grid.
     */
    private val SLOT = intArrayOf(0, 1, 3, 2)

    /** Start angle of the quarter for habit ordinal [i]; 180 degrees is 9 o'clock. */
    private fun startAngle(i: Int): Float = 180f + SLOT[i] * 90f + GAP / 2

    // Shaders and mask filters are made once, not every frame.
    private val closedBlur = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)

    // The Perfect ring keeps every quarter in its own colour and place (green top left, orange top
    // right, cyan bottom right, violet bottom left). Neighbours blend briefly at 3, 6 and 9 o'clock;
    // orange reaches cyan through magenta (a straight orange-cyan mix turns khaki grey), and nothing
    // passes through yellow. Green and orange never blend: the sweep starts and ends at 12 o'clock, so
    // they meet there in a crisp butt join, with no gap, in the ring and in its glow alike.
    // Sweep fractions start at 12 o'clock (see [sweepAt12]) and run clockwise.
    private const val GREEN = 0xFF3BE38F.toInt()
    private const val CYAN = 0xFF43D3EA.toInt()
    private const val VIOLET = 0xFFA98BFF.toInt()
    private const val ORANGE = 0xFFFF8A3D.toInt()
    private const val MAGENTA = 0xFFE86BD6.toInt()
    private val sweep = SweepGradient(
        50f, 50f,
        intArrayOf(ORANGE, ORANGE, MAGENTA, CYAN, CYAN, VIOLET, VIOLET, GREEN, GREEN),
        floatArrayOf(0f, 0.225f, 0.25f, 0.275f, 0.46f, 0.54f, 0.71f, 0.79f, 1f),
    ).apply { setLocalMatrix(sweepAt12) }

    /** Turns a sweep (which starts at 3 o'clock) back a quarter, so it starts at 12. */
    private val sweepAt12: Matrix get() = Matrix().apply { setRotate(-90f, 50f, 50f) }

    // A soft white highlight that travels round the sealed ring: the "alive" feel, without moving
    // the quarters out of place.
    private val shimmer = SweepGradient(
        50f, 50f,
        intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
    )

    // The Perfect glow: the ring's own colours, faded out either side of the ring by one smooth radial
    // mask (no stacked layers, so no banding). Ring, glow and shimmer are drawn once, in software, into
    // bitmaps cached per size: software sweeps have no seam line, and each frame only blits them (the
    // glow with a breathing alpha, the shimmer rotated round the centre).
    /** Box units the glow reaches past the ring's centre line, and the room kept round the 100 box. */
    private const val GLOW_REACH = 19f
    private const val PAD = 12f
    private val glowMask: RadialGradient by lazy {
        // Distance from the ring's centre line (box units) to mask strength: flat under the ring, then
        // an eased fall to nothing at GLOW_REACH, the same on both sides.
        val d = floatArrayOf(-19f, -17f, -15f, -13f, -11f, -9.5f, -8f, -6.5f, -5f, 5f, 6.5f, 8f, 9.5f, 11f, 13f, 15f, 17f, 19f)
        val f = floatArrayOf(0f, 0.044f, 0.135f, 0.258f, 0.41f, 0.538f, 0.68f, 0.834f, 1f, 1f, 0.834f, 0.68f, 0.538f, 0.41f, 0.258f, 0.135f, 0.044f, 0f)
        val outer = R + GLOW_REACH
        RadialGradient(
            50f, 50f, outer,
            IntArray(f.size) { ((f[it] * 255).roundToInt() shl 24) or 0xFFFFFF },
            FloatArray(d.size) { (R + d[it]) / outer },
            Shader.TileMode.CLAMP,
        )
    }

    private class Layers(val glow: Bitmap, val ring: Bitmap, val shine: Bitmap)

    /** A few sizes at once (the big Seal, the week strip's, a widget), so they don't evict each other. */
    private val layerCache = object : LinkedHashMap<Long, Layers>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Layers>?) = size > 6
    }
    private val blit = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowDst = RectF(-PAD, -PAD, 100f + PAD, 100f + PAD)
    private val boxDst = RectF(0f, 0f, 100f, 100f)

    private fun layers(px: Int, stroke: Float): Layers {
        val key = (px.toLong() shl 32) or stroke.toRawBits().toLong().and(0xFFFFFFFFL)
        layerCache[key]?.let { return it }
        // Each bitmap covers its box at one pixel per device pixel.
        fun paint(units: Float, offset: Float, block: (Canvas) -> Unit): Bitmap {
            val side = (px * units / 100f).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(side / units, side / units)
            c.translate(offset, offset)
            block(c)
            return bmp
        }
        val glow = paint(100f + 2 * PAD, PAD) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true; shader = sweep }
            c.drawCircle(50f, 50f, R + GLOW_REACH, p)
            p.shader = glowMask
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            c.drawRect(glowDst, p)
        }
        val ringBmp = paint(100f, 0f) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke + 1f
                isDither = true
                shader = sweep
            }
            c.drawCircle(50f, 50f, R, p)
        }
        val shine = paint(100f, 0f) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke + 1f
                shader = shimmer
            }
            c.drawCircle(50f, 50f, R, p)
        }
        return Layers(glow, ringBmp, shine).also { layerCache[key] = it }
    }

    /**
     * @param progress four values 0..1 in habit order (steps, calories, weigh-in, bath).
     * @param t seconds, for the glow breathing and the pulse of [pulse] (the quarter still open at risk).
     * @param bad index of a quarter drawn red (calories over the limit).
     */
    @Synchronized
    fun draw(
        canvas: Canvas,
        size: Float,
        progress: List<Float>,
        t: Float = 0f,
        stroke: Float = 9f,
        pulse: Int? = null,
        bad: Int? = null,
        glowOn: Boolean = true,
    ) {
        canvas.save()
        canvas.scale(size / 100f, size / 100f)
        val perfect = progress.all { it >= 1f } && bad == null
        if (perfect) {
            val breath = 0.5f + 0.5f * sin(t * 2.6f)
            val l = layers(size.roundToInt().coerceAtLeast(1), stroke)
            if (glowOn) {
                // The glow reaches GLOW_REACH (19) units past the ring's centre line, 10 past the 100 box.
                // Callers leave that much room round the box (Seal, widget inset).
                blit.alpha = (120 + 48 * breath).toInt()
                canvas.drawBitmap(l.glow, null, glowDst, blit)
            }
            blit.alpha = 255
            canvas.drawBitmap(l.ring, null, boxDst, blit)
            canvas.save()
            canvas.rotate((t * 36f) % 360f, 50f, 50f)
            canvas.drawBitmap(l.shine, null, boxDst, blit)
            canvas.restore()
            canvas.restore()
            return
        }
        for (i in 0 until 4) {
            val col = if (bad == i) RISK else COLORS[i]
            val a0 = startAngle(i)
            val span = 90f - GAP
            track.color = col
            track.alpha = 41
            track.strokeWidth = stroke
            canvas.drawArc(oval, a0, span, false, track)
            val v = progress.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            if (v <= 0f) continue
            if (v >= 1f && glowOn) {
                glow.color = col
                glow.alpha = (255 * 0.85f).toInt()
                glow.strokeWidth = stroke
                glow.maskFilter = closedBlur
                canvas.drawArc(oval, a0, span, false, glow)
            }
            arc.color = col
            arc.strokeWidth = stroke
            canvas.drawArc(oval, a0, span * v, false, arc)
            if (pulse == i) {
                // The quarter at risk pulses brighter, never dimmer: full colour at rest (t = 0), with a
                // white lift on top that comes and goes.
                val lift = 0.5f - 0.5f * cos(t * Math.PI.toFloat())
                arc.color = android.graphics.Color.WHITE
                arc.alpha = (64 * lift).toInt()
                canvas.drawArc(oval, a0, span * v, false, arc)
            }
        }
        canvas.restore()
    }

    fun bitmap(sizePx: Int, progress: List<Float>, stroke: Float = 9f, bad: Int? = null): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), sizePx.toFloat(), progress, t = 0.6f, stroke = stroke, bad = bad)
        return bmp
    }
}
