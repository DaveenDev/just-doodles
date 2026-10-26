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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sort the colors: shiny balls along the top and a bucket of each color at the bottom. Picking a
 * ball up says its color; dropping it near the bucket of the same color pops it in with a note.
 * Anywhere else, it just floats back. Once every ball is in, the buckets dance and new balls come.
 */
@SuppressLint("ViewConstructor")
class ColorSortView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { HOME, HELD, RETURNING, DROPPING, IN }

    private class Ball(val color: Int, var homeX: Float, var homeY: Float) {
        var x = homeX
        var y = homeY
        var state = State.HOME
        var pointer = -1
        var grabDx = 0f
        var grabDy = 0f
        var fromX = 0f
        var fromY = 0f
        var movedAt = 0L
        var bucket = -1
        var slot = 0
        val phase = Random.nextFloat() * 6f
    }

    private class Bucket(val color: Int) {
        var cx = 0f
        var rimY = 0f
        var bottomY = 0f
        var width = 0f
        var filled = 0
    }

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val colorNames = resources.getStringArray(R.array.color_names)

    private var balls = emptyList<Ball>()
    private var buckets = emptyList<Bucket>()
    private var ballR = 0f
    private var round = 0
    private var doneAt = -1L
    private val nextRound = Runnable { newRound() }
    private val cheerLater = Runnable { host.cheer() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val path = Path()
    private val rim = RectF()

    init {
        contentDescription = context.getString(R.string.game_color_sort)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        val k = if (round < EASY_ROUNDS) 2 else 3
        val colors = SORT_COLORS.toList().shuffled(random).take(k)
        ballR = min(width / 15f, height * 0.085f)
        val slotW = width / k.toFloat()
        val bucketW = min(slotW * 0.62f, ballR * 6.5f)
        val bucketH = height * 0.3f
        buckets = colors.mapIndexed { i, c ->
            Bucket(c).apply {
                cx = slotW * (i + 0.5f)
                bottomY = height - 14f * density
                rimY = bottomY - bucketH
                width = bucketW
            }
        }
        // Six balls, the same number of each color, in a mixed-up row along the top.
        val ballColors = List(BALLS) { colors[it % k] }.shuffled(random)
        balls = ballColors.mapIndexed { i, c ->
            Ball(c, width * (i + 0.5f) / BALLS, height * 0.2f + (random.nextFloat() - 0.5f) * height * 0.08f)
        }
        doneAt = -1L
        host.sounds.whoosh()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                // The nearest free ball within reach of the finger.
                val ball = balls.filter { it.state == State.HOME || it.state == State.RETURNING }
                    .minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
                    ?.takeIf { abs(it.x - x) < ballR * 1.6f && abs(it.y - y) < ballR * 1.6f }
                if (ball != null) {
                    ball.state = State.HELD
                    ball.pointer = e.getPointerId(i)
                    ball.grabDx = ball.x - x
                    ball.grabDy = ball.y - y
                    host.sounds.pop(1.2f)
                    host.say(colorNames[ball.color])
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i)
                val ball = balls.firstOrNull { it.state == State.HELD && it.pointer == id } ?: continue
                ball.x = e.getX(i) + ball.grabDx
                ball.y = e.getY(i) + ball.grabDy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                balls.firstOrNull { it.state == State.HELD && it.pointer == id }?.let { drop(it, now) }
            }
            MotionEvent.ACTION_CANCEL -> balls.filter { it.state == State.HELD }.forEach { drop(it, now) }
        }
        invalidate()
        return true
    }

    /** In it goes if it's over the bucket of its own color (with plenty of room for error); else home. */
    private fun drop(ball: Ball, now: Long) {
        ball.pointer = -1
        ball.fromX = ball.x
        ball.fromY = ball.y
        ball.movedAt = now
        val bucket = buckets.firstOrNull {
            abs(ball.x - it.cx) < it.width * 0.65f && ball.y > it.rimY - (it.bottomY - it.rimY) * 0.8f
        }
        if (bucket != null && bucket.color == ball.color) {
            ball.state = State.DROPPING
            ball.bucket = buckets.indexOf(bucket)
            ball.slot = bucket.filled++
            host.sounds.bloop()
            host.sounds.note(NOTES[SORT_COLORS.indexOf(ball.color)])
            if (balls.all { it.state == State.DROPPING || it.state == State.IN }) {
                doneAt = now
                round++
                postDelayed(cheerLater, CHEER_DELAY_MS)
                postDelayed(nextRound, NEXT_ROUND_MS)
            }
        } else {
            ball.state = State.RETURNING
            host.sounds.boing()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(nextRound)
        removeCallbacks(cheerLater)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        for (b in balls) {
            val t = ((now - b.movedAt).toFloat() / MOVE_MS).coerceIn(0f, 1f)
            val ease = 1f - (1f - t) * (1f - t)
            when (b.state) {
                State.RETURNING -> {
                    b.x = b.fromX + (b.homeX - b.fromX) * ease
                    b.y = b.fromY + (b.homeY - b.fromY) * ease
                    if (t >= 1f) b.state = State.HOME
                }
                State.DROPPING -> {
                    val (sx, sy) = slotOf(b)
                    b.x = b.fromX + (sx - b.fromX) * ease
                    b.y = b.fromY + (sy - b.fromY) * ease
                    if (t >= 1f) b.state = State.IN
                }
                State.HOME -> {
                    b.x = b.homeX
                    b.y = b.homeY + sin(now / 500f + b.phase) * 4f * density
                }
                else -> Unit
            }
        }

        buckets.forEachIndexed { i, bucket ->
            canvas.save()
            if (doneAt >= 0 && now - doneAt < DANCE_MS) {
                val k = (now - doneAt).toFloat() / DANCE_MS
                canvas.rotate(sin(k * PI.toFloat() * 8f + i) * 7f * (1f - k), bucket.cx, bucket.bottomY)
            }
            drawBucketBack(canvas, bucket)
            for (b in balls) if (b.bucket == i && (b.state == State.IN || b.state == State.DROPPING)) drawBall(canvas, b, 1f)
            drawBucketFront(canvas, bucket)
            canvas.restore()
        }
        for (b in balls) if (b.state == State.HOME || b.state == State.RETURNING) drawBall(canvas, b, 1f)
        for (b in balls) if (b.state == State.HELD) drawBall(canvas, b, 1.12f)

        // Keep animating: the balls bob gently all the time, inviting a touch.
        postInvalidateOnAnimation()
    }

    /** Where a sorted ball sits in its bucket: side by side, peeking over the rim. */
    private fun slotOf(b: Ball): Pair<Float, Float> {
        val bucket = buckets[b.bucket]
        val per = BALLS / buckets.size
        val spread = min(ballR * 1.5f, bucket.width / per)
        return bucket.cx + (b.slot - (per - 1) / 2f) * spread to bucket.rimY - ballR * 0.15f
    }

    private fun drawBall(canvas: Canvas, b: Ball, scale: Float) {
        val r = ballR * scale
        val color = Palette.colors[b.color]
        fill.color = 0x22000000
        canvas.drawCircle(b.x, b.y + r * 0.15f, r, fill)
        fill.color = color
        canvas.drawCircle(b.x, b.y, r, fill)
        canvas.drawCircle(b.x, b.y, r, ink)
        fill.color = 0x99FFFFFF.toInt()
        canvas.drawCircle(b.x - r * 0.35f, b.y - r * 0.35f, r * 0.25f, fill)
    }

    private fun drawBucketBack(canvas: Canvas, bucket: Bucket) {
        val half = bucket.width / 2f
        val rimH = bucket.width * 0.16f
        fill.color = Palette.mix(Palette.colors[bucket.color], Palette.INK, 0.45f)
        canvas.drawOval(bucket.cx - half, bucket.rimY - rimH, bucket.cx + half, bucket.rimY + rimH, fill)
    }

    private fun drawBucketFront(canvas: Canvas, bucket: Bucket) {
        val half = bucket.width / 2f
        val rimH = bucket.width * 0.16f
        val bottomHalf = half * 0.78f
        val color = Palette.colors[bucket.color]
        path.rewind()
        path.moveTo(bucket.cx - half, bucket.rimY)
        // The front half of the rim, then down the sides and round the bottom.
        rim.set(bucket.cx - half, bucket.rimY - rimH, bucket.cx + half, bucket.rimY + rimH)
        path.arcTo(rim, 180f, -180f, false)
        path.lineTo(bucket.cx + bottomHalf, bucket.bottomY - rimH * 0.5f)
        path.quadTo(bucket.cx, bucket.bottomY + rimH * 0.5f, bucket.cx - bottomHalf, bucket.bottomY - rimH * 0.5f)
        path.close()
        fill.color = color
        canvas.drawPath(path, fill)
        // A lighter band around the middle, like a painted stripe.
        canvas.save()
        canvas.clipPath(path)
        fill.color = Palette.mix(color, Color.WHITE, 0.35f)
        val bandY = bucket.rimY + (bucket.bottomY - bucket.rimY) * 0.45f
        canvas.drawRect(bucket.cx - half, bandY, bucket.cx + half, bandY + (bucket.bottomY - bucket.rimY) * 0.14f, fill)
        canvas.restore()
        canvas.drawPath(path, ink)
        // The rim's outline all the way round.
        canvas.drawOval(bucket.cx - half, bucket.rimY - rimH, bucket.cx + half, bucket.rimY + rimH, ink)
    }

    private companion object {
        const val BALLS = 6
        const val EASY_ROUNDS = 3
        const val MOVE_MS = 350L
        const val DANCE_MS = 1200L
        const val CHEER_DELAY_MS = 400L
        const val NEXT_ROUND_MS = 2600L

        /** Red, yellow, blue and green, as indices into [Palette.colors] and `color_names`. */
        val SORT_COLORS = intArrayOf(0, 2, 6, 4)
        /** Each sorting color's note in [Sounds.note]. */
        val NOTES = intArrayOf(4, 6, 8, 5)
    }
}
