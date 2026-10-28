package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fishing: a sunny sea with sand and swaying weed. Round, friendly fish swim across at different
 * depths, and now and then a sea creature (a whale, an octopus, a turtle...) drifts by inside a
 * bubble. A tap makes whatever it touches leap out of the water with a splash and a bloop; a
 * creature in a bubble says its name, a plain fish says "Splash!". Nothing can be missed or lost,
 * and every eighth catch gets a cheer. Every finger can catch at once.
 */
@SuppressLint("ViewConstructor")
class FishingView(context: Context, private val host: GameHost) : View(context) {

    private class Fish(
        /** The creature's name if it is a picture in a bubble, else null for a drawn fish. */
        val title: String?,
        val picture: Bitmap?,
        var x: Float,
        val baseY: Float,
        /** Pixels a second, minus for swimming left. */
        val speed: Float,
        /** Length of a drawn fish, or width of a bubble. */
        val size: Float,
        val color: Int,
        val phase: Float,
    ) {
        var jumpAt = -1L
        /** 0 = under water, 1 = broke the surface on the way up, 2 = splashed back in. */
        var stage = 0
        /** Where it was last drawn, so a tap finds it where the child sees it. */
        var drawnX = x
        var drawnY = baseY
        var angle = 0f
    }

    private class Splash(val x: Float, val y: Float, val at: Long, val drops: Boolean)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val fishes = ArrayList<Fish>()
    private val splashes = ArrayList<Splash>()
    /** Each sea creature is colored once, at one size, then reused: coloring one in takes a moment. */
    private val pictures = HashMap<String, Bitmap>()
    private val fishColors = intArrayOf(0, 1, 2, 3, 5, 6, 7, 8).map { Palette.colors[it] }

