package com.daveendev.justdoodles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Feed the animals: a hungry animal and a plate of three foods. Picking a food up says its name;
 * as it comes near, the animal's eyes follow it and its mouth opens wider. Dropped in the mouth,
 * it's eaten with three chomps and a "Yum!"; dropped anywhere else, it floats back to the plate.
 * An empty plate gets "All gone!" and a cheer, then the next hungry animal comes along.
 */
@SuppressLint("ViewConstructor")
class FeedView(context: Context, private val host: GameHost) : View(context) {

    private enum class State { HOME, HELD, RETURNING, EATING, GONE }

    private class Bite(val food: Food, val homeX: Float, val homeY: Float) {
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
    private val foodNames = resources.getStringArray(R.array.food_names)
    private val critterSounds = resources.getStringArray(R.array.critter_sounds)

    private var critter = Critter.FROG
    private var bites = emptyList<Bite>()
    private var critterX = 0f
    private var critterY = 0f
    private var critterSize = 0f
    private var foodSize = 0f
    private var plateX = 0f
    private var plateY = 0f
    private var plateW = 0f
    private var round = 0
    private var arrivedAt = 0L
    private var chompAt = -1L
    private var fullAt = -1L
    private var nextBlink = 0L

    private val sayHello = Runnable { host.say(critterSounds[critter.ordinal]) }
    private val sayAllGone = Runnable { host.say(context.getString(R.string.say_all_gone)) }
    private val cheerLater = Runnable { host.cheer() }
    private val nextCritter = Runnable { newRound() }
    private val chomps = List(3) { i -> Runnable { host.sounds.pop(1.1f - i * 0.18f) } }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Palette.INK
    }

    init {
        contentDescription = context.getString(R.string.game_feed)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        critter = Critter.entries[round % Critter.entries.size]
        critterSize = min(height * 0.88f, width * 0.5f)
        critterX = width * 0.3f
        critterY = height * 0.52f
        foodSize = min(height * 0.3f, width * 0.13f)
        plateX = width * 0.76f
        plateY = height * 0.62f
        plateW = min(width * 0.4f, foodSize * 3.9f)
        val foods = Food.entries.shuffled(random).take(FOODS)
        bites = foods.mapIndexed { i, f ->
            Bite(f, plateX + (i - 1) * foodSize * 1.15f, plateY - foodSize * 0.12f + (if (i == 1) -foodSize * 0.1f else 0f))
        }
        chompAt = -1L
        fullAt = -1L
        arrivedAt = SystemClock.uptimeMillis()
        nextBlink = arrivedAt + 2000L
        host.sounds.whoosh()
        postDelayed(sayHello, ARRIVE_MS)
        invalidate()
    }

