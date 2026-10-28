package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Choo choo: a little steam train on a track, with hills, trees and clouds sliding past. Tapping
 * the engine goes "Choo choo!" with a whoosh and a puff of smoke, and hooks on a colored car with
 * an animal or a fruit in its window. Tapping a car says what is inside. When the sixth car
 * is on, there is a cheer, the whole train rolls away and a new engine arrives. Nothing can go wrong.
 */
@SuppressLint("ViewConstructor")
class TrainView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { WAITING, LEAVING, ARRIVING }

    private class Car(val title: String?, val picture: Bitmap?, val color: Int, val bornAt: Long) {
        var wiggleAt = -1L
    }

    private class Puff(val x: Float, val y: Float, val size: Float, val at: Long)

    private class Burst(val x: Float, val y: Float, val color: Int, val at: Long)

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val fruits = setOf("Apple", "Banana", "Strawberry", "Watermelon", "Pear", "Orange", "Grapes", "Cherries", "Pineapple", "Mango")
    private val carColors = (0..8).map { Palette.colors[it] }

    private val cars = ArrayList<Car>()
    private val puffs = ArrayList<Puff>()
    private val bursts = ArrayList<Burst>()
    private val deck = ArrayList<ColoringPage>()
    private val pictures = HashMap<String, Bitmap>()
    private var colorDeck = ArrayList<Int>()
    private var engineColor = carColors[0]

    private var state = State.WAITING
    private var stateAt = 0L
    private var offset = 0f
    private var velocity = 0f
    private var scroll = 0f
    private var speedMul = 1f
    private var wheelDeg = 0f
    private var engineWiggleAt = -1L
    private var lastPuffAt = 0L
    private var lastFrame = 0L

    // Layout, all set in onSizeChanged.
    private var unit = 0f
    private var engineLeft = 0f
    private var railY = 0f
    private var groundTop = 0f

    private val cheerLater = Runnable { host.cheer() }
    private val leave = Runnable { leaveNow() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    /** Outline for the train, drawn in a 100-unit box so it scales with the train. */
    private val unitInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.INK
    }
    private val path = Path()
    private val box = RectF()

    init {
        contentDescription = context.getString(R.string.game_train)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        unit = min(w * 0.125f, h * 0.34f)
        railY = h * 0.78f
        groundTop = railY - unit * 0.2f
        engineLeft = (w - (MAX_CARS + 1) * unit) / 2f + MAX_CARS * unit
        pictures.clear()
        removeCallbacks(cheerLater)
        removeCallbacks(leave)
        newTrain(arrive = false)
        invalidate()
    }

    /** A fresh engine with no cars: already waiting, or rolling in from the left. */
    private fun newTrain(arrive: Boolean) {
        cars.clear()
        engineColor = carColors.random(random)
        if (colorDeck.isEmpty()) colorDeck = ArrayList(carColors.shuffled(random))
        velocity = 0f
        stateAt = SystemClock.uptimeMillis()
        if (arrive) {
            state = State.ARRIVING
            offset = -(width + unit)
        } else {
            state = State.WAITING
            offset = 0f
        }
    }

    private fun nextPage(): ColoringPage? {
        if (deck.isEmpty()) {
            val animals = host.group("01-animals")?.pages ?: emptyList()
            val food = host.group("05-food")?.pages?.filter { it.title in fruits } ?: emptyList()
            val all = (animals + food).ifEmpty { host.pictureGroups.flatMap { it.pages } }
            deck += all.filter { it.title != null }.shuffled(random)
        }
        return if (deck.isEmpty()) null else deck.removeAt(deck.lastIndex)
    }

    private fun nextColor(): Int {
        if (colorDeck.isEmpty()) colorDeck = ArrayList(carColors.shuffled(random))
        return colorDeck.removeAt(colorDeck.lastIndex)
    }

    private fun addCar(now: Long) {
        val page = nextPage()
        val size = (unit * 0.7f).toInt().coerceAtLeast(8)
        val picture = page?.let { p -> pictures.getOrPut(p.id) { host.picture(p, size, size) } }
        cars += Car(page?.title, picture, nextColor(), now)
        host.sounds.note(min(11, 2 + cars.size))
        if (cars.size >= MAX_CARS) {
            postDelayed(cheerLater, 600L)
            postDelayed(leave, LEAVE_AFTER_MS)
        }
    }

    private fun leaveNow() {
        if (state != State.WAITING) return
        state = State.LEAVING
        stateAt = SystemClock.uptimeMillis()
        velocity = 0f
        host.sounds.whoosh()
    }

    // Where things are on screen.
    private fun engineX() = engineLeft + offset
    private fun carX(i: Int) = engineLeft + offset - (i + 1) * unit
    private fun chimneyX() = engineX() + unit * 0.77f
    private fun chimneyY() = railY - unit + unit * 0.12f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val m = e.actionMasked
        if (m != MotionEvent.ACTION_DOWN && m != MotionEvent.ACTION_POINTER_DOWN) return true
        tap(e.getX(e.actionIndex), e.getY(e.actionIndex), SystemClock.uptimeMillis())
        return true
    }

    private fun tap(x: Float, y: Float, now: Long) {
        val slop = 8f * density
        val top = railY - unit
        if (x in (engineX() - slop)..(engineX() + unit + slop) && y in (top - slop)..(railY + slop)) {
            choo(now)
            return
        }
        for (i in cars.indices.reversed()) {
            val left = carX(i)
            if (x in left..(left + unit) && y in (railY - unit * 0.9f)..(railY + slop)) {
                val car = cars[i]
                car.wiggleAt = now
                host.sounds.pop(0.9f + i * 0.1f)
                car.title?.let { host.say("$it!") }
                return
            }
        }
        // Anywhere else: a note and a little burst of color, so every tap answers.
        host.sounds.note(random.nextInt(12))
        bursts += Burst(x, y, carColors.random(random), now)
    }

    private fun choo(now: Long) {
        engineWiggleAt = now
        host.sounds.whoosh()
        host.say(context.getString(R.string.g_train_choo))
        for (k in 0 until 3) puffs += Puff(chimneyX(), chimneyY(), unit * (0.22f + 0.04f * k), now + k * 140L)
        if (state == State.WAITING && cars.size < MAX_CARS) addCar(now)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cheerLater)
        removeCallbacks(leave)
        super.onDetachedFromWindow()
    }

    // ---- Moving ----

    private fun update(now: Long, dt: Float) {
        val target = if (state == State.LEAVING) 3.5f else 1f
        speedMul += (target - speedMul) * min(1f, dt * 2f)
        val dx = BASE_SPEED_DP * density * speedMul * dt
        scroll += dx
        wheelDeg += dx / (unit * 0.13f) * (180f / PI.toFloat())

        when (state) {
            State.LEAVING -> {
                velocity += width * 0.8f * dt
                offset += velocity * dt
                if (offset > width) newTrain(arrive = true)
            }
            State.ARRIVING -> {
                val t = ((now - stateAt) / ARRIVE_MS.toFloat()).coerceIn(0f, 1f)
                val ease = 1f - (1f - t) * (1f - t) * (1f - t)
                offset = -(width + unit) * (1f - ease)
                if (t >= 1f) {
                    state = State.WAITING
                    offset = 0f
                }
            }
            State.WAITING -> Unit
        }

        // A small puff every so often, even when nobody has tapped.
        if (now - lastPuffAt > 800L) {
            lastPuffAt = now
            puffs += Puff(chimneyX(), chimneyY(), unit * 0.14f, now)
        }
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0 || unit <= 0f) return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        update(now, dt)

        canvas.drawColor(0xFFB3E5FC.toInt())
        drawSun(canvas)
        drawClouds(canvas)
        drawHills(canvas, groundTop, height * 0.22f, 160f * density, 0.12f, 0xFFB2DFDB.toInt(), 0.5f)
        drawHills(canvas, groundTop + height * 0.02f, height * 0.12f, 110f * density, 0.3f, 0xFFA5D6A7.toInt(), 2f)
        fill.color = 0xFF8BC34A.toInt()
        canvas.drawRect(0f, groundTop + height * 0.02f, width.toFloat(), height.toFloat(), fill)
        drawTrees(canvas)
        drawTrack(canvas)
        drawTrain(canvas, now)
        drawPuffs(canvas, now)
        drawBursts(canvas, now)
        postInvalidateOnAnimation()
    }

    private fun drawSun(c: Canvas) {
        val r = height * 0.07f
        val x = width * 0.1f
        val y = height * 0.15f
        fill.color = 0xFFFFD54F.toInt()
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, ink)
        fill.color = Palette.INK
        c.drawCircle(x - r * 0.3f, y - r * 0.15f, r * 0.09f, fill)
        c.drawCircle(x + r * 0.3f, y - r * 0.15f, r * 0.09f, fill)
        box.set(x - r * 0.35f, y - r * 0.1f, x + r * 0.35f, y + r * 0.5f)
        c.drawArc(box, 25f, 130f, false, ink)
    }

    /** Clouds drift past slowly, far behind everything else. */
    private fun drawClouds(c: Canvas) {
        val period = unit * 4.5f
        val sc = scroll * 0.12f
        val first = floor(sc / period).toInt()
        val count = (width / period).toInt() + 3
        fill.color = Palette.PAPER
        for (j in 0 until count) {
            val idx = first + j
            val x = idx * period - sc
            val cy = groundTop * (0.1f + 0.14f * (idx * 5).mod(3))
            val r = unit * 0.17f
            c.drawCircle(x, cy, r, fill)
            c.drawCircle(x + r * 1.1f, cy - r * 0.35f, r * 1.2f, fill)
            c.drawCircle(x + r * 2.3f, cy, r, fill)
            box.set(x - r, cy, x + r * 3.3f, cy + r)
            c.drawRoundRect(box, r * 0.5f, r * 0.5f, fill)
        }
    }

    private fun drawHills(c: Canvas, baseY: Float, amp: Float, len: Float, parallax: Float, color: Int, phase: Float) {
        val sc = scroll * parallax
        val step = 14f * density
        path.rewind()
        path.moveTo(0f, height.toFloat())
        var x = 0f
        while (true) {
            val v = 0.5f + 0.3f * sin((x + sc) / len + phase) + 0.2f * sin((x + sc) / (len * 0.43f) + phase * 2f)
            path.lineTo(x, baseY - amp * v)
            if (x >= width) break
            x += step
        }
        path.lineTo(x, height.toFloat())
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun drawTrees(c: Canvas) {
        val period = unit * 1.6f
        val sc = scroll * 0.55f
        val first = floor(sc / period).toInt() - 1
        val count = (width / period).toInt() + 4
        val baseY = railY - unit * 0.1f
        for (j in 0 until count) {
            val idx = first + j
            val x = idx * period - sc
            when ((idx * 7 + 3).mod(5)) {
                0, 1 -> tree(c, x, baseY, unit * 0.62f, 0xFF43A047.toInt())
                2 -> bush(c, x, baseY, unit * 0.3f)
                3 -> tree(c, x, baseY, unit * 0.45f, 0xFF66BB6A.toInt())
                else -> Unit // A gap in the trees.
            }
        }
    }

    private fun tree(c: Canvas, x: Float, baseY: Float, h: Float, leaf: Int) {
        fill.color = 0xFF8D5524.toInt()
        box.set(x - h * 0.06f, baseY - h * 0.45f, x + h * 0.06f, baseY)
        c.drawRect(box, fill)
        c.drawRect(box, ink)
        fill.color = leaf
        c.drawCircle(x, baseY - h * 0.62f, h * 0.34f, fill)
        c.drawCircle(x, baseY - h * 0.62f, h * 0.34f, ink)
        fill.color = 0x44FFFFFF
        c.drawCircle(x - h * 0.1f, baseY - h * 0.72f, h * 0.1f, fill)
    }

    private fun bush(c: Canvas, x: Float, baseY: Float, r: Float) {
        fill.color = 0xFF2E9D4A.toInt()
        box.set(x - r, baseY - r * 1.1f, x + r, baseY)
        c.drawOval(box, fill)
        c.drawOval(box, ink)
        fill.color = 0xFFE53935.toInt()
        c.drawCircle(x - r * 0.3f, baseY - r * 0.6f, r * 0.14f, fill)
        c.drawCircle(x + r * 0.35f, baseY - r * 0.45f, r * 0.14f, fill)
    }

    private fun drawTrack(c: Canvas) {
        // Gravel, sleepers sliding by, then the rail on top.
        fill.color = 0xFFBCAAA4.toInt()
        c.drawRect(0f, railY, width.toFloat(), railY + unit * 0.16f, fill)
        val spacing = unit * 0.32f
        val shift = scroll % spacing
        fill.color = 0xFF8D6E63.toInt()
        var x = -shift
        while (x < width) {
            c.drawRect(x, railY + unit * 0.02f, x + unit * 0.14f, railY + unit * 0.14f, fill)
            x += spacing
        }
        ink.strokeWidth = 6f * density
        c.drawLine(0f, railY, width.toFloat(), railY, ink)
        ink.strokeWidth = 3f * density
        fill.color = 0xFFB0BEC5.toInt()
        c.drawRect(0f, railY - 1.5f * density, width.toFloat(), railY + 1.5f * density, fill)
    }

    private fun drawTrain(c: Canvas, now: Long) {
        val s = unit / 100f
        for (i in cars.indices) {
            val car = cars[i]
            var dy = 0f
            var angle = 0f
            val born = ((now - car.bornAt) / CAR_DROP_MS.toFloat()).coerceIn(0f, 1f)
            if (born < 1f) dy = -(1f - born) * (1f - born) * unit * 1.2f
            if (car.wiggleAt >= 0L) {
                val k = (now - car.wiggleAt) / WIGGLE_MS.toFloat()
                if (k < 1f) {
                    angle = sin(k * PI.toFloat() * 4f) * 6f * (1f - k)
                    dy -= abs(sin(k * PI.toFloat() * 2f)) * unit * 0.05f * (1f - k)
                }
            }
            c.save()
            c.translate(carX(i), railY - unit + dy)
            c.rotate(angle, unit / 2f, unit)
            c.scale(s, s)
            drawCar(c, car)
            c.restore()
        }

        var dy = 0f
        var sy = 1f
        if (engineWiggleAt >= 0L) {
            val k = (now - engineWiggleAt) / WIGGLE_MS.toFloat()
            if (k < 1f) {
                dy = -abs(sin(k * PI.toFloat() * 2f)) * unit * 0.06f * (1f - k)
                sy = 1f + 0.04f * cos(k * PI.toFloat() * 4f) * (1f - k)
            }
        }
        c.save()
        c.translate(engineX(), railY - unit + dy)
        c.scale(s, s)
        c.scale(1f, sy, 0f, 100f)
        drawEngine(c)
        c.restore()
    }

    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        box.set(l, t, r, b)
        fill.color = color
        c.drawRoundRect(box, rad, rad, fill)
        c.drawRoundRect(box, rad, rad, unitInk)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, unitInk)
    }

    private fun wheel(c: Canvas, x: Float, y: Float, r: Float) {
        circle(c, x, y, r, 0xFF607D8B.toInt())
        c.save()
        c.rotate(wheelDeg, x, y)
        c.drawLine(x - r * 0.75f, y, x + r * 0.75f, y, unitInk)
        c.drawLine(x, y - r * 0.75f, x, y + r * 0.75f, unitInk)
        c.restore()
        circle(c, x, y, r * 0.3f, 0xFFECEFF1.toInt())
    }

    /** A boxcar in a 100 x 100 box: a colored body with a window showing what is inside. */
    private fun drawCar(c: Canvas, car: Car) {
        val dark = Palette.mix(car.color, Palette.INK, 0.3f)
        // The coupling to whatever is in front of it.
        unitInk.strokeWidth = 6f
        c.drawLine(94f, 80f, 108f, 80f, unitInk)
        unitInk.strokeWidth = 3.5f
        roundRect(c, 4f, 18f, 96f, 80f, 8f, car.color)
        roundRect(c, 4f, 18f, 96f, 28f, 6f, dark)
        roundRect(c, 16f, 32f, 84f, 74f, 8f, Palette.PAPER)
        car.picture?.let {
            box.set(28f, 30f, 72f, 74f)
            c.drawBitmap(it, null, box, bitmapPaint)
        }
        // A sliver of shine along the top.
        fill.color = 0x55FFFFFF
        box.set(10f, 31f, 90f, 35f)
        c.drawRect(box, fill)
        wheel(c, 24f, 88f, 11f)
        wheel(c, 76f, 88f, 11f)
    }

    /** The engine in a 100 x 100 box, facing right, with a friendly face on its boiler. */
    private fun drawEngine(c: Canvas) {
        val color = engineColor
        val dark = Palette.mix(color, Palette.INK, 0.3f)
        // Chimney first, so the boiler sits in front of its foot.
        roundRect(c, 70f, 22f, 84f, 42f, 2f, dark)
        roundRect(c, 66f, 12f, 88f, 24f, 4f, dark)
        roundRect(c, 2f, 74f, 98f, 84f, 3f, 0xFF78909C.toInt())
        roundRect(c, 38f, 36f, 92f, 78f, 14f, color)
        roundRect(c, 6f, 22f, 42f, 78f, 6f, color)
        roundRect(c, 2f, 12f, 46f, 24f, 5f, dark)
        roundRect(c, 14f, 30f, 34f, 52f, 5f, 0xFFB3E5FC.toInt())
        circle(c, 52f, 37f, 8f, 0xFFFDD835.toInt())
        // Headlight and cow-catcher at the front.
        circle(c, 93f, 58f, 5f, 0xFFFFF59D.toInt())
        path.rewind()
        path.moveTo(86f, 78f)
        path.lineTo(99f, 95f)
        path.lineTo(86f, 95f)
        path.close()
        fill.color = 0xFF90A4AE.toInt()
        c.drawPath(path, fill)
        c.drawPath(path, unitInk)
        // The face.
        circle(c, 60f, 52f, 5.5f, Palette.PAPER)
        circle(c, 78f, 52f, 5.5f, Palette.PAPER)
        fill.color = Palette.INK
        c.drawCircle(61.5f, 52.5f, 2.6f, fill)
        c.drawCircle(79.5f, 52.5f, 2.6f, fill)
        box.set(62f, 56f, 76f, 70f)
        c.drawArc(box, 20f, 140f, false, unitInk)
        wheel(c, 24f, 86f, 14f)
        wheel(c, 66f, 88f, 12f)
    }

    private fun drawPuffs(c: Canvas, now: Long) {
        val it = puffs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            val t = (now - p.at) / PUFF_MS.toFloat()
            if (t >= 1f) {
                it.remove()
                continue
            }
            if (t < 0f) continue
            // Smoke rises, grows, drifts back and fades.
            val x = p.x - BASE_SPEED_DP * density * speedMul * 0.5f * (t * PUFF_MS / 1000f) - unit * 0.1f * t
            val y = p.y - unit * 0.7f * t
            val r = p.size * (0.6f + t * 0.9f)
            fill.color = (((1f - t) * 220).toInt() shl 24) or 0xF5F5F5
            c.drawCircle(x, y, r, fill)
        }
    }

    private fun drawBursts(c: Canvas, now: Long) {
        val it = bursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val t = (now - b.at) / BURST_MS.toFloat()
            if (t >= 1f) {
                it.remove()
                continue
            }
            fill.color = (b.color and 0xFFFFFF) or (((1f - t) * 255).toInt() shl 24)
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                val d = unit * (0.15f + t * 0.35f)
                c.drawCircle(b.x + d * cos(a), b.y + d * sin(a), unit * 0.06f * (1f - t), fill)
            }
        }
    }

    private companion object {
        const val MAX_CARS = 6
        /** How fast the scenery slides by, in dp a second. */
        const val BASE_SPEED_DP = 70f
        const val LEAVE_AFTER_MS = 2200L
        const val ARRIVE_MS = 1800L
        const val CAR_DROP_MS = 450L
        const val WIGGLE_MS = 500L
        const val PUFF_MS = 1600L
        const val BURST_MS = 500L
    }
}
