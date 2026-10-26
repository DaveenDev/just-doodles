package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** The hungry animals of "Feed the animals". What they say is in `critter_sounds`, in this order. */
enum class Critter { FROG, PUPPY, CAT, PIG }

/** What they eat. Names in `food_names`, in this order. */
enum class Food { APPLE, BANANA, CARROT, COOKIE, STRAWBERRY, WATERMELON }

/**
 * Artwork for "Feed the animals", drawn in code like the [Stickers]: each animal and food fits a
 * 100 x 100 box. The animals can open their mouths, look around, blink and blush.
 */
object Critters {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val stem = Paint(ink).apply {
        strokeWidth = 5f
        color = 0xFF8D5524.toInt()
    }
    private val path = Path()
    private val box = RectF()

    private const val BROWN = 0xFF8D5524.toInt()
    private const val LEAF = 0xFF43A047.toInt()
    private const val MOUTH = 0xFFB71C1C.toInt()
    private const val TONGUE = 0xFFFF8A80.toInt()
    private const val BLUSH = 0xB3FF6FA0.toInt()

    /** Where each animal's mouth is in its 100 x 100 box (always in the middle), and how wide it opens. */
    const val MOUTH_X = 50f
    fun mouthY(critter: Critter) = when (critter) {
        Critter.FROG -> 66f
        Critter.PUPPY -> 76f
        Critter.CAT -> 72f
        Critter.PIG -> 79f
    }
    private fun mouthWidth(critter: Critter) = when (critter) {
        Critter.FROG -> 50f
        Critter.PUPPY -> 28f
        Critter.CAT -> 26f
        Critter.PIG -> 26f
    }

    /**
     * Draws [critter] with its mouth [open] from 0 (a smile) to 1 (wide open), its eyes looking
     * toward ([lookX], [lookY]) (each -1 to 1), maybe [blinking], and cheeks pink by [blush] (0 to 1).
     */
    fun draw(c: Canvas, critter: Critter, open: Float, lookX: Float, lookY: Float, blinking: Boolean, blush: Float) {
        when (critter) {
            Critter.FROG -> frog(c, lookX, lookY, blinking)
            Critter.PUPPY -> puppy(c, lookX, lookY, blinking)
            Critter.CAT -> cat(c, lookX, lookY, blinking)
            Critter.PIG -> pig(c, lookX, lookY, blinking)
        }
        if (blush > 0f) {
            fill.color = BLUSH
            fill.alpha = (Color.alpha(BLUSH) * blush).toInt()
            val y = mouthY(critter) - 8f
            c.drawCircle(22f, y, 7f, fill)
            c.drawCircle(78f, y, 7f, fill)
            fill.alpha = 255
        }
        mouth(c, MOUTH_X, mouthY(critter), mouthWidth(critter), open)
    }

    fun drawFood(c: Canvas, food: Food) {
        when (food) {
            Food.APPLE -> apple(c)
            Food.BANANA -> banana(c)
            Food.CARROT -> carrot(c)
            Food.COOKIE -> cookie(c)
            Food.STRAWBERRY -> strawberry(c)
            Food.WATERMELON -> watermelon(c)
        }
    }

    // ---- Pieces ----------------------------------------------------------------------------

