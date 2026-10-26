package com.daveendev.justdoodles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The topics behind the start menu's Learn card, in the order their cards appear. Each card shows
 * [icon] on [color], with [label] underneath for the grown-ups; [say] is said when it's picked.
 */
enum class Lesson(val icon: Icon, val label: Int, val say: Int, val color: Int) {
    COLORS(Icon.LEARN_COLORS, R.string.lesson_colors, R.string.say_lesson_colors, 0xFFE57373.toInt()),
    SHAPES(Icon.LEARN_SHAPES, R.string.lesson_shapes, R.string.say_lesson_shapes, 0xFF4FC3F7.toInt()),
    COUNT(Icon.LEARN_COUNT, R.string.lesson_count, R.string.say_lesson_count, 0xFFFFB74D.toInt()),
    ANIMALS(Icon.LEARN_ANIMALS, R.string.lesson_animals, R.string.say_lesson_animals, 0xFF81C784.toInt()),
    ABC(Icon.LEARN_ABC, R.string.lesson_abc, R.string.say_lesson_abc, 0xFFBA68C8.toInt()),
    BODY(Icon.LEARN_BODY, R.string.lesson_body, R.string.say_lesson_body, 0xFFF06292.toInt()),
    FOOD(Icon.LEARN_FOOD, R.string.lesson_food, R.string.say_lesson_food, 0xFFFF8A65.toInt()),
    SEA(Icon.LEARN_SEA, R.string.lesson_sea, R.string.say_lesson_sea, 0xFF4DD0E1.toInt()),
    BUGS(Icon.LEARN_BUGS, R.string.lesson_bugs, R.string.say_lesson_bugs, 0xFFAED581.toInt()),
    SKY(Icon.LEARN_SKY, R.string.lesson_sky, R.string.say_lesson_sky, 0xFF9FA8DA.toInt());

    fun create(context: Context, host: GameHost): View = when (this) {
        COLORS -> LearnView(context, host, Decks.colors(context))
        SHAPES -> LearnView(context, host, Decks.shapes(context, host))
        COUNT -> LearnView(context, host, Decks.count(context))
        ANIMALS -> LearnView(context, host, Decks.animals(context, host))
        ABC -> LearnView(context, host, Decks.abc(context, host))
        BODY -> BodyView(context, host)
        FOOD -> LearnView(context, host, Decks.group(context, host, "05-food"))
        SEA -> LearnView(context, host, Decks.group(context, host, "02-sea"))
        BUGS -> LearnView(context, host, Decks.group(context, host, "08-bugs"))
        SKY -> LearnView(context, host, Decks.group(context, host, "06-sky"))
    }
}

/**
 * One card of a Learn topic. [look] is said when the card comes in or is tapped ("A is for
 * apple!"), [ask] is the quiz question ("Where's the letter A?"), [answer] is said when the
 * child finds it, and [other] when it's tapped while another one was asked for ("That's A!").
 * [note] is a marimba note to play with it, and [count] how many things pop in one by one as
 * they're counted. Cards with [inQuiz] false are only looked at. Cards of the same [kind] look
 * too much alike to be told apart in the quiz, so they're never asked about together.
 */
class LearnItem(
    val look: String,
    val ask: String,
    val other: String,
    val answer: String = look,
    val note: Int = -1,
    val count: Int = 0,
    val inQuiz: Boolean = true,
    val kind: String? = null,
    val art: (Canvas, RectF, CardState) -> Unit,
)

/** How a card is drawn right now, and where its pictures come from. */
class CardState(private val pictures: (ColoringPage, Int, Int) -> Bitmap) {
    /** How many of a counting card's things are showing. */
    var shown = 0
    /** True on the quiz's smaller cards. */
    var quiz = false

    fun picture(page: ColoringPage, width: Int, height: Int) = pictures(page, width, height)
}

/** The cards of each Learn topic, with their words from strings.xml. */
object Decks {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val box = RectF()
    private val side = RectF()

