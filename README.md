# Just Doodles

A drawing and coloring app for toddlers. No ads, no tracking, no internet. On Google Play there's one optional upgrade, **Just Doodles PLUS** (₱79, one payment, no subscription), which grown-ups can buy behind a parent check.

The app never asks for internet access, so it physically cannot show ads or send anything anywhere. The sideloaded build asks for **no permissions at all**. The Google Play build asks for Google Play's billing permission, which lets it ask the Play Store app on the phone about PLUS, and for network *state* (whether the phone is online, which the billing library checks; it can't use the network). The internet permission that the billing library's logging would add is removed in [`app/src/play/AndroidManifest.xml`](app/src/play/AndroidManifest.xml). Drawings are stored only in the app's private storage on the device.

## Store listing

Ready to paste. Google Play doesn't allow price or promo words like "Free" or "No Ads" in the **title**, so those go in the short description instead. F-Droid and direct APK downloads have no such rule.

| Where | Text |
| --- | --- |
| Name under the app icon | `Just Doodles` |
| Google Play title (30 characters max) | `Just Doodles: Kids Drawing` |
| F-Droid or APK download title | `Just Doodles – Free Drawing, No Ads` |
| Short description (80 characters max) | `Drawing & coloring for toddlers. No ads, no internet. One-time PLUS upgrade.` |

**Full description:**

> Just Doodles is a drawing and coloring app for little ones, and that's all it is. No ads, no accounts and no internet access, so there is nothing to tap by accident and nothing that leaves the phone.
>
> It's free to play, with 70 coloring pages, three games, three Learn topics and crayons in 12 colors. One optional upgrade, Just Doodles PLUS, unlocks everything else for a single payment: no subscription, and future updates included. It's for grown-ups, behind a parent check; children never see ads, prices or sales pop-ups.
>
> • Four big buttons to start: Free Draw, Color, Play or Learn, with no reading needed
> • Learn: colors, shapes, counting to ten, animal sounds, the ABC and the parts of the body, all said out loud, each followed by a gentle "Where's red?" game
> • Play: ten little games. Peekaboo (rub the cloud away to find a picture), Where is it? ("Where's the cow?"), a rainbow xylophone, sorting colored balls into buckets, feeding hungry animals, following a dotted road, popping picture bubbles, finding matching pairs, a shape sorter and big-piece jigsaws. Nothing to lose, no timers, no scores
> • Free Draw: white or black paper, stickers to drop in, and pattern crayons that draw hearts, stars, zigzags, train tracks and paw prints
> • Color: 210 coloring pages in 10 groups (animals, sea, garden, things that go, food, sky, farm, bugs, toys, shapes). Tap a shape to fill it, or color by hand
> • 12 bright crayon colors in 3 sizes, plus a magic crayon: rainbow, glitter, glow and mirror
> • Stamps, an eraser and undo
> • Gentle musical sounds, and the colors, pictures and parts said out loud
> • A happy celebration when a page is finished
> • Baby mode for ages 1–2: every touch blooms shapes and music, with no buttons at all
> • A bedtime timer that puts the app to sleep with a lullaby
> • Every picture is saved automatically, and grown-ups can save or share one
> • Back button and accidental taps can't leave or wipe a drawing; grown-up settings sit behind a long press
>
> **Free:** Free Draw with crayons and the eraser; the first 7 pictures of every coloring group; Peekaboo, Where is it? and the xylophone; Colors, Shapes and Counting in Learn; baby mode and the bedtime timer.
> **PLUS (one payment):** all 210 coloring pages; Colors sort, Feed me, Roads, Bubble pop, Memory, Shape sorter and Jigsaw; Animal sounds, ABC, My body, Food, Sea animals, Bugs and Sky; the magic crayon, stamps, stickers and pattern crayons; saving and sharing drawings; and new pictures and tools as they come.
>
> Made by a parent. No ads, ever, in the free version too.

## What's in it

