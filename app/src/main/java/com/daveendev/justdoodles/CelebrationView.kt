package com.daveendev.justdoodles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * "You finished the picture!" Confetti shoots up from both bottom corners and big stars pop
 * up, for under three seconds. It never blocks a touch, so coloring can carry straight on,
 * and it removes itself when done. Skipped when the phone has animations switched off.
 */
class CelebrationView(context: Context) : View(context) {

    private class Piece(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val color: Int, val w: Float, val h: Float, var angle: Float, val spin: Float, val round: Boolean,
    )

    private class Star(val fx: Float, val fy: Float, val size: Float, val delay: Float, val color: Int)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val pieces = ArrayList<Piece>()
    private val stars = List(6) { i ->
        Star(
            fx = 0.15f + random.nextFloat() * 0.7f,
            fy = 0.15f + random.nextFloat() * 0.55f,
            size = (26f + random.nextFloat() * 22f) * density,
            delay = i * 0.12f,
            color = COLORS[i % COLORS.size],
        )
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPath = Path()
    private val matrix = Matrix()
    private var startAt = -1L
    private var lastFrame = 0L
    private val animationsOff = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Never takes a touch: little fingers keep coloring underneath. */
    override fun onTouchEvent(event: MotionEvent) = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (pieces.isNotEmpty()) return
        for (side in 0..1) {
            val fromX = if (side == 0) w * 0.06f else w * 0.94f
            repeat(45) {
                // Up and toward the middle, fanned out a little.
                val angle = Math.toRadians(-62.0 + (random.nextDouble() - 0.5) * 36).toFloat()
                val speed = h * (0.9f + random.nextFloat() * 0.65f)
                val dir = if (side == 0) 1f else -1f
                pieces += Piece(
                    x = fromX, y = h * 1.02f,
                    vx = cos(angle) * speed * dir, vy = sin(angle) * speed,
                    color = COLORS[random.nextInt(COLORS.size)],
                    w = (7f + random.nextFloat() * 5f) * density,
                    h = (11f + random.nextFloat() * 7f) * density,
                    angle = random.nextFloat() * 360f,
                    spin = (random.nextFloat() - 0.5f) * 720f,
                    round = random.nextInt(4) == 0,
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (startAt < 0) {
            startAt = now
            lastFrame = now
        }
        val t = (now - startAt) / 1000f
        val dt = min(0.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        if (animationsOff || t > DURATION) {
            post { (parent as? ViewGroup)?.removeView(this) }
            return
        }
        val fade = ((DURATION - t) / 0.6f).coerceIn(0f, 1f)

        val gravity = height * 1.4f
        for (p in pieces) {
            p.vy += gravity * dt
            p.vx *= 1f - 0.8f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            paint.color = p.color
            paint.alpha = (255 * fade).toInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            // Squash with the spin, so the paper looks like it's flipping over.
            canvas.scale(1f, cos(Math.toRadians(p.angle * 2.0).toFloat()))
            if (p.round) canvas.drawCircle(0f, 0f, p.w * 0.6f, paint)
            else canvas.drawRect(-p.w / 2, -p.h / 2, p.w / 2, p.h / 2, paint)
            canvas.restore()
        }

        for (s in stars) {
            val p = ((t - s.delay) / 0.4f).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val q = p - 1f
            val pop = 1f + 3.2f * q * q * q + 2.2f * q * q
            val twinkle = 1f + 0.08f * sin(t * 9f + s.delay * 10f)
            val r = s.size * pop * twinkle
            matrix.setScale(r, r)
            matrix.postRotate(t * 40f)
            matrix.postTranslate(s.fx * width, s.fy * height)
            starPath.rewind()
            Stamps.star.transform(matrix, starPath)
            paint.color = s.color
            paint.alpha = (255 * fade).toInt()
            canvas.drawPath(starPath, paint)
        }
        postInvalidateOnAnimation()
    }

    private companion object {
        const val DURATION = 2.8f
        val COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFFFF8F00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
            0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFFF6FB0.toInt(),
        )
    }
}
