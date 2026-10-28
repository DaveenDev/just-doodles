package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
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
 * Stars: a night sky with a sleepy moon. Smiling stars drift slowly down; a tap catches one, and it
 * swoops into the glass jar at the bottom with a sparkle and a note, each one a step higher up the
 * scale. When the jar is full of glowing stars it shines, cheers, and starts again empty. Nothing
 * is ever missed or lost, and every finger can catch a star.
 */
@SuppressLint("ViewConstructor")
class StarsView(context: Context, private val host: GameHost) : View(context) {

    private class Star(val x: Float, var y: Float, val r: Float, val speed: Float, val phase: Float)

    /** A caught star on its way to its place [slot] in the jar. */
    private class Flyer(val fromX: Float, val fromY: Float, val r: Float, val slot: Int, val at: Long)

    private class Glint(val x: Float, val y: Float, val vx: Float, val vy: Float, val size: Float, val at: Long)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val words: Array<String> = resources.getStringArray(R.array.g_stars_words)

    private val stars = ArrayList<Star>()
    private val flyers = ArrayList<Flyer>()
    private val glints = ArrayList<Glint>()
    private var nextSpawnAt = 0L
    private var lastFrame = 0L
    private var caught = 0
    private val cheerLater = Runnable { host.cheer() }

    /** Stars that have landed in the jar, and stars sent so far (landed or still flying). */
    private var jarStars = 0
    private var sent = 0
    /** When the jar filled up, or 0 when it is filling. It shines for a moment, then empties. */
    private var fullAt = 0L

    // Layout, worked out in onSizeChanged.
    private var moonX = 0f
    private var moonY = 0f
    private var moonR = 0f
    private val jar = RectF()
    private var mouthX = 0f
    private var mouthY = 0f
    private var jarStarR = 0f
    private val slotX = FloatArray(JAR_STARS)
    private val slotY = FloatArray(JAR_STARS)
    private var dotX = FloatArray(0)
    private var dotY = FloatArray(0)
    private var dotPhase = FloatArray(0)