- **Animated splash** on launch: the Just Doodles logo bounces in as a sticker with a sparkle burst and a small DaveenDev credit fades in, then "Just Doodles" drops in letter by letter. About 3 seconds; any tap skips it. It only plays on a fresh start, not when coming back from Home.
- **Start menu** with four big picture cards: **Free Draw**, **Color**, **Play** and **Learn**. The labels are for grown-ups; the pictures and the spoken name are enough for little ones.
- **Free Draw**: first pick a sheet: fresh **white** or **black** paper, or one drawn on before (newest first). Up to 30 drawn sheets are kept. Blank paper has two extra tools:
  - **Stickers**: 12 pictures (sun, cloud, rainbow, tree, flower, house, car, fish, cat, duck, butterfly, balloon) in the crayon color. The sticker follows the finger and drops onto the paper when the finger lifts. The size button makes it smaller or bigger.
  - **Pattern crayon**: draws a pattern along the finger instead of a line: **dots**, **zigzag**, **waves**, **train tracks**, **hearts**, **stars**, **flowers** or **paw prints**.
  - Tap the sticker or pattern button once to pick it up, and again to see all of them and choose one.
- **Color**: **210 coloring pages** in 10 groups of 21:
  - **Animals**: cat, dog, bunny, duck, pig, bear, elephant, lion, monkey, giraffe, owl, penguin, frog, panda, fox, mouse, koala, hippo, hedgehog, tiger, snake
  - **Sea**: fish, whale, octopus, crab, turtle, jellyfish, starfish, shark, dolphin, seashell, pufferfish, clownfish, treasure chest, lighthouse, sandcastle, seahorse, submarine, anchor, seal, bucket, swim ring
  - **Garden**: house, flower, butterfly, tree, mushroom, ladybug, sunflower, tulip, daisy, cactus, pine tree, palm tree, leaf, birdhouse, watering can, flower pot, wheelbarrow, shovel, acorn, apple tree, bench
  - **Things that go**: car, bus, train, boat, plane, rocket, bicycle, truck, fire truck, helicopter, ambulance, police car, hot air balloon, motorbike, digger, taxi, scooter, sailboat, skateboard, garbage truck, jeepney
  - **Food**: apple, banana, ice cream, cupcake, strawberry, watermelon, pizza, donut, cookie, pear, orange, grapes, cherries, pineapple, burger, mango, birthday cake, lollipop, egg, bread, milk
  - **Sky**: sun, balloons, star, moon, rainbow, kite, cloud, rain cloud, umbrella, snowflake, planet, UFO, shooting star, bird, sunrise, storm cloud, parachute, Earth, paper plane, raindrop, telescope
  - **Farm**: cow, horse, sheep, hen, chick, goat, barn, tractor, windmill, scarecrow, hay bales, carrot, corn, pumpkin, egg basket, rooster, donkey, carabao, turkey, tomato, milk can
  - **Bugs**: bee, beehive, snail, caterpillar, spider, spider web, ant, dragonfly, grasshopper, beetle, firefly, worm, moth, honeycomb, centipede, cocoon, butterfly net, ant hill, bug jar, magnifying glass, cicada
  - **Toys**: beach ball, teddy bear, blocks, robot, crayons, spinning top, drum, xylophone, rattle, pinwheel, jack in the box, doll, tricycle, piano, gift, rocking horse, rubber duck, stacking rings, trumpet, puzzle, slide
  - **Shapes**: circle, square, triangle, rectangle, oval, star, heart, diamond, pentagon, hexagon, octagon, cross, crescent, arrow, half circle, trapezoid, parallelogram, teardrop, ring, lightning, arch. Each one smiles and comes with three small copies underneath to match.

  Pick a group, then a picture. **Tap** a shape to fill it with color, or **drag** to color by hand. The outlines always stay on top, and tapping a part says its name ("Ear!", "Wheel!").
