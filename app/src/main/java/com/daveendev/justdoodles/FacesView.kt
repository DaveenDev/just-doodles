package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Funny faces: one big round face in the middle. Tapping the eyes, nose, mouth, hair or ears
 * changes that part to the next style with a boing and a word ("Big eyes!", "Smile!"); tapping the
 * cheeks makes them blush; one of the eye styles is a wink, with a sparkle. Tapping the rest of the
 * face makes it giggle and tapping around it changes the background color. There is nothing to
 * get wrong, and every finger can tap at once.
 */
@SuppressLint("ViewConstructor")
class FacesView(context: Context, private val host: GameHost) : View(context) {

    private class Spark(val x: Float, val y: Float, val at: Long)

    private val random = Random(System.nanoTime())
    private val eyeWords = resources.getStringArray(R.array.g_faces_eyes)
    private val noseWords = resources.getStringArray(R.array.g_faces_noses)
    private val mouthWords = resources.getStringArray(R.array.g_faces_mouths)
    private val hairWords = resources.getStringArray(R.array.g_faces_hair)
    private val earWords = resources.getStringArray(R.array.g_faces_ears)

    // The style chosen for each part: eyes, nose, mouth, hair, ears (see the P_ constants).
    private val style = IntArray(5)
    private val pulseAt = LongArray(5) { -10_000L }
    private var hairTaps = 1
    private var background = 0
    private var blush = 0f
    private var blushTarget = 0f
    private var bounceAt = -10_000L
    private var nextBlink = 0L
    private val sparks = ArrayList<Spark>()

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var lastFrame = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    /** The face is drawn in units of its own radius, so these widths are fractions of it. */
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.04f
        color = Palette.INK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
        strokeWidth = 0.07f
    }
    private val path = Path()
    private val clip = Path()
    private val rect = RectF()
    private val heart = Path().apply {
        moveTo(0f, 0.2f)
        cubicTo(-0.32f, -0.05f, -0.15f, -0.27f, 0f, -0.08f)
        cubicTo(0.15f, -0.27f, 0.32f, -0.05f, 0f, 0.2f)
        close()
    }
    private val star = Path().apply {
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val r = if (i % 2 == 0) 0.23f else 0.1f
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    init {
        contentDescription = context.getString(R.string.game_faces)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        radius = min(w * 0.3f, h * 0.38f)
        cx = w / 2f
        // The hair reaches a little over a radius above the head, so nudge the head down to centre it all.
        cy = h / 2f + 0.175f * radius
        invalidate()
    }

    // ---- Touch ------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        if (radius <= 0f) return true
        val i = e.actionIndex
        tap((e.getX(i) - cx) / radius, (e.getY(i) - cy) / radius, SystemClock.uptimeMillis())
        return true
    }

    /** [ux], [uy]: where the finger landed, in face radii from the middle of the face. */
    private fun tap(ux: Float, uy: Float, now: Long) {
        val eyes = min(hypot(ux - EYE_X, uy - EYE_Y), hypot(ux + EYE_X, uy - EYE_Y))
        val cheeks = min(hypot(ux - 0.62f, uy - 0.3f), hypot(ux + 0.62f, uy - 0.3f))
        when {
            eyes < 0.3f -> change(P_EYES, now)
            hypot(ux, uy - 0.12f) < 0.2f -> change(P_NOSE, now)
            abs(ux) < 0.4f && uy > 0.38f && uy < 0.8f -> change(P_MOUTH, now)
            cheeks < 0.22f -> {
                blushTarget = if (blushTarget > 0f) 0f else 1f
                host.sounds.note(9)
                if (blushTarget > 0f) host.say(context.getString(R.string.g_faces_blush))
            }
            abs(ux) > 0.92f && abs(ux) < 1.7f && uy > -0.6f && uy < 0.7f -> change(P_EARS, now)
            uy < -0.4f && hypot(ux, uy) < 1.75f -> change(P_HAIR, now)
            hypot(ux, uy) < 1f -> {
                bounceAt = now
                host.sounds.pop(0.9f + random.nextFloat() * 0.5f)
                if (random.nextInt(3) == 0) host.say(context.getString(R.string.g_faces_giggle))
            }
            else -> {
                background = (background + 1) % BACKGROUNDS.size
                host.sounds.pop(1.2f)
            }
        }
    }

    private fun change(part: Int, now: Long) {
        val word = when (part) {
            P_EYES -> { style[P_EYES] = (style[P_EYES] + 1) % EYE_STYLES; eyeWords.getOrNull(style[P_EYES]) }
            P_NOSE -> { style[P_NOSE] = (style[P_NOSE] + 1) % NOSE_STYLES; noseWords.getOrNull(style[P_NOSE]) }
            P_MOUTH -> { style[P_MOUTH] = (style[P_MOUTH] + 1) % MOUTH_STYLES; mouthWords.getOrNull(style[P_MOUTH]) }
            P_HAIR -> { hairTaps++; hairWords.getOrNull(hairTaps % HAIR_STYLES) }
            else -> { style[P_EARS] = (style[P_EARS] + 1) % EAR_STYLES; earWords.getOrNull(style[P_EARS]) }
        }
        pulseAt[part] = now
        host.sounds.boing()
        if (word != null) host.say(word)
        if (part == P_EYES && style[P_EYES] == WINK) {
            sparks += Spark(cx + EYE_X * radius, cy + EYE_Y * radius, now)
            host.sounds.sparkle()
        }
    }

    // ---- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        blush += (blushTarget - blush) * min(1f, dt * 6f)

        val bg = BACKGROUNDS[background]
        canvas.drawColor(bg)
        fill.color = Palette.mix(bg, Color.WHITE, 0.45f)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawCircle(w * 0.1f, h * 0.2f, min(w, h) * 0.16f, fill)
        canvas.drawCircle(w * 0.92f, h * 0.78f, min(w, h) * 0.22f, fill)
        canvas.drawCircle(w * 0.86f, h * 0.12f, min(w, h) * 0.08f, fill)
        canvas.drawCircle(w * 0.08f, h * 0.88f, min(w, h) * 0.1f, fill)

        if (radius > 0f) {
            val bobY = sin(now / 600f) * 0.012f * radius
            val b = (now - bounceAt) / 500f
            val squash = if (b < 1f) 0.08f * (1f - b) * sin(b * 3f * PIF) else 0f
            canvas.save()
            canvas.translate(cx, cy + bobY)
            canvas.scale(radius * (1f + squash), radius * (1f - squash))
            drawFace(canvas, now)
            canvas.restore()
            drawSparks(canvas, now)
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private fun bump(part: Int, now: Long): Float {
        val t = (now - pulseAt[part]) / 500f
        return if (t >= 1f) 1f else 1f + 0.3f * (1f - t) * sin(t * 3f * PIF)
    }

    private inline fun scaled(c: Canvas, px: Float, py: Float, s: Float, draw: () -> Unit) {
        c.save()
        c.translate(px, py)
        c.scale(s, s)
        c.translate(-px, -py)
        draw()
        c.restore()
    }

    private fun blob(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.color = color
        c.drawOval(l, t, r, b, fill)
        c.drawOval(l, t, r, b, ink)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) = blob(c, x - r, y - r, x + r, y + r, color)

    private fun solid(c: Canvas, p: Path, color: Int) {
        fill.color = color
        c.drawPath(p, fill)
        c.drawPath(p, ink)
    }

    /** Everything is drawn in units of the face radius, with (0, 0) in the middle of the face. */
    private fun drawFace(c: Canvas, now: Long) {
        val hairColor = HAIR_COLORS[hairTaps % HAIR_COLORS.size]
        val hairStyle = hairTaps % HAIR_STYLES

        scaled(c, 0f, -0.9f, bump(P_HAIR, now)) { drawHairBehind(c, hairStyle, hairColor) }
        for (side in -1..1 step 2) {
            c.save()
            c.scale(side.toFloat(), 1f)
            scaled(c, 1f, 0f, bump(P_EARS, now)) { drawEar(c, style[P_EARS]) }
            c.restore()
        }

        // The head.
        fill.color = SKIN
        c.drawCircle(0f, 0f, 1f, fill)
        c.drawCircle(0f, 0f, 1f, ink)
        if (blush > 0.02f) {
            fill.color = Color.argb((blush * 190).toInt(), 0xFF, 0x6F, 0x91)
            c.drawOval(-0.82f, 0.17f, -0.42f, 0.43f, fill)
            c.drawOval(0.42f, 0.17f, 0.82f, 0.43f, fill)
        }

        scaled(c, 0f, -0.9f, bump(P_HAIR, now)) { drawHairFront(c, hairStyle, hairColor) }
        scaled(c, 0f, EYE_Y, bump(P_EYES, now)) { drawEyes(c, now) }
        scaled(c, 0f, 0.12f, bump(P_NOSE, now)) { drawNose(c, style[P_NOSE]) }
        scaled(c, 0f, 0.58f, bump(P_MOUTH, now)) { drawMouth(c, style[P_MOUTH]) }
    }

    private fun drawEar(c: Canvas, which: Int) {
        when (which) {
            0 -> {
                blob(c, 0.88f, -0.2f, 1.18f, 0.2f, SKIN)
                fill.color = EAR_INNER
                c.drawOval(0.95f, -0.1f, 1.1f, 0.1f, fill)
            }
            1 -> {
                blob(c, 0.85f, -0.36f, 1.36f, 0.36f, SKIN)
                fill.color = EAR_INNER
                c.drawOval(0.95f, -0.2f, 1.25f, 0.2f, fill)
            }
            2 -> {
                path.rewind()
                path.moveTo(0.9f, -0.25f)
                path.lineTo(1.5f, -0.7f)
                path.lineTo(1.0f, 0.2f)
                path.close()
                solid(c, path, SKIN)
                path.rewind()
                path.moveTo(1.0f, -0.18f)
                path.lineTo(1.3f, -0.5f)
                path.lineTo(1.03f, 0.08f)
                path.close()
                fill.color = EAR_INNER
                c.drawPath(path, fill)
            }
            3 -> {
                c.save()
                c.rotate(12f, 1f, -0.1f)
                blob(c, 0.9f, -0.15f, 1.28f, 0.72f, DOG_EAR)
                c.restore()
            }
            4 -> {
                circle(c, 0.98f, -0.3f, 0.36f, SKIN)
                fill.color = EAR_INNER
                c.drawCircle(0.98f, -0.3f, 0.22f, fill)
            }
            else -> {
                c.save()
                c.rotate(25f, 1f, -0.2f)
                blob(c, 0.82f, -1.2f, 1.18f, -0.1f, SKIN)
                fill.color = EAR_INNER
                c.drawOval(0.9f, -1.05f, 1.1f, -0.25f, fill)
                c.restore()
            }
        }
    }

    private fun drawHairBehind(c: Canvas, which: Int, color: Int) {
        when (which) {
            2 -> for (side in -1..1 step 2) {
                blob(c, if (side < 0) -1.35f else 0.75f, -1.1f, if (side < 0) -0.75f else 1.35f, -0.5f, color)
            }
            3 -> for (k in 0 until 9) {
                val a = Math.toRadians(190.0 + k * 20.0)
                circle(c, (cos(a) * 0.98).toFloat(), (sin(a) * 0.98).toFloat(), 0.27f, color)
            }
            4 -> for (k in 0 until 7) {
                val a = Math.toRadians(205.0 + k * 21.7)
                val half = Math.toRadians(10.0)
                path.rewind()
                path.moveTo((cos(a - half) * 0.92).toFloat(), (sin(a - half) * 0.92).toFloat())
                path.lineTo((cos(a) * 1.4).toFloat(), (sin(a) * 1.4).toFloat())
                path.lineTo((cos(a + half) * 0.92).toFloat(), (sin(a + half) * 0.92).toFloat())
                path.close()
                solid(c, path, color)
            }
            5 -> {
                fill.color = color
                c.drawRoundRect(-1.12f, -0.9f, 1.12f, 1.0f, 0.5f, 0.5f, fill)
                c.drawRoundRect(-1.12f, -0.9f, 1.12f, 1.0f, 0.5f, 0.5f, ink)
            }
        }
    }

    private fun drawHairFront(c: Canvas, which: Int, color: Int) {
        when (which) {
            0 -> {
                // One curl on a bald head.
                path.rewind()
                path.moveTo(0f, -0.97f)
                path.cubicTo(-0.05f, -1.25f, 0.28f, -1.3f, 0.25f, -1.12f)
                path.cubicTo(0.22f, -1.0f, 0.08f, -1.05f, 0.12f, -1.15f)
                line.color = Palette.INK
                line.strokeWidth = 0.15f
                c.drawPath(path, line)
                line.color = color
                line.strokeWidth = 0.08f
                c.drawPath(path, line)
            }
            1 -> hairCap(c, -0.42f, color, 5)
            2 -> {
                hairCap(c, -0.45f, color, 6)
                for (side in -1..1 step 2) circle(c, 0.82f * side, -0.7f, 0.11f, BOW)
            }
            3 -> hairCap(c, -0.72f, color, 7)
            4 -> hairCap(c, -0.6f, color, 5)
            else -> {
                hairCap(c, -0.4f, color, 4)
                circle(c, 0.5f, -0.95f, 0.15f, BOW)
                circle(c, 0.82f, -0.85f, 0.15f, BOW)
                circle(c, 0.66f, -0.9f, 0.07f, Color.WHITE)
            }
        }
    }

    /** Hair covering the top of the head, down to a scalloped fringe at height [fringeY]. */
    private fun hairCap(c: Canvas, fringeY: Float, color: Int, scallops: Int) {
        clip.rewind()
        clip.addCircle(0f, 0f, 1.03f, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        path.rewind()
        path.moveTo(-1.2f, -1.2f)
        path.lineTo(1.2f, -1.2f)
        path.lineTo(1.2f, fringeY)
        val step = 2.4f / scallops
        for (i in 0 until scallops) {
            path.quadTo(1.2f - (i + 0.5f) * step, fringeY + 0.18f, 1.2f - (i + 1) * step, fringeY)
        }
        path.close()
        solid(c, path, color)
        c.restore()
        val a = Math.toDegrees(Math.asin((-fringeY / 1.03f).toDouble())).toFloat()
        rect.set(-1.03f, -1.03f, 1.03f, 1.03f)
        c.drawArc(rect, 180f + a, 180f - 2f * a, false, ink)
    }

    private fun eyeOpen(now: Long): Float {
        if (nextBlink == 0L) nextBlink = now + 2000L
        val t = now - nextBlink
        if (t < 0L) return 1f
        if (t > BLINK_MS) {
            nextBlink = now + 2200L + random.nextLong(2500L)
            return 1f
        }
        return abs(1f - 2f * t / BLINK_MS)
    }

    private fun drawEyes(c: Canvas, now: Long) {
        val open = eyeOpen(now).coerceAtLeast(0.1f)
        for (side in -1..1 step 2) {
            val ex = EYE_X * side
            when (style[P_EYES]) {
                0 -> roundEye(c, ex, EYE_Y, 0.17f, open)
                1 -> roundEye(c, ex, EYE_Y, 0.26f, open)
                2 -> {
                    fill.color = Palette.INK
                    c.drawOval(ex - 0.08f, EYE_Y - 0.08f * open, ex + 0.08f, EYE_Y + 0.08f * open, fill)
                }
                3 -> happyEye(c, ex)
                4 -> {
                    val beat = 1f + 0.08f * sin(now / 180f)
                    c.save()
                    c.translate(ex, EYE_Y)
                    c.scale(beat, beat)
                    solid(c, heart, HEART_RED)
                    c.restore()
                }
                5 -> {
                    c.save()
                    c.translate(ex, EYE_Y)
                    solid(c, star, STAR_GOLD)
                    c.restore()
                }
                else -> if (side < 0) roundEye(c, ex, EYE_Y, 0.17f, open) else happyEye(c, ex)
            }
        }
    }

    private fun roundEye(c: Canvas, x: Float, y: Float, r: Float, open: Float) {
        c.save()
        c.translate(x, y)
        c.scale(1f, open)
        fill.color = Color.WHITE
        c.drawCircle(0f, 0f, r, fill)
        c.drawCircle(0f, 0f, r, ink)
        fill.color = Palette.INK
        c.drawCircle(0f, 0f, r * 0.55f, fill)
        fill.color = Color.WHITE
        c.drawCircle(-r * 0.2f, -r * 0.22f, r * 0.17f, fill)
        c.restore()
    }

    private fun happyEye(c: Canvas, x: Float) {
        line.color = Palette.INK
        line.strokeWidth = 0.07f
        c.drawArc(x - 0.15f, EYE_Y - 0.08f, x + 0.15f, EYE_Y + 0.2f, 200f, 140f, false, line)
    }

    private fun drawNose(c: Canvas, which: Int) {
        when (which) {
            0 -> circle(c, 0f, 0.12f, 0.11f, 0xFFFF8A65.toInt())
            1 -> {
                path.rewind()
                path.moveTo(0f, 0f)
                path.lineTo(-0.1f, 0.2f)
                path.lineTo(0.1f, 0.2f)
                path.close()
                solid(c, path, 0xFFFF8A65.toInt())
            }
            2 -> blob(c, -0.07f, 0.07f, 0.07f, 0.2f, 0xFFE8A07A.toInt())
            3 -> {
                circle(c, 0f, 0.12f, 0.19f, 0xFFE53935.toInt())
                fill.color = Color.WHITE
                c.drawCircle(-0.07f, 0.05f, 0.04f, fill)
            }
            4 -> {
                blob(c, -0.17f, 0f, 0.17f, 0.24f, 0xFFFFA8B8.toInt())
                fill.color = Palette.INK
                c.drawOval(-0.1f, 0.07f, -0.03f, 0.17f, fill)
                c.drawOval(0.03f, 0.07f, 0.1f, 0.17f, fill)
            }
            else -> {
                path.rewind()
                path.moveTo(-0.1f, 0.04f)
                path.lineTo(0.1f, 0.04f)
                path.lineTo(0f, 0.17f)
                path.close()
                solid(c, path, 0xFFFF6FB0.toInt())
                c.drawLine(0f, 0.17f, 0f, 0.25f, ink)
            }
        }
    }

    private fun drawMouth(c: Canvas, which: Int) {
        when (which) {
            0 -> smile(c)
            1 -> {
                path.rewind()
                path.moveTo(0.32f, 0.5f)
                rect.set(-0.32f, 0.2f, 0.32f, 0.8f)
                path.arcTo(rect, 0f, 180f, false)
                path.close()
                solid(c, path, MOUTH_RED)
                c.save()
                c.clipPath(path)
                fill.color = TONGUE
                c.drawOval(-0.17f, 0.64f, 0.17f, 0.86f, fill)
                c.restore()
            }
            2 -> blob(c, -0.13f, 0.42f, 0.13f, 0.78f, MOUTH_RED)
            3 -> {
                blob(c, -0.12f, 0.58f, 0.12f, 0.9f, TONGUE)
                c.drawLine(0f, 0.62f, 0f, 0.78f, ink)
                smile(c)
            }
            4 -> {
                fill.color = Color.WHITE
                c.drawRoundRect(-0.34f, 0.45f, 0.34f, 0.72f, 0.12f, 0.12f, fill)
                c.drawRoundRect(-0.34f, 0.45f, 0.34f, 0.72f, 0.12f, 0.12f, ink)
                c.drawLine(0f, 0.45f, 0f, 0.72f, ink)
                c.drawLine(-0.17f, 0.45f, -0.17f, 0.72f, ink)
                c.drawLine(0.17f, 0.45f, 0.17f, 0.72f, ink)
                c.drawLine(-0.34f, 0.585f, 0.34f, 0.585f, ink)
            }
            else -> {
                blob(c, -0.16f, 0.46f, 0.16f, 0.74f, 0xFFFF5C8A.toInt())
                c.drawLine(-0.1f, 0.6f, 0.1f, 0.6f, ink)
            }
        }
    }

    private fun smile(c: Canvas) {
        line.color = Palette.INK
        line.strokeWidth = 0.07f
        c.drawArc(-0.28f, 0.3f, 0.28f, 0.72f, 20f, 140f, false, line)
    }

    private fun drawSparks(c: Canvas, now: Long) {
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val t = (now - s.at) / SPARK_MS.toFloat()
            if (t >= 1f) {
                it.remove()
                continue
            }
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                val d = radius * (0.1f + 0.35f * t)
                fill.color = if (k % 2 == 0) STAR_GOLD else Color.WHITE
                c.drawCircle(s.x + d * cos(a).toFloat(), s.y + d * sin(a).toFloat(), radius * 0.05f * (1f - t), fill)
            }
        }
    }

    private companion object {
        const val P_EYES = 0
        const val P_NOSE = 1
        const val P_MOUTH = 2
        const val P_HAIR = 3
        const val P_EARS = 4
        const val EYE_STYLES = 7
        const val NOSE_STYLES = 6
        const val MOUTH_STYLES = 6
        const val HAIR_STYLES = 6
        const val EAR_STYLES = 6
        const val WINK = 6
        const val EYE_X = 0.36f
        const val EYE_Y = -0.2f
        const val BLINK_MS = 160L
        const val SPARK_MS = 700L
        val PIF = Math.PI.toFloat()

        val SKIN = 0xFFFFCC99.toInt()
        const val EAR_INNER = 0xFFFFA8A8.toInt()
        const val DOG_EAR = 0xFFA1662F.toInt()
        const val BOW = 0xFFFF6FB0.toInt()
        const val HEART_RED = 0xFFE53935.toInt()
        const val STAR_GOLD = 0xFFFFD54F.toInt()
        const val MOUTH_RED = 0xFFB71C1C.toInt()
        const val TONGUE = 0xFFFF6F91.toInt()

        val HAIR_COLORS = intArrayOf(
            0xFF8D5524.toInt(), 0xFF263238.toInt(), 0xFFFDD835.toInt(), 0xFFFF8F00.toInt(),
            0xFFFF6FB0.toInt(), 0xFF8E24AA.toInt(), 0xFF29B6F6.toInt(),
        )
        val BACKGROUNDS = intArrayOf(0xFFB3E5FC.toInt(), 0xFFC8E6C9.toInt(), 0xFFE1BEE7.toInt(), 0xFFFFF59D.toInt())
    }
}
