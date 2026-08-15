package com.rjnx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Mio Glass Orbit:
 * - glowing blue/purple orb
 * - white/blue orbit ring
 * - soft outer aura
 *
 * Package: com.rjnx
 */
class MioOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var angle = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
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

        // Large soft aura.
        glowPaint.shader = RadialGradient(
            cx, cy, r * 1.45f,
            intArrayOf(
                Color.argb(105, 75, 180, 255),
                Color.argb(58, 65, 80, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .48f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.maskFilter = BlurMaskFilter(r * .22f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, r * 1.12f, glowPaint)

        // Glass sphere.
        orbPaint.shader = RadialGradient(
            cx - r * .34f,
            cy - r * .42f,
            r * 1.38f,
            intArrayOf(
                Color.rgb(116, 219, 255),
                Color.rgb(66, 86, 245),
                Color.rgb(45, 30, 170),
                Color.rgb(18, 16, 76),
                Color.rgb(8, 10, 39)
            ),
            floatArrayOf(0f, .25f, .55f, .82f, 1f),
            Shader.TileMode.CLAMP
        )
        orbPaint.setShadowLayer(r * .22f, 0f, 0f, Color.argb(160, 67, 148, 255))
        canvas.drawCircle(cx, cy, r, orbPaint)

        // Purple/blue glossy highlight.
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG)
        highlight.shader = RadialGradient(
            cx + r * .35f,
            cy + r * .25f,
            r * .9f,
            intArrayOf(
                Color.argb(115, 198, 94, 255),
                Color.argb(35, 70, 120, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, highlight)

        // Orbit ring.
        canvas.save()
        canvas.rotate(angle, cx, cy)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = maxOf(2f, r * .014f)
        ringPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(25, 255, 255, 255),
                Color.argb(245, 245, 245, 255),
                Color.argb(80, 145, 220, 255),
                Color.argb(20, 255, 255, 255)
            ),
            floatArrayOf(0f, .18f, .65f, 1f)
        )
        ringPaint.setShadowLayer(r * .045f, 0f, 0f, Color.argb(170, 130, 195, 255))

        val oval = RectF(
            cx - r * 1.48f,
            cy - r * .33f,
            cx + r * 1.48f,
            cy + r * .33f
        )
        canvas.drawOval(oval, ringPaint)
        canvas.restore()

        // Small glass highlight on the upper-left.
        val shine = Paint(Paint.ANTI_ALIAS_FLAG)
        shine.shader = LinearGradient(
            cx - r * .65f, cy - r * .75f,
            cx + r * .05f, cy - r * .25f,
            Color.argb(110, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * .92f, shine)
    }
}
