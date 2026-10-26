package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The opening show, played over the paper on a fresh launch:
 * the Just Doodles logo bounces in as a sticker with a sparkle burst, a small DaveenDev credit
 * fades in, a rainbow crayon squiggle draws itself, and "Just Doodles" drops in letter by
 * letter, all among floating bubbles.
 * Then everything fades away to reveal the paper. Any tap skips ahead.
 *
 * Everything is computed from the time since the first frame, so the timeline below
 * is the whole story. The view removes itself when it is done.
 */
@SuppressLint("ViewConstructor")
class SplashView(context: Context) : View(context) {

    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.just_doodles_logo).apply {
        // It's drawn smaller than the file, so let the GPU use smoother scaled-down copies.
        setHasMipMap(true)
    }
    private val devLogo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.daveendev_logo)
    private val density = resources.displayMetrics.density
    private val reduceMotion = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f

    /** Called with 0, 1, 2... as each title letter first hits the ground, for a xylophone run. */
    var onLetterLanded: ((Int) -> Unit)? = null

    private var startAt = -1L
    private var done = false
    private val landed = BooleanArray(TITLE.length)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    // The logo is painted through a shader, so its rounded corners come out smooth.
    private val logoShader = BitmapShader(logo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val logoMatrix = Matrix()
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { shader = logoShader }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private val letterEdge = Paint(letterFill).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val crayon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val sparkle = Path()
    private val squiggle = Path()
    private val squiggleDrawn = Path()
    private val squiggleMeasure = PathMeasure()
    private val tileRect = RectF()

    // Layout, worked out in onSizeChanged.
    /** Side of the logo's square sticker, white edge included. */
    private var logoSize = 0f
    private var logoCx = 0f
    private var logoCy = 0f
    private var letterSize = 0f
    private var titleBaseline = 0f
    private val letterX = FloatArray(TITLE.length)
    /** Where the small DaveenDev credit goes. Left empty, the credit isn't drawn. */
    private val devRect = RectF()

    private class Bubble(val x: Float, val radiusDp: Float, val speed: Float, val offset: Float, val wobble: Float, val color: Int)

    private val bubbles = Random(7).let { r ->
        List(18) { i ->
            Bubble(
                x = r.nextFloat(),
                radiusDp = 6f + r.nextFloat() * 22f,
                speed = 0.10f + r.nextFloat() * 0.14f,
                offset = r.nextFloat(),
                wobble = r.nextFloat() * 2f * PI.toFloat(),
                color = RAINBOW[i % RAINBOW.size],
            )
        }
    }

    init {
        isClickable = true
        contentDescription = "DaveenDev presents Just Doodles"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        letterSize = min(h * 0.13f, w * 0.085f)
        letterFill.textSize = letterSize
        letterEdge.textSize = letterSize
        letterEdge.strokeWidth = letterSize * 0.16f
        titleBaseline = h * 0.75f

        // The logo is a square sticker, centered in the space above the title, with a little
        // air left for its bounce. It is never enlarged more than 1.5x, or it would turn blurry.
        val top = h * 0.04f
        val space = titleBaseline - letterSize * 0.85f - top
        logoSize = minOf(space * 0.92f, w * 0.6f, logo.width * 1.5f)
        logoCx = w / 2f
        logoCy = top + space / 2f
        // Fit the picture inside the sticker's white edge, centered on (0,0) like drawLogo.
        val inner = logoSize * (1f - 2f * TILE_EDGE)
        logoMatrix.setScale(inner / logo.width, inner / logo.height)
        logoMatrix.postTranslate(-inner / 2f, -inner / 2f)
        logoShader.setLocalMatrix(logoMatrix)
        val widths = FloatArray(TITLE.length)
        letterFill.getTextWidths(TITLE, widths)
        var x = (w - widths.sum()) / 2f
        for (i in TITLE.indices) {
            letterX[i] = x
            x += widths[i]
        }

        // A wavy crayon line under the title, from its first letter to its last.
        val left = letterX.first()
        val right = x
        val y = h * 0.87f
        val amp = letterSize * 0.12f
        val wave = letterSize * 0.9f
        squiggle.rewind()
        squiggle.moveTo(left, y)
        var sx = left
        var up = true
        while (sx < right) {
            val next = min(sx + wave, right)
            squiggle.quadTo((sx + next) / 2f, if (up) y - amp * 2 else y + amp * 2, next, y)
            sx = next
            up = !up
        }
        squiggleMeasure.setPath(squiggle, false)
        crayon.strokeWidth = letterSize * 0.14f
        crayon.shader = LinearGradient(left, 0f, right, 0f, RAINBOW, null, Shader.TileMode.CLAMP)

        // The DaveenDev credit: a small "made by" mark in the bottom-right corner, clear of the
        // centered title and squiggle. Its file is only 300x100, so it's never drawn much bigger.
        val devH = min(h * 0.07f, devLogo.height * 1.2f)
        val devW = devH * devLogo.width / devLogo.height
        val margin = 16f * density
        devRect.set(w - margin - devW, h - margin - devH, w - margin, h - margin)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A tap skips straight to the fade-out.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && startAt >= 0) {
            val t = elapsed()
            if (t < FADE_START) startAt -= ((FADE_START - t) * 1000).toLong()
        }
        return true
    }

    private fun elapsed() = (SystemClock.uptimeMillis() - startAt) / 1000f

    override fun onDraw(canvas: Canvas) {
        if (startAt < 0) startAt = SystemClock.uptimeMillis()
        val real = elapsed()
        // With animations turned off in the phone's settings, show the finished picture, then fade.
        val t = if (reduceMotion) SETTLED + max(0f, real - 1f) else real

        canvas.drawColor(BACKGROUND)
        drawBubbles(canvas, t)
        drawCredit(canvas, t)
        drawLogo(canvas, t)
        drawSparkles(canvas, t)
        drawSquiggle(canvas, t)
        drawTitle(canvas, t)

        val fade = ((t - FADE_START) / (END - FADE_START)).coerceIn(0f, 1f)
        alpha = 1f - fade
        scaleX = 1f + fade * 0.08f
        scaleY = scaleX

        if (t >= END) {
            if (!done) {
                done = true
                post { (parent as? ViewGroup)?.removeView(this) }
            }
        } else {
            postInvalidateOnAnimation()
        }
    }

    private fun drawBubbles(canvas: Canvas, t: Float) {
        val h = height.toFloat()
        val appear = (t / 0.4f).coerceIn(0f, 1f)
        for (b in bubbles) {
            val r = b.radiusDp * density
            // Rise from below the bottom edge to above the top, then start again.
            val travel = h + 2 * r
            val progress = (b.offset + t * b.speed) % 1f
            val y = h + r - progress * travel
            val x = b.x * width + sin(t * 1.6f + b.wobble) * 10f * density
            fill.color = b.color
            fill.alpha = (110 * appear).toInt()
            canvas.drawCircle(x, y, r, fill)
            fill.color = 0xFFFFFFFF.toInt()
            fill.alpha = (150 * appear).toInt()
            canvas.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.22f, fill)
        }
    }

    private fun drawLogo(canvas: Canvas, t: Float) {
        val p = ((t - LOGO_START) / 0.75f).coerceIn(0f, 1f)
        if (p <= 0f) return
        var scale = easeOutBack(p, 2.2f)
        // After landing, a gentle "breathing" so it feels alive.
        if (t > LOGO_START + 0.75f) scale *= 1f + 0.015f * sin((t - LOGO_START - 0.75f) * 4f)
        val wiggle = sin(p * PI.toFloat() * 2.5f) * 8f * (1f - p)

        val a = min(1f, p * 3f)
        val half = logoSize / 2f
        val corner = logoSize * TILE_CORNER
        val edge = logoSize * TILE_EDGE
        canvas.save()
        canvas.translate(logoCx, logoCy)
        canvas.rotate(wiggle)
        canvas.scale(scale, scale)
        // Soft shadow, then the white sticker edge, then the picture on top.
        tileRect.set(-half, -half + logoSize * 0.03f, half, half + logoSize * 0.03f)
        fill.color = 0x1A000000
        fill.alpha = (0x1A * a).toInt()
        canvas.drawRoundRect(tileRect, corner, corner, fill)
        tileRect.set(-half, -half, half, half)
        fill.color = 0xFFFFFFFF.toInt()
        fill.alpha = (255 * a).toInt()
        canvas.drawRoundRect(tileRect, corner, corner, fill)
        tileRect.inset(edge, edge)
        logoPaint.alpha = (255 * a).toInt()
        canvas.drawRoundRect(tileRect, corner - edge, corner - edge, logoPaint)
        canvas.restore()
    }

    private fun drawCredit(canvas: Canvas, t: Float) {
        // A quiet fade-in, no bouncing: the credit shouldn't steal the show.
        val p = ((t - CREDIT_START) / 0.5f).coerceIn(0f, 1f)
        if (p <= 0f || devRect.isEmpty) return
        bitmapPaint.alpha = (255 * p).toInt()
        canvas.drawBitmap(devLogo, null, devRect, bitmapPaint)
    }

    private fun drawSparkles(canvas: Canvas, t: Float) {
        // A burst of ten sparkles flying out from the logo...
        val p = ((t - SPARKLE_START) / 0.9f).coerceIn(0f, 1f)
        if (p > 0f && p < 1f) {
            val reach = easeOutCubic(p)
            val size = sin(p * PI.toFloat()) * letterSize * 0.28f
            for (i in 0 until 10) {
                val a = Math.toRadians(i * 36.0 + 18.0)
                val x = logoCx + cos(a).toFloat() * logoSize * 0.78f * reach
                val y = logoCy + sin(a).toFloat() * logoSize * 0.72f * reach
                drawSparkle(canvas, x, y, size, p * 180f, RAINBOW[i % RAINBOW.size])
            }
        }
        // ...then three that keep twinkling just outside its corners.
        if (t > SPARKLE_START + 0.6f) {
            val spots = arrayOf(floatArrayOf(-0.62f, -0.42f), floatArrayOf(0.6f, -0.55f), floatArrayOf(0.62f, 0.4f))
            for ((i, s) in spots.withIndex()) {
                val twinkle = (sin(t * 5f + i * 2.1f) + 1f) / 2f
                val grow = ((t - SPARKLE_START - 0.6f) / 0.3f).coerceIn(0f, 1f)
                drawSparkle(
                    canvas, logoCx + s[0] * logoSize, logoCy + s[1] * logoSize,
                    letterSize * (0.10f + 0.10f * twinkle) * grow, t * 40f, 0xFFFFC107.toInt(),
                )
            }
        }
    }

    private fun drawSparkle(canvas: Canvas, x: Float, y: Float, r: Float, rotation: Float, color: Int) {
        if (r <= 0f) return
        val k = r * 0.3f
        sparkle.rewind()
        sparkle.moveTo(0f, -r)
        sparkle.lineTo(k, -k)
        sparkle.lineTo(r, 0f)
        sparkle.lineTo(k, k)
        sparkle.lineTo(0f, r)
        sparkle.lineTo(-k, k)
        sparkle.lineTo(-r, 0f)
        sparkle.lineTo(-k, -k)
        sparkle.close()
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        fill.color = color
        canvas.drawPath(sparkle, fill)
        canvas.restore()
    }

    private fun drawSquiggle(canvas: Canvas, t: Float) {
        val p = easeInOut(((t - SQUIGGLE_START) / 0.9f).coerceIn(0f, 1f))
        if (p <= 0f) return
        squiggleDrawn.rewind()
        squiggleMeasure.getSegment(0f, squiggleMeasure.length * p, squiggleDrawn, true)
        canvas.drawPath(squiggleDrawn, crayon)
    }

    private fun drawTitle(canvas: Canvas, t: Float) {
        var k = 0
        for (i in TITLE.indices) {
            val ch = TITLE[i]
            if (ch == ' ') continue
            val p = ((t - TITLE_START - k * LETTER_STAGGER) / 0.55f).coerceIn(0f, 1f)
            val color = TITLE_COLORS[k % TITLE_COLORS.size]
            if (p >= FIRST_BOUNCE && !landed[k] && !reduceMotion) {
                landed[k] = true
                onLetterLanded?.invoke(k)
            }
            k++
            if (p <= 0f) continue
            // Each letter drops from above and bounces to a stop.
            val y = titleBaseline - (1f - easeOutBounce(p)) * height * 0.5f
            val a = (255 * min(1f, p * 4f)).toInt()
            val s = ch.toString()
            val x = letterX[i]
            fill.color = 0x30000000
            fill.alpha = a * 0x30 / 255
            fill.typeface = letterFill.typeface
            fill.textSize = letterSize
            canvas.drawText(s, x + letterSize * 0.04f, y + letterSize * 0.06f, fill)
            letterEdge.alpha = a
            canvas.drawText(s, x, y, letterEdge)
            letterFill.color = color
            letterFill.alpha = a
            canvas.drawText(s, x, y, letterFill)
        }
    }

    private companion object {
        const val TITLE = "Just Doodles"

        // Timeline, in seconds.
        const val LOGO_START = 0.15f
        const val CREDIT_START = 0.4f
        const val SPARKLE_START = 0.75f
        const val SQUIGGLE_START = 1.2f
        const val TITLE_START = 1.25f
        const val LETTER_STAGGER = 0.06f
        /** How far into its drop a letter first touches the ground (see [easeOutBounce]). */
        const val FIRST_BOUNCE = 1f / 2.75f
        const val SETTLED = 2.6f
        const val FADE_START = 3.0f
        const val END = 3.4f

        const val BACKGROUND = 0xFFFFF4E3.toInt()

        /** The logo sticker's corner radius and white edge, as fractions of its side. */
        const val TILE_CORNER = 0.2f
        const val TILE_EDGE = 0.035f

        val RAINBOW = intArrayOf(
            0xFFE53935.toInt(), 0xFFFF8F00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
            0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(),
        )

        /** Like [RAINBOW], with a deeper yellow and a pink, so every letter stands out on cream. */
        val TITLE_COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFFFF8F00.toInt(), 0xFFF9A825.toInt(), 0xFF43A047.toInt(),
            0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFFF4F9A.toInt(),
        )

        fun easeOutBack(p: Float, c: Float): Float {
            val q = p - 1f
            return 1f + (c + 1f) * q * q * q + c * q * q
        }

        fun easeOutCubic(p: Float): Float {
            val q = 1f - p
            return 1f - q * q * q
        }

        fun easeInOut(p: Float) = p * p * (3f - 2f * p)

        fun easeOutBounce(p: Float): Float {
            val n = 7.5625f
            val d = 2.75f
            return when {
                p < 1f / d -> n * p * p
                p < 2f / d -> { val q = p - 1.5f / d; n * q * q + 0.75f }
                p < 2.5f / d -> { val q = p - 2.25f / d; n * q * q + 0.9375f }
                else -> { val q = p - 2.625f / d; n * q * q + 0.984375f }
            }
        }
    }
}
