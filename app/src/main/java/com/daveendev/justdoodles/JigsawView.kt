package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Make the picture: a colored picture cut into big straight pieces (2, then 3, then 4) that sit
 * at the sides, with a faint copy showing where they go. A piece dropped near its place snaps in
 * with a note; anywhere else it floats back. The finished picture bounces and is named, with a cheer.
 */
@SuppressLint("ViewConstructor")
class JigsawView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { HOME, HELD, RETURNING, IN }

    /** One piece: [src] in the picture, [goalX]/[goalY] its place on screen, [homeX]/[homeY] where it waits. */
    private class Piece(val src: Rect, val goalX: Float, val goalY: Float, val homeX: Float, val homeY: Float) {
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

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())

    private var picture: Bitmap? = null
    private var title = ""
    private val target = RectF()
    private var pieces = emptyList<Piece>()
    private var round = 0
    private var doneAt = -1L
    /** Pictures from the last few rounds, kept out of the next ones. */
    private val recent = ArrayDeque<String>()

    private val nextRound = Runnable { newRound() }
    private val cheerLater = Runnable { host.cheer() }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 60 }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
    }
    private val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x55263238
    }
    private val dst = RectF()

    init {
        contentDescription = context.getString(R.string.game_jigsaw)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        val all = host.pictureGroups.flatMap { it.pages }.filter { it.title != null }
        val page = all.filter { it.id !in recent }.ifEmpty { all }.randomOrNull(random) ?: return
        recent.addLast(page.id)
        if (recent.size > RECENT) recent.removeFirst()
        title = page.title ?: ""

        val tw = min(width * 0.5f, height * 0.78f * 4f / 3f)
        val th = tw * 3f / 4f
        target.set((width - tw) / 2f, (height - th) / 2f, (width + tw) / 2f, (height + th) / 2f)
        val bmp = host.picture(page, tw.toInt().coerceAtLeast(1), th.toInt().coerceAtLeast(1))
        picture = bmp

        val (columns, rows) = when (round) {
            0 -> 2 to 1
            1 -> 3 to 1
            else -> 2 to 2
        }
        val pw = bmp.width / columns
        val ph = bmp.height / rows
        val cells = ArrayList<Rect>()
        for (r in 0 until rows) for (c in 0 until columns) cells += Rect(c * pw, r * ph, (c + 1) * pw, (r + 1) * ph)

        // Waiting spots down both sides, left and right in turn, in a random order.
        val sideW = target.left
        val spots = cells.indices.map { k ->
            val onLeft = k % 2 == 0
            val slotsOnSide = (cells.size + if (onLeft) 1 else 0) / 2
            val n = k / 2
            val cy = height * (n + 1f) / (slotsOnSide + 1f)
            val cx = if (onLeft) sideW / 2f else width - sideW / 2f
            cx to cy
        }.shuffled(random)
        pieces = cells.mapIndexed { k, src ->
            val (cx, cy) = spots[k]
            Piece(src, target.left + src.left, target.top + src.top, cx - src.width() / 2f, cy - src.height() / 2f)
        }
        doneAt = -1L
        host.sounds.whoosh()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = picture ?: return
        val now = SystemClock.uptimeMillis()
        var moving = false

        // The faint picture where the pieces go, with each piece's place marked.
        canvas.drawBitmap(bmp, null, target, ghostPaint)
        for (p in pieces) canvas.drawRect(p.goalX, p.goalY, p.goalX + p.src.width(), p.goalY + p.src.height(), slot)

        // A happy bounce once it's done.
        val bounce = if (doneAt < 0) 1f else {
            val t = (now - doneAt) / BOUNCE_MS.toFloat()
            if (t < 1f) moving = true
            1f + 0.06f * sin(t.coerceIn(0f, 1f) * Math.PI.toFloat() * 2f) * (1f - t.coerceIn(0f, 1f))
        }
        canvas.save()
        canvas.scale(bounce, bounce, target.centerX(), target.centerY())
        for (p in pieces.sortedBy { if (it.state == State.HELD) 2 else if (it.state == State.IN) 0 else 1 }) {
            if (p.state == State.RETURNING) {
                val t = ((now - p.movedAt) / RETURN_MS.toFloat()).coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t)
                p.x = p.fromX + (p.homeX - p.fromX) * e
                p.y = p.fromY + (p.homeY - p.fromY) * e
                if (t >= 1f) p.state = State.HOME else moving = true
            }
            dst.set(p.x, p.y, p.x + p.src.width(), p.y + p.src.height())
            canvas.drawBitmap(bmp, p.src, dst, bitmapPaint)
            if (p.state != State.IN || doneAt < 0) canvas.drawRect(dst, edge)
        }
        if (doneAt >= 0) canvas.drawRect(target, edge)
        canvas.restore()
        if (moving) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (doneAt >= 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val p = pieces.lastOrNull {
                    (it.state == State.HOME || it.state == State.RETURNING) &&
                        x >= it.x && x <= it.x + it.src.width() && y >= it.y && y <= it.y + it.src.height()
                } ?: return true
                p.state = State.HELD
                p.pointer = e.getPointerId(i)
                p.grabDx = p.x - x
                p.grabDy = p.y - y
                host.sounds.pop(1.1f)
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
        val near = abs(p.x - p.goalX) < p.src.width() * SNAP && abs(p.y - p.goalY) < p.src.height() * SNAP
        if (!cancelled && near) {
            p.state = State.IN
            p.x = p.goalX
            p.y = p.goalY
            host.sounds.note(pieces.count { it.state == State.IN } * 2 + 2)
            if (pieces.all { it.state == State.IN }) {
                doneAt = SystemClock.uptimeMillis()
                round++
                if (title.isNotEmpty()) host.say("$title!")
                postDelayed(cheerLater, 900L)
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
        /** How close (as a share of the piece's size) counts as "in its place". */
        const val SNAP = 0.28f
        const val RETURN_MS = 320L
        const val BOUNCE_MS = 700L
        const val NEXT_ROUND_MS = 3200L
        const val RECENT = 6
    }
}
