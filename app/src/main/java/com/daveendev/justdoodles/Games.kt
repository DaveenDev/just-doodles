package com.daveendev.justdoodles

import android.content.Context
import android.graphics.Bitmap
import android.view.View

/**
 * The games behind the start menu's Play card, in the order their cards appear. Each card shows
 * [icon] on [color], with [label] underneath for the grown-ups; [say] is said when it's picked.
 */
enum class Game(val icon: Icon, val label: Int, val say: Int, val color: Int) {
    PEEKABOO(Icon.PEEKABOO, R.string.game_peekaboo, R.string.say_game_peekaboo, 0xFF4FC3F7.toInt()),
    FIND_IT(Icon.FIND_IT, R.string.game_find_it, R.string.say_game_find_it, 0xFFFFB74D.toInt()),
    XYLOPHONE(Icon.XYLOPHONE, R.string.game_xylophone, R.string.say_game_xylophone, 0xFFBA68C8.toInt()),
    COLOR_SORT(Icon.COLOR_SORT, R.string.game_color_sort, R.string.say_game_color_sort, 0xFF81C784.toInt()),
    FEED(Icon.FEED, R.string.game_feed, R.string.say_game_feed, 0xFFF06292.toInt()),
    TRACE(Icon.TRACE, R.string.game_trace, R.string.say_game_trace, 0xFFFF8A65.toInt()),
    BUBBLES(Icon.GAME_BUBBLES, R.string.game_bubbles, R.string.say_game_bubbles, 0xFF4DD0E1.toInt()),
    MEMORY(Icon.GAME_MEMORY, R.string.game_memory, R.string.say_game_memory, 0xFF9FA8DA.toInt()),
    SHAPE_SORT(Icon.GAME_SHAPE_SORT, R.string.game_shape_sort, R.string.say_game_shape_sort, 0xFFFFD54F.toInt()),
    JIGSAW(Icon.GAME_JIGSAW, R.string.game_jigsaw, R.string.say_game_jigsaw, 0xFFAED581.toInt()),
    BALLOONS(Icon.GAME_BALLOONS, R.string.game_balloons, R.string.say_game_balloons, 0xFFE57373.toInt()),
    DRUMS(Icon.GAME_DRUMS, R.string.game_drums, R.string.say_game_drums, 0xFFFFB74D.toInt()),
    FACES(Icon.GAME_FACES, R.string.game_faces, R.string.say_game_faces, 0xFFFFCA28.toInt()),
    FISHING(Icon.GAME_FISHING, R.string.game_fishing, R.string.say_game_fishing, 0xFF4FC3F7.toInt()),
    STARS(Icon.GAME_STARS, R.string.game_stars, R.string.say_game_stars, 0xFF7986CB.toInt()),
    TAP_COUNT(Icon.GAME_COUNT, R.string.game_count, R.string.say_game_count, 0xFF4DB6AC.toInt()),
    BIG_SMALL(Icon.GAME_BIG_SMALL, R.string.game_big_small, R.string.say_game_big_small, 0xFFBA68C8.toInt()),
    SHADOWS(Icon.GAME_SHADOWS, R.string.game_shadows, R.string.say_game_shadows, 0xFF90A4AE.toInt()),
    TRAIN(Icon.GAME_TRAIN, R.string.game_train, R.string.say_game_train, 0xFFFF8A65.toInt()),
    WIPE(Icon.GAME_WIPE, R.string.game_wipe, R.string.say_game_wipe, 0xFF81C784.toInt()),
    FLOWERS(Icon.GAME_FLOWERS, R.string.game_flowers, R.string.say_game_flowers, 0xFFF06292.toInt()),
    BLOCKS(Icon.GAME_BLOCKS, R.string.game_blocks, R.string.say_game_blocks, 0xFF64B5F6.toInt());

    fun create(context: Context, host: GameHost): View = when (this) {
        PEEKABOO -> PeekabooView(context, host)
        FIND_IT -> FindItView(context, host)
        XYLOPHONE -> XylophoneView(context, host)
        COLOR_SORT -> ColorSortView(context, host)
        FEED -> FeedView(context, host)
        TRACE -> TraceView(context, host)
        BUBBLES -> BubblePopView(context, host)
        MEMORY -> MemoryView(context, host)
        SHAPE_SORT -> ShapeSortView(context, host)
        JIGSAW -> JigsawView(context, host)
        BALLOONS -> BalloonsView(context, host)
        DRUMS -> DrumsView(context, host)
        FACES -> FacesView(context, host)
        FISHING -> FishingView(context, host)
        STARS -> StarsView(context, host)
        TAP_COUNT -> TapCountView(context, host)
        BIG_SMALL -> BigSmallView(context, host)
        SHADOWS -> ShadowsView(context, host)
        TRAIN -> TrainView(context, host)
        WIPE -> WipeView(context, host)
        FLOWERS -> FlowersView(context, host)
        BLOCKS -> BlocksView(context, host)
    }

    companion object {
        /** The coloring groups the picture games use: the words a two-year-old knows best. */
        val PICTURE_GROUPS = setOf("01-animals", "02-sea", "04-things-that-go", "05-food", "07-farm")
    }
}

/** What a game needs from the app. */
interface GameHost {
    val sounds: Sounds

    /** Says [text] out loud, if the spoken names are on. */
    fun say(text: String)

    /** "Well done!": confetti, a jingle and a cheer. */
    fun cheer()

    /** The coloring groups to take pictures from (see [Game.PICTURE_GROUPS]). */
    val pictureGroups: List<Category>

    /** [page] in color at [width] x [height]: as the child colored it, or colored in for them. */
    fun picture(page: ColoringPage, width: Int, height: Int): Bitmap

    /** The coloring group in folder [id] ("10-shapes"), if there is one. */
    fun group(id: String): Category?

    /** A picture by its title ("Cow"): a coloring page, or one of the extra pictures in `assets/learn`. */
    fun page(title: String): ColoringPage?
}