- **Play**: ten games, each on its own card. None of them can be lost: a wrong move gets a gentle wiggle or the piece floats back, never a buzzer.
  - **Peekaboo**: a picture hides under a cloud. Rub the cloud away; once most of it is gone, the picture bounces and is named ("Peekaboo! Cow!"). Tap for the next one.
  - **Where is it?**: two pictures (three after a few rounds) and the voice asks "Where's the cow?". The right one gets a cheer; any other one says what it is ("That's the pig!").
  - **Xylophone**: eight rainbow bars on the five-note scale. Slide across to play them all; the music button plays *Mary Had a Little Lamb* and lights the bars.
  - **Colors**: drag balls into the bucket of the same color. Picking one up says its color.
  - **Feed me**: drag food into a hungry frog, puppy, cat or pig. Its eyes follow the food and its mouth opens wider as it comes near. Three chomps and "Yum!".
  - **Roads**: drag a car (or a butterfly, balloon or cat) along a dotted road to its house (or flower, cloud or tree). It only moves while the finger stays on the road. The roads go straight, wavy, over a hill, zigzag and round a loop.
  - **Bubble pop**: bubbles float up with a picture inside each. Tap to pop one; it says what was in it. Every ten pops, a cheer.
  - **Memory**: cards lie face down (2 pairs, then 3, then 4). Turn two over; a match stays up with a sparkle, anything else turns back after a moment.
  - **Shapes** (shape sorter): drag a circle, square, triangle, star or heart into its hole on the board. Picking one up says its name; anywhere else, it floats back.
  - **Jigsaw**: a colored picture in 2, then 3, then 4 big straight pieces. Drag each piece to its place on the faint copy; once it is done, the picture bounces and is named.

  The picture games use the Animals, Sea, Things that go, Food and Farm pages. A page the child has colored shows up as they colored it; the others are colored in for them. The groups are listed in `Game.PICTURE_GROUPS` in `Games.kt`.
