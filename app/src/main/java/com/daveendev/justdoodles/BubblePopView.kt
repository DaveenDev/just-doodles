package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bubble pop: bubbles float up from the bottom, each with a little picture inside. A tap pops one
 * with a sparkle and says what was in it ("Duck!"). They never stop coming, nothing is missed, and
 * every ten pops gets a cheer. Every finger can pop at once.
 */
@SuppressLint("ViewConstructor")
class BubblePopView(context: Context, private val host: GameHost) : View(context) {

    private class Bubble(val title: String, val picture: Bitmap, var x: Float, var y: Float, val r: Float, val speed: Float, val phase: Float) {
        /** Where it was last drawn, wobble included, so a tap finds it where the child sees it. */
        var drawnX = x
    }

    private class Burst(val x: Float, val y: Float, val r: Float, val at: Long)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val pages = host.pictureGroups.flatMap { it.pages }.filter { it.title != null }
    /** Each picture is colored once, at one size, then reused: coloring one in takes a moment. */
    private val pictures = HashMap<String, Bitmap>()
    private val bubbles = ArrayList<Bubble>()
    private val bursts = ArrayList<Burst>()
    private var nextSpawnAt = 0L
    private var lastFrame = 0L
    private var pops = 0
    private val cheerLater = Runnable { host.cheer() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFF4FC3F7.toInt()
    }
    private val box = RectF()
    private val clip = Path()

    init {
        contentDescription = context.getString(R.string.game_bubbles)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bubbles.clear()
        pictures.clear()
        nextSpawnAt = 0L
        invalidate()
    }

    private fun maxRadius() = min(width, height) * 0.16f

    private fun spawn() {
        if (pages.isEmpty() || width == 0) return
        val page = pages.random(random)
        val size = (maxRadius() * 2f).toInt().coerceAtLeast(1)
        val picture = pictures.getOrPut(page.id) { host.picture(page, size, size * 3 / 4) }
        val r = min(width, height) * (0.11f + random.nextFloat() * 0.05f)
        val x = r + random.nextFloat() * (width - 2 * r)
        val speed = height / (5f + random.nextFloat() * 3f) // Pixels a second: 5 to 8 seconds to cross.
        bubbles += Bubble(page.title ?: "", picture, x, height + r, r, speed, random.nextFloat() * 6f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        if (bubbles.size < MAX_BUBBLES && now >= nextSpawnAt) {
            spawn()
            nextSpawnAt = now + 800L + random.nextLong(700L)
        }

        val it = bubbles.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y -= b.speed * dt
            if (b.y + b.r < 0f) {
                it.remove()
                continue
            }
            b.drawnX = b.x + sin(now / 600f + b.phase) * b.r * 0.18f
            drawBubble(canvas, b)
        }

        val bi = bursts.iterator()
        while (bi.hasNext()) {
            val burst = bi.next()
            val t = (now - burst.at) / BURST_MS.toFloat()
            if (t >= 1f) {
                bi.remove()
                continue
            }
            fill.color = Color.argb(((1f - t) * 255).toInt(), 0x4F, 0xC3, 0xF7)
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                val d = burst.r * (0.6f + t * 0.8f)
                canvas.drawCircle(burst.x + d * cos(a).toFloat(), burst.y + d * sin(a).toFloat(), burst.r * 0.12f * (1f - t), fill)
            }
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawBubble(c: Canvas, b: Bubble) {
        // The picture is on white paper, so the whole inside of the bubble is white and the picture
        // is cut to the circle: no rectangle shows. A light blue tint on top makes it a bubble.
        clip.rewind()
        clip.addCircle(b.drawnX, b.y, b.r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        fill.color = Color.WHITE
        c.drawCircle(b.drawnX, b.y, b.r, fill)
        val w = b.r * 1.9f
        val h = w * b.picture.height / b.picture.width.coerceAtLeast(1)
        box.set(b.drawnX - w / 2f, b.y - h / 2f, b.drawnX + w / 2f, b.y + h / 2f)
        c.drawBitmap(b.picture, null, box, bitmapPaint)
        fill.color = 0x224FC3F7
        c.drawCircle(b.drawnX, b.y, b.r, fill)
        c.restore()
        c.drawCircle(b.drawnX, b.y, b.r, rim)
        fill.color = 0xCCFFFFFF.toInt()
        c.drawOval(b.drawnX - b.r * 0.62f, b.y - b.r * 0.7f, b.drawnX - b.r * 0.2f, b.y - b.r * 0.42f, fill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        // The one drawn last is on top.
        val hit = bubbles.lastOrNull { hypot(x - it.drawnX, y - it.y) < it.r * 1.15f } ?: return true
        bubbles.remove(hit)
        bursts += Burst(hit.drawnX, hit.y, hit.r, SystemClock.uptimeMillis())
        host.sounds.pop(0.8f + random.nextFloat() * 0.5f)
        if (hit.title.isNotEmpty()) host.say("${hit.title}!")
        pops++
        if (pops % CHEER_EVERY == 0) postDelayed(cheerLater, 400L)
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_BUBBLES = 5
        const val BURST_MS = 400L
        const val CHEER_EVERY = 10
    }
}
