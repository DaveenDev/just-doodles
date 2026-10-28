# Brief: 12 new Play games + 12 new Learn topics

Goal: Play and Learn each go from 10 to 22 items (7 free, 15 PLUS). The shared scaffolding is done
(enum entries, card labels and spoken lines in `res/values/strings_more_menu.xml`, the Icon enum
entries, the Plus sets, the card grid). Each helper writes ONLY the new files named in its task.

## Rules for every helper
- Project: `E:\DEVELOPER (WEB)\FREE DRAWING APP`, Kotlin in `app/src/main/java/com/daveendev/justdoodles/`.
- Audience: a 2-year-old. Plain Android framework only: NO AndroidX, NO libraries, NO INTERNET permission,
  no ads, no analytics, no asset files, no new permissions. Everything is drawn in code.
- No on-screen text for the child (the grown-ups' card label is the only text). Big touch targets, nothing
  can be failed, nothing scary or loud, no timers that punish. Every tap does something nice (a sound, a
  spoken word, a wiggle). Many fingers can touch at once; handle ACTION_POINTER_DOWN too.
- Spoken words go through `host.say("...")` (text-to-speech). Sounds through `host.sounds`:
  `note(index 0..11)`, `pop(pitch)`, `bloop()`, `boing()`, `whoosh()`, `sparkle()`, `jingle()`. A cheer (confetti
  + jingle) is `host.cheer()`, use it sparingly (every ~8-10 successes, or when a round is finished).
- Pictures: `host.pictureGroups` (the coloring groups animals, sea, things-that-go, food, farm),
  `host.group(id)` ("01-animals" ... "10-shapes"), `host.page("Cow")` finds any of the ~210 coloring pages by
  title (may be null), and `host.picture(page, w, h)` renders it in color (cache the Bitmap, rendering takes a
  moment; do it lazily, never every frame). See `Games.kt` for `GameHost`.
- Existing helpers you may call (read them, do not edit them): `Palette` (colors, `Palette.INK`),
  `Stickers.draw(canvas, Sticker.X, color)` (100x100 box), `Stamps`, `Critters`, `Icons`.
- Look at `BubblePopView.kt`, `FeedView.kt`, `MemoryView.kt`, `PeekabooView.kt` first and copy their
  conventions: `@SuppressLint("ViewConstructor") class XView(context: Context, private val host: GameHost) : View(context)`,
  `contentDescription = context.getString(R.string.game_x)`, frame loop with `postInvalidateOnAnimation()` while
  `isAttachedToWindow`, delta time capped at 0.05 s, `removeCallbacks` in `onDetachedFromWindow`, rebuild
  state in `onSizeChanged`, draw with the app's look (ink outline `Palette.INK`, bright fills, rounded shapes).
  Match the surrounding code's comment density and naming; KDoc on the class says what the game is and does.
- Extra strings: put them in YOUR OWN new file `app/src/main/res/values/strings_<yourgame>.xml`, never in
  `strings.xml` or `strings_more_menu.xml`. Name them `g_<key>_<what>`. Escape apostrophes (`\'`).
  The card label `R.string.game_<key>` and the line said on entering `R.string.say_game_<key>` already exist.
- Do NOT edit any other existing file (Games.kt, Icons.kt, MainActivity.kt, Plus.kt, strings.xml...).
  Other helpers are writing other new files at the same time. Do NOT run Gradle (the lead compiles
  everything at the end). So write careful, compile-ready Kotlin: check every API you use by reading its
  source; no guessing signatures. Import everything you use. Kotlin 2.x, minSdk is in `app/build.gradle.kts`.
- When finished, reply with: files written, any string resource names used, anything the lead must know.

## The 12 games (enum `Game`, class name, what it is)
| Key | Class | Idea |
|---|---|---|
| BALLOONS | `BalloonsView` | Colored balloons float up; a tap pops one (sparkle burst), plays that color's note (`Palette.colors` index -> `note`) and says the color name (`R.array.color_names` in strings.xml). Endless, gentle. |
| DRUMS | `DrumsView` | 4 big colored drum pads (and a cymbal-ish round one) in a row; each tap bangs with a soft pitched pop/bloop at a different pitch, the pad squashes and ripples. Never loud. Optional: a tiny animal drummer on top. |
| FACES | `FacesView` | A big round face in the middle. Tapping the eyes, nose, mouth, hair or ears cycles to a new style (5+ each) with a boing and a word ("Big eyes!", "Smile!"). Tap the cheeks to blush. Sparkle on wink. |
| FISHING | `FishingView` | A sea scene (waves, sand). Fish swim across at different depths. Tap a fish: it jumps with a splash (bloop) and says its name if it is a picture fish, else just "Splash!". Mix of drawn fish and `host.page` sea creatures (Fish, Whale, Octopus, Crab, Turtle, Jellyfish, Starfish). |
| STARS | `StarsView` | Night sky, a big moon. Stars fall slowly; tap one to catch it and it flies into a jar at the bottom which fills with glowing stars (sparkle, note). When the jar is full, cheer and start empty. |
| TAP_COUNT | `TapCountView` | A handful (1-5) of picture items (host.page pictures or Stickers) appear. Each tap on one makes it glow and the voice counts "One", "Two"... After the last one: "Three apples!" plus cheer, then a new set. |
| BIG_SMALL | `BigSmallView` | Two copies of the same picture, one big one small. Voice asks "Where is the big cow?" or "small". Tap right = cheer-lite and next; tap wrong = gentle "That is the small one" and ask again. Never a fail screen. |
| SHADOWS | `ShadowsView` | A dark silhouette (the picture drawn as a flat dark shape: render the colored picture then draw it with a black color-filter or `ColorMatrix`) at the top, 3 colored pictures to choose from. Tap the match: it flies onto the shadow and lights up, says the name. Wrong: gentle wobble, "Try again". |
| TRAIN | `TrainView` | A track with scenery scrolling. Tap the engine = "Choo choo!" with a whoosh and a puff of smoke; each tap adds a colored car (with an animal or fruit picture on it) up to 6, the train rolls off and a new one arrives. Tapping a car says what is in it. |
| WIPE | `WipeView` | A picture is hidden under a foggy gray-blue cloud/scribble layer. A finger rubs it away (erase with a Bitmap + PorterDuff.Mode.CLEAR line stroke on an offscreen layer). At ~60% revealed it finishes by itself: sparkle and the name is said, then a new picture. Rubbing makes a soft sound (use `host.sounds.pop(...)` sparingly). |
| FLOWERS | `FlowersView` | Soil along the bottom. Tapping anywhere plants a seed which sprouts and blooms into a random flower (different colors/shapes), with a grow animation, a note and "Pretty!"/the color name sometimes. A bee or butterfly flutters between flowers. Max ~12, oldest fades. |
| BLOCKS | `BlocksView` | Colored blocks (squares, rectangles, triangles, arches). Tap the ground to drop the next block onto the stack (it lands with a soft bounce, a note rising up the scale). After about 8 blocks the tower sways, cheers and tumbles gently into a pile (boing), then restarts. |

## Icons
Icons are drawn in code into a 100 x 100 box. `Icon` entries exist. Game icons go in `GameIcons.kt`
(`object GameIcons { fun draw(c: Canvas, icon: Icon) }`), topic icons in `LessonIcons.kt`
(`object LessonIcons { fun draw(c: Canvas, icon: Icon) }`). Read `Icons.kt` (peekaboo, feed, food, sea, bugs
drawings and the shared paint style: ink outline width 4, rounded caps and joins, bright fills with a white
highlight, big simple friendly shapes with kawaii faces where it fits) and match that look. Fill the box well:
the shape should use roughly the whole 100x100 so it reads on a small card. Use `Stickers.draw` and `Palette`
if helpful. Have your own private Paint objects (those in Icons are private).

## The 12 Learn topics (icons only here; the decks are done by the lead)
WILD (lion face), PETS (dog + cat), BIRDS (a bird on a branch), FARM (barn), GARDEN (flower + watering can),
CARS (a car or truck), FLYING (plane or hot air balloon), FRUITS (apple/banana/strawberry), WEATHER (sun
peeking from a cloud with raindrops), SPACE (rocket + stars + planet), TOYS (teddy bear or blocks), BEACH
(sand castle, bucket or seashell).
