package com.daveendev.justdoodles

import android.content.SharedPreferences

/**
 * Just Doodles PLUS, the one-time upgrade on Google Play: whether it's on, and what it unlocks.
 * Every lock asks here, so the rules live in one place. [PlusStore] (one per build flavor)
 * switches it on or off; the Play build keeps the answer in the prefs, so PLUS works offline
 * and is right from the first frame.
 */
object Plus {
    /** Free pictures in each coloring group: the first ones by file number. */
    const val FREE_PAGES_PER_GROUP = 7

    /** The games and Learn topics that are free; the others need PLUS. */
    val FREE_GAMES = setOf(Game.PEEKABOO, Game.FIND_IT, Game.XYLOPHONE)
    val FREE_LESSONS = setOf(Lesson.COLORS, Lesson.SHAPES, Lesson.COUNT)

    /** The crayon and the eraser are always there; every other tool needs PLUS. */
    private val FREE_TOOLS = setOf(Tool.BRUSH, Tool.ERASER)

    var isOn = false
        private set

    /** Called when [isOn] changes, so what's on the screen can catch up. */
    var onChange: (() -> Unit)? = null

    private var prefs: SharedPreferences? = null

    fun load(prefs: SharedPreferences) {
        this.prefs = prefs
        isOn = prefs.getBoolean(KEY_PLUS, false)
    }

    /** Switches PLUS on or off. With [persist] false it isn't saved (the sideloaded build). */
    fun set(on: Boolean, persist: Boolean = true) {
        if (persist) prefs?.edit()?.putBoolean(KEY_PLUS, on)?.apply()
        if (on == isOn) return
        isOn = on
        onChange?.invoke()
    }

    /** Whether picture number [index] of its group can be colored. One the child already drew on always can. */
    fun pageOpen(index: Int, hasDrawing: Boolean) = isOn || hasDrawing || index < FREE_PAGES_PER_GROUP

    fun toolOpen(tool: Tool) = isOn || tool in FREE_TOOLS

    /** Saving a drawing to the phone, or sharing it. */
    fun canExport() = isOn

    fun gameOpen(game: Game) = isOn || game in FREE_GAMES

    fun lessonOpen(lesson: Lesson) = isOn || lesson in FREE_LESSONS

    private const val KEY_PLUS = "plus"
}

/** Where buying PLUS stands, as Google Play last said. */
enum class PlusState { NOT_OWNED, PENDING, OWNED, UNAVAILABLE }

/** What the last buy or restore came to, for the PLUS page to put into words. */
enum class PlusResult {
    /** Paid for, and PLUS is on. Also when a payment that was waiting goes through. */
    BOUGHT,
    /** Waiting for the payment (cash at a shop, say). PLUS turns on by itself once it clears. */
    PENDING,
    /** The grown-up closed Play's purchase sheet. Nothing to say. */
    CANCELED,
    /** Restore found PLUS on this Google account, and it's on again. */
    RESTORED,
    /** Restore found no PLUS on the Google account signed in to the Play Store. */
    NOT_FOUND,
    /** Couldn't reach Google Play: the phone is offline, or Play is busy. Worth trying again. */
    NO_CONNECTION,
    /** Play purchases don't work on this phone or account: no Play Store, signed out, or blocked. */
    NOT_AVAILABLE,
    /** Anything else Play said no to, like a declined card or a product Play doesn't know yet. */
    FAILED,
}