    private fun shape(c: Canvas, p: Path, color: Int) {
        fill.color = color
        c.drawPath(p, fill)
        c.drawPath(p, ink)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, ink)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        box.set(l, t, r, b)
        fill.color = color
        c.drawOval(box, fill)
        c.drawOval(box, ink)
    }

    private fun triangle(c: Canvas, color: Int, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        path.rewind()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.close()
        shape(c, path, color)
    }

    /** An eye whose pupil looks toward (lookX, lookY), or a closed curve while blinking. */
    private fun eye(c: Canvas, x: Float, y: Float, r: Float, lookX: Float, lookY: Float, blinking: Boolean) {
        if (blinking) {
            path.rewind()
            path.moveTo(x - r, y)
            path.quadTo(x, y + r * 0.7f, x + r, y)
            c.drawPath(path, ink)
            return
        }
        circle(c, x, y, r, Color.WHITE)
        val px = x + lookX * r * 0.38f
        val py = y + lookY * r * 0.38f
        fill.color = Palette.INK
        c.drawCircle(px, py, r * 0.52f, fill)
        fill.color = Color.WHITE
        c.drawCircle(px + r * 0.18f, py - r * 0.2f, r * 0.18f, fill)
    }

    private fun mouth(c: Canvas, x: Float, y: Float, w: Float, open: Float) {
        val half = w / 2f
        if (open < 0.08f) {
            path.rewind()
            path.moveTo(x - half, y - 2f)
            path.quadTo(x, y + w * 0.32f, x + half, y - 2f)
            c.drawPath(path, ink)
            return
        }
        val h = w * 0.85f * open
        box.set(x - half, y - h * 0.3f, x + half, y + h * 0.7f)
        fill.color = MOUTH
        c.drawOval(box, fill)
        c.save()
        path.rewind()
        path.addOval(box, Path.Direction.CW)
        c.clipPath(path)
        fill.color = TONGUE
        c.drawOval(x - half * 0.6f, y + h * 0.25f, x + half * 0.6f, y + h * 0.95f, fill)
        c.restore()
        c.drawOval(box, ink)
    }

    // ---- Animals ---------------------------------------------------------------------------

    private fun frog(c: Canvas, lx: Float, ly: Float, blinking: Boolean) {
        val green = 0xFF7CB342.toInt()
        circle(c, 29f, 26f, 15f, green)
        circle(c, 71f, 26f, 15f, green)
        oval(c, 6f, 28f, 94f, 92f, green)
        fill.color = 0xFFAED581.toInt()
        c.drawOval(22f, 60f, 78f, 88f, fill)
        eye(c, 29f, 25f, 10f, lx, ly, blinking)
        eye(c, 71f, 25f, 10f, lx, ly, blinking)
        fill.color = Palette.INK
        c.drawCircle(45f, 46f, 1.6f, fill)
        c.drawCircle(55f, 46f, 1.6f, fill)
    }

    private fun puppy(c: Canvas, lx: Float, ly: Float, blinking: Boolean) {
        val tan = 0xFFD7A26C.toInt()
        oval(c, 6f, 18f, 32f, 66f, BROWN)
        oval(c, 68f, 18f, 94f, 66f, BROWN)
        circle(c, 50f, 52f, 36f, tan)
        oval(c, 30f, 54f, 70f, 88f, 0xFFF3D9B1.toInt())
        oval(c, 43f, 55f, 57f, 64f, Palette.INK)
        c.drawLine(50f, 64f, 50f, 70f, ink)
        eye(c, 37f, 42f, 7f, lx, ly, blinking)
        eye(c, 63f, 42f, 7f, lx, ly, blinking)
    }

    private fun cat(c: Canvas, lx: Float, ly: Float, blinking: Boolean) {
        val orange = 0xFFFFA726.toInt()
        triangle(c, orange, 12f, 40f, 20f, 4f, 44f, 22f)
        triangle(c, orange, 88f, 40f, 80f, 4f, 56f, 22f)
        triangle(c, 0xFFFF8A80.toInt(), 19f, 32f, 22f, 13f, 36f, 23f)
        triangle(c, 0xFFFF8A80.toInt(), 81f, 32f, 78f, 13f, 64f, 23f)
        oval(c, 10f, 16f, 90f, 92f, orange)
        eye(c, 35f, 46f, 9f, lx, ly, blinking)
        eye(c, 65f, 46f, 9f, lx, ly, blinking)
        triangle(c, 0xFFFF6FB0.toInt(), 45f, 58f, 55f, 58f, 50f, 64f)
        for (sign in intArrayOf(-1, 1)) {
            c.drawLine(50f + sign * 14f, 64f, 50f + sign * 38f, 60f, ink)
            c.drawLine(50f + sign * 14f, 68f, 50f + sign * 38f, 70f, ink)
        }
    }

    private fun pig(c: Canvas, lx: Float, ly: Float, blinking: Boolean) {
        val pink = 0xFFF8A5C2.toInt()
        triangle(c, pink, 16f, 34f, 14f, 8f, 38f, 22f)
        triangle(c, pink, 84f, 34f, 86f, 8f, 62f, 22f)
        circle(c, 50f, 54f, 37f, pink)
        oval(c, 35f, 52f, 65f, 72f, 0xFFF48FB1.toInt())
        fill.color = 0xFFAD1457.toInt()
        c.drawOval(41f, 58f, 47f, 66f, fill)
        c.drawOval(53f, 58f, 59f, 66f, fill)
        eye(c, 36f, 40f, 7f, lx, ly, blinking)
        eye(c, 64f, 40f, 7f, lx, ly, blinking)
    }

    // ---- Food ------------------------------------------------------------------------------

    private fun apple(c: Canvas) {
        c.drawLine(50f, 32f, 54f, 12f, stem)
        oval(c, 54f, 10f, 76f, 24f, LEAF)
        path.rewind()
        path.moveTo(50f, 30f)
        path.cubicTo(70f, 18f, 94f, 32f, 90f, 58f)
        path.cubicTo(86f, 84f, 66f, 96f, 50f, 88f)
        path.cubicTo(34f, 96f, 14f, 84f, 10f, 58f)
        path.cubicTo(6f, 32f, 30f, 18f, 50f, 30f)
        path.close()
        shape(c, path, 0xFFE53935.toInt())
        fill.color = 0x88FFFFFF.toInt()
        c.drawOval(22f, 40f, 32f, 56f, fill)
    }

    private fun banana(c: Canvas) {
        path.rewind()
        path.moveTo(16f, 30f)
        path.cubicTo(18f, 78f, 62f, 92f, 90f, 62f)
        path.lineTo(88f, 54f)
        path.cubicTo(62f, 74f, 30f, 66f, 26f, 28f)
        path.close()
        shape(c, path, 0xFFFDD835.toInt())
        fill.color = BROWN
        c.drawCircle(21f, 28f, 4f, fill)
        c.drawCircle(89f, 58f, 3.5f, fill)
    }

    private fun carrot(c: Canvas) {
        for (a in floatArrayOf(-28f, 0f, 28f)) {
            c.save()
            c.rotate(a, 50f, 30f)
            oval(c, 44f, 4f, 56f, 32f, LEAF)
            c.restore()
        }
        path.rewind()
        path.moveTo(30f, 30f)
        path.quadTo(50f, 22f, 70f, 30f)
        path.quadTo(62f, 70f, 52f, 94f)
        path.quadTo(48f, 96f, 46f, 92f)
        path.quadTo(36f, 66f, 30f, 30f)
        path.close()
        shape(c, path, 0xFFFF8F00.toInt())
        c.drawLine(38f, 46f, 48f, 46f, ink)
        c.drawLine(52f, 60f, 62f, 60f, ink)
        c.drawLine(42f, 72f, 50f, 72f, ink)
    }

    private fun cookie(c: Canvas) {
        circle(c, 50f, 52f, 38f, 0xFFD7A26C.toInt())
        fill.color = 0xFF5D4037.toInt()
        for ((x, y) in listOf(36f to 38f, 60f to 34f, 66f to 56f, 44f to 60f, 30f to 62f, 54f to 74f)) {
            c.drawCircle(x, y, 5f, fill)
        }
    }

    private fun strawberry(c: Canvas) {
        path.rewind()
        path.moveTo(50f, 94f)
        path.cubicTo(14f, 72f, 10f, 30f, 50f, 30f)
        path.cubicTo(90f, 30f, 86f, 72f, 50f, 94f)
        path.close()
        shape(c, path, 0xFFE53935.toInt())
        fill.color = 0xFFFFF59D.toInt()
        for ((x, y) in listOf(36f to 46f, 50f to 44f, 64f to 46f, 42f to 60f, 58f to 60f, 50f to 74f, 34f to 62f, 66f to 62f)) {
            c.drawOval(x - 2f, y - 3f, x + 2f, y + 3f, fill)
        }
        path.rewind()
        path.moveTo(50f, 36f)
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72 + 36)
            val b = Math.toRadians(-90.0 + i * 72)
            path.lineTo(50f + 20f * Math.cos(b).toFloat(), 30f + 10f * Math.sin(b).toFloat())
            path.lineTo(50f + 6f * Math.cos(a).toFloat(), 30f + 4f * Math.sin(a).toFloat())
        }
        path.close()
        shape(c, path, LEAF)
    }

    private fun watermelon(c: Canvas) {
        box.set(8f, -6f, 92f, 78f)
        fill.color = 0xFF2E9D4A.toInt()
        c.drawArc(box, 0f, 180f, true, fill)
        c.drawArc(box, 0f, 180f, true, ink)
        box.set(15f, 1f, 85f, 71f)
        fill.color = 0xFFC8E6C9.toInt()
        c.drawArc(box, 0f, 180f, true, fill)
        box.set(19f, 5f, 81f, 67f)
        fill.color = 0xFFFF5252.toInt()
        c.drawArc(box, 0f, 180f, true, fill)
        fill.color = Palette.INK
        for ((x, y) in listOf(36f to 46f, 50f to 54f, 64f to 46f, 44f to 62f, 56f to 62f)) {
            c.drawOval(x - 2f, y - 3f, x + 2f, y + 3f, fill)
        }
    }
}
