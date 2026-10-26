package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Follow the road: a dotted road from a traveler (a car, a butterfly...) to where it's going
 * (a house, a flower...). Dragging along the road takes the traveler with the finger, leaving a
 * rainbow behind it and playing a rising tune. It only moves while the finger stays on the road
 * just ahead of it, so it can't jump to the end. Roads get curlier: straight, wavy, a hill,
 * a zigzag and a loop.
 */
@SuppressLint("ViewConstructor")
class TraceView(context: Context, private val host: GameHost) : View(context) {

    private enum class Road { STRAIGHT, WAVE, HILL, ZIGZAG, LOOP }

    /** Who travels, where to, and their colors. */
    private class Trip(val traveler: Sticker, val travelerTint: Int, val home: Sticker, val homeTint: Int)

    private val density = resources.displayMetrics.density
    private val stickerNames = resources.getStringArray(R.array.sticker_names)
    private val step = 1.5f * density

    private var round = 0
    private var trip = TRIPS[0]
    private val road = Path()
    /** The road as points [step] apart, with the direction it's heading at each one, in degrees. */
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var angles = FloatArray(0)
    /** How far along the road the traveler is, as a point index. */
    private var at = 0
    private var lastTenth = 0
    private var roundAt = 0L
    private var doneAt = -1L

    private val cheerLater = Runnable { host.cheer() }
    private val nextRound = Runnable { newRound() }