- **Learn**: ten topics, each on its own card. One big card at a time is named out loud; tap it to hear it again, and use the orange arrows (or a swipe) for the next one. After five cards come three quick rounds of "Where's red?" about the cards just seen (two to choose from, three once that's easy). The right one sparkles and gets a cheer; any other one just says what it is.
  - **Colors**: the 12 crayon colors as big blobs of paint, each with its own note.
  - **Shapes**: the 21 smiling pages of the Shapes group.
  - **Counting**: 1 to 10. Suns, ducks, fish and more pop in one by one as they're counted ("One, two, three! Three fish!"), then the number shows. The quiz uses 1 to 5.
  - **Animal sounds**: 17 animals from the coloring pages ("The cow says moo!"; "Who says woof woof?").
  - **ABC**: a big letter with a picture for each one ("B is for bus!"). The quiz shows just the letters.
  - **My body**: a big friendly child. Tap a part and it lights up and is named; after a few taps the voice asks "Where's the nose?". A tap close to the right part counts.
  - **Food**, **Sea animals**, **Bugs** and **Sky**: every picture of that coloring group, named ("Banana!"), then "Where's the banana?".

  The words are in `strings.xml` (`animal_sounds`, `abc_words`, `count_things`, `number_words`). The pictures come from the coloring pages, plus six that are only for Learn in [`app/src/main/assets/learn`](app/src/main/assets/learn) (the body, nest, queen, van, yo-yo and zebra), which don't show up in Color.
- **Big, chunky buttons** sized for little fingers, no text to read. The orange **back arrow** at the top left goes back one step: from the paper to the sheets or pictures, and from there to the start menu.
- **Crayon** in 12 bright colors, in 3 sizes (tap the dots button to change size).
- **Magic crayon**: tap it once to pick it up, then tap again to switch between **rainbow** (changes color as you draw), **glitter** (sparkles around the line, and glittery fills on coloring pages), **glow** (a neon line with a soft halo) and **mirror** (everything is drawn on both halves, like a butterfly).
- **Stamps**: tap or drag to sprinkle stamps; tap the button again for the next one (star, heart, flower, duck, cat, fish, car, sun).
- **Gentle sounds**: every color plays its own marimba note (on a five-note scale, so any sequence sounds nice), plus pops, a water-drop "bloop" for fills, a boing for stamps and stickers, and a soft crayon scratch while drawing. All the sounds are generated in code; no audio files.
- **Says the names out loud** ("Red!", "Glitter!", "Big!", "Fish!", "Paw prints!") using the phone's own text-to-speech voice, a little slower and higher than everyday speech. Only voices that work without the internet are used, so the words never leave the phone. The app picks the clearest one, or a grown-up can choose another in the settings.
- **Eraser**.
- **Multi-touch**: every finger draws its own line (toddlers rarely use just one).
- **"All done!" celebration**: once about 70% of a coloring page's shapes (and of its area) have color, confetti bursts, stars pop, the picture does a happy bounce, a jingle plays and a voice cheers. It never blocks coloring, and plays once per finished page.
- **Baby mode (ages 1–2)**: no buttons at all. Every touch blooms a shape with a soft note, and dragging leaves a rainbow trail that plays a little tune and fades away, so the screen never fills up. Turn it on in the grown-up settings.
- **Every page remembers its picture.** Switching pages or leaving the app never loses a drawing.
- **Undo** (the curved arrow, under the colors).
- **Clean page** (under the colors) needs a **1-second hold** (a green ring fills up), so a stray tap can't wipe a picture. Undo brings it back anyway.

### Toddler-proofing

- Runs full-screen, with the status and navigation bars hidden.
- The **Back button never leaves the app** and never takes a drawing off the paper. It only closes the grown-up settings or a sticker or pattern tray, or steps back through the menus. Leave the app with the **Home** gesture or button.
- Landscape only, and flipping the phone around doesn't disturb the picture.

### Grown-up settings

**Press and hold the gray gear** in the top-right corner of the start menu (or of the sheet and picture pickers) for **2 seconds** until its ring fills. In baby mode, the gear is in the top-right corner of the screen. From there you can:

- turn the **sounds**, the **spoken names** and **baby mode** on or off,
- pick the **voice**: **Automatic** (the clearest of the phone's voices that work without the internet, ties going to the one chosen in Android's own settings), or any other one with the arrows. Every tap says a few words in that voice. **More voices** opens Android's text-to-speech settings, where more can be downloaded (in Google's, tap the gear, then *Install voice data*),
- set the **bedtime timer** (off, 10, 20 or 30 minutes). When time is up, the picture fades under a sleepy moon with a lullaby and drawing stops, until a grown-up holds the gear for 2 seconds,
- **pin the app** (Android asks you to confirm),
- open **Drawings** to **save** a picture to the phone, **share** it, or **delete** it.

Settings are remembered. Saving uses the phone's own "save as" screen and sharing uses the share sheet, so the app still needs no storage permission.

**Just Doodles PLUS** (Google Play build only). Below Drawings is a **⭐ Just Doodles PLUS** button. It first asks a harder sum (a two-digit number, six answers to pick from; a wrong pick goes back to the settings), then shows what PLUS adds, **Get PLUS** with Google Play's price, and **Restore purchase**. Google Play's own purchase sheet does the paying. PLUS comes back by itself after a reinstall with the same Google account, and a refund takes it away again the next time the app opens; drawings are never touched either way.

On the free version, things that need PLUS show a small smiling padlock. Tapping one shows the picture on a card with a padlock, a soft chime and "Ooh, that one's locked! Ask a grown-up." It never shows a price or a way to buy, closes on any tap or by itself after 3 seconds, and only appears right after a child taps something locked. The small gray gear on that card, held for 2 seconds, takes a grown-up to the sum and then the PLUS page. Pages a child already colored stay open even if they're in PLUS.

