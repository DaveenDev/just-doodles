package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Pictures the little artist can drop onto blank paper. Names in `sticker_names`, in this order. */
enum class Sticker { SUN, CLOUD, RAINBOW, TREE, FLOWER, HOUSE, CAR, FISH, CAT, DUCK, BUTTERFLY, BALLOON }

/**
 * Sticker artwork, drawn in code like the [Icons]. Each one is drawn into a 100 x 100 box with a
 * dark outline, and its main part takes the current crayon color, so picking red then the car
 * gives a red car.
 */
object Stickers {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val path = Path()
    private val part = Path()
    private val box = RectF()

    private const val BROWN = 0xFF8D5524.toInt()
    private const val LEAF = 0xFF43A047.toInt()
    private const val GLASS = 0xFFB3E5FC.toInt()
    private const val SUNNY = 0xFFFDD835.toInt()
    private const val BEAK = 0xFFFF8F00.toInt()
    private const val PINK = 0xFFFF8A80.toInt()

    fun draw(c: Canvas, sticker: Sticker, crayon: Int) {
        // On black, the dark outlines, eyes and smiles would vanish; a deep gray keeps them.
        val tint = if (Color.luminance(crayon) < 0.05f) 0xFF546E7A.toInt() else crayon
        when (sticker) {
            Sticker.SUN -> sun(c, tint)
            Sticker.CLOUD -> cloud(c, tint)
            Sticker.RAINBOW -> rainbow(c)
            Sticker.TREE -> tree(c, tint)
            Sticker.FLOWER -> flower(c, tint)
            Sticker.HOUSE -> house(c, tint)
            Sticker.CAR -> car(c, tint)
            Sticker.FISH -> fish(c, tint)
            Sticker.CAT -> cat(c, tint)
            Sticker.DUCK -> duck(c, tint)
            Sticker.BUTTERFLY -> butterfly(c, tint)
            Sticker.BALLOON -> balloon(c, tint)
        }
    }

    private fun light(tint: Int) = Palette.mix(tint, Color.WHITE, 0.5f)
    private fun dark(tint: Int) = Palette.mix(tint, Palette.INK, 0.3f)

    /** Fills [p] with [color], then outlines it. */
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