    fun colors(context: Context): List<LearnItem> {
        val names = context.resources.getStringArray(R.array.color_names)
        return Palette.colors.indices.map { i ->
            val name = names.getOrElse(i) { "" }
            LearnItem(
                look = "$name!",
                ask = context.getString(R.string.say_where_color, name.lowercase()),
                other = context.getString(R.string.say_thats_color, name.lowercase()),
                note = i,
                kind = COLOR_KINDS.getOrNull(i),
            ) { c, r, _ -> blob(c, r, Palette.colors[i]) }
        }
    }

    /** Colors too close for a toddler to tell apart, in Palette.colors order. */
    private val COLOR_KINDS = arrayOf(
        "red", "orange", "yellow", "green", "green", "blue",
        "blue", "purple", "pink", "brown", "orange", "black",
    )

    fun shapes(context: Context, host: GameHost): List<LearnItem> = group(context, host, SHAPES_GROUP)

    /** Every picture in a coloring group ("05-food"), named: "Banana!", then "Where's the banana?" */
    fun group(context: Context, host: GameHost, id: String): List<LearnItem> =
        host.group(id)?.pages.orEmpty().mapNotNull { page ->
            val title = page.title ?: return@mapNotNull null
            LearnItem(
                look = "$title!",
                ask = context.getString(R.string.say_where_is, title.lowercase()),
                other = context.getString(R.string.say_thats_the, title.lowercase()),
            ) { c, r, s -> picture(c, r, s, page) }
        }

    fun count(context: Context): List<LearnItem> {
        val words = context.resources.getStringArray(R.array.number_words)
        val things = context.resources.getStringArray(R.array.count_things)
        return words.indices.mapNotNull { i ->
            val n = i + 1
            val (stickerName, nouns) = things.getOrNull(i)?.split('=', limit = 2) ?: return@mapNotNull null
            val sticker = Sticker.entries.firstOrNull { it.name == stickerName.trim() } ?: Sticker.SUN
            val one = nouns.substringBefore('|').trim()
            val many = nouns.substringAfter('|').trim()
            val noun = if (n == 1) one else many
            val total = context.getString(R.string.say_count_total, words[i], noun)
            val color = Palette.colors[i % (Palette.colors.size - 1)]
            LearnItem(
                look = words.take(n).joinToString(", ") + "! " + total,
                ask = if (n == 1) context.getString(R.string.say_where_one, one)
                else context.getString(R.string.say_where_count, words[i].lowercase(), many),
                other = total,
                answer = total,
                count = n,
                inQuiz = n <= QUIZ_MAX_COUNT,
            ) { c, r, s -> counting(c, r, sticker, color, n, s.shown, n.toString()) }
        }
    }

    fun animals(context: Context, host: GameHost): List<LearnItem> =
        context.resources.getStringArray(R.array.animal_sounds).mapNotNull { entry ->
            val title = entry.substringBefore('=').trim()
            val sound = entry.substringAfter('=').trim()
            val page = host.page(title) ?: return@mapNotNull null
            LearnItem(
                look = context.getString(R.string.say_animal_says, title.lowercase(), sound.lowercase()),
                ask = context.getString(R.string.say_who_says, sound.lowercase()),
                other = context.getString(R.string.say_thats_the, title.lowercase()),
            ) { c, r, s -> picture(c, r, s, page) }
        }

    fun abc(context: Context, host: GameHost): List<LearnItem> =
        context.resources.getStringArray(R.array.abc_words).mapIndexed { i, entry ->
            val letter = entry.substringBefore('=').trim()
            val word = entry.substringAfter('=').trim()
            val page = host.page(word)
            val color = Palette.colors[i % (Palette.colors.size - 1)]
            LearnItem(
                look = context.getString(R.string.say_is_for, letter, word.lowercase()),
                ask = context.getString(R.string.say_where_letter, letter),
                other = context.getString(R.string.say_thats_letter, letter),
            ) { c, r, s ->
                val glyphs = letter.uppercase() + letter.lowercase()
                if (s.quiz || page == null) {
                    letters(c, r, glyphs, color)
                } else {
                    box.set(r.left, r.top, r.left + r.width() * 0.45f, r.bottom)
                    letters(c, box, glyphs, color)
                    side.set(r.left + r.width() * 0.42f, r.top, r.right, r.bottom)
                    picture(c, side, s, page)
                }
            }
        }