    private fun mouthX() = critterX + (Critters.MOUTH_X / 100f - 0.5f) * critterSize
    private fun mouthY() = critterY + (Critters.mouthY(critter) / 100f - 0.5f) * critterSize

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (now - arrivedAt < ARRIVE_MS || fullAt >= 0) return true
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val bite = bites.filter { it.state == State.HOME || it.state == State.RETURNING }
                    .minByOrNull { hypot(it.x - x, it.y - y) }
                    ?.takeIf { abs(it.x - x) < foodSize * 0.75f && abs(it.y - y) < foodSize * 0.75f }
                if (bite != null) {
                    bite.state = State.HELD
                    bite.pointer = e.getPointerId(i)
                    bite.grabDx = bite.x - x
                    bite.grabDy = bite.y - y
                    host.sounds.pop(1.2f)
                    host.say(foodNames[bite.food.ordinal])
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i)
                val bite = bites.firstOrNull { it.state == State.HELD && it.pointer == id } ?: continue
                bite.x = e.getX(i) + bite.grabDx
                bite.y = e.getY(i) + bite.grabDy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                bites.firstOrNull { it.state == State.HELD && it.pointer == id }?.let { drop(it, now) }
            }
            MotionEvent.ACTION_CANCEL -> bites.filter { it.state == State.HELD }.forEach { drop(it, now) }
        }
        invalidate()
        return true
    }

    /** Into the mouth if it's near enough (toddler-near, not grown-up-near); otherwise back to the plate. */
    private fun drop(bite: Bite, now: Long) {
        bite.pointer = -1
        bite.fromX = bite.x
        bite.fromY = bite.y
        bite.movedAt = now
        if (hypot(bite.x - mouthX(), bite.y - mouthY()) < critterSize * MOUTH_REACH) {
            bite.state = State.EATING
            chompAt = now
            chomps.forEachIndexed { i, r -> postDelayed(r, i * CHOMP_MS / 3) }
            host.say(context.getString(R.string.say_yum))
            if (bites.all { it.state == State.EATING || it.state == State.GONE }) {
                fullAt = now
                round++
                postDelayed(sayAllGone, ALL_GONE_DELAY_MS)
                postDelayed(cheerLater, CHEER_DELAY_MS)
                postDelayed(nextCritter, NEXT_ROUND_MS)
            }
        } else {
            bite.state = State.RETURNING
            host.sounds.boing()
        }
    }

    override fun onDetachedFromWindow() {
        for (r in chomps) removeCallbacks(r)
        removeCallbacks(sayHello)
        removeCallbacks(sayAllGone)
        removeCallbacks(cheerLater)
        removeCallbacks(nextCritter)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()

        for (b in bites) {
            val t = ((now - b.movedAt).toFloat() / MOVE_MS).coerceIn(0f, 1f)
            val ease = 1f - (1f - t) * (1f - t)
            when (b.state) {
                State.RETURNING -> {
                    b.x = b.fromX + (b.homeX - b.fromX) * ease
                    b.y = b.fromY + (b.homeY - b.fromY) * ease
                    if (t >= 1f) b.state = State.HOME
                }
                State.EATING -> {
                    b.x = b.fromX + (mouthX() - b.fromX) * ease
                    b.y = b.fromY + (mouthY() - b.fromY) * ease
                    if (t >= 1f) b.state = State.GONE
                }
                else -> Unit
            }
        }

        // The plate, with whatever's left on it.
        fill.color = 0xFFECEFF1.toInt()
        val plateH = plateW * 0.32f
        canvas.drawOval(plateX - plateW / 2f, plateY - plateH / 2f, plateX + plateW / 2f, plateY + plateH / 2f, fill)
        canvas.drawOval(plateX - plateW / 2f, plateY - plateH / 2f, plateX + plateW / 2f, plateY + plateH / 2f, ink)
        fill.color = 0xFFFFFFFF.toInt()
        val inner = 0.72f
        canvas.drawOval(plateX - plateW * inner / 2f, plateY - plateH * inner / 2f, plateX + plateW * inner / 2f, plateY + plateH * inner / 2f, fill)
        for (b in bites) if (b.state == State.HOME || b.state == State.RETURNING) drawBite(canvas, b, 1f)

        // The animal slides in from the left, then waits with its mouth ready.
        val arrive = ((now - arrivedAt).toFloat() / ARRIVE_MS).coerceIn(0f, 1f)
        val ease = 1f - (1f - arrive) * (1f - arrive) * (1f - arrive)
        val held = bites.firstOrNull { it.state == State.HELD }
        var open = 0f
        if (held != null) {
            val d = hypot(held.x - mouthX(), held.y - mouthY())
            open = (1f - (d - critterSize * MOUTH_REACH) / (critterSize * 0.9f)).coerceIn(0.2f, 1f)
        }
        if (chompAt >= 0 && now - chompAt < CHOMP_MS) {
            val k = (now - chompAt).toFloat() / CHOMP_MS
            open = abs(sin(k * PI.toFloat() * 3f)) * 0.8f
        }
        // The eyes follow the food in hand, or glance at the plate.
        val lookAtX = held?.x ?: plateX
        val lookAtY = held?.y ?: plateY
        val lx = lookAtX - critterX
        val ly = lookAtY - (critterY - critterSize * 0.15f)
        val len = hypot(lx, ly).coerceAtLeast(1f)
        if (now > nextBlink + BLINK_MS) nextBlink = now + 2500L + random.nextLong(2500L)
        val blinking = now in nextBlink..nextBlink + BLINK_MS
        val blush = if (chompAt >= 0) (1f - (now - chompAt).toFloat() / BLUSH_MS).coerceIn(0f, 1f) else 0f
        var bounce = 1f
        if (fullAt >= 0 && now - fullAt in CHOMP_MS..CHOMP_MS + BOUNCE_MS) {
            val k = (now - fullAt - CHOMP_MS).toFloat() / BOUNCE_MS
            bounce = 1f + 0.08f * sin(k * PI.toFloat() * 4f) * (1f - k)
        }

        canvas.save()
        canvas.translate(critterX - critterSize / 2f - (1f - ease) * width * 0.7f, critterY - critterSize / 2f)
        canvas.scale(bounce, bounce, critterSize / 2f, critterSize)
        canvas.scale(critterSize / 100f, critterSize / 100f)
        Critters.draw(canvas, critter, open, lx / len, ly / len, blinking, blush)
        canvas.restore()

        for (b in bites) {
            when (b.state) {
                State.EATING -> {
                    val t = ((now - b.movedAt).toFloat() / MOVE_MS).coerceIn(0f, 1f)
                    drawBite(canvas, b, 1f - t)
                }
                State.HELD -> drawBite(canvas, b, 1.12f)
                else -> Unit
            }
        }

        // Keep animating: the animal blinks and watches, even while nothing is happening.
        postInvalidateOnAnimation()
    }

    private fun drawBite(canvas: Canvas, b: Bite, scale: Float) {
        if (scale <= 0f) return
        val s = foodSize * scale
        canvas.save()
        canvas.translate(b.x - s / 2f, b.y - s / 2f)
        canvas.scale(s / 100f, s / 100f)
        Critters.drawFood(canvas, b.food)
        canvas.restore()
    }

    private companion object {
        const val FOODS = 3
        /** How close to the mouth counts as in it, as a share of the animal's size. */
        const val MOUTH_REACH = 0.22f
        const val ARRIVE_MS = 600L
        const val MOVE_MS = 300L
        const val CHOMP_MS = 700L
        const val BLUSH_MS = 1500L
        const val BOUNCE_MS = 900L
        const val BLINK_MS = 140L
        const val ALL_GONE_DELAY_MS = 1000L
        const val CHEER_DELAY_MS = 2100L
        const val NEXT_ROUND_MS = 3800L
    }
}
