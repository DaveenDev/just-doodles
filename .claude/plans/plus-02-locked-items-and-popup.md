# PLUS 02: locked items and the "ask a grown-up" pop-up

Part of the PLUS plans; start at `plus-00-overview.md`. Size: medium. Needs plan 01.
Files: `Icons.kt` (padlock art), `Buttons.kt` (badges on `ThumbView`, `ToolButton`, `MenuCard`), a new `LockedView.kt`, `MainActivity.kt`, `Sounds.kt` (one soft sound), `strings.xml`.

## Goal

A child can always see that there's more. Tapping something locked gets a short, friendly answer instead of a dead button. A grown-up nearby gets a quick way to PLUS from there, behind the gate.

## What you asked for

"For the locked content, add a pop-up that tells the child to tell mommy/daddy to buy it."

## The wording (decided 2026-10-22)

The pop-up is in, and the voice says **"Ooh, that one's locked! Ask a grown-up."** rather than "Tell Mommy or Daddy to buy it." You chose this after reading the reasons below.

- **Google Play review.** Just Doodles is aimed at children, so Google Play's Families policy covers its in-app offers. Google rejects or removes kids' apps for overly aggressive commercial tactics. Telling a two-year-old to get their parents to buy something is the clearest example, and the line most likely to get the app flagged.
- **The law in some markets.** EU consumer law bans telling children to buy something or to get their parents to buy it for them (Unfair Commercial Practices Directive, Annex I, no. 28), and EU regulators have applied that to in-app purchases. It only matters if you release there, but Play's review isn't country by country.
- **Every family.** "A grown-up" covers grandparents, single parents, nannies and teachers, and matches the app's "For grown-ups" wording.
- **It still does what you want.** The child learns it's locked and goes to a grown-up, and the grown-up is a few taps from PLUS.

Don't change `locked_say` to anything that mentions buying or names a parent. The same goes for its translations.

## Where locks show

- **Coloring picture grid** (`buildPages`): locked pictures show their outline slightly faded (alpha about 0.6), with a small gold padlock in a corner. Not gray and not crossed out: they should look like something to look forward to.
- **Tool column** (`buildMainRow`): the Magic, Stamp, Pattern and Sticker buttons get a small padlock badge, but keep their icon and tint.
- **Drawings page** (grown-ups only): Save and Share show a "PLUS" tag (plan 05).
- **Play and Learn screens**: the 7 PLUS game cards and the 7 PLUS topic cards get the padlock (plan 03). Nothing inside a free game or topic is locked.
- **Category cards and the start menu**: no locks. All 10 groups are free, and the four menu cards always open.

## The pop-up (`LockedView.kt`)

An overlay on top of the current screen, like the sticker tray or `CelebrationView`:

- **Look:** a white rounded card in the middle showing **the locked thing itself** (the picture, or the tool's icon), with a big friendly padlock with a smile that wiggles once.
- **Sound:** a soft two-note "ding-dong" from `Sounds`. Never a buzzer.
- **Voice:** `locked_say`. To avoid nagging, the full line plays at most once every 30 seconds; in between, it's just the sound and the wiggle.
- **Closing:** any tap closes it, it closes itself after 3 seconds, and Back closes it too (add it to the Back handling next to the trays).
- **Grown-up button:** a small gray button in the card's corner (screen-reader label "For grown-ups: hold to see PLUS"). It's a `HoldButton` with the usual 2-second hold, and leads to the settings, then the sum, then the PLUS page (plan 05). A short tap does nothing.
- **Never** shows a price, a Buy button, a store link, or the word "buy".
- **Only** appears right after a tap on something locked. Never by itself: not on launch, not after a celebration, not on a timer.

## What a tap does

| Tapped | Today | With locks |
|---|---|---|
| A locked picture | Opens the page | Pop-up; the page doesn't open |
| A locked picture the child already colored | Opens | Opens (a child's own drawing is never locked) |
| A locked tool | Picks the tool and says its name | Pop-up; the current tool stays |
| A locked game or topic card | Opens the game or topic | Says its name, then the pop-up; it doesn't open |
| Save or Share (grown-ups) | Saves or shares | Sum, then the PLUS page |

## New strings (`strings.xml`)

- `locked_say`: `Ooh, that one\'s locked! Ask a grown-up.`
- `locked_grownup`: `For grown-ups: hold to see PLUS`
- `plus_tag`: `PLUS`

Translations go in `values-xx/strings.xml`, like the other spoken words.

## Steps

1. Draw `Icon.LOCK` in `Icons.kt`: a rounded gold padlock with a small smile, drawn in code like the other icons.
2. Add a `locked` flag to `ThumbView`, `ToolButton` and `MenuCard` that draws the badge and fades the picture.
3. Write `LockedView` and `MainActivity.showLocked(what)`, where `what` is a page, a tool, or a card's icon.
4. Hook it into the picture grid. Plans 03 and 04 hook it into the games, Learn and the tools.

## Checks

- Tap a locked picture 10 times fast: one pop-up at a time, the voice plays once, nothing opens, no crash.
- The pop-up never traps the child: any tap closes it, and it closes itself.
- With PLUS on (the `direct` build, or after buying): no badges and no pop-ups anywhere.
- The badge is still readable at the 5-across picture grid size on the realme (2354x1080).
- The bedtime timer still takes over cleanly while the pop-up is open.