    private fun polygon(c: Canvas, color: Int, vararg xy: Float) {
        path.rewind()
        path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i + 1])
        path.close()
        shape(c, path, color)
    }

    /** A friendly eye: white, with a dark pupil and a twinkle. */
    private fun eye(c: Canvas, x: Float, y: Float, r: Float) {
        circle(c, x, y, r, Color.WHITE)
        fill.color = Palette.INK
        c.drawCircle(x + r * 0.15f, y + r * 0.1f, r * 0.55f, fill)
        fill.color = Color.WHITE
        c.drawCircle(x + r * 0.3f, y - r * 0.15f, r * 0.2f, fill)
    }

    private fun smile(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, dip: Float) {
        path.rewind()
        path.moveTo(x0, y0)
        path.quadTo((x0 + x1) / 2f, (y0 + y1) / 2f + dip, x1, y1)
        c.drawPath(path, ink)
    }

    private fun sun(c: Canvas, tint: Int) {
        for (i in 0 until 12) {
            c.save()
            c.rotate(i * 30f, 50f, 50f)
            part.rewind()
            part.moveTo(43f, 21f)
            part.lineTo(50f, 3f)
            part.lineTo(57f, 21f)
            part.close()
            shape(c, part, tint)
            c.restore()
        }
        circle(c, 50f, 50f, 30f, tint)
        eye(c, 40f, 45f, 5f)
        eye(c, 60f, 45f, 5f)
        smile(c, 39f, 57f, 61f, 57f, 10f)
    }

    private fun cloud(c: Canvas, tint: Int) {
        // Raindrops in the crayon color, under a pale cloud.
        for ((x, y) in listOf(32f to 84f, 50f to 90f, 68f to 84f)) {
            path.rewind()
            path.moveTo(x, y - 11f)
            path.cubicTo(x + 3f, y - 6f, x + 7f, y - 2f, x + 7f, y + 2f)
            path.cubicTo(x + 7f, y + 6f, x + 4f, y + 9f, x, y + 9f)
            path.cubicTo(x - 4f, y + 9f, x - 7f, y + 6f, x - 7f, y + 2f)
            path.cubicTo(x - 7f, y - 2f, x - 3f, y - 6f, x, y - 11f)
            path.close()
            shape(c, path, tint)
        }
        path.rewind()
        path.addCircle(30f, 50f, 15f, Path.Direction.CW)
        part.rewind()
        part.addCircle(50f, 38f, 20f, Path.Direction.CW)
        path.op(part, Path.Op.UNION)
        part.rewind()
        part.addCircle(70f, 48f, 15f, Path.Direction.CW)
        path.op(part, Path.Op.UNION)
        part.rewind()
        part.addRoundRect(16f, 46f, 84f, 68f, 11f, 11f, Path.Direction.CW)
        path.op(part, Path.Op.UNION)
        shape(c, path, light(tint))
        eye(c, 42f, 52f, 4f)
        eye(c, 58f, 52f, 4f)
        smile(c, 44f, 60f, 56f, 60f, 6f)
    }

    private fun rainbow(c: Canvas) {
        val bands = intArrayOf(0xFFE53935.toInt(), 0xFFFF8F00.toInt(), SUNNY, LEAF, 0xFF2962FF.toInt())
        for (i in bands.indices) {
            val outer = 44f - i * 7f
            val inner = outer - 7f
            path.rewind()
            path.arcTo(50f - outer, 74f - outer, 50f + outer, 74f + outer, 180f, 180f, false)
            path.arcTo(50f - inner, 74f - inner, 50f + inner, 74f + inner, 0f, -180f, false)
            path.close()
            shape(c, path, bands[i])
        }
        for ((x, flip) in listOf(14f to 1f, 86f to -1f)) {
            path.rewind()
            path.addCircle(x - 6f * flip, 76f, 9f, Path.Direction.CW)
            part.rewind()
            part.addCircle(x + 5f * flip, 70f, 10f, Path.Direction.CW)
            path.op(part, Path.Op.UNION)
            part.rewind()
            part.addCircle(x + 9f * flip, 80f, 8f, Path.Direction.CW)
            path.op(part, Path.Op.UNION)
            shape(c, path, Color.WHITE)
        }
    }

    private fun tree(c: Canvas, tint: Int) {
        path.rewind()
        path.addRoundRect(42f, 56f, 58f, 94f, 4f, 4f, Path.Direction.CW)
        shape(c, path, BROWN)
        path.rewind()
        for ((x, y, r) in listOf(Triple(50f, 28f, 21f), Triple(29f, 44f, 17f), Triple(71f, 44f, 17f), Triple(40f, 58f, 15f), Triple(60f, 58f, 15f))) {
            part.rewind()
            part.addCircle(x, y, r, Path.Direction.CW)
            path.op(part, Path.Op.UNION)
        }
        shape(c, path, tint)
        fill.color = light(tint)
        c.drawCircle(42f, 24f, 5f, fill)
        c.drawCircle(26f, 42f, 4f, fill)
    }

    private fun flower(c: Canvas, tint: Int) {
        path.rewind()
        path.addRoundRect(47.5f, 52f, 52.5f, 96f, 2.5f, 2.5f, Path.Direction.CW)
        shape(c, path, LEAF)
        path.rewind()
        path.moveTo(51f, 82f)
        path.cubicTo(56f, 68f, 66f, 64f, 76f, 64f)
        path.cubicTo(74f, 75f, 63f, 82f, 51f, 82f)
        path.close()
        shape(c, path, LEAF)
        for (i in 0 until 6) {
            val a = Math.toRadians(-90.0 + i * 60)
            circle(c, 50f + 18f * Math.cos(a).toFloat(), 36f + 18f * Math.sin(a).toFloat(), 12.5f, tint)
        }
        circle(c, 50f, 36f, 11f, if (Color.luminance(tint) > 0.6f) BEAK else SUNNY)
    }

    private fun house(c: Canvas, tint: Int) {
        path.rewind()
        path.addRect(64f, 18f, 73f, 40f, Path.Direction.CW)
        shape(c, path, dark(tint))
        path.rewind()
        path.addRect(22f, 46f, 78f, 92f, Path.Direction.CW)
        shape(c, path, tint)
        polygon(c, dark(tint), 12f, 49f, 50f, 14f, 88f, 49f)
        path.rewind()
        path.moveTo(43f, 92f)
        path.lineTo(43f, 70f)
        path.quadTo(43f, 62f, 50f, 62f)
        path.quadTo(57f, 62f, 57f, 70f)
        path.lineTo(57f, 92f)
        path.close()
        shape(c, path, BROWN)
        for (x in floatArrayOf(27f, 62f)) {
            path.rewind()
            path.addRect(x, 56f, x + 11f, 67f, Path.Direction.CW)
            shape(c, path, GLASS)
            c.drawLine(x + 5.5f, 56f, x + 5.5f, 67f, ink)
            c.drawLine(x, 61.5f, x + 11f, 61.5f, ink)
        }
    }

    private fun car(c: Canvas, tint: Int) {
        path.rewind()
        path.addRoundRect(7f, 50f, 93f, 75f, 10f, 10f, Path.Direction.CW)
        part.rewind()
        part.moveTo(25f, 52f)
        part.lineTo(33f, 30f)
        part.quadTo(34f, 28f, 37f, 28f)
        part.lineTo(63f, 28f)
        part.quadTo(66f, 28f, 67f, 30f)
        part.lineTo(78f, 52f)
        part.close()
        path.op(part, Path.Op.UNION)
        shape(c, path, tint)
        polygon(c, GLASS, 31f, 49f, 37.5f, 33f, 48f, 33f, 48f, 49f)
        polygon(c, GLASS, 52f, 49f, 52f, 33f, 62f, 33f, 70f, 49f)
        for (x in floatArrayOf(29f, 71f)) {
            circle(c, x, 76f, 11f, 0xFF37474F.toInt())
            circle(c, x, 76f, 4.5f, 0xFFCFD8DC.toInt())
        }
        circle(c, 88f, 58f, 3f, SUNNY)
    }

    private fun fish(c: Canvas, tint: Int) {
        polygon(c, dark(tint), 66f, 50f, 93f, 28f, 87f, 50f, 93f, 72f)
        path.rewind()
        path.moveTo(34f, 33f)
        path.quadTo(46f, 12f, 62f, 32f)
        path.close()
        shape(c, path, dark(tint))
        oval(c, 10f, 26f, 74f, 74f, tint)
        path.rewind()
        path.moveTo(48f, 34f)
        path.quadTo(57f, 50f, 48f, 66f)
        c.drawPath(path, ink)
        path.rewind()
        path.moveTo(58f, 37f)
        path.quadTo(65f, 50f, 58f, 63f)
        c.drawPath(path, ink)
        eye(c, 28f, 45f, 7f)
        smile(c, 15f, 57f, 26f, 57f, 5f)
    }

    private fun cat(c: Canvas, tint: Int) {
        polygon(c, tint, 18f, 44f, 22f, 6f, 46f, 26f)
        polygon(c, tint, 82f, 44f, 78f, 6f, 54f, 26f)
        polygon(c, PINK, 25f, 34f, 26f, 16f, 38f, 27f)
        polygon(c, PINK, 75f, 34f, 74f, 16f, 62f, 27f)
        oval(c, 12f, 20f, 88f, 90f, tint)
        for (x in floatArrayOf(36f, 64f)) {
            oval(c, x - 7f, 41f, x + 7f, 59f, Color.WHITE)
            fill.color = Palette.INK
            box.set(x - 3f, 44f, x + 3f, 57f)
            c.drawOval(box, fill)
        }
        polygon(c, PINK, 45f, 63f, 55f, 63f, 50f, 68f)
        path.rewind()
        path.moveTo(50f, 68f)
        path.quadTo(50f, 75f, 42f, 75f)
        path.moveTo(50f, 68f)
        path.quadTo(50f, 75f, 58f, 75f)
        c.drawPath(path, ink)
        for (s in floatArrayOf(-1f, 1f)) {
            c.drawLine(50f + 20f * s, 66f, 50f + 42f * s, 61f, ink)
            c.drawLine(50f + 20f * s, 71f, 50f + 42f * s, 74f, ink)
        }
    }

    private fun duck(c: Canvas, tint: Int) {
        polygon(c, tint, 16f, 64f, 5f, 42f, 30f, 54f)
        oval(c, 10f, 48f, 82f, 92f, tint)
        path.rewind()
        path.moveTo(29f, 64f)
        path.quadTo(44f, 54f, 60f, 65f)
        path.quadTo(46f, 80f, 29f, 71f)
        path.close()
        shape(c, path, dark(tint))
        circle(c, 63f, 33f, 20f, tint)
        path.rewind()
        path.moveTo(78f, 30f)
        path.quadTo(96f, 31f, 97f, 38f)
        path.quadTo(91f, 46f, 79f, 43f)
        path.close()
        shape(c, path, BEAK)
        eye(c, 67f, 29f, 5f)
    }

    private fun butterfly(c: Canvas, tint: Int) {
        circle(c, 29f, 36f, 21f, tint)
        circle(c, 71f, 36f, 21f, tint)
        circle(c, 33f, 67f, 15f, tint)
        circle(c, 67f, 67f, 15f, tint)
        val spot = light(tint)
        circle(c, 27f, 34f, 8f, spot)
        circle(c, 73f, 34f, 8f, spot)
        circle(c, 32f, 69f, 5f, spot)
        circle(c, 68f, 69f, 5f, spot)
        val body = 0xFF5D4037.toInt()
        path.rewind()
        path.addRoundRect(45f, 26f, 55f, 86f, 5f, 5f, Path.Direction.CW)
        shape(c, path, body)
        circle(c, 50f, 22f, 8f, body)
        path.rewind()
        path.moveTo(47f, 16f)
        path.quadTo(42f, 6f, 34f, 6f)
        path.moveTo(53f, 16f)
        path.quadTo(58f, 6f, 66f, 6f)
        c.drawPath(path, ink)
        circle(c, 34f, 6f, 3f, body)
        circle(c, 66f, 6f, 3f, body)
    }

    private fun balloon(c: Canvas, tint: Int) {
        path.rewind()
        path.moveTo(50f, 72f)
        path.quadTo(42f, 80f, 50f, 88f)
        path.quadTo(58f, 95f, 50f, 100f)
        c.drawPath(path, ink)
        polygon(c, dark(tint), 44f, 75f, 56f, 75f, 50f, 67f)
        oval(c, 24f, 6f, 76f, 70f, tint)
        fill.color = 0xCCFFFFFF.toInt()
        box.set(32f, 16f, 42f, 34f)
        c.drawOval(box, fill)
    }
}
