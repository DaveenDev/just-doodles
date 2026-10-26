package com.daveendev.justdoodles

import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Reads the small subset of SVG used by the coloring pages in `assets/pages`:
 * `<svg viewBox>`, `<path d>`, `<circle>`, `<ellipse>`, `<rect>`, `<line>`, `<polyline>` and `<polygon>`.
 *
 * Styling is ignored: every shape becomes an outline. Transforms are not supported, and arcs
 * (the `A` path command) are drawn as straight lines, so pages should avoid both.
 *
 * A `<title>` right inside `<svg>` names the page; one inside a shape names that part of the
 * picture (`<circle ...><title>Eye</title></circle>`).
 */
object SvgParser {

    /**
     * [title] is the page's `<title>`, which the app says out loud when the page is picked.
     * [names] has one entry per shape: the part's name, or null.
     */
    class Drawing(val viewBox: RectF, val shapes: List<Path>, val title: String?, val names: List<String?>)

    fun parse(input: InputStream): Drawing {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var viewBox = RectF(0f, 0f, 100f, 100f)
        val shapes = ArrayList<Path>()
        val names = ArrayList<String?>()
        var title: String? = null
        // The shape whose tag we're inside (for its <title>), and how deep that tag is.
        var openShape = -1
        var openDepth = 0

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == openDepth) openShape = -1
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val attr = { name: String -> parser.getAttributeValue(null, name) }
            val num = { name: String -> attr(name)?.trim()?.removeSuffix("px")?.toFloatOrNull() ?: 0f }
            val shapeCount = shapes.size

            when (parser.name) {
                "svg" -> {
                    val vb = attr("viewBox")?.let(::numbers)
                    viewBox = if (vb != null && vb.size == 4) {
                        RectF(vb[0], vb[1], vb[0] + vb[2], vb[1] + vb[3])
                    } else {
                        RectF(0f, 0f, num("width").takeIf { it > 0 } ?: 100f, num("height").takeIf { it > 0 } ?: 100f)
                    }
                }
                "title" -> {
                    val depth = parser.depth
                    val text = parser.nextText().trim().ifEmpty { null }
                    if (openShape >= 0 && depth == openDepth + 1) names[openShape] = text
                    else if (depth == 2) title = text
                }
                "path" -> attr("d")?.let { shapes += PathData.parse(it) }
                "circle" -> shapes += Path().apply {
                    addCircle(num("cx"), num("cy"), num("r"), Path.Direction.CW)
                }
                "ellipse" -> shapes += Path().apply {
                    val cx = num("cx"); val cy = num("cy"); val rx = num("rx"); val ry = num("ry")
                    addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
                }
                "rect" -> shapes += Path().apply {
                    val x = num("x"); val y = num("y")
                    val box = RectF(x, y, x + num("width"), y + num("height"))
                    // As in SVG, a missing rx or ry copies the other one.
                    val rx = attr("rx")?.toFloatOrNull() ?: attr("ry")?.toFloatOrNull() ?: 0f
                    val ry = attr("ry")?.toFloatOrNull() ?: rx
                    if (rx > 0f || ry > 0f) addRoundRect(box, rx, ry, Path.Direction.CW)
                    else addRect(box, Path.Direction.CW)
                }
                "line" -> shapes += Path().apply {
                    moveTo(num("x1"), num("y1"))
                    lineTo(num("x2"), num("y2"))
                }
                "polyline", "polygon" -> {
                    val p = numbers(attr("points") ?: "")
                    if (p.size >= 4) shapes += Path().apply {
                        moveTo(p[0], p[1])
                        var i = 2
                        while (i + 1 < p.size) {
                            lineTo(p[i], p[i + 1])
                            i += 2
                        }
                        if (parser.name == "polygon") close()
                    }
                }
            }
            if (shapes.size > shapeCount) {
                names += null
                openShape = shapes.size - 1
                openDepth = parser.depth
            }
        }
        return Drawing(viewBox, shapes, title, names)
    }

    private fun numbers(s: String): List<Float> {
        val t = PathData.Tokenizer(s)
        return buildList { while (t.hasNumber()) add(t.number()) }
    }
}

