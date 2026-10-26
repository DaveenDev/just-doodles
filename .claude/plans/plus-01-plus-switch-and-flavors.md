# PLUS 01: the PLUS switch and the two flavors

Part of the PLUS plans; start at `plus-00-overview.md`. Size: small. Depends on nothing.

## Goal

One place that answers "is PLUS on?". Every lock reads it, and it changes live, with no restart, when a purchase goes through or a refund comes back. It behaves differently in each flavor, without `if (flavor)` checks scattered around the code.

## Design

### `Plus.kt` (main source set)

A small object, in the style of `Palette`:

- `val isOn: Boolean`: what every lock reads.
- `fun load(prefs)`: called in `onCreate`. It reads a new key, `KEY_PLUS`, from the existing `state` prefs. Caching it there means PLUS works offline and is right from the first frame.
- `fun set(on: Boolean, persist: Boolean = true)`: only `PlusStore` calls this. If the value changed, it calls `onChange`.
- `var onChange: (() -> Unit)?`: `MainActivity` sets this to refresh whatever is on screen (see below).
- The limits as constants, so they're easy to change:
  - `FREE_PAGES_PER_GROUP = 7`
  - `FREE_GAMES = setOf(Game.PEEKABOO, Game.FIND_IT, Game.XYLOPHONE)`
  - `FREE_LESSONS = setOf(Lesson.COLORS, Lesson.SHAPES, Lesson.COUNT)`
  - the set of locked tools
- The questions the UI asks, so the rules live in one place:
  - `pageOpen(index: Int, hasDrawing: Boolean)`: true if `index < FREE_PAGES_PER_GROUP`, or PLUS is on, or the child already drew on the page.
  - `toolOpen(tool: Tool)`: always true for `BRUSH` and `ERASER`. `MAGIC`, `STAMP`, `PATTERN` and `STICKER` need PLUS.
  - `canExport()`: Save to phone and Share.
  - `gameOpen(game: Game)` and `lessonOpen(lesson: Lesson)`: true with PLUS, else whether it's in `FREE_GAMES` / `FREE_LESSONS`.

### `PlusStore`: one per flavor, same class name and methods

- `app/src/direct/java/com/daveendev/justdoodles/PlusStore.kt`: no billing at all. `start()` calls `Plus.set(true, persist = false)`. Not saving means a `play` build installed over a `direct` one starts on the free tier. `available` is false, so no PLUS page ever shows, and the donate page stays.
- `app/src/play/java/com/daveendev/justdoodles/PlusStore.kt`: the real Play Billing code (plan 06). Until then, a stub that's always `UNAVAILABLE`.
- Both have:
  - `start(activity)`, `refresh()`, `stop()`
  - `val available: Boolean`
  - `val price: String?`
  - `val state` (`NOT_OWNED`, `PENDING`, `OWNED`, `UNAVAILABLE`)
  - `var onStateChange: (() -> Unit)?`
  - `buy(activity)`, `restore(onDone: (Boolean) -> Unit)`

The main code only ever calls `PlusStore`, so neither flavor's code needs to know about the other. This extends the split the project already uses for `show_donate` (`res/values/flags.xml` vs `src/play/res/values/flags.xml`) from resources to code.

### Live refresh

When `Plus.onChange` fires, `MainActivity`:

- rebuilds the current picker, menu, game list or Learn list with `showScreen(screen)`, so badges appear or disappear;
- calls `refreshButtons()` to update the tool column;
- switches to the crayon if the easel holds a tool that's now locked (after a refund);
- updates the status line if the grown-up panel is open on the PLUS page.

### Saved state that's locked now

This happens on first run of the PLUS version and after a refund.

- `toolFor()` in `MainActivity.kt` already falls back to the crayon for a tool the mode doesn't use. Make it also check `Plus.toolOpen`.
- If `colorPageId` points at a locked page the child never drew on, fall back to no page, so the picture picker shows. Never open a locked page automatically.

## Steps

1. Add `Plus.kt` with the constants, the cache and the questions above.
2. Add both `PlusStore.kt` files. The `play` one is a stub until plan 06.
3. In `MainActivity`:
   - `onCreate`: `Plus.load(prefs)`, then `plusStore.start(this)`, both before any screen is built;
   - `onResume`: `plusStore.refresh()`;
   - `onDestroy`: `plusStore.stop()`.
4. Wire `Plus.onChange` as described under "Live refresh".
5. Extend `ParentPanel.Host` with `plusAvailable`, `plusState`, `plusPrice`, `buyPlus()` and `restorePlus(onDone)`, all handed through from `PlusStore`.

## Checks

- `./gradlew assembleDirectRelease --console=plain </dev/null`: everything unlocked, same as today.
- `assemblePlayRelease`, sideloaded with no Play product set up yet: free tier, no crash.
- Flip `KEY_PLUS` with a temporary debug call: badges appear and disappear without a restart.
