package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Balloons: big, bright balloons drift up the sky on wobbly strings. A tap pops one in a burst of
 * confetti, plays that color's note and says the color ("Red!"). They never stop coming, nothing
 * is missed, and every tenth pop gets a cheer. Every finger can pop at once.
 */
@SuppressLint("ViewConstructor")
class BalloonsView(context: Context, private val host: GameHost) : View(context) {

    private class Balloon(val color: Int, val x: Float, var y: Float, val r: Float, val speed: Float, val phase: Float) {
        /** Where it was last drawn, wobble included, so a tap finds it where the child sees it. */
        var drawnX = x
    }

    /** A bit of confetti (or a twinkle) flying out from a pop. */
    private class Bit(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int, val size: Float, val at: Long)

    /** The ring that spreads out where a balloon was. */
    private class Ring(val x: Float, val y: Float, val r: Float, val color: Int, val at: Long)

    private class Cloud(var x: Float, val y: Float, val size: Float, val speed: Float)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val colorNames: Array<String> = resources.getStringArray(R.array.color_names)
    private val balloons = ArrayList<Balloon>()
    private val bits = ArrayList<Bit>()
    private val rings = ArrayList<Ring>()
    private val clouds = ArrayList<Cloud>()
    private var nextSpawnAt = 0L
    private var lastFrame = 0L
    private var pops = 0
    private val cheerLater = Runnable { host.cheer() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Palette.INK
    }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF78909C.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
    }
    private val sky = Paint()
    private val box = RectF()
    private val path = Path()

    init {
        contentDescription = context.getString(R.string.game_balloons)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sky.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF81D4FA.toInt(), 0xFFE1F5FE.toInt(), Shader.TileMode.CLAMP)
        balloons.clear()
        bits.clear()
        rings.clear()
        clouds.clear()
        if (w > 0 && h > 0) {
            val s = min(w, h).toFloat()
            for (k in 0 until 3) {
                clouds += Cloud(random.nextFloat() * w, h * (0.12f + 0.28f * k), s * (0.14f + random.nextFloat() * 0.06f), s * (0.012f + random.nextFloat() * 0.01f))
            }
            // Start with a few balloons already up in the sky, so there is something to pop at once.
            for (k in 0 until 4) spawn(h * (0.25f + 0.2f * k))
        }
        nextSpawnAt = 0L
        invalidate()
    }

    /** One more balloon, rising from just below the bottom edge unless [atY] says where to start. */
    private fun spawn(atY: Float = -1f) {
        if (width == 0 || height == 0) return
        val r = min(width, height) * (0.12f + random.nextFloat() * 0.04f)
        val x = r + random.nextFloat() * (width - 2 * r)
        val y = if (atY >= 0f) atY else height + r * 1.3f
        val speed = height / (7f + random.nextFloat() * 4f) // Pixels a second: 7 to 11 seconds to cross.
        balloons += Balloon(COLORS[random.nextInt(COLORS.size)], x, y, r, speed, random.nextFloat() * 6f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
        for (cloud in clouds) {
            cloud.x += cloud.speed * dt * 4f
            if (cloud.x - cloud.size * 2f > width) cloud.x = -cloud.size * 2f
            drawCloud(canvas, cloud)
        }

        if (balloons.size < MAX_BALLOONS && now >= nextSpawnAt) {
            spawn()
            nextSpawnAt = now + 700L + random.nextLong(700L)
        }
        val it = balloons.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y -= b.speed * dt
            if (b.y < -b.r * 1.4f) {
                it.remove()
                continue
            }
            b.drawnX = b.x + sin(now / 700f + b.phase) * b.r * 0.2f
            drawBalloon(canvas, b, now)
        }

        val ri = rings.iterator()
        while (ri.hasNext()) {
            val ring = ri.next()
            val t = (now - ring.at) / RING_MS.toFloat()
            if (t >= 1f) {
                ri.remove()
                continue
            }
            ringPaint.color = ring.color
            ringPaint.alpha = ((1f - t) * 200).toInt()
            canvas.drawCircle(ring.x, ring.y, ring.r * (0.7f + t * 1.1f), ringPaint)
        }

        val bi = bits.iterator()
        while (bi.hasNext()) {
            val bit = bi.next()
            val age = (now - bit.at) / 1000f
            val t = age / (BIT_MS / 1000f)
            if (t >= 1f) {
                bi.remove()
                continue
            }
            fill.color = bit.color
            fill.alpha = ((1f - t) * 255).toInt()
            val x = bit.x + bit.vx * age
            val y = bit.y + bit.vy * age + 500f * density * age * age
            canvas.drawCircle(x, y, bit.size * (1f - 0.5f * t), fill)
            fill.alpha = 255
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawCloud(c: Canvas, cloud: Cloud) {
        fill.color = 0xCCFFFFFF.toInt()
        val s = cloud.size
        c.drawCircle(cloud.x, cloud.y, s * 0.5f, fill)
        c.drawCircle(cloud.x + s * 0.55f, cloud.y - s * 0.2f, s * 0.65f, fill)
        c.drawCircle(cloud.x + s * 1.2f, cloud.y, s * 0.5f, fill)
        box.set(cloud.x, cloud.y, cloud.x + s * 1.2f, cloud.y + s * 0.5f)
        c.drawRoundRect(box, s * 0.25f, s * 0.25f, fill)
    }

    private fun drawBalloon(c: Canvas, b: Balloon, now: Long) {
        val cx = b.drawnX
        val cy = b.y
        val ry = b.r * 1.2f
        val color = Palette.colors[b.color]
        val knotY = cy + ry

        // The string, swaying a little.
        val sway = sin(now / 500f + b.phase) * b.r * 0.25f
        path.rewind()
        path.moveTo(cx, knotY + b.r * 0.18f)
        path.cubicTo(cx - sway, knotY + b.r * 0.8f, cx + sway, knotY + b.r * 1.4f, cx - sway * 0.5f, knotY + b.r * 2.0f)
        c.drawPath(path, stringPaint)

        // The knot.
        path.rewind()
        path.moveTo(cx, knotY - b.r * 0.05f)
        path.lineTo(cx - b.r * 0.16f, knotY + b.r * 0.2f)
        path.lineTo(cx + b.r * 0.16f, knotY + b.r * 0.2f)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
        c.drawPath(path, ink)

        // The balloon, with a shine.
        box.set(cx - b.r, cy - ry, cx + b.r, cy + ry)
        fill.color = color
        c.drawOval(box, fill)
        c.drawOval(box, ink)
        fill.color = 0x88FFFFFF.toInt()
        c.drawOval(cx - b.r * 0.62f, cy - ry * 0.74f, cx - b.r * 0.22f, cy - ry * 0.32f, fill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        tap(e.getX(e.actionIndex), e.getY(e.actionIndex))
        return true
    }

    private fun tap(x: Float, y: Float) {
        // The one drawn last is on top. A little extra room around each balloon, for big fingers.
        val hit = balloons.lastOrNull { b ->
            val dx = (x - b.drawnX) / (b.r * 1.25f)
            val dy = (y - b.y) / (b.r * 1.45f)
            dx * dx + dy * dy < 1f
        }
        val now = SystemClock.uptimeMillis()
        if (hit == null) {
            // Missed: a tiny twinkle, so the touch still does something.
            twinkle(x, y, 0xFFFFFFFF.toInt(), 6, now)
            host.sounds.pop(1.5f + random.nextFloat() * 0.3f)
            return
        }
        balloons.remove(hit)
        val color = Palette.colors[hit.color]
        rings += Ring(hit.drawnX, hit.y, hit.r, color, now)
        for (k in 0 until 14) {
            val a = random.nextFloat() * 6.2832f
            val v = (140f + random.nextFloat() * 260f) * density
            bits += Bit(
                hit.drawnX, hit.y, cos(a) * v, sin(a) * v - 120f * density,
                if (k % 3 == 0) 0xFFFFFFFF.toInt() else color,
                (4f + random.nextFloat() * 5f) * density, now,
            )
        }
        host.sounds.note(hit.color)
        host.sounds.pop(1f + random.nextFloat() * 0.4f)
        host.say(colorNames[hit.color])
        pops++
        if (pops % CHEER_EVERY == 0) postDelayed(cheerLater, 500L)
    }

    private fun twinkle(x: Float, y: Float, color: Int, count: Int, now: Long) {
        for (k in 0 until count) {
            val a = random.nextFloat() * 6.2832f
            val v = (60f + random.nextFloat() * 120f) * density
            bits += Bit(x, y, cos(a) * v, sin(a) * v, color, (3f + random.nextFloat() * 3f) * density, now)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_BALLOONS = 7
        const val RING_MS = 450L
        const val BIT_MS = 800L
        const val CHEER_EVERY = 10

        /** The balloon colors, as indexes into [Palette.colors] and `color_names`: no brown, peach or black. */
        val COLORS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
    }
}
