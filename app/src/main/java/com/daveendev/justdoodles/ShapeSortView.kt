package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * Sort the shapes: colorful shapes along the top, and a board with a hole for each one below.
 * Picking a shape up says its name; dropping it on its own hole pops it in with a note. Anywhere
 * else, it just floats back. Once every shape is in, a cheer, and the next board has one more.
 */
@SuppressLint("ViewConstructor")
class ShapeSortView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { HOME, HELD, RETURNING, IN }

    private class Piece(val kind: Int, val homeX: Float, val homeY: Float) {
        var x = homeX
        var y = homeY
        var state = State.HOME
        var pointer = -1
        var grabDx = 0f
        var grabDy = 0f
        var fromX = 0f
        var fromY = 0f
        var movedAt = 0L
    }

    private class Hole(val kind: Int, val x: Float, val y: Float)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val names = resources.getStringArray(R.array.sort_shape_names)

    private var pieces = emptyList<Piece>()
    private var holes = emptyList<Hole>()
    private var size = 0f
    private var round = 0
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
    private val matrix = Matrix()

    init {
        contentDescription = context.getString(R.string.game_shape_sort)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        val n = min(START_SHAPES + round, MAX_SHAPES)
        val kinds = KINDS.indices.shuffled(random).take(n)
        size = min(width / (n + 1f) * 0.32f, height * 0.13f)
        val step = width / (n + 1f)
        pieces = kinds.mapIndexed { i, k -> Piece(k, step * (i + 1), height * 0.24f) }
        // The holes in a different order from the shapes, so it's a real match, not just "straight down".
        val order = kinds.shuffled(random).let { if (n > 1 && it == kinds) it.reversed() else it }
        holes = order.mapIndexed { i, k -> Hole(k, step * (i + 1), height * 0.72f) }
        host.sounds.whoosh()
        invalidate()
    }

    /** The outline of shape [kind] (circle, square, triangle, star or heart) around ([cx], [cy]), [r] across half-way. */
    private fun shape(kind: Int, cx: Float, cy: Float, r: Float): Path {
        path.rewind()
        when (kind) {
            CIRCLE -> path.addCircle(cx, cy, r, Path.Direction.CW)
            SQUARE -> path.addRoundRect(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, r * 0.15f, r * 0.15f, Path.Direction.CW)
            TRIANGLE -> {
                path.moveTo(cx, cy - r)
                path.lineTo(cx + r * 1.05f, cy + r * 0.8f)
                path.lineTo(cx - r * 1.05f, cy + r * 0.8f)
                path.close()
            }
            else -> {
                matrix.setScale(r, r)
                matrix.postTranslate(cx, cy)
                Stamps.shape(if (kind == STAR) StampKind.STAR else StampKind.HEART).transform(matrix, path)
            }
        }
        return path
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var moving = false

        // The board, and a dark hole for each shape.
        if (holes.isNotEmpty()) {
            fill.color = 0xFFFFB74D.toInt()
            val top = holes[0].y - size * 1.7f
            val bottom = holes[0].y + size * 1.7f
            canvas.drawRoundRect(size * 0.6f, top, width - size * 0.6f, bottom, size * 0.5f, size * 0.5f, fill)
            canvas.drawRoundRect(size * 0.6f, top, width - size * 0.6f, bottom, size * 0.5f, size * 0.5f, ink)
        }
        fill.color = 0xFF5D4037.toInt()
        for (h in holes) canvas.drawPath(shape(h.kind, h.x, h.y, size * 1.08f), fill)

        // Shapes that are in first, then the rest, and the ones being held on top of everything.
        for (p in pieces.sortedBy { if (it.state == State.HELD) 2 else if (it.state == State.IN) 0 else 1 }) {
            if (p.state == State.RETURNING) {
                val t = ((now - p.movedAt) / RETURN_MS.toFloat()).coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t)
                p.x = p.fromX + (p.homeX - p.fromX) * e
                p.y = p.fromY + (p.homeY - p.fromY) * e
                if (t >= 1f) p.state = State.HOME else moving = true
            }
            val lift = if (p.state == State.HELD) 1.12f else 1f
            fill.color = KINDS[p.kind]
            val outline = shape(p.kind, p.x, p.y, size * lift)
            canvas.drawPath(outline, fill)
            canvas.drawPath(outline, ink)
        }
        if (moving) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val p = pieces.lastOrNull {
                    (it.state == State.HOME || it.state == State.RETURNING) && hypot(x - it.x, y - it.y) < size * 1.4f
                } ?: return true
                p.state = State.HELD
                p.pointer = e.getPointerId(i)
                p.grabDx = p.x - x
                p.grabDy = p.y - y
                host.sounds.pop(1.1f)
                host.say("${names.getOrElse(p.kind) { "" }}!")
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (p in pieces) {
                    if (p.state != State.HELD) continue
                    val i = e.findPointerIndex(p.pointer)
                    if (i < 0) continue
                    p.x = e.getX(i) + p.grabDx
                    p.y = e.getY(i) + p.grabDy
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = e.getPointerId(e.actionIndex)
                for (p in pieces) {
                    if (p.state != State.HELD) continue
                    if (e.actionMasked != MotionEvent.ACTION_CANCEL && p.pointer != id) continue
                    drop(p, cancelled = e.actionMasked == MotionEvent.ACTION_CANCEL)
                }
                invalidate()
            }
        }
        return true
    }

    private fun drop(p: Piece, cancelled: Boolean) {
        val hole = holes.first { it.kind == p.kind }
        if (!cancelled && hypot(p.x - hole.x, p.y - hole.y) < size * 1.1f) {
            p.state = State.IN
            p.x = hole.x
            p.y = hole.y
            host.sounds.note(p.kind * 2 + 1)
            if (pieces.all { it.state == State.IN }) {
                round++
                postDelayed(cheerLater, 400L)
                postDelayed(nextRound, NEXT_ROUND_MS)
            }
        } else {
            p.state = State.RETURNING
            p.fromX = p.x
            p.fromY = p.y
            p.movedAt = SystemClock.uptimeMillis()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(nextRound)
        removeCallbacks(cheerLater)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CIRCLE = 0
        const val SQUARE = 1
        const val TRIANGLE = 2
        const val STAR = 3

        /** Each shape's color, in [R.array.sort_shape_names] order: circle, square, triangle, star, heart. */
        val KINDS = intArrayOf(0xFF29B6F6.toInt(), 0xFFE53935.toInt(), 0xFF8BC34A.toInt(), 0xFFFDD835.toInt(), 0xFFFF6FB0.toInt())

        const val START_SHAPES = 2
        const val MAX_SHAPES = 4
        const val RETURN_MS = 320L
        const val NEXT_ROUND_MS = 2400L
    }
}
