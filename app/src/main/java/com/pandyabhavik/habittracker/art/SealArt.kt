package com.pandyabhavik.habittracker.art

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Seal: four concentric rings, one per habit, each filling clockwise from 12 o'clock. Outer to
 * inner: steps, weigh-in, calories, bath (green and orange never sit side by side, so their glows never
 * mix into yellow). Closed rings glow; a Perfect Day makes the whole set breathe with a white shimmer
 * travelling round. Drawn on a 100 x 100 box, scaled to [size].
 */
object SealArt {
    val COLORS = intArrayOf(0xFF3BE38F.toInt(), 0xFFFF8A3D.toInt(), 0xFFA98BFF.toInt(), 0xFF43D3EA.toInt())
    const val RISK = 0xFFFF5A6A.toInt()

    /** Ring position (0 = outermost) for each habit ordinal (steps, calories, weigh-in, bath). */
    private val LANE = intArrayOf(0, 2, 1, 3)

    /** Centre-line radius of the outer ring; the rest leaves room for its glow. */
    private const val OUTER = 44f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /** Ring thickness in box units for a caller's [stroke] (the old single-ring width). */
    fun thickness(stroke: Float): Float = stroke * 0.8f

    private fun gap(t: Float) = maxOf(1.6f, t * 0.2f)

    /** Inner edge radius of the innermost ring, in box units: the room left in the middle. */
    fun holeRadius(stroke: Float): Float {
        val t = thickness(stroke)
        return OUTER - 3 * (t + gap(t)) - t / 2
    }

    private fun radius(lane: Int, t: Float) = OUTER - lane * (t + gap(t))

    private fun blur(r: Float): BlurMaskFilter = blurs.getOrPut((r * 10).roundToInt()) { BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL) }

    // A soft white highlight that travels round each ring on a Perfect Day.
    private val shimmer = SweepGradient(
        50f, 50f,
        intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.42f, 0.5f, 0.58f, 1f),
    )
    private val shimmerMatrix = Matrix()

    /**
     * @param progress four values 0..1 in habit order (steps, calories, weigh-in, bath).
     * @param t seconds, for the glow breathing and the pulse of [pulse] (the ring still open at risk).
     * @param bad index of a ring drawn red (calories over the limit).
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
        val th = thickness(stroke)
        val perfect = progress.all { it >= 1f } && bad == null
        val breath = if (perfect) 0.5f + 0.5f * sin(t * 2.6f) else 0f
        for (i in 0 until 4) {
            val col = if (bad == i) RISK else COLORS[i]
            val r = radius(LANE[i], th)
            oval.set(50 - r, 50 - r, 50 + r, 50 + r)
            // The track: a dim full circle of the ring's own colour.
            track.color = col
            track.alpha = 46
            track.strokeWidth = th
            canvas.drawCircle(50f, 50f, r, track)
            val v = progress.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            if (v <= 0f) continue
            val sweep = 360f * v
            if (v >= 1f && glowOn) {
                glow.color = col
                glow.alpha = if (perfect) (130 + 70 * breath).toInt() else 150
                glow.strokeWidth = th
                glow.maskFilter = blur(th * 0.45f)
                canvas.drawCircle(50f, 50f, r, glow)
            }
            arc.color = col
            arc.alpha = 255
            arc.strokeWidth = th
            if (v >= 1f) canvas.drawCircle(50f, 50f, r, arc) else canvas.drawArc(oval, -90f, sweep, false, arc)
            if (pulse == i) {
                // The ring at risk pulses brighter, never dimmer: full colour at rest, with a white lift.
                val lift = 0.5f - 0.5f * cos(t * Math.PI.toFloat())
                arc.color = android.graphics.Color.WHITE
                arc.alpha = (64 * lift).toInt()
                canvas.drawArc(oval, -90f, sweep, false, arc)
            }
            if (perfect) {
                shimmerMatrix.setRotate((t * 40f + i * 37f) % 360f, 50f, 50f)
                shimmer.setLocalMatrix(shimmerMatrix)
                shine.shader = shimmer
                shine.strokeWidth = th
                canvas.drawCircle(50f, 50f, r, shine)
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
