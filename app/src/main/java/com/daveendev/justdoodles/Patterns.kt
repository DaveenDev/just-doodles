package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** What the pattern crayon draws along the finger's path. Names in `pattern_names`, in this order. */
enum class Pattern { DOTS, ZIGZAG, WAVES, TRACKS, HEARTS, STARS, FLOWERS, PAWS }

/**
 * Draws one finger's line as a [pattern] in [color]. [unit] is the pattern's size in canvas pixels:
 * roughly the width of the line, or the size of one heart.
 *
 * Feed it the finger's path with [start], [lineTo] and [finish]. It walks the path in small
 * steps, so shapes come out evenly spaced however fast the finger moves, and it keeps a smoothed
 * heading, so zigzags and tracks don't wobble with every jitter of the touch.
 */
class PatternPen(
    private val pattern: Pattern,
    private val color: Int,
    private val unit: Float,
    private val random: Random = Random,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@PatternPen.color }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = this@PatternPen.color
    }
    private val tiePaint = Paint(line).apply { color = Palette.mix(this@PatternPen.color, Palette.INK, 0.35f) }
    private val path = Path()
    private val ties = Path()
    private val shape = Path()
    private val matrix = Matrix()

    private var x = 0f
    private var y = 0f
    private var startX = 0f
    private var startY = 0f
    /** Smoothed direction of travel, known once the finger first moves. */
    private var headed = false
    private var hx = 0f
    private var hy = 0f
    private var travelled = 0f
    private var nextMark = 0f
    private var marks = 0
    /** Paw prints alternate left and right. */
    private var side = 1f
    /** Last points of the one or two lines that zigzags, waves and tracks are made of. */
    private var ax = 0f
    private var ay = 0f
    private var bx = 0f
    private var by = 0f

    private val isLine get() = pattern == Pattern.ZIGZAG || pattern == Pattern.WAVES || pattern == Pattern.TRACKS

    private val spacing get() = unit * when (pattern) {
        Pattern.DOTS -> 1.25f
        Pattern.PAWS -> 1.15f
        Pattern.FLOWERS -> 1.5f
        else -> 1.45f
    }

    fun start(c: Canvas, x: Float, y: Float) {
        this.x = x
        this.y = y
        startX = x
        startY = y
        ax = x
        ay = y
        if (pattern == Pattern.PAWS) {
            // A paw print needs to know which way it's walking, so the first one waits a moment.
            nextMark = unit * 0.35f
        } else if (!isLine) {
            mark(c, x, y)
            nextMark = spacing
        }
    }

    fun lineTo(c: Canvas, tx: Float, ty: Float) {
        val length = hypot(tx - x, ty - y)
        if (length < 0.5f) return
        val ux = (tx - x) / length
        val uy = (ty - y) / length
        val step = unit / 10f
        path.rewind()
        ties.rewind()
        var drawn = 0f
        while (drawn < length) {
            val s = min(step, length - drawn)
            x += ux * s
            y += uy * s
            drawn += s
            travelled += s
            if (!headed) {
                headed = true
                hx = ux
                hy = uy
                firstHeading()
            } else {
                val k = (s / (unit * 0.8f)).coerceAtMost(1f)
                hx += (ux - hx) * k
                hy += (uy - hy) * k
                val h = hypot(hx, hy)
                if (h > 0f) { hx /= h; hy /= h }
            }
            // Left of the direction of travel.
            val nx = -hy
            val ny = hx
            when (pattern) {
                Pattern.ZIGZAG, Pattern.WAVES -> {
                    val o = offset(travelled)
                    if (path.isEmpty) path.moveTo(ax, ay)
                    ax = x + nx * o
                    ay = y + ny * o
                    path.lineTo(ax, ay)
                }
                Pattern.TRACKS -> {
                    val r = unit * 0.5f
                    path.moveTo(ax, ay)
                    ax = x + nx * r
                    ay = y + ny * r
                    path.lineTo(ax, ay)
                    path.moveTo(bx, by)
                    bx = x - nx * r
                    by = y - ny * r
                    path.lineTo(bx, by)
                    if (travelled >= nextMark) {
                        tie(x, y, nx, ny)
                        nextMark += unit * 0.75f
                    }
                }
                else -> if (travelled >= nextMark) {
                    mark(c, x, y)
                    nextMark += spacing
                }
            }
        }
        if (pattern == Pattern.TRACKS) {
            tiePaint.strokeWidth = unit * 0.24f
            c.drawPath(ties, tiePaint)
            line.strokeWidth = unit * 0.17f
            c.drawPath(path, line)
        } else if (isLine) {
            line.strokeWidth = unit * 0.3f
            c.drawPath(path, line)
        }
    }

    /** Ends the line. A tap that never moved still leaves something behind. */
    fun finish(c: Canvas) {
        if (marks > 0 || (isLine && travelled > 0f)) return
        if (pattern == Pattern.PAWS) {
            hx = 0f
            hy = -1f
            side = 0f
            mark(c, x, y)
        } else if (isLine) {
            c.drawCircle(x, y, unit * 0.3f, fill)
        }
    }

    private fun firstHeading() {
        if (pattern == Pattern.TRACKS) {
            val r = unit * 0.5f
            ax = startX - hy * r
            ay = startY + hx * r
            bx = startX + hy * r
            by = startY - hx * r
            tie(startX, startY, -hy, hx)
            nextMark = unit * 0.75f
        }
    }

    /** Sideways offset of a zigzag or wave at [d] along the line. */
    private fun offset(d: Float): Float {
        return if (pattern == Pattern.ZIGZAG) {
            val p = d / (unit * 1.6f)
            val f = p - floor(p)
            unit * 0.5f * when {
                f < 0.25f -> 4f * f
                f < 0.75f -> 2f - 4f * f
                else -> 4f * f - 4f
            }
        } else {
            unit * 0.45f * sin(2.0 * PI * d / (unit * 2.0)).toFloat()
        }
    }

    /** One sleeper across a train track. */
    private fun tie(cx: Float, cy: Float, nx: Float, ny: Float) {
        val r = unit * 0.7f
        ties.moveTo(cx + nx * r, cy + ny * r)
        ties.lineTo(cx - nx * r, cy - ny * r)
    }

    /** One shape of a dotted pattern, centered on (cx, cy). */
    private fun mark(c: Canvas, cx: Float, cy: Float) {
        marks++
        when (pattern) {
            Pattern.DOTS -> c.drawCircle(cx, cy, unit * 0.4f, fill)
            Pattern.HEARTS -> stamp(c, Stamps.heart, cx, cy, unit * 0.55f, 0f)
            Pattern.STARS -> stamp(c, Stamps.star, cx, cy, unit * 0.62f, random.nextFloat() * 40f - 20f)
            Pattern.FLOWERS -> {
                val angle = random.nextFloat() * 72f
                stamp(c, Stamps.shape(StampKind.FLOWER), cx, cy, unit * 0.6f, angle)
                fill.color = if (Color.luminance(color) > 0.6f) 0xFFFF8F00.toInt() else Color.WHITE
                c.drawCircle(cx, cy, unit * 0.6f * 0.32f, fill)
                fill.color = color
            }
            Pattern.PAWS -> {
                val px = cx - hy * side * unit * 0.45f
                val py = cy + hx * side * unit * 0.45f
                // Toes point the way the finger is going.
                val angle = Math.toDegrees(atan2(hy, hx).toDouble()).toFloat() + 90f
                stamp(c, paw, px, py, unit * 0.62f, angle)
                side = -side
            }
            else -> Unit
        }
    }

    private fun stamp(c: Canvas, shape: Path, cx: Float, cy: Float, r: Float, angle: Float) {
        matrix.setScale(r, r)
        matrix.postRotate(angle)
        matrix.postTranslate(cx, cy)
        this.shape.rewind()
        shape.transform(matrix, this.shape)
        c.drawPath(this.shape, fill)
    }

    companion object {
        /** A paw print, toes up, centered on (0,0) with a radius of about 1. */
        private val paw = Path().apply {
            addOval(-0.55f, -0.2f, 0.55f, 0.7f, Path.Direction.CW)
            addCircle(-0.62f, -0.38f, 0.22f, Path.Direction.CW)
            addCircle(-0.22f, -0.7f, 0.24f, Path.Direction.CW)
            addCircle(0.22f, -0.7f, 0.24f, Path.Direction.CW)
            addCircle(0.62f, -0.38f, 0.22f, Path.Direction.CW)
        }

        /** A short sample of [pattern] across a 100 x 100 box, for buttons. */
        fun drawSample(c: Canvas, pattern: Pattern, color: Int) {
            // A fixed seed, so the stars don't jiggle every time the button redraws.
            val pen = PatternPen(pattern, color, 24f, Random(7))
            pen.start(c, 14f, 50f)
            pen.lineTo(c, 86f, 50f)
            pen.finish(c)
        }
    }
}