What's free and what's PLUS is set in [`Plus.kt`](app/src/main/java/com/daveendev/justdoodles/Plus.kt).

At the bottom of the sideloaded build's settings is **Support the developer**. It first asks a quick sum (four answers to pick from; a wrong pick goes back to the settings), then shows the donate options: **PayPal**, **Buy Me a Coffee** and **GCash / Maya**. Links open in the phone's browser, so the app itself still has no internet access. Tapping the GCash / Maya number copies it.

The donate links and number are in [`strings.xml`](app/src/main/res/values/strings.xml) (`donate_paypal_url`, `donate_bmc_url`, `donate_wallet_number`). Leave one empty to hide that option.

> **Tip for parents: pin the app.** Android's *App pinning* locks the phone to one app until you unlock it. The **Pin the app** button in the grown-up settings does it for you, or you can do it yourself:
> Turn it on in **Settings → Security (or Security & privacy) → More security settings → App pinning**.
> Then open Just Doodles, open **Recent apps**, tap the app's icon at the top of its card and choose **Pin**.
> To unpin: hold **Back + Recent apps** (or swipe up and hold with gesture navigation), then enter your PIN.

## Building

Requirements: JDK 17 or newer, and the Android SDK (platform 36). The SDK location is set in `local.properties` (`sdk.dir=...`).

There are two versions (Gradle *flavors*):