    // ---- Card art -------------------------------------------------------------------------

    private fun picture(c: Canvas, r: RectF, s: CardState, page: ColoringPage) {
        c.drawBitmap(s.picture(page, r.width().toInt(), r.height().toInt()), null, r, bitmapPaint)
    }

    /** A round splash of paint with a shine on it. */
    private fun blob(c: Canvas, r: RectF, color: Int) {
        val cx = r.centerX()
        val cy = r.centerY()
        val radius = min(r.width(), r.height()) * 0.36f
        path.rewind()
        val bumps = 7
        val steps = 180
        for (k in 0..steps) {
            val a = k * 2 * PI / steps
            val wobble = 1f + 0.05f * sin(a * bumps).toFloat() + 0.03f * cos(a * 3 + 1).toFloat()
            val x = cx + radius * wobble * cos(a).toFloat()
            val y = cy + radius * wobble * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.color = color
        c.drawPath(path, fill)
        ink.strokeWidth = radius * 0.05f
        c.drawPath(path, ink)
        fill.color = 0x66FFFFFF
        c.drawOval(cx - radius * 0.55f, cy - radius * 0.62f, cx - radius * 0.1f, cy - radius * 0.32f, fill)
    }

    /** A big letter pair ("Aa") in [color], outlined in ink. */
    private fun letters(c: Canvas, r: RectF, glyphs: String, color: Int) {
        text.textSize = r.height() * 0.62f
        // Wide letters ("Mm", "Ww") shrink to fit with a margin.
        val wide = text.measureText(glyphs)
        if (wide > r.width() * 0.84f) text.textSize *= r.width() * 0.84f / wide
        val y = r.centerY() - (text.descent() + text.ascent()) / 2f
        text.style = Paint.Style.FILL
        text.color = color
        c.drawText(glyphs, r.centerX(), y, text)
        text.style = Paint.Style.STROKE
        text.strokeWidth = text.textSize * 0.035f
        text.strokeJoin = Paint.Join.ROUND
        text.color = Palette.INK
        c.drawText(glyphs, r.centerX(), y, text)
    }

    /**
     * [n] stickers in up to two rows on the right, the first [shown] of them showing, and the
     * numeral on the left once they're all counted.
     */
    private fun counting(c: Canvas, r: RectF, sticker: Sticker, color: Int, n: Int, shown: Int, numeral: String) {
        val numW = r.width() * 0.3f
        if (shown >= n) {
            box.set(r.left, r.top, r.left + numW, r.bottom)
            text.textSize = min(box.height() * 0.6f, box.width() * 0.75f)
            val y = box.centerY() - (text.descent() + text.ascent()) / 2f
            text.style = Paint.Style.FILL
            text.color = color
            c.drawText(numeral, box.centerX(), y, text)
            text.style = Paint.Style.STROKE
            text.strokeWidth = text.textSize * 0.04f
            text.color = Palette.INK
            c.drawText(numeral, box.centerX(), y, text)
        }
        val rows = if (n > 5) 2 else 1
        val cols = (n + rows - 1) / rows
        val areaW = r.width() - numW
        val cell = min(areaW * 0.92f / cols, r.height() * 0.8f / rows)
        val left = r.left + numW + (areaW - cell * cols) / 2f
        val top = r.centerY() - cell * rows / 2f
        for (k in 0 until shown.coerceAtMost(n)) {
            val row = k / cols
            val col = k % cols
            // A short second row sits in the middle.
            val inRow = if (row == rows - 1) n - row * cols else cols
            val x = left + (cols - inRow) * cell / 2f + col * cell
            c.save()
            c.translate(x + cell * 0.06f, top + row * cell + cell * 0.06f)
            c.scale(cell * 0.88f / 100f, cell * 0.88f / 100f)
            Stickers.draw(c, sticker, color)
            c.restore()
        }
    }

    private const val SHAPES_GROUP = "10-shapes"
    /** Counting cards up to this number come up in the quiz; bigger ones are only counted. */
    private const val QUIZ_MAX_COUNT = 5
}
