package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pictures on the twelve newer Learn topic cards (wild animals, pets, birds, farm, garden, cars, flying,
 * fruits, weather, space, toys, beach). Same look as [Icons]: drawn in code into a 100 x 100 box, ink outline,
 * rounded caps and joins, bright fills with a white shine and little friendly faces. [Icons.draw] hands the
 * twelve LEARN_ icons to [draw].
 */
object LessonIcons {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    /** A colored stroke, drawn on its own or over a wider ink stroke so it comes out outlined. */
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val tmp = Path()

    private const val SHINE = 0x66FFFFFF
    private const val BLUSH = 0x66FF6FB0
    private const val PINK = 0xFFFF8A80.toInt()
    private const val YELLOW = 0xFFFDD835.toInt()
    private const val GREEN = 0xFF4CAF50.toInt()
    private const val BROWN = 0xFF8D5524.toInt()

    fun draw(c: Canvas, icon: Icon) {
        when (icon) {
            Icon.LEARN_WILD -> wild(c)
            Icon.LEARN_PETS -> pets(c)
            Icon.LEARN_BIRDS -> birds(c)
            Icon.LEARN_FARM -> farm(c)
            Icon.LEARN_GARDEN -> garden(c)
            Icon.LEARN_CARS -> cars(c)
            Icon.LEARN_FLYING -> flying(c)
            Icon.LEARN_FRUITS -> fruits(c)
            Icon.LEARN_WEATHER -> weather(c)
            Icon.LEARN_SPACE -> space(c)
            Icon.LEARN_TOYS -> toys(c)
            Icon.LEARN_BEACH -> beach(c)
            else -> {}
        }
    }

    // ---- small helpers -------------------------------------------------------------------------------------

