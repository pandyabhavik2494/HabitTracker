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
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
    // right, cyan bottom right, violet bottom left). Neighbours blend softly at 3, 6 and 9 o'clock
    // (orange-cyan, cyan-violet, violet-green: none of them passes through yellow); green and orange
    // never blend: they meet at a tiny gap at 12 o'clock, where the ring starts and ends.
    // Sweep fractions start at 3 o'clock and run clockwise.
    private const val GREEN = 0xFF3BE38F.toInt()
    private const val CYAN = 0xFF43D3EA.toInt()
    private const val VIOLET = 0xFFA98BFF.toInt()
    private const val ORANGE = 0xFFFF8A3D.toInt()
    /** Halfway from orange to cyan, so the blend across the sweep's start angle has no seam. */
    private const val ORANGE_CYAN = 0xFFA1AE93.toInt()
    /** Degrees of the gap at 12 o'clock between the green and orange quarters. */
    private const val SEAM = 2.5f
    private val sweep = SweepGradient(
        50f, 50f,
        intArrayOf(ORANGE_CYAN, CYAN, CYAN, VIOLET, VIOLET, GREEN, GREEN, ORANGE, ORANGE, ORANGE_CYAN),
        floatArrayOf(0f, 0.04f, 0.21f, 0.29f, 0.46f, 0.54f, 0.75f, 0.75f, 0.96f, 1f),
    )

    // The glow uses the same colours but fades out on both sides of 12 o'clock, so the hard green/orange
    // stop never shows as a line through the glow.
    private val glowSweep = SweepGradient(
        50f, 50f,
        intArrayOf(
            ORANGE_CYAN, CYAN, CYAN, VIOLET, VIOLET, GREEN, GREEN, GREEN and 0x00FFFFFF,
            ORANGE and 0x00FFFFFF, ORANGE, ORANGE, ORANGE_CYAN,
        ),
        floatArrayOf(0f, 0.04f, 0.21f, 0.29f, 0.46f, 0.54f, 0.69f, 0.75f, 0.75f, 0.81f, 0.96f, 1f),
    )

    // A soft white highlight that travels round the sealed ring: the "alive" feel, without moving
    // the quarters out of place.
    private val shimmer = SweepGradient(
        50f, 50f,
        intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
    )
    private val shimmerMatrix = Matrix()

    // The Perfect glow: a stack of faint, ever wider full circles. Full circles have no start point
    // (so no seam), and nothing is blurred or masked (so no clipped edge).
    private const val GLOW_LAYERS = 12
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

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
            if (glowOn) {
                // Widest layer: stroke + 2 + 2.4 * 11, about 19 units past the ring's centre line at the
                // default stroke. Callers leave that much room round the 100 box (Seal, widget inset).
                glowPaint.shader = glowSweep
                for (k in GLOW_LAYERS - 1 downTo 0) {
                    glowPaint.strokeWidth = stroke + 2f + 2.4f * k
                    glowPaint.alpha = (13 + 9 * breath).toInt()
                    canvas.drawCircle(50f, 50f, R, glowPaint)
                }
                glowPaint.shader = null
                glowPaint.alpha = 255
            }
            val start = 270f + SEAM / 2
            val sweepDeg = 360f - SEAM
            ring.strokeWidth = stroke + 1f
            ring.shader = sweep
            canvas.drawArc(oval, start, sweepDeg, false, ring)
            shimmerMatrix.setRotate((t * 36f) % 360f, 50f, 50f)
            shimmer.setLocalMatrix(shimmerMatrix)
            ring.shader = shimmer
            canvas.drawArc(oval, start, sweepDeg, false, ring)
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