| Flavor | For | PLUS | Donate page | Build | Output |
| --- | --- | --- | --- | --- | --- |
| `direct` | APK handed out by hand, F-Droid | Everything unlocked, nothing to buy | Yes | `./gradlew assembleDirectRelease` | `app/build/outputs/apk/direct/release/app-direct-release.apk` |
| `play` | Google Play | Free version, plus the PLUS upgrade through Google Play Billing | No (Play doesn't allow links to outside payment pages) | `./gradlew bundlePlayRelease` | `app/build/outputs/bundle/playRelease/app-play-release.aab` |

The donate switch is the `show_donate` flag in `res/values/flags.xml`; `app/src/play/res/values/flags.xml` turns it off. PLUS is handled by `PlusStore.kt`, one per flavor: `app/src/direct/...` just switches PLUS on, `app/src/play/...` talks to Google Play. Only the `play` flavor uses a library (Google's Play Billing Library); the `direct` flavor still uses none.

**Testing PLUS on a phone.** The phone in the house runs a debug-signed build, and a release build (upload key) can't update it without uninstalling, which deletes every drawing. So test there with `./gradlew assemblePlayDebug` (free version, padlocks) or `assembleDirectDebug` (everything unlocked). Real test purchases need the Play Console setup below.

**Signing.** Release builds are signed with the **upload key** in `signing/`: `justdoodles-upload.jks` plus `keystore.properties`, which holds its password. **Back up the whole `signing` folder somewhere safe (not just this PC) and never share it.** Every Play Store update has to be signed with this key. If the folder is missing (on another machine), release builds fall back to the debug key, which is fine for sideloading but Play will refuse them.

For every new release, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.

## Publishing on Google Play

1. Host [`privacy-policy.html`](privacy-policy.html) at a public web address (GitHub Pages, Netlify, Vercel...). Play needs its URL.
2. **Payments profile**: Play Console → Setup → Payments profile. Selling PLUS needs one linked to the developer account.
3. Upload `app-play-release.aab` to the **internal testing** track first (Play needs an upload with the billing permission before you can create a product), then to **closed testing**. New personal developer accounts need 12 testers opted in for 14 days in a row before Production unlocks.
4. **The PLUS product**: Monetize → Products → One-time products → create `plus_lifetime` (exactly that; it must match `PlusStore.PRODUCT_ID`, and a product id can't be reused once deleted). Name *Just Doodles PLUS*; description *Unlock every coloring page, game and Learn topic, plus magic crayons, stamps, stickers, patterns and saving. One payment, no subscription.* Price **₱79**; let Play convert the other countries. Activate it.
5. **License testing** (Setup → License testing): add your own Google account(s). Testers get test cards and are never charged. Install the internal-testing build from the tester link and try: buy (*always approves*), decline (*always declines*), the *slow test card* (waiting for payment, then PLUS turns on by itself), cancel, uninstall and reinstall (PLUS comes back), airplane mode (PLUS stays on), and a refund with **revoke** in Order management (back to free on the next open, drawings kept).
6. App content: privacy policy URL; target audience *under 5*; ads: *no*; app access: *all features without login* (PLUS is optional); content rating questionnaire. **Data safety**: the app itself collects and shares nothing; check Google's guidance on the Play Billing Library and declare what it says. The "Contains in-app purchases" label appears by itself once the product is active.
7. Store listing: the text above, a 512 × 512 icon, a 1024 × 500 feature graphic and 2–8 screenshots (no side more than twice the other, so crop the phone's 2354 × 1080 shots to about 2160 × 1080).
8. **What's new** for this release (1.2): *New: Just Doodles PLUS, a one-time upgrade for grown-ups with 140 more coloring pages, more games and Learn topics, magic crayons, stamps, stickers and saving. Still no ads, ever.*
9. After the 14 days, apply for production access and promote the release.

Before each release, check the Play build's permissions with `aapt2 dump permissions app/build/outputs/apk/play/release/app-play-release.apk`. As of 1.2 it lists exactly `com.android.vending.BILLING`, `android.permission.ACCESS_NETWORK_STATE` and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (Android's way of keeping the app's internal messages private). **Never `android.permission.INTERNET`**; if it shows up, a library update brought it back.

## Installing on a phone or tablet

**Over USB**: enable *Developer options → USB debugging* on the device, plug it in, then:

```bash
adb install -r app/build/outputs/apk/direct/release/app-direct-release.apk
```

**Without a cable**: copy `app-direct-release.apk` to the device (Google Drive, email, USB storage...), open it in the Files app, and allow *Install unknown apps* for that app when asked.

## Adding your own coloring pages

Pages are plain SVG files in [`app/src/main/assets/pages`](app/src/main/assets/pages), one folder per group: `01-animals`, `02-sea`, and so on. Groups show up in folder-name order, and the pages inside a group in the order of the number at the start of their file name (so `91-cow` comes before `100-scarecrow`). A group's name comes from its folder name without the number (`04-things-that-go` becomes "Things that go"), and its first page is the picture on its card.

To add a page, put the SVG in a group's folder. To add a group, make a new numbered folder. File names have to be different across all folders, since each page's drawing is saved under its file name, so keep numbering them: `211-whatever.svg`, `212-...`. A group can hold any number of pages: up to 6 pictures sit three across, and longer groups switch to more across so they still fit on one screen (21 now: six across, in four rows). Likewise 10 groups sit five across, in two rows.

Pages use a simple subset of SVG: `viewBox`, `<path>`, `<circle>`, `<ellipse>`, `<rect>`, `<line>`, `<polyline>` and `<polygon>`. Colors and stroke styles are ignored (every shape becomes a black outline), and **transforms and arcs (`A` in paths) aren't supported**. For tap-to-fill to work, make the shapes closed: a gap in an outline lets the paint flood out into the neighboring area.

The existing files open fine in any browser, which is the easiest way to preview a new page.

Give each page a `<title>` (for example `<title>Fish</title>`): that's the word the app says out loud when the page is picked. A `<title>` inside a shape names that part, said when it's tapped: `<circle cx="300" cy="200" r="20"><title>Eye</title></circle>`.

## Speaking another language

The spoken words (color names, tool, pattern and sticker names, cheers) and the start menu's two labels are in [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml). To add a language, copy that file to `values-xx/strings.xml`, where `xx` is the language code (for example `values-fil` for Filipino), translate the words, and set `voice_locale` to the same code. Phones set to that language will then use the translated words with a matching voice, if the phone has one installed.

## The splash logos

The big logo is [`app/src/main/res/drawable-nodpi/just_doodles_logo.jpg`](app/src/main/res/drawable-nodpi/just_doodles_logo.jpg), the app icon artwork scaled down to 768×768 (the 1254 px original was 1.6 MB, which would have tripled the app's size). The splash shows it as a rounded sticker with a white edge, so its cream background doesn't have to match the splash's exactly. To change it, replace that file with any square picture under the same name. No code changes needed.

The small credit is [`app/src/main/res/drawable-nodpi/daveendev_logo.png`](app/src/main/res/drawable-nodpi/daveendev_logo.png) (the version with dark-gold lettering, made for light backgrounds). It's only 300×100 px, which is plenty at credit size.

## Code tour

All the code is in [`app/src/main/java/com/daveendev/justdoodles`](app/src/main/java/com/daveendev/justdoodles), plus one `PlusStore.kt` per flavor in `app/src/direct` and `app/src/play`. It uses only the plain Android framework; the one library is Google's Play Billing Library, in the `play` flavor only.

| File | What it does |
| --- | --- |
| `MainActivity.kt` | Start menu, sheet and picture pickers, the easel's buttons, sticker and pattern trays, saving and sharing, bedtime, full-screen and Back-button handling |
| `SplashView.kt` | The animated opening splash |
| `BabyView.kt` | Baby mode: blooms, fading rainbow trails, music |
| `CelebrationView.kt` | The confetti-and-stars "all done!" overlay |
| `ParentPanel.kt` | The grown-up settings card, the Drawings page, the sums that guard donating and buying, and the PLUS page |
| `Plus.kt` | Whether PLUS is on, and what's free without it (7 pages per group, which games, topics and tools) |
| `PlusStore.kt` (`src/direct`, `src/play`) | Buying, restoring and refunds through Google Play Billing (Play build), or just PLUS on (sideloaded build) |
| `LockedView.kt` | The "Ask a grown-up" card a child sees on tapping something locked |
| `SleepView.kt` | The bedtime screen: sleepy moon, stars and drifting z's |
| `Sounds.kt` | Generates every sound in code and plays it |
| `Voice.kt` | Says names out loud with the phone's text-to-speech, and picks the voice (offline voices only) |
| `DrawingView.kt` | The paper: painting, multi-touch, tap-to-fill, stamps, stickers, pattern crayon, undo |
| `PageStore.kt` | Saves and loads each page's picture |
| `Paper.kt` | The sheets of white and black paper |
| `SharedFiles.kt` | Hands one drawing at a time to the share sheet, without a storage permission |
| `ColoringPage.kt`, `SvgParser.kt` | Load the coloring groups and their outline pages from SVG |
| `Icons.kt`, `Buttons.kt` | Button and menu artwork (drawn in code) and the button views |
| `Palette.kt`, `Stamps.kt` | Colors, crayon sizes, stamp shapes |
| `Stickers.kt` | Sticker artwork, drawn in code |
| `Patterns.kt` | The pattern crayon: dots, zigzags, tracks, hearts, paw prints... |
| `Games.kt` | The list of games behind the Play card, and what a game can ask of the app (`GameHost`) |
| `PeekabooView.kt`, `FindItView.kt`, `XylophoneView.kt`, `ColorSortView.kt`, `FeedView.kt`, `TraceView.kt`, `BubblePopView.kt`, `MemoryView.kt`, `ShapeSortView.kt`, `JigsawView.kt` | One game each |
| `Critters.kt` | The hungry animals and their food, drawn in code |
| `AutoColor.kt` | Colors in a coloring page for the picture games |
| `Lessons.kt` | The Learn topics and the cards of each one |
| `LearnView.kt` | A Learn topic: look at the cards, then "Where's red?" |
| `BodyView.kt` | My body: tap a part, then "Where's the nose?" |
#   j u s t - d o o d l e s  
 