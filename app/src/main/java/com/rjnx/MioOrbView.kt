package com.rjnx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Mio RGB Glass Orbit
 *
 * - 3 close tilted neon RGB rings
 * - clockwise continuous rotation
 * - small moon revolving around the orb
 * - breathing/glowing glass sphere
 * - lightweight custom Canvas animation
 */
class MioOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.30f

        // Gentle breathing effect.
        val pulse = 1f + 0.025f * sin(progress * Math.PI * 2.0).toFloat()
        val orbR = r * pulse

        drawAura(canvas, cx, cy, orbR)
        drawBackRings(canvas, cx, cy, orbR)

        drawOrb(canvas, cx, cy, orbR)

        // Front/inner ring layer gives the "around the planet" depth.
        drawFrontRing(canvas, cx, cy, orbR)

        drawMoon(canvas, cx, cy, orbR)
        drawGloss(canvas, cx, cy, orbR)
    }

    private fun drawAura(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val shift = (sin(progress * Math.PI * 2.0) * 0.5 + 0.5).toFloat()

        glowPaint.shader = RadialGradient(
            cx, cy, r * 1.65f,
            intArrayOf(
                Color.argb(120, 50, 210, 255),
                Color.argb(80, 110, 70, 255),
                Color.argb(55, 255, 55, 210),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .35f, .68f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.maskFilter = BlurMaskFilter(r * (0.24f + shift * 0.04f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, r * 1.18f, glowPaint)
    }

    private fun drawOrb(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        orbPaint.shader = RadialGradient(
            cx - r * .34f,
            cy - r * .45f,
            r * 1.45f,
            intArrayOf(
                Color.rgb(170, 225, 255),
                Color.rgb(75, 180, 255),
                Color.rgb(77, 65, 245),
                Color.rgb(180, 55, 245),
                Color.rgb(18, 14, 75),
                Color.rgb(7, 9, 38)
            ),
            floatArrayOf(0f, .18f, .40f, .63f, .84f, 1f),
            Shader.TileMode.CLAMP
        )
        orbPaint.setShadowLayer(r * .24f, 0f, 0f, Color.argb(210, 60, 120, 255))
        canvas.drawCircle(cx, cy, r, orbPaint)

        // Pink/cyan glass refraction.
        highlightPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(105, 60, 220, 255),
                Color.argb(100, 145, 75, 255),
                Color.argb(115, 255, 70, 220),
                Color.argb(80, 60, 210, 255),
                Color.argb(105, 60, 220, 255)
            ),
            floatArrayOf(0f, .28f, .52f, .78f, 1f)
        )
        canvas.drawCircle(cx, cy, r * .97f, highlightPaint)
    }

    private fun setupRingPaint(color1: Int, color2: Int, width: Float) {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = width
        ringPaint.strokeCap = Paint.Cap.ROUND
        ringPaint.shader = LinearGradient(
            0f, 0f, width, width,
            color1,
            color2,
            Shader.TileMode.MIRROR
        )
        ringPaint.setShadowLayer(width * 4f, 0f, 0f, color1)
    }

    private fun drawBackRings(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rotation = -58f

        canvas.save()
        canvas.rotate(rotation, cx, cy)

        val rings = arrayOf(
            Triple(Color.argb(225, 70, 220, 255), Color.argb(225, 105, 85, 255), 1.00f),
            Triple(Color.argb(225, 160, 75, 255), Color.argb(230, 255, 65, 205), 1.10f),
            Triple(Color.argb(225, 65, 120, 255), Color.argb(220, 210, 75, 255), 1.20f)
        )

        rings.forEachIndexed { index, item ->
            val scale = item.third
            val oval = RectF(
                cx - r * (1.28f + index * .07f) * scale,
                cy - r * (.26f + index * .012f),
                cx + r * (1.28f + index * .07f) * scale,
                cy + r * (.26f + index * .012f)
            )
            setupRingPaint(item.first, item.second, maxOf(2.2f, r * .018f))
            canvas.drawOval(oval, ringPaint)
        }

        canvas.restore()
    }

    private fun drawFrontRing(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.save()

        // Android canvas positive rotation is clockwise.
        canvas.rotate(-58f, cx, cy)

        val oval = RectF(
            cx - r * 1.42f,
            cy - r * .30f,
            cx + r * 1.42f,
            cy + r * .30f
        )

        setupRingPaint(
            Color.argb(245, 70, 220, 255),
            Color.argb(245, 255, 70, 220),
            maxOf(2.5f, r * .020f)
        )
        canvas.drawOval(oval, ringPaint)

        canvas.restore()
    }

    private fun drawMoon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Moon follows the smallest ring clockwise.
        val theta = progress * Math.PI * 2.0
        val rx = r * 1.28f
        val ry = r * .26f

        // Calculate position on the tilted ellipse.
        val localX = cos(theta).toFloat() * rx
        val localY = sin(theta).toFloat() * ry

        val tilt = Math.toRadians(-58.0)
        val x = cx + localX * cos(tilt).toFloat() - localY * sin(tilt).toFloat()
        val y = cy + localX * sin(tilt).toFloat() + localY * cos(tilt).toFloat()

        val moonR = r * .095f

        moonGlowPaint.shader = RadialGradient(
            x, y, moonR * 3.5f,
            intArrayOf(
                Color.argb(170, 90, 225, 255),
                Color.argb(80, 180, 90, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .35f, 1f),
            Shader.TileMode.CLAMP
        )
        moonGlowPaint.maskFilter = BlurMaskFilter(moonR * 1.4f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(x, y, moonR * 2f, moonGlowPaint)

        moonPaint.shader = RadialGradient(
            x - moonR * .35f,
            y - moonR * .35f,
            moonR * 1.5f,
            Color.rgb(235, 250, 255),
            Color.rgb(65, 75, 180),
            Shader.TileMode.CLAMP
        )
        moonPaint.setShadowLayer(moonR * 1.3f, 0f, 0f, Color.argb(200, 80, 190, 255))
        canvas.drawCircle(x, y, moonR, moonPaint)
    }

    private fun drawGloss(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val shine = Paint(Paint.ANTI_ALIAS_FLAG)
        shine.shader = LinearGradient(
            cx - r * .75f,
            cy - r * .82f,
            cx + r * .15f,
            cy - r * .18f,
            Color.argb(135, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * .94f, shine)
    }
}