    /** Fills the current [path] with [color] and outlines it in ink. */
    private fun shape(c: Canvas, color: Int) {
        fill.color = color
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, line)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill.color = color
        c.drawOval(l, t, r, b, fill)
        c.drawOval(l, t, r, b, line)
    }

    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        fill.color = color
        c.drawRoundRect(l, t, r, b, rad, rad, fill)
        c.drawRoundRect(l, t, r, b, rad, rad, line)
    }

    /** A filled, outlined polygon through the x, y pairs in [pts]. */
    private fun poly(c: Canvas, color: Int, vararg pts: Float) {
        path.rewind()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        shape(c, color)
    }

    /** A round ink eye with a tiny white shine. */
    private fun eye(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Palette.INK
        c.drawCircle(x, y, r, fill)
        fill.color = Color.WHITE
        c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.38f, fill)
    }

    private fun cheek(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = BLUSH
        c.drawCircle(x, y, r, fill)
    }

    /** The current [path] stroked [width] wide in [color], over a wider ink stroke. */
    private fun band(c: Canvas, color: Int, width: Float) {
        stroke.color = Palette.INK
        stroke.strokeWidth = width + 6f
        c.drawPath(path, stroke)
        stroke.color = color
        stroke.strokeWidth = width
        c.drawPath(path, stroke)
    }

    private fun colorLine(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, stroke)
    }

    private fun unionRect(l: Float, t: Float, r: Float, b: Float) {
        tmp.rewind()
        tmp.addRect(l, t, r, b, Path.Direction.CW)
        path.op(tmp, Path.Op.UNION)
    }

    /** A four-pointed gold sparkle of radius [r]. */
    private fun star4(c: Canvas, cx: Float, cy: Float, r: Float, color: Int = 0xFFFFC107.toInt()) {
        val k = r * 0.3f
        path.rewind()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + k, cy - k)
        path.lineTo(cx + r, cy)
        path.lineTo(cx + k, cy + k)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - k, cy + k)
        path.lineTo(cx - r, cy)
        path.lineTo(cx - k, cy - k)
        path.close()
        shape(c, color)
    }

    /** A fluffy cloud about 48 * [s] wide with its middle at ([cx], [cy]). */
    private fun cloud(c: Canvas, cx: Float, cy: Float, s: Float, color: Int = Color.WHITE) {
        path.rewind()
        path.addRoundRect(cx - 22f * s, cy + 2f * s, cx + 26f * s, cy + 16f * s, 7f * s, 7f * s, Path.Direction.CW)
        val puffs = arrayOf(floatArrayOf(-12f, 4f, 10f), floatArrayOf(2f, -4f, 13f), floatArrayOf(16f, 3f, 10f))
        for (p in puffs) {
            tmp.rewind()
            tmp.addCircle(cx + p[0] * s, cy + p[1] * s, p[2] * s, Path.Direction.CW)
            path.op(tmp, Path.Op.UNION)
        }
        shape(c, color)
    }

    // ---- the twelve topics ---------------------------------------------------------------------------------

    /** Wild animals: a smiling lion with a big round mane. */
    private fun wild(c: Canvas) {
        val mane = 0xFFE0831E.toInt()
        for (i in 0 until 10) {
            val a = Math.toRadians(i * 36.0 - 90.0)
            circle(c, 50f + 31f * cos(a).toFloat(), 53f + 31f * sin(a).toFloat(), 13f, mane)
        }
        fill.color = mane
        c.drawCircle(50f, 53f, 32f, fill)
        circle(c, 50f, 53f, 26f, 0xFFFFC857.toInt())
        fill.color = SHINE
        c.drawOval(33f, 33f, 45f, 41f, fill)
        oval(c, 36f, 54f, 64f, 76f, 0xFFFFF3D6.toInt())
        eye(c, 39f, 44f, 4f)
        eye(c, 61f, 44f, 4f)
        cheek(c, 31f, 56f, 4.5f)
        cheek(c, 69f, 56f, 4.5f)
        path.rewind()
        path.moveTo(44f, 54f)
        path.lineTo(56f, 54f)
        path.lineTo(50f, 61f)
        path.close()
        shape(c, 0xFF6D4C41.toInt())
        c.drawLine(50f, 61f, 50f, 65f, line)
        c.drawArc(41f, 59f, 50f, 70f, 10f, 160f, false, line)
        c.drawArc(50f, 59f, 59f, 70f, 10f, 160f, false, line)
    }

    /** Pets: a floppy-eared puppy and a kitten side by side. */
    private fun pets(c: Canvas) {
        // The puppy, in a box where its head is 22 across from its middle.
        c.save()
        c.translate(31f, 60f)
        c.scale(1.12f, 1.12f)
        c.save()
        c.rotate(18f, -19f, -6f)
        oval(c, -27f, -20f, -11f, 8f, BROWN)
        c.restore()
        c.save()
        c.rotate(-18f, 19f, -6f)
        oval(c, 11f, -20f, 27f, 8f, BROWN)
        c.restore()
        circle(c, 0f, 0f, 22f, 0xFFE0B27A.toInt())
        fill.color = BROWN
        c.drawCircle(-9f, -4f, 8f, fill)
        eye(c, -8f, -3f, 3f)
        eye(c, 8f, -3f, 3f)
        oval(c, -10f, 3f, 10f, 18f, 0xFFFFF3D6.toInt())
        oval(c, -3.5f, 12f, 3.5f, 21f, 0xFFFF8A9A.toInt())
        c.drawArc(-8f, 7f, 0f, 17f, 10f, 160f, false, line)
        c.drawArc(0f, 7f, 8f, 17f, 10f, 160f, false, line)
        oval(c, -4.5f, 3f, 4.5f, 9f, Palette.INK)
        c.restore()

        // The kitten.
        c.save()
        c.translate(71f, 52f)
        c.scale(1.12f, 1.12f)
        poly(c, 0xFFFFA040.toInt(), -19f, -8f, -18f, -30f, -3f, -16f)
        poly(c, 0xFFFFA040.toInt(), 19f, -8f, 18f, -30f, 3f, -16f)
        poly(c, PINK, -16f, -13f, -15.5f, -24f, -7.5f, -17f)
        poly(c, PINK, 16f, -13f, 15.5f, -24f, 7.5f, -17f)
        circle(c, 0f, 0f, 22f, 0xFFFFA040.toInt())
        colorLine(c, 0f, -21f, 0f, -14f, 0xFFE65100.toInt(), 3f)
        colorLine(c, -7f, -19.5f, -5f, -13f, 0xFFE65100.toInt(), 3f)
        colorLine(c, 7f, -19.5f, 5f, -13f, 0xFFE65100.toInt(), 3f)
        eye(c, -9f, -2f, 3.2f)
        eye(c, 9f, -2f, 3.2f)
        cheek(c, -14f, 8f, 4f)
        cheek(c, 14f, 8f, 4f)
        poly(c, PINK, -3f, 3f, 3f, 3f, 0f, 6.5f)
        c.drawArc(-7f, 5f, 0f, 13f, 10f, 160f, false, line)
        c.drawArc(0f, 5f, 7f, 13f, 10f, 160f, false, line)
        c.restore()
    }

    /** Birds: a round blue bird perched on a branch. */
    private fun birds(c: Canvas) {
        // The branch, with a leaf.
        path.rewind()
        path.moveTo(2f, 88f)
        path.lineTo(98f, 83f)
        band(c, BROWN, 8f)
        path.rewind()
        path.moveTo(74f, 82f)
        path.quadTo(80f, 66f, 94f, 66f)
        path.quadTo(92f, 80f, 74f, 82f)
        path.close()
        shape(c, GREEN)
        // Feet.
        colorLine(c, 42f, 76f, 42f, 85f, 0xFFFF8F00.toInt(), 4f)
        colorLine(c, 56f, 76f, 56f, 84f, 0xFFFF8F00.toInt(), 4f)
        // Tail, tuft, body, belly and wing.
        poly(c, 0xFF1E88E5.toInt(), 26f, 52f, 3f, 52f, 9f, 70f, 30f, 68f)
        colorLine(c, 48f, 23f, 43f, 11f, Palette.INK, 4f)
        colorLine(c, 53f, 23f, 58f, 12f, Palette.INK, 4f)
        circle(c, 49f, 50f, 28f, 0xFF42A5F5.toInt())
        oval(c, 38f, 52f, 68f, 77f, 0xFFE3F2FD.toInt())
        fill.color = SHINE
        c.drawOval(33f, 30f, 45f, 38f, fill)
        path.rewind()
        path.moveTo(22f, 50f)
        path.cubicTo(24f, 38f, 44f, 38f, 46f, 54f)
        path.cubicTo(44f, 72f, 24f, 68f, 22f, 50f)
        path.close()
        shape(c, 0xFF1E88E5.toInt())
        // Beak, eye, cheek.
        poly(c, 0xFFFF8F00.toInt(), 73f, 44f, 91f, 51f, 73f, 57f)
        eye(c, 62f, 42f, 4.2f)
        cheek(c, 63f, 56f, 4.5f)
    }

    /** Farm: a red barn with white doors and a hay loft, on a green hill. */
    private fun farm(c: Canvas) {
        oval(c, -4f, 80f, 104f, 104f, 0xFF81C784.toInt())
        // The barn walls and its roof trim.
        path.rewind()
        path.moveTo(12f, 90f)
        path.lineTo(12f, 46f)
        path.lineTo(22f, 28f)
        path.lineTo(50f, 14f)
        path.lineTo(78f, 28f)
        path.lineTo(88f, 46f)
        path.lineTo(88f, 90f)
        path.close()
        shape(c, 0xFFE53935.toInt())
        fill.color = SHINE
        c.drawRect(16f, 52f, 22f, 86f, fill)
        path.rewind()
        path.moveTo(9f, 47f)
        path.lineTo(21f, 25f)
        path.lineTo(50f, 10f)
        path.lineTo(79f, 25f)
        path.lineTo(91f, 47f)
        band(c, 0xFFB71C1C.toInt(), 6f)
        // Hay loft window.
        fill.color = Color.WHITE
        c.drawRect(42f, 30f, 58f, 46f, fill)
        c.drawRect(42f, 30f, 58f, 46f, line)
        c.drawLine(50f, 30f, 50f, 46f, line)
        c.drawLine(42f, 38f, 58f, 38f, line)
        // The big doors, each with a cross.
        fill.color = Color.WHITE
        c.drawRect(30f, 56f, 70f, 90f, fill)
        for (x in floatArrayOf(30f, 50f)) {
            colorLine(c, x + 2.5f, 58.5f, x + 17.5f, 87.5f, 0xFFE53935.toInt(), 3f)
            colorLine(c, x + 17.5f, 58.5f, x + 2.5f, 87.5f, 0xFFE53935.toInt(), 3f)
        }
        c.drawRect(30f, 56f, 70f, 90f, line)
        c.drawLine(50f, 56f, 50f, 90f, line)
    }

    /** Garden: a smiling flower next to a watering can that is pouring. */
    private fun garden(c: Canvas) {
        oval(c, -4f, 84f, 104f, 106f, 0xFF81C784.toInt())
        // The flower: stem, leaf, petals, face.
        colorLine(c, 22f, 46f, 22f, 92f, Palette.INK, 9f)
        colorLine(c, 22f, 46f, 22f, 92f, GREEN, 4f)
        path.rewind()
        path.moveTo(22f, 78f)
        path.quadTo(30f, 62f, 42f, 66f)
        path.quadTo(38f, 80f, 22f, 78f)
        path.close()
        shape(c, GREEN)
        for (i in 0 until 5) {
            val a = Math.toRadians(-90.0 + i * 72.0)
            circle(c, 22f + 12.5f * cos(a).toFloat(), 31f + 12.5f * sin(a).toFloat(), 9.5f, 0xFFFF6FB0.toInt())
        }
        circle(c, 22f, 31f, 9.5f, YELLOW)
        eye(c, 18.5f, 29.5f, 2f)
        eye(c, 25.5f, 29.5f, 2f)
        c.drawArc(18f, 29f, 26f, 37f, 20f, 140f, false, line)
        // The watering can, tipped so the spout points down at the flower.
        c.save()
        c.rotate(-18f, 66f, 66f)
        path.rewind()
        path.moveTo(52f, 74f)
        path.lineTo(36f, 54f)
        band(c, 0xFF1E88E5.toInt(), 6f)
        path.rewind()
        path.addArc(70f, 50f, 98f, 80f, -90f, 180f)
        band(c, 0xFF1565C0.toInt(), 5f)
        roundRect(c, 48f, 46f, 84f, 86f, 8f, 0xFF42A5F5.toInt())
        fill.color = SHINE
        c.drawRoundRect(53f, 52f, 59f, 78f, 3f, 3f, fill)
        poly(c, 0xFF1565C0.toInt(), 30f, 50f, 42f, 46f, 40f, 62f, 28f, 60f)
        eye(c, 63f, 62f, 3f)
        eye(c, 75f, 62f, 3f)
        c.drawArc(64f, 62f, 74f, 72f, 20f, 140f, false, line)
        c.restore()
        // Water drops.
        for (p in arrayOf(floatArrayOf(30f, 74f), floatArrayOf(38f, 84f), floatArrayOf(29f, 90f))) {
            drop(c, p[0], p[1], 0.8f)
        }
    }

    /** A water drop about 14 * [s] tall, its middle at ([x], [y]). */
    private fun drop(c: Canvas, x: Float, y: Float, s: Float) {
        path.rewind()
        path.moveTo(x, y - 9f * s)
        path.cubicTo(x + 2f * s, y - 5f * s, x + 6f * s, y - 2f * s, x + 6f * s, y + 2f * s)
        path.cubicTo(x + 6f * s, y + 6f * s, x - 6f * s, y + 6f * s, x - 6f * s, y + 2f * s)
        path.cubicTo(x - 6f * s, y - 2f * s, x - 2f * s, y - 5f * s, x, y - 9f * s)
        path.close()
        shape(c, 0xFF4FC3F7.toInt())
        fill.color = 0xAAFFFFFF.toInt()
        c.drawCircle(x - 2.2f * s, y + 1f * s, 1.6f * s, fill)
    }

    /** Cars and trucks: a cheerful red car on two round wheels. */
    private fun cars(c: Canvas) {
        val red = 0xFFE53935.toInt()
        poly(c, red, 22f, 50f, 32f, 20f, 68f, 20f, 80f, 50f)
        val glass = 0xFFB3E5FC.toInt()
        poly(c, glass, 30f, 45f, 36f, 27f, 48f, 27f, 48f, 45f)
        poly(c, glass, 53f, 45f, 53f, 27f, 65f, 27f, 73f, 45f)
        roundRect(c, 4f, 46f, 96f, 80f, 12f, red)
        fill.color = SHINE
        c.drawRoundRect(12f, 51f, 84f, 57f, 3f, 3f, fill)
        circle(c, 87f, 61f, 5.5f, YELLOW)
        circle(c, 13f, 61f, 4.5f, 0xFFFF8F00.toInt())
        c.drawArc(40f, 55f, 60f, 73f, 20f, 140f, false, line)
        for (x in floatArrayOf(28f, 72f)) {
            circle(c, x, 80f, 14f, 0xFF455A64.toInt())
            circle(c, x, 80f, 6f, 0xFFCFD8DC.toInt())
        }
    }

    /** Up and away: a striped hot air balloon floating between two clouds. */
    private fun flying(c: Canvas) {
        cloud(c, 15f, 84f, 0.6f)
        cloud(c, 85f, 32f, 0.5f)
        // Ropes and basket.
        c.drawLine(41f, 72f, 40f, 84f, line)
        c.drawLine(59f, 72f, 60f, 84f, line)
        roundRect(c, 37f, 83f, 63f, 98f, 4f, 0xFFD2884A.toInt())
        c.drawLine(37f, 90f, 63f, 90f, line)
        // The balloon: red with a yellow stripe in the middle.
        path.rewind()
        path.moveTo(40f, 72f)
        path.cubicTo(10f, 58f, 14f, 8f, 50f, 8f)
        path.cubicTo(86f, 8f, 90f, 58f, 60f, 72f)
        path.close()
        shape(c, 0xFFE53935.toInt())
        c.save()
        c.clipPath(path)
        fill.color = YELLOW
        c.drawOval(31f, 0f, 69f, 90f, fill)
        c.drawOval(31f, 0f, 69f, 90f, line)
        fill.color = Color.WHITE
        c.drawOval(44f, 0f, 56f, 90f, fill)
        c.drawOval(44f, 0f, 56f, 90f, line)
        fill.color = SHINE
        c.drawOval(20f, 20f, 28f, 40f, fill)
        c.restore()
        c.drawPath(path, line)
    }

    /** Fruits: a smiling apple, a strawberry and a banana. */
    private fun fruits(c: Canvas) {
        // Banana, behind the others.
        path.rewind()
        path.moveTo(40f, 78f)
        path.quadTo(64f, 104f, 96f, 58f)
        path.quadTo(70f, 82f, 40f, 78f)
        path.close()
        shape(c, YELLOW)
        circle(c, 96f, 58f, 3f, 0xFF6D4C41.toInt())
        circle(c, 40f, 78f, 3f, 0xFF6D4C41.toInt())
        // Apple.
        path.rewind()
        path.moveTo(32f, 30f)
        path.cubicTo(52f, 22f, 62f, 44f, 50f, 64f)
        path.cubicTo(44f, 74f, 38f, 70f, 32f, 70f)
        path.cubicTo(26f, 70f, 20f, 74f, 14f, 64f)
        path.cubicTo(2f, 44f, 12f, 22f, 32f, 30f)
        path.close()
        shape(c, 0xFFE53935.toInt())
        fill.color = 0x66FFFFFF
        c.drawOval(14f, 38f, 21f, 50f, fill)
        c.drawLine(32f, 30f, 34f, 17f, line)
        path.rewind()
        path.moveTo(35f, 22f)
        path.quadTo(46f, 9f, 56f, 15f)
        path.quadTo(46f, 27f, 35f, 22f)
        path.close()
        shape(c, GREEN)
        eye(c, 24f, 49f, 3f)
        eye(c, 40f, 49f, 3f)
        c.drawArc(26f, 50f, 38f, 62f, 25f, 130f, false, line)
        cheek(c, 18f, 58f, 3.8f)
        cheek(c, 46f, 58f, 3.8f)
        // Strawberry with its green hat and seeds.
        path.rewind()
        path.moveTo(76f, 58f)
        path.cubicTo(58f, 48f, 56f, 28f, 66f, 24f)
        path.cubicTo(72f, 22f, 76f, 26f, 76f, 26f)
        path.cubicTo(76f, 26f, 80f, 22f, 86f, 24f)
        path.cubicTo(96f, 28f, 94f, 48f, 76f, 58f)
        path.close()
        shape(c, 0xFFEF3B4B.toInt())
        poly(c, GREEN, 62f, 26f, 70f, 23f, 76f, 14f, 82f, 23f, 90f, 26f, 80f, 31f, 72f, 31f)
        fill.color = 0xFFFFF59D.toInt()
        for (p in arrayOf(
            floatArrayOf(68f, 37f), floatArrayOf(84f, 37f), floatArrayOf(76f, 42f), floatArrayOf(70f, 48f), floatArrayOf(82f, 48f),
        )) {
            c.drawCircle(p[0], p[1], 1.8f, fill)
        }
    }

    /** Weather: the sun peeking over a happy rain cloud, with drops falling. */
    private fun weather(c: Canvas) {
        val sx = 72f
        val sy = 27f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(
                sx + 20f * cos(a).toFloat(), sy + 20f * sin(a).toFloat(),
                sx + 27f * cos(a).toFloat(), sy + 27f * sin(a).toFloat(), line,
            )
        }
        circle(c, sx, sy, 15f, YELLOW)
        fill.color = SHINE
        c.drawOval(sx - 9f, sy - 10f, sx - 3f, sy - 4f, fill)
        cloud(c, 46f, 50f, 1.5f)
        eye(c, 40f, 55f, 3.2f)
        eye(c, 58f, 55f, 3.2f)
        c.drawArc(44f, 54f, 54f, 64f, 20f, 140f, false, line)
        cheek(c, 33f, 62f, 4f)
        cheek(c, 65f, 62f, 4f)
        drop(c, 26f, 87f, 1.1f)
        drop(c, 46f, 91f, 1.1f)
        drop(c, 66f, 87f, 1.1f)
    }

    /** Space: a rocket zooming off, a ringed planet and twinkly stars. */
    private fun space(c: Canvas) {
        // The ringed planet: back of the ring, ball, front of the ring.
        val px = 22f
        val py = 78f
        c.save()
        c.rotate(-20f, px, py)
        path.rewind()
        path.addArc(px - 23f, py - 8f, px + 23f, py + 8f, 180f, 180f)
        band(c, YELLOW, 3f)
        c.restore()
        circle(c, px, py, 15f, 0xFFAB47BC.toInt())
        fill.color = SHINE
        c.drawOval(px - 10f, py - 11f, px - 3f, py - 5f, fill)
        c.save()
        c.rotate(-20f, px, py)
        path.rewind()
        path.addArc(px - 23f, py - 8f, px + 23f, py + 8f, 0f, 180f)
        band(c, YELLOW, 3f)
        c.restore()

        star4(c, 14f, 22f, 9f)
        star4(c, 90f, 56f, 7f)
        star4(c, 40f, 10f, 6f)
        star4(c, 12f, 50f, 5f)

        // The rocket, drawn upright then tipped to point up and to the right.
        c.save()
        c.translate(58f, 46f)
        c.rotate(40f)
        c.scale(0.8f, 0.8f)
        c.translate(-50f, -48f)
        path.rewind()
        path.moveTo(40f, 66f)
        path.quadTo(42f, 82f, 50f, 96f)
        path.quadTo(58f, 82f, 60f, 66f)
        path.close()
        shape(c, 0xFFFF8F00.toInt())
        poly(c, YELLOW, 45f, 66f, 50f, 84f, 55f, 66f)
        poly(c, 0xFFE53935.toInt(), 34f, 46f, 17f, 72f, 35f, 68f)
        poly(c, 0xFFE53935.toInt(), 66f, 46f, 83f, 72f, 65f, 68f)
        path.rewind()
        path.moveTo(50f, 4f)
        path.cubicTo(72f, 22f, 73f, 46f, 66f, 68f)
        path.lineTo(34f, 68f)
        path.cubicTo(27f, 46f, 28f, 22f, 50f, 4f)
        path.close()
        fill.color = 0xFFF5F5F5.toInt()
        c.drawPath(path, fill)
        c.save()
        c.clipPath(path)
        fill.color = 0xFFE53935.toInt()
        c.drawRect(0f, 0f, 100f, 24f, fill)
        c.drawLine(0f, 24f, 100f, 24f, line)
        fill.color = SHINE
        c.drawRect(36f, 24f, 42f, 66f, fill)
        c.restore()
        c.drawPath(path, line)
        roundRect(c, 39f, 66f, 61f, 75f, 3f, 0xFF90A4AE.toInt())
        circle(c, 50f, 42f, 10f, 0xFFB3E5FC.toInt())
        fill.color = Color.WHITE
        c.drawCircle(46.5f, 38.5f, 3f, fill)
        c.restore()
    }

    /** Toys: a cuddly teddy bear in a red bow tie. */
    private fun toys(c: Canvas) {
        val fur = 0xFFB5793E.toInt()
        val tan = 0xFFF1C892.toInt()
        // Arms and body first, the head sits on top.
        circle(c, 21f, 80f, 10f, fur)
        circle(c, 79f, 80f, 10f, fur)
        oval(c, 27f, 64f, 73f, 98f, fur)
        oval(c, 37f, 76f, 63f, 96f, tan)
        // Ears.
        circle(c, 25f, 22f, 12.5f, fur)
        circle(c, 25f, 22f, 6.5f, tan)
        circle(c, 75f, 22f, 12.5f, fur)
        circle(c, 75f, 22f, 6.5f, tan)
        // Head and face.
        circle(c, 50f, 46f, 31f, fur)
        fill.color = SHINE
        c.drawOval(32f, 24f, 44f, 33f, fill)
        oval(c, 35f, 46f, 65f, 70f, tan)
        eye(c, 37f, 38f, 4f)
        eye(c, 63f, 38f, 4f)
        cheek(c, 29f, 52f, 4.5f)
        cheek(c, 71f, 52f, 4.5f)
        oval(c, 44f, 49f, 56f, 58f, Palette.INK)
        fill.color = Color.WHITE
        c.drawOval(46.5f, 50.5f, 50f, 53f, fill)
        c.drawLine(50f, 58f, 50f, 62f, line)
        c.drawArc(41f, 56f, 50f, 67f, 10f, 160f, false, line)
        c.drawArc(50f, 56f, 59f, 67f, 10f, 160f, false, line)
        // Bow tie.
        poly(c, 0xFFE53935.toInt(), 50f, 80f, 34f, 71f, 34f, 89f)
        poly(c, 0xFFE53935.toInt(), 50f, 80f, 66f, 71f, 66f, 89f)
        circle(c, 50f, 80f, 5f, 0xFFC62828.toInt())
    }

    /** Beach: a sand castle with a flag, on a heap of sand. */
    private fun beach(c: Canvas) {
        oval(c, 2f, 80f, 98f, 100f, 0xFFFFE0B2.toInt())
        // Keep, two towers and their battlements, all one outline.
        path.rewind()
        path.addRect(34f, 44f, 66f, 88f, Path.Direction.CW)
        unionRect(12f, 34f, 38f, 88f)
        unionRect(62f, 34f, 88f, 88f)
        for (x in floatArrayOf(12f, 22f, 32f, 62f, 72f, 82f)) unionRect(x, 27f, x + 6f, 34f)
        unionRect(34f, 38f, 41f, 44f)
        unionRect(59f, 38f, 66f, 44f)
        shape(c, 0xFFFFCC80.toInt())
        fill.color = SHINE
        c.drawRect(15f, 38f, 19f, 80f, fill)
        c.drawRect(65f, 38f, 69f, 80f, fill)
        // Bricks, slits and the arched door.
        colorLine(c, 16f, 72f, 34f, 72f, 0xFFE6A85C.toInt(), 2.5f)
        colorLine(c, 66f, 72f, 84f, 72f, 0xFFE6A85C.toInt(), 2.5f)
        colorLine(c, 38f, 52f, 62f, 52f, 0xFFE6A85C.toInt(), 2.5f)
        fill.color = 0xFF6D4C41.toInt()
        c.drawRoundRect(21f, 50f, 29f, 62f, 3f, 3f, fill)
        c.drawRoundRect(71f, 50f, 79f, 62f, 3f, 3f, fill)
        path.rewind()
        path.moveTo(42f, 88f)
        path.lineTo(42f, 74f)
        path.arcTo(42f, 60f, 58f, 88f, 180f, 180f, false)
        path.lineTo(58f, 88f)
        path.close()
        shape(c, 0xFF6D4C41.toInt())
        // The flag.
        c.drawLine(50f, 38f, 50f, 9f, line)
        poly(c, 0xFFE53935.toInt(), 50f, 9f, 72f, 16f, 50f, 24f)
    }
}