/** Turns SVG path data ("M 10 10 C 20 0, 40 0, 50 10 Z") into an Android [Path]. */
object PathData {

    fun parse(d: String): Path {
        val path = Path()
        val t = Tokenizer(d)
        var cmd = 'M'
        var last = ' '
        var x = 0f; var y = 0f            // current point
        var startX = 0f; var startY = 0f  // start of the current sub-path, for Z
        var ctrlX = 0f; var ctrlY = 0f    // previous control point, for S and T

        while (true) {
            val next = t.command()
            if (next != null) {
                cmd = next
            } else if (!t.hasNumber() || cmd == 'Z' || cmd == 'z') {
                break
            }
            val relative = cmd.isLowerCase()
            val ox = if (relative) x else 0f
            val oy = if (relative) y else 0f

            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + t.number(); y = oy + t.number()
                    path.moveTo(x, y)
                    startX = x; startY = y
                    // Extra coordinate pairs after a move are line segments.
                    cmd = if (relative) 'l' else 'L'
                }
                'L' -> {
                    x = ox + t.number(); y = oy + t.number()
                    path.lineTo(x, y)
                }
                'H' -> {
                    x = ox + t.number()
                    path.lineTo(x, y)
                }
                'V' -> {
                    y = oy + t.number()
                    path.lineTo(x, y)
                }
                'C' -> {
                    val x1 = ox + t.number(); val y1 = oy + t.number()
                    ctrlX = ox + t.number(); ctrlY = oy + t.number()
                    x = ox + t.number(); y = oy + t.number()
                    path.cubicTo(x1, y1, ctrlX, ctrlY, x, y)
                }
                'S' -> {
                    val smooth = last == 'C' || last == 'S'
                    val x1 = if (smooth) 2 * x - ctrlX else x
                    val y1 = if (smooth) 2 * y - ctrlY else y
                    ctrlX = ox + t.number(); ctrlY = oy + t.number()
                    x = ox + t.number(); y = oy + t.number()
                    path.cubicTo(x1, y1, ctrlX, ctrlY, x, y)
                }
                'Q' -> {
                    ctrlX = ox + t.number(); ctrlY = oy + t.number()
                    x = ox + t.number(); y = oy + t.number()
                    path.quadTo(ctrlX, ctrlY, x, y)
                }
                'T' -> {
                    val smooth = last == 'Q' || last == 'T'
                    ctrlX = if (smooth) 2 * x - ctrlX else x
                    ctrlY = if (smooth) 2 * y - ctrlY else y
                    x = ox + t.number(); y = oy + t.number()
                    path.quadTo(ctrlX, ctrlY, x, y)
                }
                'A' -> {
                    repeat(5) { t.number() }
                    x = ox + t.number(); y = oy + t.number()
                    path.lineTo(x, y)
                }
                'Z' -> {
                    path.close()
                    x = startX; y = startY
                }
                else -> return path
            }
            last = cmd.uppercaseChar()
        }
        return path
    }

    class Tokenizer(private val s: String) {
        private var i = 0

        private fun skipSeparators() {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
        }

        fun hasNumber(): Boolean {
            skipSeparators()
            if (i >= s.length) return false
            val c = s[i]
            return c.isDigit() || c == '-' || c == '+' || c == '.'
        }

        fun command(): Char? {
            skipSeparators()
            return if (i < s.length && s[i].isLetter()) s[i++] else null
        }

        fun number(): Float {
            skipSeparators()
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var seenDot = false
            var seenExp = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c.isDigit() -> i++
                    c == '.' && !seenDot && !seenExp -> { seenDot = true; i++ }
                    (c == 'e' || c == 'E') && !seenExp -> {
                        seenExp = true
                        i++
                        if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                    }
                    else -> break
                }
            }
            return s.substring(start, i).toFloat()
        }
    }
}
