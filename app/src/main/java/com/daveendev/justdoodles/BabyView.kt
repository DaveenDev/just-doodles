package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Baby mode, for ages 1 to 2: no buttons, nothing to choose and nothing to clear.
 * Any touch makes a shape bloom with a soft note, and dragging leaves a rainbow trail
 * that plays a little tune and slowly fades away, so the screen never fills up.
 * Babies under two don't use tools yet, so cause and effect is the whole game here.
 */
class BabyView(context: Context) : View(context) {

    var sounds: Sounds? = null

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private class Finger(var x: Float, var y: Float, var hue: Float) {
        var sinceBloom = 0f
    }

    private class Bloom(
        val x: Float, val y: Float, val shape: Int, val color: Int,
        val size: Float, val spin: Float, val born: Long,
    )

    private val fingers = SparseArray<Finger>()
    private val blooms = ArrayList<Bloom>()

    // Trail pieces, oldest first, in a ring buffer.
    private val segX0 = FloatArray(MAX_SEGMENTS)
    private val segY0 = FloatArray(MAX_SEGMENTS)
    private val segX1 = FloatArray(MAX_SEGMENTS)
    private val segY1 = FloatArray(MAX_SEGMENTS)
    private val segColor = IntArray(MAX_SEGMENTS)
    private val segBorn = LongArray(MAX_SEGMENTS)
    private var segStart = 0
    private var segCount = 0

    /** Where on the five-note scale the music is; it wanders up and down like a tune. */
    private var noteIndex = 5

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 30f * resources.displayMetrics.density
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePath = Path()
    private val matrix = Matrix()

    init {
        contentDescription = "Baby mode: touch anywhere"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val f = Finger(e.getX(i), e.getY(i), random.nextFloat() * 360f)
                fingers.put(e.getPointerId(i), f)
                bloom(f.x, f.y, now)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val f = fingers[e.getPointerId(i)] ?: continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    val d = hypot(x - f.x, y - f.y)
                    if (d < 2f) continue
                    f.hue = (f.hue + d / density * 0.6f) % 360f
                    addSegment(f.x, f.y, x, y, Color.HSVToColor(floatArrayOf(f.hue, 0.75f, 1f)), now)
                    f.x = x
                    f.y = y
                    f.sinceBloom += d
                    if (f.sinceBloom > 110f * density) {
                        f.sinceBloom = 0f
                        bloom(x, y, now)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> fingers.remove(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> fingers.clear()
        }
        invalidate()
        return true
    }

    private fun bloom(x: Float, y: Float, now: Long) {
        blooms += Bloom(
            x, y,
            shape = random.nextInt(4),
            color = BRIGHT[random.nextInt(BRIGHT.size)],
            size = (34f + random.nextFloat() * 30f) * density,
            spin = random.nextFloat() * 120f - 60f,
            born = now,
        )
        if (blooms.size > 60) blooms.removeAt(0)
        noteIndex = (noteIndex + random.nextInt(-2, 3)).coerceIn(0, 11)
        sounds?.note(noteIndex, 0.6f)
    }

    private fun addSegment(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, now: Long) {
        val i = (segStart + segCount) % MAX_SEGMENTS
        if (segCount == MAX_SEGMENTS) segStart = (segStart + 1) % MAX_SEGMENTS else segCount++
        segX0[i] = x0; segY0[i] = y0; segX1[i] = x1; segY1[i] = y1
        segColor[i] = color
        segBorn[i] = now
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        canvas.drawColor(BACKGROUND)
        drawFloaters(canvas, now)

        // Forget trail pieces that have fully faded.
        while (segCount > 0 && now - segBorn[segStart] > TRAIL_MS) {
            segStart = (segStart + 1) % MAX_SEGMENTS
            segCount--
        }
        for (k in 0 until segCount) {
            val i = (segStart + k) % MAX_SEGMENTS
            val life = 1f - (now - segBorn[i]).toFloat() / TRAIL_MS
            trailPaint.color = segColor[i]
            trailPaint.alpha = (255 * min(1f, life * 1.5f)).toInt()
            canvas.drawLine(segX0[i], segY0[i], segX1[i], segY1[i], trailPaint)
        }

        blooms.removeAll { now - it.born > BLOOM_MS }
        for (b in blooms) drawBloom(canvas, b, now)

        // Keep animating: the background bubbles always drift, which invites a touch.
        postInvalidateOnAnimation()
    }

    private fun drawBloom(canvas: Canvas, b: Bloom, now: Long) {
        val age = (now - b.born) / 1000f
        val grow = min(1f, age / 0.35f)
        val q = grow - 1f
        val pop = 1f + 3.2f * q * q * q + 2.2f * q * q // springy "boing" into place
        val fade = ((BLOOM_MS / 1000f - age) / 0.8f).coerceIn(0f, 1f)
        val r = b.size * pop
        matrix.setScale(r, r)
        matrix.postRotate(b.spin * age)
        matrix.postTranslate(b.x, b.y - (1f - fade) * 40f * density)
        shapePath.rewind()
        when (b.shape) {
            0 -> Stamps.star.transform(matrix, shapePath)
            1 -> Stamps.heart.transform(matrix, shapePath)
            2 -> Stamps.shape(StampKind.FLOWER).transform(matrix, shapePath)
            else -> shapePath.addCircle(b.x, b.y - (1f - fade) * 40f * density, r * 0.85f, Path.Direction.CW)
        }
        fill.color = b.color
        fill.alpha = (255 * fade).toInt()
        canvas.drawPath(shapePath, fill)
        if (b.shape == 2) {
            fill.color = Color.WHITE
            fill.alpha = (255 * fade).toInt()
            canvas.drawCircle(b.x, b.y - (1f - fade) * 40f * density, r * 0.32f, fill)
        }
    }

    /** A few big soft bubbles drifting up behind everything. */
    private fun drawFloaters(canvas: Canvas, now: Long) {
        val t = now / 1000f
        for (i in 0 until 7) {
            val r = (30f + (i * 37 % 40)) * density
            val speed = 0.03f + (i % 3) * 0.015f
            val progress = ((i * 0.37f) + t * speed) % 1f
            val y = height + r - progress * (height + 2 * r)
            val x = width * ((i * 0.618f) % 1f) + sin(t * 0.8f + i) * 20f * density
            fill.color = BRIGHT[i % BRIGHT.size]
            fill.alpha = 40
            canvas.drawCircle(x, y, r, fill)
        }
    }

    private companion object {
        const val MAX_SEGMENTS = 3000
        const val TRAIL_MS = 3500L
        const val BLOOM_MS = 2200L
        const val BACKGROUND = 0xFFFFF4E3.toInt()

        val BRIGHT = intArrayOf(
            0xFFE53935.toInt(), 0xFFFF8F00.toInt(), 0xFFFDD835.toInt(), 0xFF7CB342.toInt(),
            0xFF29B6F6.toInt(), 0xFF2962FF.toInt(), 0xFF8E24AA.toInt(), 0xFFFF6FB0.toInt(),
        )
    }
}
