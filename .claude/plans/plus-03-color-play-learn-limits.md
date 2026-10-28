# PLUS 03: what's free in Color, Play and Learn

Part of the PLUS plans; start at `plus-00-overview.md`. Size: small to medium. Needs plan 01, and plan 02 for the pop-up on locked items.
Files: `MainActivity.kt` (`buildPages`, `choosePage`, `buildGames`, `buildLessons`, `buildGame`, `buildLesson`), `Plus.kt`.

## Color: 7 of 21 per group

**Rule:** in each group, the first 7 pages by file number are free, and pages 8 to 21 are PLUS. A page the child already drew on (`store.has(id)`) stays open either way.

**Growing the groups from 15 to 21 (decided 2026-10-22):** 60 new pages, 6 per group, numbered 151 to 210 and following the page rules in `SENDOFF.md` (closed shapes, no arcs or transforms, a `<title>`). With 21 pictures, `columnsFor(21, maxRows = 3)` gives 6 across in 4 rows, and `cardSize` shrinks the cards to fit; check on the realme that they're still big enough to tap.

The rule goes by position, so new pages added to the end of a group are PLUS automatically. A group's card picture is its first page, so it's always free.

The free set would be:

| Group | Free (7) |
|---|---|
| Animals | cat, dog, bunny, duck, pig, bear, elephant |
| Sea | fish, whale, octopus, crab, turtle, jellyfish, starfish |
| Garden | house, flower, butterfly, tree, mushroom, ladybug, sunflower |
| Things that go | car, bus, train, boat, plane, rocket, bicycle |
| Food | apple, banana, ice cream, cupcake, strawberry, watermelon, pizza |
| Sky | sun, balloons, star, moon, rainbow, kite, cloud |
| Farm | cow, horse, sheep, hen, chick, goat, barn |
| Bugs | bee, beehive, snail, caterpillar, spider, spider web, ant |
| Toys | beach ball, teddy bear, blocks, robot, crayons, spinning top, drum |
| Shapes | circle, square, triangle, rectangle, oval, star, heart |

These are most toddlers' first words, so the free set holds up as a full app on its own, and PLUS adds the less everyday ones (lion, penguin, dolphin, helicopter...). To hand-pick instead, keep a list of free page ids rather than going by position.

**Where it goes:**

- `buildPages(cat)`: check `Plus.pageOpen(index, store.has(page.id))` for each page. A locked page gets the badge (plan 02), and its tap calls `showLocked(page)` instead of `choosePage`.
- `choosePage(id)`, and restoring `colorPageId` in `onCreate`: refuse a locked page. This guards against a stale saved id.

## Play and Learn: 7 whole games and 7 whole topics free, 15 more of each on PLUS (decided 2026-10-28)

First decided 2026-10-22 as 3 free and 7 PLUS of each. On 2026-10-28 the free sets grew to 7, and 12 new games and 12 new topics were added, so each screen now has 22 cards (a 6 x 4 grid).

| | Free (7) | PLUS (15) |
|---|---|---|
| Play | Peekaboo, Where is it?, Xylophone, Colors, Feed me, Roads, Bubble pop | Memory, Shape sorter, Jigsaw, Balloons, Drums, Funny faces, Fishing, Stars, Counting, Big and small, Shadows, Choo choo, Rub and see, Flowers, Blocks |
| Learn | Colors, Shapes, Counting, Animal sounds, ABC, My body, Food | Sea animals, Bugs, Sky, Wild animals, Pets, Birds, Farm, Garden, Cars and trucks, Up and away, Fruits, Weather, Space, Toys, Beach |

The free ones are the first 7 cards on each screen. The new Learn topics are built from coloring pages by title (the `words_*` arrays in `arrays_more_lessons.xml`) or a whole group; the new games are `BalloonsView`, `DrumsView`, `FacesView`, `FishingView`, `StarsView`, `TapCountView`, `BigSmallView`, `ShadowsView`, `TrainView`, `WipeView`, `FlowersView` and `BlocksView`.

**Why this split:** every free activity is complete, the child meets one locked card rather than decks that end early, and nothing changes inside any game or topic.

**Free games and topics are complete.** Peekaboo and Where is it? use every picture from their 5 groups, including pages that are PLUS in Color: showing a picture isn't coloring it. Learn's Shapes shows every page in the Shapes group, Colors all 12 colors, and Counting goes 1 to 10.

**How:**

- **In `Plus.kt`:** `FREE_GAMES = setOf(Game.PEEKABOO, Game.FIND_IT, Game.XYLOPHONE)` and `FREE_LESSONS = setOf(Lesson.COLORS, Lesson.SHAPES, Lesson.COUNT)`, with `gameOpen(game)` and `lessonOpen(lesson)` (true with PLUS, else membership in the set). Explicit sets, so reordering the cards never changes what's free.
- **`buildGames()` and `buildLessons()` in `MainActivity.kt`:** a locked `MenuCard` gets `locked = true` (the badge from plan 02). Its tap still plays the pop sound and says the game's or topic's name, then calls `showLocked(icon)`. It doesn't set `game` or `lesson`, and doesn't open the screen.
- **`buildGame(g)` and `buildLesson(l)`:** if it's locked, show `Screen.GAMES` or `Screen.LESSONS` instead. `game` and `lesson` come back from `savedInstanceState` (`KEY_GAME`, `KEY_LESSON` in `onCreate`), so without this guard a refund between restarts could reopen a locked game.
- **Nothing changes in the game or Learn views** (`PeekabooView`, `LearnView`, `BodyView` and the rest).

## Checks

- Free tier: every free picture opens, and every locked one shows the pop-up. A locked page the child had colored before still opens.
- On the Play and Learn screens, the 15 locked cards show the padlock; tapping one says its name, shows the pop-up, and doesn't open it.
- The 7 free games and the 7 free topics play in full.
- A refund while a PLUS game is open goes back to the Games screen on the next rebuild (plan 01's live refresh).
- PLUS on: everything opens, as today.
