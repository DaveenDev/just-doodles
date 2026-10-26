package com.daveendev.justdoodles

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** The stamps, in the order a second tap on the stamp button steps through them. */
enum class StampKind { STAR, HEART, FLOWER, DUCK, CAT, FISH, CAR, SUN }

/**
 * Shapes for the stamp tool. Each one is centered on (0,0) with a radius of about 1.
 *
 * A stamp is a body in the crayon color plus a few details (eyes, wheels, windows) in colors
 * of their own, so a duck still looks like a duck whatever color it's stamped in.
 */
object Stamps {
    val star: Path = starPath(points = 5, outer = 1f, inner = 0.45f)

    val heart: Path = Path().apply {
        moveTo(0f, 0.9f)
        cubicTo(-0.55f, 0.45f, -1.05f, 0.05f, -0.98f, -0.42f)
        cubicTo(-0.9f, -0.95f, -0.25f, -1.05f, 0f, -0.55f)
        cubicTo(0.25f, -1.05f, 0.9f, -0.95f, 0.98f, -0.42f)
        cubicTo(1.05f, 0.05f, 0.55f, 0.45f, 0f, 0.9f)
        close()
    }

    /** Five round petals, joined into one shape. */
    private val flower: Path = union(*Array(5) { i ->
        val a = Math.toRadians(-90.0 + i * 72)
        circle(0.55f * cos(a).toFloat(), 0.55f * sin(a).toFloat(), 0.45f)
    })

    /** What a detail is painted with. */
    private enum class Detail {
        /** White: eyes, windows. */
        LIGHT,
        /** Nearly black: pupils, wheels, a smile. */
        DARK,
        /** Orange (red on an orange stamp): a beak, a nose, a headlight. */
        ACCENT,
        /** A darker shade of the crayon color: a wing, the inside of an ear. */
        SHADE,
        /** The flower's middle: orange on a pale flower, white on any other. */
        CENTER,
    }

    private class Stamp(val body: Path, vararg val details: Pair<Path, Detail>)

    private val stamps: Map<StampKind, Stamp> = mapOf(
        StampKind.STAR to Stamp(star),
        StampKind.HEART to Stamp(heart),
        StampKind.FLOWER to Stamp(flower, circle(0f, 0f, 0.32f) to Detail.CENTER),

        // A duck swimming to the left.
        StampKind.DUCK to Stamp(
            union(
                oval(-0.72f, -0.08f, 0.78f, 0.72f),           // body
                poly(0.5f, 0.12f, 0.98f, -0.18f, 0.74f, 0.42f), // tail
                oval(-0.6f, -0.3f, -0.12f, 0.2f),             // neck
                circle(-0.38f, -0.42f, 0.36f),                // head
            ),
            Path().apply {
                moveTo(-0.66f, -0.47f)
                quadTo(-1.02f, -0.5f, -1.04f, -0.36f)
                quadTo(-0.98f, -0.25f, -0.66f, -0.3f)
                close()
            } to Detail.ACCENT,
            Path().apply {
                moveTo(-0.08f, 0.28f)
                quadTo(0.3f, 0f, 0.62f, 0.22f)
                quadTo(0.32f, 0.56f, -0.08f, 0.28f)
                close()
            } to Detail.SHADE,
            circle(-0.42f, -0.52f, 0.1f) to Detail.LIGHT,
            circle(-0.45f, -0.52f, 0.055f) to Detail.DARK,
        ),

        // A cat's face.
        StampKind.CAT to Stamp(
            union(
                oval(-0.78f, -0.5f, 0.78f, 0.82f),
                poly(-0.72f, -0.05f, -0.66f, -0.92f, -0.2f, -0.48f),
                poly(0.72f, -0.05f, 0.66f, -0.92f, 0.2f, -0.48f),
            ),
            poly(-0.6f, -0.24f, -0.6f, -0.72f, -0.3f, -0.46f) to Detail.SHADE,
            poly(0.6f, -0.24f, 0.6f, -0.72f, 0.3f, -0.46f) to Detail.SHADE,
            oval(-0.46f, -0.1f, -0.14f, 0.26f) to Detail.LIGHT,
            oval(0.14f, -0.1f, 0.46f, 0.26f) to Detail.LIGHT,
            oval(-0.36f, -0.04f, -0.24f, 0.22f) to Detail.DARK,
            oval(0.24f, -0.04f, 0.36f, 0.22f) to Detail.DARK,
            poly(-0.1f, 0.36f, 0.1f, 0.36f, 0f, 0.48f) to Detail.ACCENT,
        ),

        // A fish swimming to the left.
        StampKind.FISH to Stamp(
            union(
                oval(-0.85f, -0.52f, 0.5f, 0.52f),
                poly(0.35f, 0f, 0.95f, -0.5f, 0.8f, 0f, 0.95f, 0.5f),
                Path().apply {
                    moveTo(-0.32f, -0.42f)
                    quadTo(-0.02f, -0.86f, 0.25f, -0.38f)
                    close()
                },
            ),
            Path().apply {
                moveTo(-0.2f, -0.3f)
                quadTo(0.02f, 0f, -0.2f, 0.3f)
                quadTo(-0.08f, 0f, -0.2f, -0.3f)
                close()
            } to Detail.SHADE,
            circle(-0.5f, -0.1f, 0.15f) to Detail.LIGHT,
            circle(-0.54f, -0.1f, 0.08f) to Detail.DARK,
        ),

        // A little car, side on.
        StampKind.CAR to Stamp(
            union(
                Path().apply { addRoundRect(RectF(-0.95f, -0.12f, 0.95f, 0.38f), 0.16f, 0.16f, Path.Direction.CW) },
                poly(-0.55f, -0.1f, -0.36f, -0.55f, 0.32f, -0.55f, 0.56f, -0.1f),
            ),
            poly(-0.46f, -0.16f, -0.31f, -0.47f, -0.04f, -0.47f, -0.04f, -0.16f) to Detail.LIGHT,
            poly(0.04f, -0.16f, 0.04f, -0.47f, 0.27f, -0.47f, 0.45f, -0.16f) to Detail.LIGHT,
            circle(0.86f, 0.04f, 0.07f) to Detail.ACCENT,
            circle(-0.52f, 0.4f, 0.25f) to Detail.DARK,
            circle(0.52f, 0.4f, 0.25f) to Detail.DARK,
            circle(-0.52f, 0.4f, 0.1f) to Detail.LIGHT,
            circle(0.52f, 0.4f, 0.1f) to Detail.LIGHT,
        ),

        // A smiling sun.
        StampKind.SUN to Stamp(
            union(circle(0f, 0f, 0.56f), *Array(10) { i ->
                val a = Math.toRadians(i * 36.0)
                val b = Math.toRadians(11.0)
                poly(
                    (cos(a) * 1.0).toFloat(), (sin(a) * 1.0).toFloat(),
                    (cos(a + b) * 0.5).toFloat(), (sin(a + b) * 0.5).toFloat(),
                    (cos(a - b) * 0.5).toFloat(), (sin(a - b) * 0.5).toFloat(),
                )
            }),
            circle(-0.2f, -0.12f, 0.075f) to Detail.DARK,
            circle(0.2f, -0.12f, 0.075f) to Detail.DARK,
            Path().apply {
                moveTo(-0.26f, 0.1f)
                quadTo(0f, 0.42f, 0.26f, 0.1f)
                quadTo(0f, 0.26f, -0.26f, 0.1f)
                close()
            } to Detail.DARK,
        ),
    )

