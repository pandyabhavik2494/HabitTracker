package com.pandyabhavik.habittracker.art

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import kotlin.math.sin

/**
 * The Seal: four quarter arcs, one per habit (steps top right, calories bottom right, weigh-in
 * bottom left, bath top left). Closed quarters glow. A Perfect Day joins them into one iridescent ring.
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
    private val shaderMatrix = Matrix()

    // Mask filters are immutable; the breathing glow steps through a few cached radii instead of
    // allocating a new filter every frame.
    private const val BREATH_STEPS = 16
    private val breathBlur = Array(BREATH_STEPS + 1) { BlurMaskFilter(6f + 8f * it / BREATH_STEPS, BlurMaskFilter.Blur.NORMAL) }
    private val closedBlur = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
    private val sweep = SweepGradient(
        50f, 50f,
        intArrayOf(0xFF3BE38F.toInt(), 0xFF43D3EA.toInt(), 0xFFA98BFF.toInt(), 0xFFFF8A3D.toInt(), 0xFF3BE38F.toInt()),
        null,
    )

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
            shaderMatrix.setRotate((t * 40f) % 360f, 50f, 50f)
            sweep.setLocalMatrix(shaderMatrix)
            if (glowOn) {
                glow.shader = sweep
                glow.strokeWidth = stroke + 4f
                glow.maskFilter = breathBlur[(breath * BREATH_STEPS).toInt().coerceIn(0, BREATH_STEPS)]
                glow.alpha = (150 + 80 * breath).toInt()
                canvas.drawCircle(50f, 50f, R, glow)
                glow.shader = null
                glow.alpha = 255
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
            val a0 = i * 90f + GAP / 2 - 90f
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
