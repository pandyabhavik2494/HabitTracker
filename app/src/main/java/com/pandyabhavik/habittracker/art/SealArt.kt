package com.pandyabhavik.habittracker.art

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.sin

/**
 * The Seal: four quarter arcs, one per habit, laid out like the Today tiles: steps top left,
 * calories top right, bath bottom right, weigh-in bottom left. Closed quarters glow. A Perfect Day joins them into one iridescent ring.
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
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val oval = RectF(50 - R, 50 - R, 50 + R, 50 + R)

    /**
     * Clockwise slot (0 = top left, 1 = top right, 2 = bottom right, 3 = bottom left) for each habit
     * ordinal (steps, calories, weigh-in, bath), so the ring matches the tile grid.
     */
    private val SLOT = intArrayOf(0, 1, 3, 2)

    /** Start angle of the quarter for habit ordinal [i]; 180 degrees is 9 o'clock. */
    private fun startAngle(i: Int): Float = 180f + SLOT[i] * 90f + GAP / 2

    // Shaders and mask filters are immutable; the breathing glow steps through a few cached spreads
    // instead of allocating every frame.
    private const val BREATH_STEPS = 16
    private val closedBlur = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)

    // The Perfect ring: green, cyan, violet, pink, orange and back the same way. Orange only ever
    // meets pink and green only meets cyan, so no stretch of the ring blends through yellow or olive.
    // The first colour repeats at the end, and both ends hold it flat for a moment, so the sweep's
    // start angle leaves no seam.
    private const val GREEN = 0xFF3BE38F.toInt()
    private const val CYAN = 0xFF43D3EA.toInt()
    private const val VIOLET = 0xFFA98BFF.toInt()
    private const val PINK = 0xFFFF8FD0.toInt()
    private const val ORANGE = 0xFFFF8A3D.toInt()
    private val sweep = SweepGradient(
        50f, 50f,
        intArrayOf(GREEN, GREEN, CYAN, VIOLET, PINK, ORANGE, PINK, VIOLET, CYAN, GREEN, GREEN),
        floatArrayOf(0f, 0.02f, 0.14f, 0.26f, 0.38f, 0.5f, 0.62f, 0.74f, 0.86f, 0.98f, 1f),
    )

    // The Perfect glow is the sweep masked by a soft radial band around the ring, filled as a disc.
    // No blur mask filter (which left a hairline at the stroke's start point) and no path seam.
    private var glowStroke = Float.NaN
    private var glowCache: Array<Shader> = emptyArray()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun glowShaders(stroke: Float): Array<Shader> {
        if (stroke != glowStroke) {
            glowStroke = stroke
            glowCache = Array(BREATH_STEPS + 1) {
                val spread = stroke / 2f + 6f + 10f * it / BREATH_STEPS
                val outer = R + spread
                val inner = (R - spread).coerceAtLeast(0f)
                val band = RadialGradient(
                    50f, 50f, outer,
                    intArrayOf(0x00000000, 0x00000000, 0x66000000, 0xFF000000.toInt(), 0x55000000, 0x00000000),
                    floatArrayOf(0f, inner / outer, (R - stroke / 2f) / outer, R / outer, (R + spread * 0.4f) / outer, 1f),
                    Shader.TileMode.CLAMP,
                )
                ComposeShader(sweep, band, PorterDuff.Mode.DST_IN)
            }
        }
        return glowCache
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
            // Turn the canvas rather than the shader: the ring is round, and the cached glow shaders
            // (built on the unrotated sweep) turn with it.
            canvas.rotate((t * 40f) % 360f, 50f, 50f)
            if (glowOn) {
                val spreadMax = stroke / 2f + 16f
                glowPaint.shader = glowShaders(stroke)[(breath * BREATH_STEPS).toInt().coerceIn(0, BREATH_STEPS)]
                glowPaint.alpha = (150 + 80 * breath).toInt()
                canvas.drawCircle(50f, 50f, R + spreadMax, glowPaint)
                glowPaint.shader = null
                glowPaint.alpha = 255
            }
            ring.shader = sweep
            ring.strokeWidth = stroke + 1f
            canvas.drawCircle(50f, 50f, R, ring)
            ring.shader = null
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
            var alpha = 255
            if (pulse == i) alpha = (255 * (0.55f + 0.45f * (0.5f + 0.5f * sin(t * Math.PI.toFloat())))).toInt()
            if (v >= 1f && glowOn) {
                glow.color = col
                glow.alpha = (alpha * 0.85f).toInt()
                glow.strokeWidth = stroke
                glow.maskFilter = closedBlur
                canvas.drawArc(oval, a0, span, false, glow)
            }
            arc.color = col
            arc.alpha = alpha
            arc.strokeWidth = stroke
            canvas.drawArc(oval, a0, span * v, false, arc)
        }
        canvas.restore()
    }

    fun bitmap(sizePx: Int, progress: List<Float>, stroke: Float = 9f, bad: Int? = null): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), sizePx.toFloat(), progress, t = 0.6f, stroke = stroke, bad = bad)
        return bmp
    }
}