    private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 14f * density
        color = 0xFFB0BEC5.toInt()
        pathEffect = DashPathEffect(floatArrayOf(0.1f, 30f * density), 0f)
    }
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 18f * density
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        color = 0xFFFFC107.toInt()
    }
    private val trailPath = Path()
    private val hsv = floatArrayOf(0f, 0.75f, 1f)

    init {
        contentDescription = context.getString(R.string.game_trace)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        trip = TRIPS[round % TRIPS.size]
        buildRoad(Road.entries[round % Road.entries.size])
        val measure = PathMeasure(road, false)
        val length = measure.length
        val n = max(2, (length / step).toInt() + 1)
        xs = FloatArray(n)
        ys = FloatArray(n)
        angles = FloatArray(n)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        for (i in 0 until n) {
            measure.getPosTan(length * i / (n - 1), pos, tan)
            xs[i] = pos[0]
            ys[i] = pos[1]
            angles[i] = (atan2(tan[1], tan[0]) * 180.0 / PI).toFloat()
        }
        at = 0
        lastTenth = 0
        doneAt = -1L
        roundAt = SystemClock.uptimeMillis()
        host.sounds.whoosh()
        invalidate()
    }

    private fun buildRoad(kind: Road) {
        val w = width.toFloat()
        val h = height.toFloat()
        val l = w * 0.1f
        val r = w * 0.9f
        road.rewind()
        when (kind) {
            Road.STRAIGHT -> {
                road.moveTo(l, h * 0.6f)
                road.lineTo(r, h * 0.6f)
            }
            Road.WAVE -> {
                val mid = h * 0.58f
                val a = h * 0.26f
                val seg = (r - l) / 3f
                road.moveTo(l, mid)
                for (i in 0 until 3) {
                    val x0 = l + i * seg
                    val y = mid + (if (i % 2 == 0) -a else a)
                    road.cubicTo(x0 + seg * 0.35f, y, x0 + seg * 0.65f, y, x0 + seg, mid)
                }
            }
            Road.HILL -> {
                val low = h * 0.8f
                val high = h * 0.25f
                road.moveTo(l, low)
                road.cubicTo(l + (r - l) * 0.3f, low, l + (r - l) * 0.32f, high, (l + r) / 2f, high)
                road.cubicTo(l + (r - l) * 0.68f, high, l + (r - l) * 0.7f, low, r, low)
            }
            Road.ZIGZAG -> {
                road.moveTo(l, h * 0.72f)
                for (i in 1..4) road.lineTo(l + (r - l) * i / 4f, if (i % 2 == 1) h * 0.3f else h * 0.72f)
            }
            Road.LOOP -> {
                // Along, once round a loop-the-loop like a roller coaster, and on to the end.
                val base = h * 0.82f
                val radius = h * 0.3f
                val cx = w / 2f
                val loop = RectF(cx - radius, base - 2 * radius, cx + radius, base)
                road.moveTo(l, base)
                road.lineTo(cx, base)
                road.arcTo(loop, 90f, -180f, false)
                road.arcTo(loop, -90f, -180f, false)
                road.lineTo(r, base)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (doneAt >= 0 || xs.isEmpty()) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                var best = at
                for (i in 0 until e.pointerCount) best = max(best, reach(e.getX(i), e.getY(i)))
                if (best > at) advance(best)
            }
        }
        return true
    }

    /** The furthest point, a little way ahead of the traveler, that a finger at (x, y) is on; or where it is now. */
    private fun reach(x: Float, y: Float): Int {
        val tolerance = TOLERANCE_DP * density
        val end = min(xs.size - 1, at + (AHEAD_DP * density / step).toInt())
        var found = at
        for (j in at..end) if (hypot(xs[j] - x, ys[j] - y) <= tolerance) found = j
        return found
    }

    private fun advance(to: Int) {
        at = to
        // A rising note every tenth of the way, up the scale.
        val tenth = at * 10 / (xs.size - 1)
        if (tenth > lastTenth) {
            lastTenth = tenth
            host.sounds.note(1 + tenth, 0.7f)
        }
        if (at >= xs.size - 1) {
            doneAt = SystemClock.uptimeMillis()
            round++
            host.sounds.sparkle()
            host.say(stickerNames[trip.home.ordinal])
            postDelayed(cheerLater, CHEER_DELAY_MS)
            postDelayed(nextRound, NEXT_ROUND_MS)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        removeCallbacks(nextRound)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (xs.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        var busy = false

        canvas.drawPath(road, dots)

        // The rainbow left behind, a few points at a time, each a little further round the colors.
        var s = 0
        while (s < at) {
            val e = min(at, s + 12)
            trailPath.rewind()
            trailPath.moveTo(xs[s], ys[s])
            for (j in s + 1..e) trailPath.lineTo(xs[j], ys[j])
            hsv[0] = (s * step / density / 3f) % 360f
            trail.color = Color.HSVToColor(hsv)
            canvas.drawPath(trailPath, trail)
            s = e
        }

        // Where it's going, sitting at the end of the road; it bounces on arrival.
        val homeSize = min(120f * density, height * 0.28f)
        var homeScale = 1f
        if (doneAt >= 0 && now - doneAt < BOUNCE_MS) {
            val k = (now - doneAt).toFloat() / BOUNCE_MS
            homeScale = 1f + 0.18f * sin(k * PI.toFloat() * 3f) * (1f - k)
            busy = true
        }
        val endX = xs.last()
        val endY = ys.last()
        drawSticker(canvas, trip.home, trip.homeTint, endX, endY - homeSize * 0.4f, homeSize * homeScale)

        // The traveler. Before it has moved, a ring pulses around it: "start here".
        val size = min(96f * density, height * 0.22f)
        if (at == 0) {
            val k = ((now - roundAt) % PULSE_MS).toFloat() / PULSE_MS
            ring.alpha = (255 * (1f - k)).toInt()
            canvas.drawCircle(xs[0], ys[0], size * (0.5f + 0.4f * k), ring)
            busy = true
        }
        val vehicle = trip.traveler == Sticker.CAR
        if (vehicle) {
            // A car keeps its wheels on the road, even upside down round the loop.
            canvas.save()
            canvas.rotate(angles[at], xs[at], ys[at])
            drawSticker(canvas, trip.traveler, trip.travelerTint, xs[at], ys[at] - size * 0.32f, size)
            canvas.restore()
        } else {
            // Everything else bobs along over the road.
            val bob = sin(now / 180f) * 5f * density
            drawSticker(canvas, trip.traveler, trip.travelerTint, xs[at], ys[at] + bob, size)
            busy = true
        }

        if (busy) postInvalidateOnAnimation()
    }

    private fun drawSticker(canvas: Canvas, sticker: Sticker, tint: Int, cx: Float, cy: Float, size: Float) {
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(size / 100f, size / 100f)
        Stickers.draw(canvas, sticker, tint)
        canvas.restore()
    }

    private companion object {
        /** How close to the road a finger must stay, and how far ahead of the traveler it may be. */
        const val TOLERANCE_DP = 45f
        const val AHEAD_DP = 110f
        const val PULSE_MS = 1100L
        const val BOUNCE_MS = 900L
        const val CHEER_DELAY_MS = 900L
        const val NEXT_ROUND_MS = 2800L

        val TRIPS = listOf(
            Trip(Sticker.CAR, 0xFFE53935.toInt(), Sticker.HOUSE, 0xFFFF8F00.toInt()),
            Trip(Sticker.BUTTERFLY, 0xFFFF6FB0.toInt(), Sticker.FLOWER, 0xFF8E24AA.toInt()),
            Trip(Sticker.BALLOON, 0xFF2962FF.toInt(), Sticker.CLOUD, 0xFF29B6F6.toInt()),
            Trip(Sticker.CAT, 0xFFFF8F00.toInt(), Sticker.TREE, 0xFF2E9D4A.toInt()),
        )
    }
}
