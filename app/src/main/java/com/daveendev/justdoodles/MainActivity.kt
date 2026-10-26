package com.daveendev.justdoodles

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {

    /**
     * What fills the screen. The easel (tools, paper and colors) is always there underneath;
     * the others are full-screen pages laid over it. New screens go at the end: the screen is
     * saved by its position in this list.
     */
    private enum class Screen { MENU, PAPERS, CATEGORIES, PAGES, EASEL, GAMES, GAME, LESSONS, LESSON }

    private lateinit var prefs: SharedPreferences
    private lateinit var store: PageStore
    private lateinit var categories: List<Category>
    private lateinit var pages: List<ColoringPage>
    private lateinit var sounds: Sounds
    private lateinit var voice: Voice
    private lateinit var colorNames: Array<String>
    private lateinit var patternNames: Array<String>
    private lateinit var stickerNames: Array<String>

    private lateinit var root: FrameLayout
    private lateinit var mainRow: View
    private lateinit var babyLayer: View
    private lateinit var drawing: DrawingView
    private lateinit var sizeButton: ToolButton
    private lateinit var undoButton: ToolButton
    private val toolButtons = LinkedHashMap<Tool, ToolButton>()
    private val swatches = ArrayList<ColorSwatch>()
    private var screenView: View? = null
    private var tray: View? = null
    private var parentPanel: View? = null
    private var sleepView: View? = null
    /** The "ask a grown-up" card, while it's up (see [showLocked]). */
    private var lockedView: View? = null
    /** When "Ask a grown-up" was last said, so it isn't said again and again. */
    private var lockedSaidAt = 0L

    private var screen = Screen.EASEL
    /** The category whose pictures the [Screen.PAGES] screen shows. */
    private var category: Category? = null
    /** The game the [Screen.GAME] screen plays. */
    private var game: Game? = null
    /** The topic the [Screen.LESSON] screen teaches. */
    private var lesson: Lesson? = null
    /** Extra pictures for the Learn topics (`assets/learn`), loaded the first time they're needed. */
    private val learnPages by lazy { ColoringPage.loadFolder(assets, "learn") }

    private lateinit var papers: MutableList<Paper>
    private var mode = Mode.FREE
    /** The sheet of paper on the easel in free drawing, and the picture on it in coloring. */
    private var freePageId = ColoringPage.BLANK
    private var colorPageId = ""
    /** Each mode remembers its own tool: stickers and patterns are only for blank paper. */
    private var freeTool = Tool.BRUSH
    private var colorTool = Tool.BRUSH
    private var colorIndex = 0
    private var babyMode = false

    /** The page on the easel right now. */
    private var pageId: String
        get() = if (mode == Mode.FREE) freePageId else colorPageId
        set(value) {
            if (mode == Mode.FREE) freePageId = value else colorPageId = value
        }

    /** When the bedtime timer runs out (milliseconds since 1970), or 0 when it's off. */
    private var bedtimeAt = 0L
    /** The bedtime timer's length in minutes, so the settings can show which one was picked. */
    private var bedtimeMinutes = 0
    private val bedtimeCheck = Runnable { checkBedtime() }

    /** The drawing waiting for the phone's "save as" screen to say where it goes. */
    private var exportId: String? = null

    /** Buys and restores PLUS through Google Play (the Play build), or just switches it on (the sideloaded one). */
    private val plusStore = PlusStore()
    private val plusChanged = { onPlusChanged() }

    private val isTablet get() = resources.configuration.smallestScreenWidthDp >= 600
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = dp(v).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("state", MODE_PRIVATE)
        // Before anything is built, so the locks are right from the first frame.
        Plus.load(prefs)
        plusStore.start(this)
        // News from Google Play (a purchase, a restore, a price) redraws the PLUS page if it's open.
        plusStore.onStateChange = { (parentPanel as? ParentPanel)?.plusChanged() }
        store = PageStore(this)
        categories = ColoringPage.loadCategories(assets)
        pages = categories.flatMap { it.pages }
        colorNames = resources.getStringArray(R.array.color_names)
        patternNames = resources.getStringArray(R.array.pattern_names)
        stickerNames = resources.getStringArray(R.array.sticker_names)
        sounds = Sounds(this).apply { enabled = prefs.getBoolean(KEY_SOUNDS, true) }
        voice = Voice(this).apply {
            enabled = prefs.getBoolean(KEY_VOICE, true)
            chosen = prefs.getString(KEY_VOICE_NAME, null)
        }
        babyMode = prefs.getBoolean(KEY_BABY, false)
        papers = Paper.decode(prefs.getString(KEY_PAPERS, null))
        bedtimeAt = prefs.getLong(KEY_BEDTIME, 0L)
        bedtimeMinutes = prefs.getInt(KEY_BEDTIME_MINUTES, 0)
        exportId = savedInstanceState?.getString(KEY_EXPORT)

        // Before there was a start menu, one page was saved for both kinds of drawing.
        val oldPage = prefs.getString(KEY_PAGE, null)
        freePageId = (prefs.getString(KEY_FREE_PAGE, null) ?: oldPage)?.takeIf { paperFor(it) != null }
            ?: papers.firstOrNull()?.id ?: addPaper(dark = false).id
        colorPageId = (prefs.getString(KEY_COLOR_PAGE, null) ?: oldPage)?.takeIf { pageFor(it) != null && pageOpen(it) }
            ?: pages.firstOrNull()?.id ?: ""
        mode = Mode.entries.getOrNull(prefs.getInt(KEY_MODE, 0)) ?: Mode.FREE
        if (colorPageId.isEmpty()) mode = Mode.FREE
        freeTool = toolFor(Mode.FREE, prefs.getInt(KEY_FREE_TOOL, prefs.getInt(KEY_TOOL, 0)))
        colorTool = toolFor(Mode.COLOR, prefs.getInt(KEY_TOOL, 0))

        drawing = DrawingView(this)
        colorIndex = prefs.getInt(KEY_COLOR, 0).coerceIn(Palette.colors.indices)
        drawing.color = Palette.colors[colorIndex]
        drawing.tool = if (mode == Mode.FREE) freeTool else colorTool
        drawing.magic = Magic.entries.getOrNull(prefs.getInt(KEY_MAGIC, 0)) ?: Magic.RAINBOW
        drawing.stamp = StampKind.entries.getOrNull(prefs.getInt(KEY_STAMP, 0)) ?: StampKind.STAR
        drawing.pattern = Pattern.entries.getOrNull(prefs.getInt(KEY_PATTERN, 0)) ?: Pattern.HEARTS
        drawing.sticker = Sticker.entries.getOrNull(prefs.getInt(KEY_STICKER, 0)) ?: Sticker.SUN
        drawing.sizeIndex = prefs.getInt(KEY_SIZE, 1).coerceIn(Palette.brushSizesDp.indices)
        drawing.sounds = sounds
        drawing.onHistoryChanged = { updateUndo() }
        drawing.onPageComplete = { celebrate() }
        drawing.onPartTapped = { voice.say(it) }

        root = FrameLayout(this)
        mainRow = buildMainRow()
        babyLayer = buildBabyLayer()
        root.addView(mainRow)
        root.addView(babyLayer)
        setContentView(root)
        avoidDisplayCutout()
        enterImmersiveMode()
        handleBackButton()

        showPage(pageId)
        showToolsFor(mode)
        refreshButtons()
        applyMode()
        // A fresh start opens on the start menu. If Android rebuilt the screen (rare, since
        // turning the phone doesn't), carry on where things were.
        val saved = savedInstanceState?.let { Screen.entries.getOrNull(it.getInt(KEY_SCREEN, -1)) }
        category = savedInstanceState?.getString(KEY_CATEGORY)?.let { id -> categories.firstOrNull { it.id == id } }
        game = savedInstanceState?.getString(KEY_GAME)?.let { name -> Game.entries.firstOrNull { it.name == name } }
        lesson = savedInstanceState?.getString(KEY_LESSON)?.let { name -> Lesson.entries.firstOrNull { it.name == name } }
        if (!babyMode) showScreen(saved ?: Screen.MENU)
        // From here on, a purchase or a refund updates what's on the screen.
        Plus.onChange = plusChanged

        // Only on a fresh start, not every time the app comes back from the background,
        // and not when it's bedtime: then the sleepy moon comes straight up instead.
        if (savedInstanceState == null && !(bedtimeAt != 0L && System.currentTimeMillis() >= bedtimeAt)) {
            val splash = SplashView(this)
            // A rising xylophone run as the title letters land.
            splash.onLetterLanded = { k -> sounds.note(k * 11 / 12, 0.5f) }
            root.addView(splash, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    override fun onResume() {
        super.onResume()
        checkBedtime()
        // Catches a payment that went through, a purchase on another phone, or a refund.
        plusStore.refresh()
    }

    override fun onPause() {
        super.onPause()
        root.removeCallbacks(bedtimeCheck)
        sounds.stopScribble()
        voice.stop()
        saveCurrentPage()
        rememberTool()
        prefs.edit()
            .remove(KEY_PAGE)
            .putString(KEY_FREE_PAGE, freePageId)
            .putString(KEY_COLOR_PAGE, colorPageId)
            .putInt(KEY_MODE, mode.ordinal)
            .putInt(KEY_COLOR, colorIndex)
            .putInt(KEY_FREE_TOOL, freeTool.ordinal)
            .putInt(KEY_TOOL, colorTool.ordinal)
            .putInt(KEY_MAGIC, drawing.magic.ordinal)
            .putInt(KEY_STAMP, drawing.stamp.ordinal)
            .putInt(KEY_PATTERN, drawing.pattern.ordinal)
            .putInt(KEY_STICKER, drawing.sticker.ordinal)
            .putInt(KEY_SIZE, drawing.sizeIndex)
            .putBoolean(KEY_SOUNDS, sounds.enabled)
            .putBoolean(KEY_VOICE, voice.enabled)
            .putString(KEY_VOICE_NAME, voice.chosen) // Null (the app picks) removes it.
            .putBoolean(KEY_BABY, babyMode)
            .apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPORT, exportId)
        outState.putInt(KEY_SCREEN, screen.ordinal)
        outState.putString(KEY_CATEGORY, category?.id)
        outState.putString(KEY_GAME, game?.name)
        outState.putString(KEY_LESSON, lesson?.name)
    }

    override fun onDestroy() {
        sounds.release()
        voice.shutdown()
        plusStore.stop()
        if (Plus.onChange === plusChanged) Plus.onChange = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    // ---- Layout -----------------------------------------------------------------------------

    private fun buildMainRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(4f), dpi(6f), 0, dpi(6f))
        }
        fun addTool(button: ToolButton) = tools.addView(button, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        addTool(ToolButton(this, Icon.BACK).apply { setOnClickListener { leaveEasel() } })
        for ((tool, icon) in listOf(
            Tool.BRUSH to Icon.BRUSH,
            Tool.MAGIC to Icon.MAGIC,
            Tool.STAMP to Icon.STAMP,
            Tool.PATTERN to Icon.PATTERN,
            Tool.STICKER to Icon.STICKER,
            Tool.ERASER to Icon.ERASER,
        )) {
            val button = ToolButton(this, icon).apply { setOnClickListener { selectTool(tool) } }
            toolButtons[tool] = button
            addTool(button)
        }
        sizeButton = ToolButton(this, Icon.SIZE).apply { setOnClickListener { cycleSize() } }
        addTool(sizeButton)
        row.addView(tools, LinearLayout.LayoutParams(toolColumnWidth(), MATCH_PARENT))

        val corner = dp(18f)
        drawing.elevation = dp(3f)
        drawing.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, corner)
            }
        }
        drawing.clipToOutline = true
        row.addView(drawing, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
            setMargins(dpi(6f), dpi(10f), dpi(6f), dpi(10f))
        })

        val colors = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpi(6f), dpi(4f), dpi(6f))
        }
        for (r in 0 until Palette.colors.size / 2) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..1) {
                val index = r * 2 + c
                val swatch = ColorSwatch(this, Palette.colors[index]).apply {
                    contentDescription = colorNames[index]
                    setOnClickListener { selectColor(index) }
                }
                swatches += swatch
                line.addView(swatch, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            }
            colors.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        // Undo and clean page sit under the colors, which leaves the tools room for big buttons.
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        undoButton = ToolButton(this, Icon.UNDO).apply { setOnClickListener { undo() } }
        actions.addView(undoButton, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        actions.addView(HoldButton(this, Icon.CLEAR) {
            drawing.clearPage()
            sounds.sparkle()
        }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        colors.addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        row.addView(colors, LinearLayout.LayoutParams(colorColumnWidth(), MATCH_PARENT))
        return row
    }

    /** Baby mode fills the whole screen; the only button is the grown-ups' gear in the corner. */
    private fun buildBabyLayer(): View {
        val layer = FrameLayout(this)
        layer.addView(BabyView(this).apply { sounds = this@MainActivity.sounds })
        val gear = HoldButton(this, Icon.GEAR, GEAR_HOLD_MS) { openParentPanel() }.apply { alpha = 0.6f }
        layer.addView(gear, FrameLayout.LayoutParams(dpi(56f), dpi(56f), Gravity.TOP or Gravity.END).apply {
            setMargins(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
        })
        return layer
    }

    private fun toolColumnWidth() = dpi(if (isTablet) 92f else 64f)

    private fun colorColumnWidth() = dpi(if (isTablet) 150f else 104f)

    private fun applyMode() {
        if (babyMode) {
            saveCurrentPage()
            closeTray()
            closeScreen()
            sounds.stopScribble()
        }
        mainRow.visibility = if (babyMode) View.GONE else View.VISIBLE
        babyLayer.visibility = if (babyMode) View.VISIBLE else View.GONE
    }

    // ---- Free drawing or coloring -----------------------------------------------------------

    /** The saved tool [ordinal], or the crayon if that tool isn't used in [mode] or needs PLUS. */
    private fun toolFor(mode: Mode, ordinal: Int): Tool {
        val tool = Tool.entries.getOrNull(ordinal) ?: Tool.BRUSH
        return if (tool in toolsOf(mode) && Plus.toolOpen(tool)) tool else Tool.BRUSH
    }

    private fun toolsOf(mode: Mode) = when (mode) {
        Mode.FREE -> Tool.entries.toSet()
        Mode.COLOR -> setOf(Tool.BRUSH, Tool.MAGIC, Tool.STAMP, Tool.ERASER)
    }

    private fun rememberTool() {
        if (mode == Mode.FREE) freeTool = drawing.tool else colorTool = drawing.tool
    }

    /** Puts the right tools out for [newMode]. The caller puts a page on the easel. */
    private fun switchMode(newMode: Mode) {
        if (newMode == mode) return
        saveCurrentPage()
        rememberTool()
        mode = newMode
        drawing.tool = if (mode == Mode.FREE) freeTool else colorTool
        showToolsFor(mode)
        refreshButtons()
    }

    private fun showToolsFor(mode: Mode) {
        val tools = toolsOf(mode)
        for ((tool, button) in toolButtons) button.visibility = if (tool in tools) View.VISIBLE else View.GONE
    }

    /** The Back button on the easel: back to the sheets of paper, or to this category's pictures. */
    private fun leaveEasel() {
        saveCurrentPage()
        if (mode == Mode.FREE) {
            showScreen(Screen.PAPERS)
        } else {
            category = categoryOf(colorPageId) ?: categories.firstOrNull()
            showScreen(Screen.PAGES)
        }
    }

    // ---- Tools and colors -------------------------------------------------------------------

    private fun selectTool(tool: Tool) {
        // A tool that needs PLUS: the "ask a grown-up" card, and the crayon in hand stays.
        if (!Plus.toolOpen(tool)) return showLocked(tool)
        if (tool == Tool.MAGIC && drawing.tool == Tool.MAGIC) {
            // Tapping the magic crayon again switches to the next kind of magic.
            drawing.magic = Magic.entries[(drawing.magic.ordinal + 1) % Magic.entries.size]
            sounds.sparkle()
        } else if (tool == Tool.STAMP && drawing.tool == Tool.STAMP) {
            // And tapping the stamps again picks the next stamp.
            drawing.stamp = StampKind.entries[(drawing.stamp.ordinal + 1) % StampKind.entries.size]
            sounds.boing()
        } else if ((tool == Tool.PATTERN || tool == Tool.STICKER) && drawing.tool == tool) {
            // Patterns and stickers have too many to step through, so a second tap lays them all out.
            openTray(tool)
            return
        } else {
            drawing.tool = tool
            if (tool == Tool.MAGIC) sounds.sparkle() else sounds.pop()
        }
        voice.say(nameOf(tool))
        refreshButtons()
    }

    private fun nameOf(tool: Tool): String = when (tool) {
        Tool.BRUSH -> getString(R.string.say_crayon)
        // The stamp says what it is: "Duck!"
        Tool.STAMP -> resources.getStringArray(R.array.stamp_names).getOrElse(drawing.stamp.ordinal) { "" }
        Tool.PATTERN -> patternNames.getOrElse(drawing.pattern.ordinal) { "" }
        Tool.STICKER -> stickerNames.getOrElse(drawing.sticker.ordinal) { "" }
        Tool.ERASER -> getString(R.string.say_eraser)
        Tool.MAGIC -> getString(when (drawing.magic) {
            Magic.RAINBOW -> R.string.say_rainbow
            Magic.GLITTER -> R.string.say_glitter
            Magic.GLOW -> R.string.say_glow
            Magic.MIRROR -> R.string.say_mirror
        })
    }

    private fun selectColor(index: Int) {
        colorIndex = index
        drawing.color = Palette.colors[index]
        // Tapping a color means "paint with this", so leave the rainbow crayon and the eraser.
        if (drawing.tool == Tool.ERASER || (drawing.tool == Tool.MAGIC && drawing.magic == Magic.RAINBOW)) {
            drawing.tool = Tool.BRUSH
        }
        sounds.note(index)
        voice.say(colorNames[index])
        refreshButtons()
    }

    private fun cycleSize() {
        drawing.sizeIndex = (drawing.sizeIndex + 1) % Palette.brushSizesDp.size
        sounds.pop(floatArrayOf(1.5f, 1f, 0.7f)[drawing.sizeIndex])
        voice.say(getString(intArrayOf(R.string.say_small, R.string.say_medium, R.string.say_big)[drawing.sizeIndex]))
        refreshButtons()
    }

    private fun undo() {
        if (!drawing.canUndo) return
        drawing.undo()
        sounds.whoosh()
    }

    private fun refreshButtons() {
        for ((tool, button) in toolButtons) {
            button.chosen = tool == drawing.tool
            button.locked = !Plus.toolOpen(tool)
            button.tint = drawing.color
            button.magic = drawing.magic
            button.stamp = drawing.stamp
            button.pattern = drawing.pattern
            button.sticker = drawing.sticker
        }
        sizeButton.tint = drawing.color
        sizeButton.sizeIndex = drawing.sizeIndex
        val usesColor = when (drawing.tool) {
            Tool.BRUSH, Tool.STAMP, Tool.PATTERN, Tool.STICKER -> true
            Tool.MAGIC -> drawing.magic != Magic.RAINBOW
            Tool.ERASER -> false
        }
        swatches.forEachIndexed { i, swatch -> swatch.chosen = usesColor && i == colorIndex }
        updateUndo()
    }

    private fun updateUndo() {
        if (::undoButton.isInitialized) undoButton.alpha = if (drawing.canUndo) 1f else 0.35f
    }

    /** Confetti, a happy jingle and a cheer. */
    private fun cheer() {
        sounds.jingle()
        val cheers = resources.getStringArray(R.array.cheers)
        voice.say(cheers.random())
        root.addView(CelebrationView(this), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** Most of a coloring page is colored in: a cheer, and the picture bounces. */
    private fun celebrate() {
        cheer()
        drawing.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150).withEndAction {
            drawing.animate().scaleX(1f).scaleY(1f).setInterpolator(BounceInterpolator()).setDuration(600).start()
        }.start()
    }

    // ---- Pattern and sticker trays ----------------------------------------------------------

    /** Lays out every pattern or sticker next to its button, to pick one with a tap. */
    private fun openTray(tool: Tool) {
        closeTray()
        val button = toolButtons[tool] ?: return
        val isPattern = tool == Tool.PATTERN
        val count = if (isPattern) Pattern.entries.size else Sticker.entries.size
        val columns = 4
        val rows = (count + columns - 1) / columns
        val pad = dpi(10f)
        val cell = min(dpi(if (isTablet) 96f else 76f), (root.height - root.paddingTop - root.paddingBottom - dpi(24f) - 2 * pad) / rows)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xFFFFF4E3.toInt())
                cornerRadius = dp(24f)
            }
            elevation = dp(8f)
            isClickable = true // Taps between the choices don't close the tray.
        }
        for (r in 0 until rows) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r * columns until min(count, (r + 1) * columns)) {
                val choice = ToolButton(this, if (isPattern) Icon.PATTERN else Icon.STICKER).apply {
                    tint = drawing.color
                    if (isPattern) {
                        pattern = Pattern.entries[k]
                        chosen = drawing.pattern == pattern
                        contentDescription = patternNames.getOrElse(k) { "" }
                    } else {
                        sticker = Sticker.entries[k]
                        chosen = drawing.sticker == sticker
                        contentDescription = stickerNames.getOrElse(k) { "" }
                    }
                    setOnClickListener { chooseFromTray(tool, k) }
                }
                line.addView(choice, LinearLayout.LayoutParams(cell, cell))
            }
            card.addView(line)
        }

        // Next to the button that opened it, as close to level with it as fits on the screen.
        val cardH = rows * cell + 2 * pad
        val here = IntArray(2)
        val rootAt = IntArray(2)
        button.getLocationInWindow(here)
        root.getLocationInWindow(rootAt)
        val buttonY = here[1] - rootAt[1] + button.height / 2
        val top = (buttonY - cardH / 2).coerceIn(root.paddingTop + dpi(8f), root.height - root.paddingBottom - cardH - dpi(8f))

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x33000000)
            setOnClickListener { closeTray() }
        }
        overlay.addView(card, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = root.paddingLeft + toolColumnWidth() + dpi(4f)
            topMargin = top
        })
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        card.pivotX = 0f
        card.pivotY = (buttonY - top).toFloat()
        card.scaleX = 0.7f
        card.scaleY = 0.7f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setInterpolator(OvershootInterpolator()).setDuration(220).start()
        tray = overlay
        sounds.pop(1.3f)
        voice.say(getString(if (isPattern) R.string.say_patterns else R.string.say_stickers))
    }

    private fun chooseFromTray(tool: Tool, index: Int) {
        if (tool == Tool.PATTERN) drawing.pattern = Pattern.entries[index] else drawing.sticker = Sticker.entries[index]
        drawing.tool = tool
        closeTray()
        if (tool == Tool.STICKER) sounds.boing() else sounds.pop()
        voice.say(nameOf(tool))
        refreshButtons()
    }

    private fun closeTray() {
        tray?.let { root.removeView(it) }
        tray = null
    }

    // ---- Pages ------------------------------------------------------------------------------

    private fun pageFor(id: String) = pages.firstOrNull { it.id == id }

    private fun paperFor(id: String) = papers.firstOrNull { it.id == id }

    private fun categoryOf(id: String) = categories.firstOrNull { c -> c.pages.any { it.id == id } }

    /** Whether coloring page [id] can be opened right now (see [Plus.pageOpen]). Sheets of paper always can. */
    private fun pageOpen(id: String): Boolean {
        val cat = categoryOf(id) ?: return true
        return Plus.pageOpen(cat.pages.indexOfFirst { it.id == id }, store.has(id))
    }

    /** Puts page [id] on the easel, just as it was left. */
    private fun showPage(id: String) {
        drawing.setPage(pageFor(id), store.load(id), paperFor(id)?.color ?: Palette.PAPER)
    }

    /** What a page is called out loud: "Fish", or "Black paper". */
    private fun pageName(id: String): String? {
        pageFor(id)?.let { return it.title }
        val dark = paperFor(id)?.dark ?: (id == NEW_DARK)
        return getString(if (dark) R.string.say_black_paper else R.string.say_white_paper)
    }

    /** A fresh sheet of paper, first in the list since it's the newest. */
    private fun addPaper(dark: Boolean): Paper {
        var id = Paper.newId()
        while (paperFor(id) != null) id += "x"
        val paper = Paper(id, dark)
        papers.add(0, paper)
        savePapers()
        return paper
    }

    private fun savePapers() = prefs.edit().putString(KEY_PAPERS, Paper.encode(papers)).apply()

    /** Forgets sheets that were never drawn on, except the one on the easel for free drawing. */
    private fun forgetEmptyPapers() {
        if (papers.removeAll { it.id != freePageId && !store.has(it.id) }) savePapers()
    }

    private fun saveCurrentPage() {
        if (!drawing.dirty) return
        drawing.snapshot()?.let { store.save(pageId, it) }
        drawing.dirty = false
    }

    /** A sheet of paper or a coloring page was picked: onto the easel with it. */
    private fun choosePage(id: String) {
        if (!pageOpen(id)) return // The picture grid shows the locked card instead; this is just in case.
        sounds.pop(1.2f)
        pageName(id)?.let { voice.say(it) }
        showScreen(Screen.EASEL)
        val newMode = if (pageFor(id) != null) Mode.COLOR else Mode.FREE
        // The same page as on the easel already: nothing to load.
        if (newMode == mode && id == pageId) return
        saveCurrentPage()
        switchMode(newMode)
        pageId = when (id) {
            NEW_WHITE -> addPaper(dark = false).id
            NEW_DARK -> addPaper(dark = true).id
            else -> id
        }
        showPage(pageId)
    }

    /**
     * The paper picker's cards: fresh white paper, fresh black paper, then the sheets already
     * drawn on (newest first). While the sheet on the easel is still empty it stands in for the
     * fresh sheet of its color, so tapping fresh paper again and again doesn't pile up empty sheets.
     */
    private fun paperIds(): List<String> {
        forgetEmptyPapers()
        val emptyNow = paperFor(freePageId)?.takeIf { !store.has(it.id) }
        val drawn = papers.filter { store.has(it.id) }
        val ids = ArrayList<String>()
        for (dark in booleanArrayOf(false, true)) {
            if (emptyNow != null && emptyNow.dark == dark) ids += emptyNow.id
            else if (drawn.size < Paper.MAX_DRAWN) ids += if (dark) NEW_DARK else NEW_WHITE
        }
        drawn.mapTo(ids) { it.id }
        return ids
    }

    // ---- Screens: the start menu, the paper picker, the categories and their pictures -------

    private fun showScreen(next: Screen) {
        if (next != Screen.EASEL && root.width == 0) {
            // Not laid out yet, so the cards can't be sized: come back once it is.
            root.post { if (screenView == null && !babyMode) showScreen(next) }
            return
        }
        closeTray()
        closeLocked()
        closeScreen()
        screen = next
        val view = when (next) {
            Screen.MENU -> buildMenu()
            Screen.PAPERS -> buildPapers()
            Screen.CATEGORIES -> buildCategories()
            Screen.PAGES -> buildPages(category ?: categories.firstOrNull() ?: return showScreen(Screen.CATEGORIES))
            Screen.GAMES -> buildGames()
            // A game or topic that needs PLUS (say, coming back after a refund) goes back to the list instead.
            Screen.GAME -> buildGame(game?.takeIf { Plus.gameOpen(it) } ?: return showScreen(Screen.GAMES))
            Screen.LESSONS -> buildLessons()
            Screen.LESSON -> buildLesson(lesson?.takeIf { Plus.lessonOpen(it) } ?: return showScreen(Screen.LESSONS))
            Screen.EASEL -> return
        }
        view.alpha = 0f
        root.addView(view, root.indexOfChild(babyLayer) + 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        view.animate().alpha(1f).setDuration(150).start()
        screenView = view
    }

    private fun closeScreen() {
        screenView?.let { root.removeView(it) }
        screenView = null
        screen = Screen.EASEL
    }

    /** Four big cards, two up and two down: draw anything, color in a picture, play a game, or learn something. */
    private fun buildMenu(): View {
        fun card(icon: Icon, label: Int, color: Int, say: Int, onTap: () -> Unit) =
            MenuCard(this, icon, getString(label), color).apply {
                setOnClickListener {
                    sounds.pop(1.1f)
                    voice.say(getString(say))
                    onTap()
                }
            }
        val cards = listOf(
            card(Icon.FREE_DRAW, R.string.menu_free_draw, 0xFFFF8A65.toInt(), R.string.say_free_draw) {
                showScreen(Screen.PAPERS)
            },
            card(Icon.COLORING, R.string.menu_color, 0xFF4FC3F7.toInt(), R.string.say_coloring) {
                showScreen(Screen.CATEGORIES)
            },
            card(Icon.PLAY, R.string.menu_play, 0xFF9CCC65.toInt(), R.string.say_play) {
                showScreen(Screen.GAMES)
            },
            card(Icon.LEARN, R.string.menu_learn, 0xFFFFCA28.toInt(), R.string.say_learn) {
                showScreen(Screen.LESSONS)
            },
        )
        val frame = screenFrame(twoRows(cards), onBack = null)
        // Where the other screens have Back: the way out of the app. Hold the X for two seconds,
        // so a toddler's tap can't close it. While the app is pinned, Android keeps it open.
        val exit = HoldButton(this, Icon.CLOSE, EXIT_HOLD_MS) { finish() }.apply {
            alpha = 0.7f
            contentDescription = getString(R.string.menu_exit)
        }
        val side = toolColumnWidth()
        frame.addView(exit, FrameLayout.LayoutParams(side, side, Gravity.TOP or Gravity.START))
        return frame
    }

    /** A big card for each game, in two rows. The ones that need PLUS show a padlock. */
    private fun buildGames(): View = bigCards(Game.entries.map { g ->
        val open = Plus.gameOpen(g)
        MenuCard(this, g.icon, getString(g.label), g.color).apply {
            locked = !open
            setOnClickListener {
                if (!open) return@setOnClickListener showLocked(g.icon, g.color)
                sounds.pop(1.1f)
                voice.say(getString(g.say))
                game = g
                showScreen(Screen.GAME)
            }
        }
    })

    /** A big card for each Learn topic, in two rows. The ones that need PLUS show a padlock. */
    private fun buildLessons(): View = bigCards(Lesson.entries.map { l ->
        val open = Plus.lessonOpen(l)
        MenuCard(this, l.icon, getString(l.label), l.color).apply {
            locked = !open
            setOnClickListener {
                if (!open) return@setOnClickListener showLocked(l.icon, l.color)
                sounds.pop(1.1f)
                voice.say(getString(l.say))
                lesson = l
                showScreen(Screen.LESSON)
            }
        }
    })

    /** [cards] filling the screen in two rows, with a Back button to the start menu. */
    private fun bigCards(cards: List<View>): View = screenFrame(twoRows(cards), onBack = { showScreen(Screen.MENU) })

    /** [cards] filling the space between the tool columns in two rows, the top row taking any odd one. */
    private fun twoRows(cards: List<View>): View {
        val side = toolColumnWidth()
        val columns = (cards.size + 1) / 2
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, dpi(16f), side, dpi(16f))
        }
        for (rowCards in cards.chunked(columns)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (card in rowCards) {
                row.addView(card, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply {
                    setMargins(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        return grid
    }

    /** [g] filling the screen, between the Back button and the gear. */
    private fun buildGame(g: Game): View {
        val side = toolColumnWidth()
        val box = FrameLayout(this).apply { setPadding(side, 0, side, 0) }
        box.addView(g.create(this, gameHost), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return screenFrame(box, onBack = { showScreen(Screen.GAMES) })
    }

    /** [l] filling the screen, between the Back button and the gear. */
    private fun buildLesson(l: Lesson): View {
        val side = toolColumnWidth()
        val box = FrameLayout(this).apply { setPadding(side, 0, side, 0) }
        box.addView(l.create(this, gameHost), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return screenFrame(box, onBack = { showScreen(Screen.LESSONS) })
    }

    private val gameHost = object : GameHost {
        override val sounds: Sounds get() = this@MainActivity.sounds
        override fun say(text: String) {
            if (sleepView == null) voice.say(text)
        }
        override fun cheer() {
            if (sleepView == null) this@MainActivity.cheer()
        }
        override val pictureGroups: List<Category> by lazy {
            categories.filter { it.id in Game.PICTURE_GROUPS }.ifEmpty { categories }
        }
        override fun picture(page: ColoringPage, width: Int, height: Int): Bitmap =
            if (store.has(page.id)) renderThumb(page.id, width, height) else AutoColor.render(page, width, height)
        override fun group(id: String) = categories.firstOrNull { it.id == id }
        override fun page(title: String) =
            (pages + learnPages).firstOrNull { it.title.equals(title, ignoreCase = true) }
    }

    /** Fresh white or black paper, and every sheet already drawn on. */
    private fun buildPapers(): View {
        val ids = paperIds()
        val (w, h) = cardSize(columns = 4, rows = 0, aspect = paperAspect())
        val cards = ids.map { id ->
            ThumbView(this, renderThumb(id, w, h), mode == Mode.FREE && id == freePageId).apply {
                contentDescription = pageName(id) ?: id
                setOnClickListener { choosePage(id) }
            }
        }
        return screenFrame(cardGrid(cards, 4, w, h), onBack = { showScreen(Screen.MENU) })
    }

    /** One card per category, each showing its first picture. */
    private fun buildCategories(): View {
        val labelShare = 0.2f
        val columns = columnsFor(categories.size, maxRows = 2)
        val (w, h) = cardSize(columns, rows = (categories.size + columns - 1) / columns, aspect = 1f / (1f / paperAspect() + labelShare))
        val current = categoryOf(colorPageId)
        val cards = categories.mapIndexed { i, cat ->
            ThumbView(this, renderCategoryCard(cat, i, w, h, (w * labelShare).roundToInt()), cat == current).apply {
                contentDescription = cat.title
                setOnClickListener {
                    sounds.pop(1.1f)
                    voice.say(cat.title)
                    category = cat
                    showScreen(Screen.PAGES)
                }
            }
        }
        return screenFrame(cardGrid(cards, columns, w, h), onBack = { showScreen(Screen.MENU) })
    }

    /** The pictures in [cat], each as it was last colored. The ones that need PLUS show a padlock. */
    private fun buildPages(cat: Category): View {
        val columns = columnsFor(cat.pages.size, maxRows = 3)
        val (w, h) = cardSize(columns, rows = (cat.pages.size + columns - 1) / columns, aspect = paperAspect())
        val cards = cat.pages.mapIndexed { i, page ->
            val open = Plus.pageOpen(i, store.has(page.id))
            ThumbView(this, renderThumb(page.id, w, h), mode == Mode.COLOR && page.id == colorPageId).apply {
                locked = !open
                val name = page.title ?: page.id
                contentDescription = if (open) name else getString(R.string.locked_name, name)
                setOnClickListener { if (open) choosePage(page.id) else showLocked(page) }
            }
        }
        return screenFrame(cardGrid(cards, columns, w, h), onBack = { showScreen(Screen.CATEGORIES) })
    }

    /** How many cards go across so that [n] of them fit in at most [maxRows] rows: three, or more for a long list. */
    private fun columnsFor(n: Int, maxRows: Int) = ((n + maxRows - 1) / maxRows).coerceIn(3, 6)

    /** A full-screen page: [content], a Back button at the top left and the grown-ups' gear at the top right. */
    private fun screenFrame(content: View, onBack: (() -> Unit)?): FrameLayout {
        val side = toolColumnWidth()
        val frame = FrameLayout(this).apply {
            setBackgroundColor(0xFFFFF4E3.toInt()) // Opaque, so the easel underneath doesn't ghost through.
            isClickable = true // Nothing reaches the easel underneath.
        }
        frame.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (onBack != null) {
            frame.addView(ToolButton(this, Icon.BACK).apply {
                setOnClickListener {
                    sounds.pop(0.9f)
                    onBack()
                }
            }, FrameLayout.LayoutParams(side, side, Gravity.TOP or Gravity.START))
        }
        // The way into the grown-up settings: hold the gray gear for two seconds.
        val gear = HoldButton(this, Icon.GEAR, GEAR_HOLD_MS) { openParentPanel() }.apply { alpha = 0.7f }
        frame.addView(gear, FrameLayout.LayoutParams(side, side, Gravity.TOP or Gravity.END))
        return frame
    }

    /** [cards] in rows of [columns], centered, scrolling if there are more than fit. */
    private fun cardGrid(cards: List<View>, columns: Int, w: Int, h: Int): View {
        val gap = dpi(8f)
        val side = toolColumnWidth()
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(side, dpi(12f), side, dpi(12f))
        }
        for (rowStart in cards.indices step columns) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (card in cards.subList(rowStart, min(rowStart + columns, cards.size))) {
                row.addView(card, LinearLayout.LayoutParams(w, h).apply { setMargins(gap, gap, gap, gap) })
            }
            grid.addView(row, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(grid)
        }
    }

    /**
     * Card width and height for a grid of [columns] across, [aspect] wide for each unit high.
     * With [rows] above 0, the cards shrink if needed so that many rows fit without scrolling.
     */
    private fun cardSize(columns: Int, rows: Int, aspect: Float): Pair<Int, Int> {
        val gap = dpi(8f)
        val side = toolColumnWidth()
        var w = (root.width - root.paddingLeft - root.paddingRight - 2 * side) / columns - 2 * gap
        var h = (w / aspect).roundToInt()
        if (rows > 0) {
            val maxH = (root.height - root.paddingTop - root.paddingBottom - dpi(24f)) / rows - 2 * gap
            if (h > maxH) {
                w = (w * maxH / h.toFloat()).roundToInt()
                h = maxH
            }
        }
        return w.coerceAtLeast(1) to h.coerceAtLeast(1)
    }

    /** The paper's width for each unit of height, measured or (before it's laid out) worked out. */
    private fun paperAspect(): Float {
        if (drawing.width > 0 && drawing.height > 0) return drawing.width / drawing.height.toFloat()
        val w = root.width - root.paddingLeft - root.paddingRight - toolColumnWidth() - colorColumnWidth() - dpi(12f)
        val h = root.height - root.paddingTop - root.paddingBottom - dpi(20f)
        return if (w > 0 && h > 0) w / h.toFloat() else 1.6f
    }

    /** A category's card: its first picture on a colored card, with its name underneath. */
    private fun renderCategoryCard(cat: Category, index: Int, w: Int, h: Int, labelH: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(CATEGORY_COLORS[index % CATEGORY_COLORS.size])
        val m = (w * 0.05f).roundToInt()
        val thumb = renderThumb(cat.pages.first().id, w - 2 * m, h - labelH - m)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.save()
        val r = dp(10f)
        val clip = Path().apply {
            addRoundRect(RectF(m.toFloat(), m.toFloat(), (w - m).toFloat(), (h - labelH).toFloat()), r, r, Path.Direction.CW)
        }
        c.clipPath(clip)
        c.drawBitmap(thumb, m.toFloat(), m.toFloat(), paint)
        c.restore()
        thumb.recycle()
        paint.apply {
            color = Palette.INK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textSize = min(labelH * 0.62f, w * 0.11f)
        }
        val baseline = h - labelH / 2f - (paint.descent() + paint.ascent()) / 2f
        c.drawText(cat.title, w / 2f, baseline, paint)
        return bmp
    }

    /** A preview card: the saved picture (if any) with the page outline on top. */
    private fun renderThumb(id: String, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dark = paperFor(id)?.dark ?: (id == NEW_DARK)
        c.drawColor(if (dark) Palette.DARK_PAPER else Palette.PAPER)
        val saved = when {
            id == NEW_WHITE || id == NEW_DARK -> null
            paperFor(id) != null && !store.has(id) -> null // An empty sheet.
            id == pageId -> drawing.thumbnail(w) ?: store.loadThumb(id, w)
            else -> store.loadThumb(id, w)
        }

        // Fit the picture without stretching, and lay the outline over exactly the same area.
        val srcW = saved?.width ?: drawing.width.takeIf { it > 0 } ?: w
        val srcH = saved?.height ?: drawing.height.takeIf { it > 0 } ?: h
        val scale = min(w / srcW.toFloat(), h / srcH.toFloat())
        val area = RectF(0f, 0f, srcW * scale, srcH * scale)
        area.offset((w - area.width()) / 2f, (h - area.height()) / 2f)
        if (saved != null) {
            c.drawBitmap(saved, null, area, Paint(Paint.FILTER_BITMAP_FLAG))
            saved.recycle()
        }

        c.save()
        c.translate(area.left, area.top)
        val page = pageFor(id)
        if (page != null) {
            page.drawOutline(c, area.width().roundToInt(), area.height().roundToInt())
        } else if (saved == null) {
            // An empty sheet shows a crayon, meaning "draw anything".
            val s = min(area.width(), area.height()) * 0.7f
            c.translate((area.width() - s) / 2f, (area.height() - s) / 2f)
            c.scale(s / 100f, s / 100f)
            Icons.draw(c, Icon.BRUSH, drawing.color, drawing.sizeIndex)
        }
        c.restore()
        return bmp
    }

    // ---- PLUS -------------------------------------------------------------------------------

    /** PLUS was bought, restored or refunded: put away tools that are locked now, and redraw the screen. */
    private fun onPlusChanged() {
        rememberTool()
        freeTool = toolFor(Mode.FREE, freeTool.ordinal)
        colorTool = toolFor(Mode.COLOR, colorTool.ordinal)
        drawing.tool = if (mode == Mode.FREE) freeTool else colorTool
        if (!Plus.isOn) closeTray()
        refreshButtons()
        if (screenView != null) showScreen(screen)
    }

    /** A child tapped [page], which needs PLUS. */
    private fun showLocked(page: ColoringPage) {
        val h = ((root.height - root.paddingTop - root.paddingBottom) * 0.48f).roundToInt()
        showLocked(renderThumb(page.id, (h * paperAspect()).roundToInt(), h))
    }

    /** A child tapped a tool that needs PLUS: its button picture, in the crayon color, on paper. */
    private fun showLocked(tool: Tool) {
        val icon = toolButtons[tool]?.icon ?: return
        val s = ((root.height - root.paddingTop - root.paddingBottom) * 0.48f).roundToInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFF4E3.toInt())
        val inset = s * 0.14f
        c.translate(inset, inset)
        c.scale((s - 2 * inset) / 100f, (s - 2 * inset) / 100f)
        Icons.draw(c, icon, drawing.color, drawing.sizeIndex, drawing.magic, drawing.stamp, drawing.pattern, drawing.sticker)
        showLocked(bmp)
    }

    /** A child tapped a game or Learn topic that needs PLUS: its picture on its own card color. */
    private fun showLocked(icon: Icon, color: Int) {
        val s = ((root.height - root.paddingTop - root.paddingBottom) * 0.48f).roundToInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        val inset = s * 0.12f
        c.translate(inset, inset)
        c.scale((s - 2 * inset) / 100f, (s - 2 * inset) / 100f)
        Icons.draw(c, icon, Palette.colors[0], 1)
        showLocked(bmp)
    }

    /**
     * Shows [subject] on the "ask a grown-up" card, with a chime. Only ever called right after a
     * child taps something locked, never by itself. The words come at most every 30 seconds.
     */
    private fun showLocked(subject: Bitmap) {
        closeLocked()
        sounds.chime()
        val now = SystemClock.uptimeMillis()
        if (lockedSaidAt == 0L || now - lockedSaidAt >= LOCKED_SAY_GAP_MS) {
            lockedSaidAt = now
            voice.say(getString(R.string.locked_say))
        }
        val view = LockedView(this, subject, GEAR_HOLD_MS, getString(R.string.locked_grownup),
            onGrownUp = {
                closeLocked()
                openParentPanel(toPlus = true)
            },
            onClose = { closeLocked() },
        )
        // On the window, like the grown-up settings, so the dimming covers the camera notch's strip too.
        (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        lockedView = view
    }

    private fun closeLocked() {
        lockedView?.let { (window.decorView as ViewGroup).removeView(it) }
        lockedView = null
    }

    // ---- Grown-up settings ------------------------------------------------------------------

    private val parentHost = object : ParentPanel.Host {
        override var soundsOn: Boolean
            get() = sounds.enabled
            set(value) { sounds.enabled = value }
        override var voiceOn: Boolean
            get() = voice.enabled
            set(value) {
                voice.enabled = value
                if (!value) voice.stop()
            }
        override fun voices() = voice.choices()
        override var voiceName: String?
            get() = voice.chosen
            set(value) { voice.chosen = value }
        override fun sayVoiceSample() = voice.preview(getString(R.string.say_voice_sample))
        override var babyOn: Boolean
            get() = babyMode
            set(value) {
                babyMode = value
                applyMode()
                // Out of baby mode: back to the start menu.
                if (!value) showScreen(Screen.MENU)
            }
        override val bedtimeChoice: Int get() = if (bedtimeAt == 0L) 0 else bedtimeMinutes
        override val bedtimeMinutesLeft: Int
            get() = if (bedtimeAt == 0L) 0
            else ceil((bedtimeAt - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(1)
        override fun setBedtime(minutes: Int) = this@MainActivity.setBedtime(minutes)
        override val pinned: Boolean get() = isPinned()
        override fun setPinned(pinned: Boolean) = this@MainActivity.setPinned(pinned)
        override fun drawingIds() = this@MainActivity.drawingIds()
        override fun drawingName(id: String) = pageName(id) ?: getString(R.string.drawings_untitled)
        override fun drawingThumb(id: String, width: Int, height: Int) = renderThumb(id, width, height)
        override fun saveDrawing(id: String) = saveToPhone(id)
        override fun shareDrawing(id: String) = this@MainActivity.shareDrawing(id)
        override fun deleteDrawing(id: String) = this@MainActivity.deleteDrawing(id)
        override val plusAvailable: Boolean get() = plusStore.available
        override val plusState: PlusState get() = plusStore.state
        override val plusPrice: String? get() = plusStore.price
        override val plusResult: PlusResult? get() = plusStore.lastResult
        override fun buyPlus() = plusStore.buy(this@MainActivity)
        override fun restorePlus(onDone: (Boolean) -> Unit) = plusStore.restore(onDone)
    }

    /** The grown-up settings. With [toPlus] it opens on the way to the PLUS page (the sum first). */
    private fun openParentPanel(toPlus: Boolean = false) {
        if (parentPanel != null) return
        saveCurrentPage() // So the drawings page shows the picture as it is right now.
        val panel = ParentPanel(this, parentHost, onClose = { closeParentPanel() }, startOnPlus = toPlus)
        // On the window itself rather than root, so the dimming also covers the strip root
        // leaves clear for a camera notch.
        (window.decorView as ViewGroup).addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        parentPanel = panel
    }

    private fun closeParentPanel() {
        parentPanel?.let { (window.decorView as ViewGroup).removeView(it) }
        parentPanel = null
        // Drawings may have been deleted in there: redraw the cards on the screen underneath.
        if (screenView != null) showScreen(screen)
    }

    // ---- Saving and sharing drawings --------------------------------------------------------

    /** Pages with a drawing saved, newest first. */
    private fun drawingIds(): List<String> {
        forgetEmptyPapers()
        return (papers.map { it.id } + pages.map { it.id })
            .filter { store.has(it) }
            .sortedByDescending { store.lastModified(it) }
    }

    /** The full-size picture for [id], with its outline on top: what gets saved or shared. */
    private fun renderPicture(id: String): Bitmap? {
        val saved = (if (id == pageId) drawing.snapshot() else null) ?: store.load(id) ?: return null
        val picture = if (saved.isMutable) saved else saved.copy(Bitmap.Config.ARGB_8888, true).also { saved.recycle() }
        pageFor(id)?.drawOutline(Canvas(picture), picture.width, picture.height)
        return picture
    }

    /** "Just Doodles - Fish - 2026-10-20 18.30.png" */
    private fun exportName(id: String): String {
        val what = (pageFor(id)?.title ?: getString(R.string.drawings_untitled)).replace(Regex("[^\\p{L}\\p{N} _-]"), "")
        val time = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())
        return "Just Doodles - $what - $time.png"
    }

    /** Asks the phone's own "save as" screen where to put the drawing. No permission needed. */
    private fun saveToPhone(id: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/png")
            .putExtra(Intent.EXTRA_TITLE, exportName(id))
        try {
            startActivityForResult(intent, REQUEST_SAVE)
            exportId = id
        } catch (e: ActivityNotFoundException) {
            toast(R.string.drawings_failed)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SAVE) return
        val id = exportId ?: return
        exportId = null
        val target = data?.data
        if (resultCode != RESULT_OK || target == null) return
        val picture = renderPicture(id) ?: return toast(R.string.drawings_failed)
        Thread({
            val saved = try {
                contentResolver.openOutputStream(target)?.use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
            } catch (e: Exception) {
                Log.w("JustDoodles", "Could not save drawing", e)
                false
            }
            runOnUiThread { toast(if (saved) R.string.drawings_saved else R.string.drawings_failed) }
        }, "export").start()
    }

    /** Opens the phone's share sheet with the drawing. Another app only ever sees that one picture. */
    private fun shareDrawing(id: String) {
        val picture = renderPicture(id) ?: return
        val name = exportName(id)
        Thread({
            val uri = try {
                SharedFiles.write(this, name, picture)
            } catch (e: Exception) {
                Log.w("JustDoodles", "Could not share drawing", e)
                null
            }
            runOnUiThread {
                if (uri == null) return@runOnUiThread toast(R.string.drawings_failed)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri(name, uri)
                try {
                    startActivity(Intent.createChooser(send, null))
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.drawings_failed)
                }
            }
        }, "share").start()
    }

    /**
     * Throws a drawing away. A sheet of paper goes altogether; a coloring page goes back to
     * clean. If it was on the easel, a fresh sheet (or the clean page) takes its place.
     */
    private fun deleteDrawing(id: String) {
        val onEasel = id == pageId
        store.delete(id)
        val paper = paperFor(id)
        if (paper != null) {
            papers.remove(paper)
            savePapers()
            if (id == freePageId) freePageId = addPaper(paper.dark).id
        }
        if (onEasel) showPage(pageId)
    }

    private fun toast(text: Int) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ---- Bedtime ----------------------------------------------------------------------------

    /** Starts the bedtime timer for [minutes] from now, or switches it off for 0. */
    private fun setBedtime(minutes: Int) {
        bedtimeMinutes = minutes
        bedtimeAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        prefs.edit().putLong(KEY_BEDTIME, bedtimeAt).putInt(KEY_BEDTIME_MINUTES, minutes).apply()
        checkBedtime()
    }

    /** Puts the app to sleep if it's bedtime, or checks again when it will be. */
    private fun checkBedtime() {
        root.removeCallbacks(bedtimeCheck)
        if (bedtimeAt == 0L || sleepView != null) return
        val left = bedtimeAt - System.currentTimeMillis()
        when {
            left > 0 -> root.postDelayed(bedtimeCheck, left)
            // A grown-up is busy in the settings: let them finish first.
            parentPanel != null -> root.postDelayed(bedtimeCheck, 30_000L)
            else -> goToSleep()
        }
    }

    private fun goToSleep() {
        closeParentPanel()
        closeTray()
        closeLocked()
        sounds.stopScribble()
        val sleep = SleepView(this) { wakeUp() }
        (window.decorView as ViewGroup).addView(sleep, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        sleepView = sleep
        sounds.lullaby()
        voice.say(getString(R.string.say_goodnight))
    }

    /** A grown-up held the gear: the timer is done with, and the settings open to set another. */
    private fun wakeUp() {
        sleepView?.let { (window.decorView as ViewGroup).removeView(it) }
        sleepView = null
        bedtimeAt = 0L
        prefs.edit().putLong(KEY_BEDTIME, 0L).apply()
        openParentPanel()
    }

    // ---- App pinning ------------------------------------------------------------------------

    private fun isPinned() =
        getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /** Android's own app pinning. Pinning asks the grown-up to confirm in a system dialog. */
    private fun setPinned(pin: Boolean) {
        try {
            if (pin) startLockTask() else stopLockTask()
        } catch (e: RuntimeException) {
            // Some phones refuse; pinning from the Recent apps screen still works there.
            Log.w("JustDoodles", "App pinning failed", e)
        }
    }

    // ---- Toddler-proofing -------------------------------------------------------------------

    /** Hides the status and navigation bars. A swipe from the edge shows them for a moment. */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 15+ is always edge-to-edge; older versions need asking, or the paper
            // would jump each time the bars slide in.
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 35) window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    /** Keeps the buttons and paper clear of any camera notch. */
    private fun avoidDisplayCutout() {
        if (Build.VERSION.SDK_INT < 28) return
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val cutout = insets.displayCutout
            view.setPadding(
                cutout?.safeInsetLeft ?: 0, cutout?.safeInsetTop ?: 0,
                cutout?.safeInsetRight ?: 0, cutout?.safeInsetBottom ?: 0,
            )
            insets
        }
    }

    /**
     * Little hands hit Back by accident all the time, so Back never takes a drawing off the
     * easel. It closes the grown-up settings or a tray, steps back through the picture pickers,
     * and only on the start menu, with nothing else open, leaves the app.
     */
    private fun handleBackButton() {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                closeTopOverlay()
            }
        }
    }

    private fun closeTopOverlay() {
        when {
            sleepView != null -> Unit // Back doesn't end bedtime either.
            parentPanel != null -> closeParentPanel()
            lockedView != null -> closeLocked()
            tray != null -> closeTray()
            screen == Screen.PAGES -> showScreen(Screen.CATEGORIES)
            screen == Screen.GAME -> showScreen(Screen.GAMES)
            screen == Screen.LESSON -> showScreen(Screen.LESSONS)
            screen == Screen.CATEGORIES || screen == Screen.PAPERS || screen == Screen.GAMES ||
                screen == Screen.LESSONS -> showScreen(Screen.MENU)
            // To the background, not closed, so it opens again where it was. While the app is
            // pinned, Android refuses and it stays put.
            screen == Screen.MENU -> moveTaskToBack(true)
        }
    }

    @Deprecated("Only used before Android 13; newer versions use the OnBackInvokedCallback above.")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        closeTopOverlay()
    }

    private companion object {
        /** From before the start menu: the one page on the easel. Read once, then replaced. */
        const val KEY_PAGE = "page"
        const val KEY_FREE_PAGE = "freePage"
        const val KEY_COLOR_PAGE = "colorPage"
        const val KEY_MODE = "mode"
        const val KEY_COLOR = "color"
        /** The coloring tool; it was the only saved tool before free drawing had its own. */
        const val KEY_TOOL = "tool"
        const val KEY_FREE_TOOL = "freeTool"
        const val KEY_MAGIC = "magic"
        const val KEY_STAMP = "stamp"
        const val KEY_PATTERN = "pattern"
        const val KEY_STICKER = "sticker"
        const val KEY_SIZE = "size"
        const val KEY_SOUNDS = "sounds"
        const val KEY_VOICE = "voice"
        const val KEY_VOICE_NAME = "voiceName"
        const val KEY_BABY = "baby"
        const val KEY_PAPERS = "papers"
        const val KEY_BEDTIME = "bedtime"
        const val KEY_BEDTIME_MINUTES = "bedtimeMinutes"
        const val KEY_EXPORT = "export"
        const val KEY_SCREEN = "screen"
        const val KEY_CATEGORY = "category"
        const val KEY_GAME = "game"
        const val KEY_LESSON = "lesson"

        /** Picker cards for a fresh sheet of white or black paper. They become real sheets when tapped. */
        const val NEW_WHITE = "new-white"
        const val NEW_DARK = "new-black"

        /** Card colors for the categories, in turn. */
        val CATEGORY_COLORS = intArrayOf(
            0xFFFFCC80.toInt(), 0xFF81D4FA.toInt(), 0xFFA5D6A7.toInt(),
            0xFFFFAB91.toInt(), 0xFFF48FB1.toInt(), 0xFFB39DDB.toInt(),
        )

        const val REQUEST_SAVE = 1
        const val GEAR_HOLD_MS = 2000L
        const val EXIT_HOLD_MS = 2000L
        /** "Ask a grown-up" is said at most this often; in between, the locked card only chimes. */
        const val LOCKED_SAY_GAP_MS = 30_000L
    }
}