    /** The stamp's outer shape, without its details. */
    fun shape(kind: StampKind): Path = stamps.getValue(kind).body

    /** Draws a [kind] stamp in [color], placed by [matrix]. [scratch] is a path to work in. */
    fun draw(c: Canvas, kind: StampKind, matrix: Matrix, crayon: Int, paint: Paint, scratch: Path) {
        val stamp = stamps.getValue(kind)
        // A black car's wheels or a black sun's smile would vanish; a deep gray keeps them, as for stickers.
        val color = if (Color.luminance(crayon) < 0.05f && stamp.details.any { it.second == Detail.DARK }) DEEP_GRAY else crayon
        scratch.rewind()
        stamp.body.transform(matrix, scratch)
        paint.color = color
        c.drawPath(scratch, paint)
        for ((detail, role) in stamp.details) {
            scratch.rewind()
            detail.transform(matrix, scratch)
            paint.color = when (role) {
                Detail.LIGHT -> Color.WHITE
                Detail.DARK -> Palette.INK
                Detail.ACCENT -> accentFor(color)
                Detail.SHADE -> shade(color)
                Detail.CENTER -> if (Color.luminance(color) > 0.6f) ORANGE else Color.WHITE
            }
            c.drawPath(scratch, paint)
        }
    }

    fun starPath(points: Int, outer: Float, inner: Float): Path = Path().apply {
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 180.0 / points)
            val x = r * cos(a).toFloat()
            val y = r * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private const val ORANGE = 0xFFFF8F00.toInt()
    private const val DEEP_GRAY = 0xFF546E7A.toInt()

    /**
     * Orange, or red when the stamp itself is orange or brown, so a beak never vanishes into its
     * duck. Yellow (hue about 49) keeps the orange beak of a rubber duck.
     */
    private fun accentFor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return if (hsv[1] > 0.4f && hsv[0] in 15f..42f) 0xFFE53935.toInt() else ORANGE
    }

    private fun shade(color: Int) = Color.rgb(
        (Color.red(color) * 0.75f).toInt(),
        (Color.green(color) * 0.75f).toInt(),
        (Color.blue(color) * 0.75f).toInt(),
    )

    private fun circle(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r, Path.Direction.CW) }

    private fun oval(left: Float, top: Float, right: Float, bottom: Float) =
        Path().apply { addOval(RectF(left, top, right, bottom), Path.Direction.CW) }

    /** A closed polygon through the points x0, y0, x1, y1, ... */
    private fun poly(vararg xy: Float) = Path().apply {
        moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
        close()
    }

    /** One shape covering all of [parts], so overlaps and opposite windings never leave holes. */
    private fun union(vararg parts: Path) = Path().apply { for (p in parts) op(p, Path.Op.UNION) }
}
