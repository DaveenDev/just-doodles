package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bedtime: the bedtime timer has run out. The picture fades away under a night sky with a
 * sleepy moon, twinkling stars and drifting z's, and every touch is ignored. Only a grown-up
 * holding the gear for two seconds wakes the app up again.
 */
@SuppressLint("ViewConstructor")
class SleepView(context: Context, onWake: () -> Unit) : FrameLayout(context) {

    private class Star(val fx: Float, val fy: Float, val radius: Float, val phase: Float)

    private val density = resources.displayMetrics.density
    private val random = Random(7)
    private val stars = List(45) {
        Star(random.nextFloat(), random.nextFloat() * 0.92f, (1.5f + random.nextFloat() * 2.5f) * density, random.nextFloat() * 6.3f)
    }
    private val startAt = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF8D6E3F.toInt()
    }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val arc = RectF()
    private var sky: LinearGradient? = null

    init {
        setWillNotDraw(false)
        isClickable = true // Takes every touch, so nothing gets drawn at bedtime.
        contentDescription = context.getString(R.string.say_goodnight)
        val gear = HoldButton(context, Icon.GEAR, 2000L) { onWake() }.apply { alpha = 0.5f }
        val size = (56 * density).roundToInt()
        val margin = (8 * density).roundToInt()
        addView(gear, LayoutParams(size, size, Gravity.TOP or Gravity.END).apply { setMargins(margin, margin, margin, margin) })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sky = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0B1433.toInt(), 0xFF2A3B6E.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - startAt) / 1000f
        // The picture fades away under the night over the first few seconds.
        val fade = (t / FADE_SECONDS).coerceIn(0f, 1f)
        val alpha = (255 * fade).roundToInt()
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = sky
        paint.alpha = alpha
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        for (s in stars) {
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.6f + s.phase))
            paint.color = Color.WHITE
            paint.alpha = (alpha * twinkle).roundToInt()
            canvas.drawCircle(s.fx * w, s.fy * h, s.radius, paint)
        }

        // The moon, breathing slowly in its sleep.
        val r = min(w, h) * 0.22f * (1f + 0.02f * sin(t * 1.3f))
        val cx = w / 2f
        val cy = h / 2f
        paint.color = MOON
        paint.alpha = alpha / 6
        canvas.drawCircle(cx, cy, r * 1.3f, paint)
        paint.alpha = alpha
        canvas.drawCircle(cx, cy, r, paint)

        // Closed eyes and a little smile.
        line.strokeWidth = r * 0.06f
        line.alpha = alpha
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * r * 0.36f
            val ey = cy - r * 0.1f
            arc.set(ex - r * 0.16f, ey - r * 0.1f, ex + r * 0.16f, ey + r * 0.1f)
            canvas.drawArc(arc, 10f, 160f, false, line)
            paint.color = CHEEK
            paint.alpha = alpha * 0x55 / 255
            canvas.drawCircle(cx + side * r * 0.55f, cy + r * 0.2f, r * 0.12f, paint)
        }
        arc.set(cx - r * 0.14f, cy + r * 0.18f, cx + r * 0.14f, cy + r * 0.38f)
        canvas.drawArc(arc, 20f, 140f, false, line)

        // Z's drifting up from the moon, each fading in and out on its way.
        for (k in 0..2) {
            val p = (t / 3f + k / 3f) % 1f
            zPaint.textSize = r * (0.22f + 0.25f * p)
            zPaint.alpha = (alpha * sin(p * PI).toFloat()).roundToInt()
            canvas.drawText("z", cx + r * (0.8f + 0.9f * p), cy - r * (0.7f + 1.1f * p), zPaint)
        }

        // Smooth while fading, then a gentler frame rate for the slow twinkle.
        if (fade < 1f) postInvalidateOnAnimation() else postInvalidateDelayed(50)
    }

    private companion object {
        const val FADE_SECONDS = 3f
        const val MOON = 0xFFFFF1B8.toInt()
        const val CHEEK = 0xFFFF8A80.toInt()
    }
}