    private var waterline = 0f
    private var sandH = 0f
    private var bubbleSize = 0
    private var nextSpawnAt = 0L
    private var lastFrame = 0L
    private var catches = 0
    private val cheerLater = Runnable { host.cheer() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val water = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    /** Outline for things drawn in a 100-unit fish box, so it scales with the fish. */
    private val unitInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFF4FC3F7.toInt()
    }
    private val foam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xAAFFFFFF.toInt()
    }
    private val weed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2E9D4A.toInt()
    }
    private val path = Path()
    private val wave = Path()
    private val sand = Path()
    private val clip = Path()
    private val box = RectF()

    init {
        contentDescription = context.getString(R.string.game_fishing)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fishes.clear()
        splashes.clear()
        pictures.clear()
        if (w <= 0 || h <= 0) return
        waterline = h * 0.2f
        sandH = h * 0.13f
        bubbleSize = (min(w, h) * 0.3f).toInt().coerceAtLeast(8)
        water.shader = LinearGradient(0f, waterline, 0f, h.toFloat(), 0xFF4FC3F7.toInt(), 0xFF0277BD.toInt(), Shader.TileMode.CLAMP)

        sand.rewind()
        sand.moveTo(0f, h.toFloat())
        val step = 14f * density
        var x = 0f
        sand.lineTo(0f, sandY(0f))
        while (x < w) {
            x += step
            sand.lineTo(x, sandY(x))
        }
        sand.lineTo(w + step, h.toFloat())
        sand.close()

        // A few fish already swimming when the game opens.
        repeat(3) { fishes += newDrawnFish(random.nextFloat() * w) }
        nextSpawnAt = 0L
        invalidate()
    }

    private fun sandY(x: Float) = height - sandH + sin(x / (90f * density)) * sandH * 0.12f

    private fun newDrawnFish(x: Float, dir: Float = if (random.nextBoolean()) 1f else -1f): Fish {
        val unit = min(width, height).toFloat()
        val size = unit * (0.2f + random.nextFloat() * 0.12f)
        return Fish(null, null, x, depth(), dir * width * (0.07f + random.nextFloat() * 0.07f), size, fishColors.random(random), random.nextFloat() * 6f)
    }

    /** A swimming height somewhere between the waves and the sand. */
    private fun depth(): Float {
        val top = waterline + height * 0.14f
        val bottom = height - sandH - height * 0.06f
        return if (bottom > top) top + random.nextFloat() * (bottom - top) else top
    }

    private fun spawn() {
        if (width == 0 || height == 0) return
        val dir = if (random.nextBoolean()) 1f else -1f
        val startX = if (dir > 0f) -bubbleSize.toFloat() else width + bubbleSize.toFloat()
        val title = if (random.nextInt(100) < 40) CREATURES.random(random) else null
        val page = title?.let { host.page(it) }
        if (title == null || page == null) {
            fishes += newDrawnFish(startX, dir)
            return
        }
        val picture = pictures.getOrPut(title) { host.picture(page, bubbleSize, bubbleSize) }
        val size = bubbleSize * (0.9f + random.nextFloat() * 0.2f)
        fishes += Fish(title, picture, startX, depth(), dir * width * (0.06f + random.nextFloat() * 0.05f), size, 0, random.nextFloat() * 6f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        if (width == 0 || height == 0) return
        if (fishes.size < MAX_FISH && now >= nextSpawnAt) {
            spawn()
            nextSpawnAt = now + 1000L + random.nextLong(900L)
        }

        drawSky(canvas, now)
        wavePath(wave, waterline, 5f * density, 38f * density, now / 700f)
        canvas.drawPath(wave, water)
        canvas.drawPath(sand, fill.apply { color = 0xFFFFE0B2.toInt() })
        drawSeabed(canvas, now)
        drawBubbles(canvas, now)

        val it = fishes.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.x += f.speed * dt
            if ((f.speed > 0f && f.x > width + f.size * 1.2f) || (f.speed < 0f && f.x < -f.size * 1.2f)) {
                it.remove()
                continue
            }
            update(f, now)
            drawFish(canvas, f)
        }

        // A second, see-through wave in front, so the fish swim inside the water.
        wavePath(wave, waterline + 3f * density, 4f * density, 30f * density, -now / 900f + 1.5f)
        canvas.drawPath(wave, fill.apply { color = 0x33FFFFFF })
        drawSplashes(canvas, now)
        postInvalidateOnAnimation()
    }

    /** Where a fish is this frame: bobbing along, or in the middle of a leap. */
    private fun update(f: Fish, now: Long) {
        val bob = sin(now / 700f + f.phase) * f.size * 0.06f
        f.angle = sin(now / 450f + f.phase) * 3f
        f.drawnX = f.x
        f.drawnY = f.baseY + bob
        if (f.jumpAt < 0L) return
        val k = (now - f.jumpAt) / JUMP_MS.toFloat()
        if (k >= 1f) {
            f.jumpAt = -1L
            f.stage = 0
            return
        }
        val dir = if (f.speed >= 0f) 1f else -1f
        // Up through the surface to a little above it, then back down in an arc.
        f.drawnY = f.baseY - sin(k * PI.toFloat()) * (f.baseY - waterline + height * 0.1f)
        f.angle = dir * (-30f + 60f * k)
        if (f.stage == 0 && k >= 0.15f) {
            f.stage = 1
            splashes += Splash(f.x, waterline, now, true)
        } else if (f.stage == 1 && k >= 0.85f) {
            f.stage = 2
            splashes += Splash(f.x, waterline, now, true)
            host.sounds.pop(1.3f)
        }
    }

    private fun wavePath(p: Path, baseY: Float, amp: Float, len: Float, shift: Float) {
        p.rewind()
        p.moveTo(0f, height.toFloat())
        val step = 12f * density
        var x = 0f
        p.lineTo(0f, baseY + amp * sin(shift))
        while (x <= width) {
            x += step
            p.lineTo(x, baseY + amp * sin(x / len + shift))
        }
        p.lineTo(x, height.toFloat())
        p.close()
    }

    private fun drawSky(c: Canvas, now: Long) {
        c.drawColor(0xFFB3E5FC.toInt())
        // The sun, with a smile.
        val sx = width * 0.86f
        val sy = waterline * 0.55f
        val sr = waterline * 0.3f
        fill.color = 0xFFFFD54F.toInt()
        c.drawCircle(sx, sy, sr, fill)
        c.drawCircle(sx, sy, sr, ink)
        fill.color = Palette.INK
        c.drawCircle(sx - sr * 0.3f, sy - sr * 0.15f, sr * 0.09f, fill)
        c.drawCircle(sx + sr * 0.3f, sy - sr * 0.15f, sr * 0.09f, fill)
        box.set(sx - sr * 0.35f, sy - sr * 0.1f, sx + sr * 0.35f, sy + sr * 0.5f)
        c.drawArc(box, 25f, 130f, false, ink)
        // One cloud, drifting very slowly.
        val cx = (width * 0.2f + now * 0.004f * density) % (width + 200f * density) - 100f * density
        val cy = waterline * 0.45f
        val cr = waterline * 0.18f
        fill.color = Palette.PAPER
        c.drawCircle(cx, cy, cr, fill)
        c.drawCircle(cx + cr * 1.1f, cy - cr * 0.3f, cr * 1.2f, fill)
        c.drawCircle(cx + cr * 2.3f, cy, cr, fill)
        box.set(cx - cr, cy, cx + cr * 3.3f, cy + cr)
        c.drawRoundRect(box, cr * 0.5f, cr * 0.5f, fill)
    }

    private fun drawSeabed(c: Canvas, now: Long) {
        fill.color = 0xFFFFCC80.toInt()
        for (i in 0 until 9) {
            val px = width * (i + 0.5f) / 9f
            val py = height - sandH * (0.3f + ((i * 37) % 5) * 0.1f)
            c.drawCircle(px, py, (3f + (i % 3) * 1.5f) * density, fill)
        }
        val strands = floatArrayOf(0.08f, 0.27f, 0.55f, 0.74f, 0.93f)
        for ((i, fx) in strands.withIndex()) {
            val x = width * fx
            val y0 = sandY(x) + 4f * density
            val len = height * (0.14f + (i % 3) * 0.03f)
            val t = now / 800f + i * 1.7f
            path.rewind()
            path.moveTo(x, y0)
            path.quadTo(x + sin(t) * len * 0.18f, y0 - len * 0.5f, x + sin(t + 1f) * len * 0.12f, y0 - len)
            c.drawPath(path, weed)
        }
    }

    /** Little bubbles rising from the sand, only for company. */
    private fun drawBubbles(c: Canvas, now: Long) {
        val top = waterline + 8f * density
        val bottom = height - sandH
        for (i in 0 until 7) {
            val period = 7000L + i * 1300L
            val k = ((now + i * 977L) % period) / period.toFloat()
            val x = width * (0.08f + i * 0.14f) + sin(k * 12f + i) * 8f * density
            val y = bottom - k * (bottom - top)
            fill.color = 0x55FFFFFF
            c.drawCircle(x, y, (4f + (i % 3) * 2f) * density, fill)
            c.drawCircle(x, y, (4f + (i % 3) * 2f) * density, foam)
        }
    }

    private fun drawFish(c: Canvas, f: Fish) {
        val dir = if (f.speed >= 0f) 1f else -1f
        c.save()
        c.translate(f.drawnX, f.drawnY)
        c.rotate(f.angle)
        if (f.picture == null) {
            val s = f.size / 100f
            c.scale(dir * s, s)
            drawFishBody(c, f.color)
        } else {
            drawBubbleFish(c, f)
        }
        c.restore()
    }

    /** A round, smiling fish facing right, in a 100 x 60 box around (0, 0). */
    private fun drawFishBody(c: Canvas, color: Int) {
        val dark = Palette.mix(color, Palette.INK, 0.2f)
        path.rewind()
        path.moveTo(-28f, 0f)
        path.lineTo(-52f, -22f)
        path.quadTo(-44f, 0f, -52f, 22f)
        path.close()
        fill.color = dark
        c.drawPath(path, fill)
        c.drawPath(path, unitInk)

        path.rewind()
        path.moveTo(-10f, -22f)
        path.quadTo(6f, -44f, 24f, -22f)
        path.close()
        c.drawPath(path, fill)
        c.drawPath(path, unitInk)

        box.set(-42f, -26f, 42f, 26f)
        fill.color = color
        c.drawOval(box, fill)
        c.drawOval(box, unitInk)
        // A shine on the back, and a stripe.
        fill.color = 0x66FFFFFF
        box.set(-24f, -20f, 12f, -9f)
        c.drawOval(box, fill)
        fill.color = Palette.mix(color, Color.WHITE, 0.45f)
        box.set(-18f, -4f, -6f, 24f)
        c.drawOval(box, fill)
        // The eye and the smile.
        fill.color = Palette.PAPER
        c.drawCircle(22f, -6f, 8f, fill)
        c.drawCircle(22f, -6f, 8f, unitInk)
        fill.color = Palette.INK
        c.drawCircle(24f, -6f, 4f, fill)
        box.set(24f, 0f, 40f, 14f)
        c.drawArc(box, 20f, 140f, false, unitInk)
    }

    /** A sea creature's picture in a round bubble, centered on (0, 0). */
    private fun drawBubbleFish(c: Canvas, f: Fish) {
        val picture = f.picture ?: return
        val r = f.size / 2f
        clip.rewind()
        clip.addCircle(0f, 0f, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        fill.color = Palette.PAPER
        c.drawCircle(0f, 0f, r, fill)
        box.set(-r, -r, r, r)
        c.drawBitmap(picture, null, box, bitmapPaint)
        fill.color = 0x224FC3F7
        c.drawCircle(0f, 0f, r, fill)
        c.restore()
        c.drawCircle(0f, 0f, r, rim)
        fill.color = 0xCCFFFFFF.toInt()
        c.drawOval(-r * 0.62f, -r * 0.7f, -r * 0.2f, -r * 0.42f, fill)
    }

    private fun drawSplashes(c: Canvas, now: Long) {
        val it = splashes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val t = (now - s.at) / SPLASH_MS.toFloat()
            if (t >= 1f) {
                it.remove()
                continue
            }
            val alpha = ((1f - t) * 255).toInt()
            // A ring spreading out over the surface.
            foam.alpha = (alpha * 0.7f).toInt()
            val rx = (14f + t * 46f) * density
            box.set(s.x - rx, s.y - rx * 0.28f, s.x + rx, s.y + rx * 0.28f)
            c.drawOval(box, foam)
            foam.alpha = 0xAA
            if (!s.drops) continue
            // Drops thrown up in a fan, falling back.
            val unit = min(width, height).toFloat()
            val seconds = (now - s.at) / 1000f
            fill.color = Palette.mix(0xFFFFFFFF.toInt(), 0xFF81D4FA.toInt(), 0.3f)
            fill.alpha = alpha
            for (j in 0 until 7) {
                val a = PI.toFloat() * (0.15f + 0.7f * j / 6f)
                val v = unit * 0.55f * (0.75f + 0.25f * (j % 3))
                val dx = cos(a) * v * seconds
                val dy = -sin(a) * v * seconds + 0.5f * unit * 2.4f * seconds * seconds
                c.drawCircle(s.x + dx, s.y + dy, (3f + (j % 3)) * density * (1f - t * 0.5f), fill)
            }
            fill.alpha = 255
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        val now = SystemClock.uptimeMillis()
        val x = e.getX(e.actionIndex)
        val y = e.getY(e.actionIndex)
        // The one drawn last is on top.
        val hit = fishes.lastOrNull { hypot(x - it.drawnX, y - it.drawnY) < max(it.size * 0.55f, 44f * density) }
        if (hit == null) {
            // Nothing there: a soft plip and a ripple, so every tap answers.
            host.sounds.pop(0.7f + random.nextFloat() * 0.4f)
            if (y > waterline) splashes += Splash(x, y, now, false)
            return true
        }
        if (hit.jumpAt >= 0L) return true
        hit.jumpAt = now
        hit.stage = 0
        host.sounds.bloop()
        val title = hit.title
        host.say(if (title != null) "$title!" else context.getString(R.string.g_fishing_splash))
        catches++
        if (catches % CHEER_EVERY == 0) postDelayed(cheerLater, 700L)
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_FISH = 6
        const val JUMP_MS = 1000L
        const val SPLASH_MS = 650L
        const val CHEER_EVERY = 8
        /** The sea creatures that swim by in bubbles. */
        val CREATURES = listOf("Fish", "Whale", "Octopus", "Crab", "Turtle", "Jellyfish", "Starfish")
    }
}