    private val sky = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        pathEffect = CornerPathEffect(4f * density)
    }
    private val starEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFF8F00.toInt()
        pathEffect = CornerPathEffect(4f * density)
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        color = Palette.INK
    }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = 0xDDFFFFFF.toInt()
    }
    private val box = RectF()
    private val path = Path()

    init {
        contentDescription = context.getString(R.string.game_stars)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        sky.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0B1033.toInt(), 0xFF3949AB.toInt(), Shader.TileMode.CLAMP)
        stars.clear()
        flyers.clear()
        glints.clear()
        jarStars = 0
        sent = 0
        fullAt = 0L
        nextSpawnAt = 0L
        if (w == 0 || h == 0) return

        val s = min(w, h).toFloat()
        moonR = s * 0.13f
        moonX = w - moonR * 1.7f
        moonY = moonR * 1.7f

        val jarH = min(h * 0.32f, w * 0.45f)
        val jarW = jarH * 0.8f
        jar.set(w / 2f - jarW / 2f, h - jarH - 10f * density, w / 2f + jarW / 2f, h - 10f * density)
        mouthX = jar.centerX()
        mouthY = jar.top + jarH * 0.12f
        jarStarR = jarW * 0.15f
        for (i in 0 until JAR_STARS) {
            val row = i / 3
            val col = i % 3
            slotX[i] = jar.left + jarW * (0.24f + 0.26f * col) + (random.nextFloat() - 0.5f) * jarW * 0.06f
            slotY[i] = jar.bottom - jarH * (0.15f + 0.18f * row)
        }

        // A sprinkle of faraway stars that just twinkle.
        val count = 45
        dotX = FloatArray(count) { random.nextFloat() * w }
        dotY = FloatArray(count) { random.nextFloat() * h * 0.85f }
        dotPhase = FloatArray(count) { random.nextFloat() * 6.3f }

        for (k in 0 until 3) spawn(h * (0.1f + 0.2f * k))
        invalidate()
    }

    /** One more falling star, entering above the top edge unless [atY] says where to start. */
    private fun spawn(atY: Float = Float.NaN) {
        if (width == 0 || height == 0) return
        val r = min(width, height) * (0.075f + random.nextFloat() * 0.025f)
        val x = r + random.nextFloat() * (width - 2 * r)
        val y = if (atY.isNaN()) -r * 1.5f else atY
        val speed = height / (9f + random.nextFloat() * 5f) // Pixels a second: 9 to 14 seconds to cross.
        stars += Star(x, y, r, speed, random.nextFloat() * 6f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
        drawSkyDots(canvas, now)
        drawMoon(canvas)

        if (stars.size < MAX_STARS && now >= nextSpawnAt) {
            spawn()
            nextSpawnAt = now + 1100L + random.nextLong(900L)
        }
        val it = stars.iterator()
        while (it.hasNext()) {
            val star = it.next()
            star.y += star.speed * dt
            if (star.y > height + star.r * 2f) {
                it.remove()
                continue
            }
            val x = star.x + sin(now / 900f + star.phase) * star.r * 0.5f
            drawStar(canvas, x, star.y, star.r, sin(now / 700f + star.phase) * 0.25f, true, 1f)
        }

        updateJar(now)
        drawJar(canvas, now)
        drawFlyers(canvas, now)
        drawGlints(canvas, now)
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun drawSkyDots(c: Canvas, now: Long) {
        fill.color = 0xFFFFFFFF.toInt()
        for (i in dotX.indices) {
            val twinkle = 0.5f + 0.5f * sin(now / 600f + dotPhase[i])
            fill.alpha = (70 + 150 * twinkle).toInt()
            c.drawCircle(dotX[i], dotY[i], (1.2f + 1.3f * twinkle) * density, fill)
        }
        fill.alpha = 255
    }

    /** The moon: a big soft glow around a pale face with closed, sleepy eyes. */
    private fun drawMoon(c: Canvas) {
        fill.color = 0x14FFF59D
        c.drawCircle(moonX, moonY, moonR * 2.0f, fill)
        fill.color = 0x22FFF59D
        c.drawCircle(moonX, moonY, moonR * 1.5f, fill)
        fill.color = 0xFFFFF59D.toInt()
        c.drawCircle(moonX, moonY, moonR, fill)
        fill.color = 0x40FFCA28
        c.drawCircle(moonX - moonR * 0.4f, moonY + moonR * 0.45f, moonR * 0.22f, fill)
        c.drawCircle(moonX + moonR * 0.45f, moonY - moonR * 0.5f, moonR * 0.16f, fill)
        c.drawCircle(moonX + moonR * 0.5f, moonY + moonR * 0.4f, moonR * 0.12f, fill)
        for (sign in floatArrayOf(-1f, 1f)) {
            box.set(moonX + sign * moonR * 0.36f - moonR * 0.17f, moonY - moonR * 0.28f, moonX + sign * moonR * 0.36f + moonR * 0.17f, moonY + moonR * 0.08f)
            c.drawArc(box, 20f, 140f, false, ink)
            fill.color = 0x55FF6FB0
            c.drawCircle(moonX + sign * moonR * 0.58f, moonY + moonR * 0.28f, moonR * 0.13f, fill)
        }
        box.set(moonX - moonR * 0.18f, moonY + moonR * 0.1f, moonX + moonR * 0.18f, moonY + moonR * 0.45f)
        c.drawArc(box, 20f, 140f, false, ink)
    }

    /** Lands arrived stars in the jar, and empties it a moment after it fills. */
    private fun updateJar(now: Long) {
        val it = flyers.iterator()
        while (it.hasNext()) {
            val f = it.next()
            if (now - f.at < FLY_MS) continue
            it.remove()
            if (f.slot >= JAR_STARS) continue // The jar was already full: it just fades into the glow.
            jarStars++
            host.sounds.sparkle()
            glint(slotX[f.slot], slotY[f.slot], 5, now)
            if (jarStars >= JAR_STARS && fullAt == 0L) {
                fullAt = now
                postDelayed(cheerLater, 200L)
            }
        }
        if (fullAt != 0L && now - fullAt > FULL_MS) {
            // Empty: the stars float up and out in a shower of sparkles, and the jar starts again.
            for (i in 0 until JAR_STARS) glint(slotX[i], slotY[i], 2, now)
            jarStars = 0
            sent = 0
            fullAt = 0L
        }
    }

    private fun drawJar(c: Canvas, now: Long) {
        val w = jar.width()
        val neckL = jar.left + w * 0.12f
        val neckR = jar.right - w * 0.12f
        val shine = if (fullAt != 0L) 0.5f + 0.5f * sin(now / 140f) else 0f

        // A warm glow inside that grows with the stars.
        val glow = jarStars / JAR_STARS.toFloat() + shine * 0.6f
        fill.color = 0xFFFFE082.toInt()
        fill.alpha = (20 + 70 * glow.coerceAtMost(1.4f)).toInt().coerceIn(0, 255)
        c.drawCircle(jar.centerX(), jar.centerY(), jar.height() * (0.55f + 0.3f * glow), fill)
        fill.alpha = 255

        // The glass: a rounded body with a narrower neck and a rim.
        path.rewind()
        path.moveTo(neckL, jar.top)
        path.lineTo(neckL, jar.top + jar.height() * 0.14f)
        path.cubicTo(jar.left, jar.top + jar.height() * 0.2f, jar.left, jar.top + jar.height() * 0.3f, jar.left, jar.top + jar.height() * 0.4f)
        path.lineTo(jar.left, jar.bottom - w * 0.18f)
        path.quadTo(jar.left, jar.bottom, jar.left + w * 0.18f, jar.bottom)
        path.lineTo(jar.right - w * 0.18f, jar.bottom)
        path.quadTo(jar.right, jar.bottom, jar.right, jar.bottom - w * 0.18f)
        path.lineTo(jar.right, jar.top + jar.height() * 0.4f)
        path.cubicTo(jar.right, jar.top + jar.height() * 0.3f, jar.right, jar.top + jar.height() * 0.2f, neckR, jar.top + jar.height() * 0.14f)
        path.lineTo(neckR, jar.top)
        fill.color = 0x26FFFFFF
        c.drawPath(path, fill)

        // The stars inside, glowing.
        for (i in 0 until min(jarStars, JAR_STARS)) {
            val pulse = 1f + 0.08f * sin(now / 300f + i) + shine * 0.12f
            fill.color = 0x33FFE082
            c.drawCircle(slotX[i], slotY[i], jarStarR * (1.5f + shine * 0.5f), fill)
            drawStar(c, slotX[i], slotY[i], jarStarR * pulse, 0f, false, 1f)
        }

        c.drawPath(path, glass)
        // The rim, and a shine down the side.
        box.set(neckL - w * 0.06f, jar.top - 8f * density, neckR + w * 0.06f, jar.top + 8f * density)
        c.drawRoundRect(box, 8f * density, 8f * density, glass)
        fill.color = 0x55FFFFFF
        box.set(jar.left + w * 0.1f, jar.top + jar.height() * 0.45f, jar.left + w * 0.17f, jar.bottom - w * 0.3f)
        c.drawRoundRect(box, w * 0.035f, w * 0.035f, fill)
    }

    private fun drawFlyers(c: Canvas, now: Long) {
        for (f in flyers) {
            val t = ((now - f.at) / FLY_MS.toFloat()).coerceIn(0f, 1f)
            val e = t * t * (3f - 2f * t)
            val toX = if (f.slot < JAR_STARS) slotX[f.slot] else mouthX
            val toY = if (f.slot < JAR_STARS) slotY[f.slot] else mouthY
            val x = f.fromX + (toX - f.fromX) * e
            // Up and over in an arc, then down into the jar.
            val y = f.fromY + (toY - f.fromY) * e - sin(t * Math.PI.toFloat()) * height * 0.1f
            val r = f.r + (jarStarR - f.r) * e
            // A little glowing tail.
            for (k in 1..3) {
                val tt = (t - k * 0.04f).coerceAtLeast(0f)
                val ee = tt * tt * (3f - 2f * tt)
                fill.color = 0xFFFFE082.toInt()
                fill.alpha = 90 - k * 22
                c.drawCircle(
                    f.fromX + (toX - f.fromX) * ee,
                    f.fromY + (toY - f.fromY) * ee - sin(tt * Math.PI.toFloat()) * height * 0.1f,
                    r * (0.8f - k * 0.15f), fill,
                )
            }
            fill.alpha = 255
            drawStar(c, x, y, r, t * 6.2832f, false, 1f)
        }
    }

    private fun drawGlints(c: Canvas, now: Long) {
        val it = glints.iterator()
        while (it.hasNext()) {
            val g = it.next()
            val age = (now - g.at) / 1000f
            val t = age / (GLINT_MS / 1000f)
            if (t >= 1f) {
                it.remove()
                continue
            }
            fill.color = 0xFFFFF59D.toInt()
            fill.alpha = ((1f - t) * 255).toInt()
            c.drawCircle(g.x + g.vx * age, g.y + g.vy * age, g.size * (1f - 0.6f * t), fill)
        }
        fill.alpha = 255
    }

    /** A rounded five-point star at ([cx], [cy]); [face] gives it eyes and a smile. */
    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float, rot: Float, face: Boolean, alpha: Float) {
        path.rewind()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.5f
            val a = rot + i * Math.PI / 5 - Math.PI / 2
            val x = (cx + rad * cos(a)).toFloat()
            val y = (cy + rad * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (face) {
            // A soft glow behind the falling stars, so they are easy to see and to catch.
            fill.color = 0x30FFE082
            c.drawCircle(cx, cy, r * 1.35f, fill)
        }
        starFill.color = 0xFFFFD54F.toInt()
        starFill.alpha = (255 * alpha).toInt()
        c.drawPath(path, starFill)
        c.drawPath(path, starEdge)
        fill.color = 0x99FFFFFF.toInt()
        c.drawCircle(cx - r * 0.25f, cy - r * 0.35f, r * 0.12f, fill)
        if (face) {
            fill.color = Palette.INK
            c.drawCircle(cx - r * 0.22f, cy - r * 0.02f, r * 0.07f, fill)
            c.drawCircle(cx + r * 0.22f, cy - r * 0.02f, r * 0.07f, fill)
            box.set(cx - r * 0.2f, cy - r * 0.04f, cx + r * 0.2f, cy + r * 0.3f)
            c.drawArc(box, 25f, 130f, false, ink)
            fill.color = 0x55FF6FB0
            c.drawCircle(cx - r * 0.4f, cy + r * 0.14f, r * 0.1f, fill)
            c.drawCircle(cx + r * 0.4f, cy + r * 0.14f, r * 0.1f, fill)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        tap(e.getX(e.actionIndex), e.getY(e.actionIndex))
        return true
    }

    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        // The star nearest the finger wins, with a good bit of extra room for big fingers.
        var hit: Star? = null
        var best = Float.MAX_VALUE
        for (s in stars) {
            val sx = s.x + sin(now / 900f + s.phase) * s.r * 0.5f
            val d = (x - sx) * (x - sx) + (y - s.y) * (y - s.y)
            val reach = s.r * 1.7f
            if (d < reach * reach && d < best) {
                best = d
                hit = s
            }
        }
        if (hit == null) {
            // Nothing there: a little twinkle and a soft high note.
            glint(x, y, 6, now)
            host.sounds.note(7 + random.nextInt(5), 0.4f)
            return
        }
        stars.remove(hit)
        val slot = if (sent < JAR_STARS && fullAt == 0L) sent else JAR_STARS
        if (slot < JAR_STARS) sent++
        flyers += Flyer(hit.x + sin(now / 900f + hit.phase) * hit.r * 0.5f, hit.y, hit.r, slot, now)
        // Each catch is a step up the scale, then it starts low again.
        host.sounds.note(2 + (slot.coerceAtMost(JAR_STARS - 1) % 8))
        glint(hit.x, hit.y, 8, now)
        caught++
        if (caught % 2 == 1) host.say(words[random.nextInt(words.size)])
    }

    private fun glint(x: Float, y: Float, count: Int, now: Long) {
        for (k in 0 until count) {
            val a = random.nextFloat() * 6.2832f
            val v = (50f + random.nextFloat() * 110f) * density
            glints += Glint(x, y, cos(a) * v, sin(a) * v, (2.5f + random.nextFloat() * 3f) * density, now)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_STARS = 5
        /** Stars the jar holds before it shines and starts again. */
        const val JAR_STARS = 12
        const val FLY_MS = 750L
        const val FULL_MS = 2200L
        const val GLINT_MS = 700L
    }
}
