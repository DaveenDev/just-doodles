# Just Doodles PLUS: overview

Status: plans 01 to 06 are built (2026-10-28); plan 07, Play Console and the release, is what's left. Written 2026-10-22. Read this first. The other `plus-0N-*.md` files in this folder are the work, numbered in build order.

## Goal

Add a one-time **Just Doodles PLUS** upgrade, **₱79**, to the Google Play build, bought with Google Play Billing. It should bring in a little money for future development while the app stays ad-free, asks for almost no permissions, and stays gentle with kids. No subscription, no ads, and nothing that interrupts a child.

The upgrade should feel worth it to a parent without making the free version feel incomplete.

## Settle these first

These came up when matching the brief against the app as it is today. Each plan uses the default shown so work can start. If a default changes, the plans still hold; only the noted spot changes.

| # | Question | What the brief says | What the app has | Default in these plans |
|---|---|---|---|---|
| 1 | **Price** | ₱49 in the opening line, but ₱79 in the PLUS heading and the closing line | | **Decided 2026-10-22: ₱79.** The price is set in Play Console, not in code: the app shows whatever price Play sends, already localized. So it only affects the Play product and the store text (plan 07). |
| 2 | **How many coloring pages** | "10 categories with 10 objects each", "7 of 20 objects per category" | **150 pages: 10 groups of 15** (`app/src/main/assets/pages`) | **Decided 2026-10-22: 21 pages per group, 7 free**: 210 pages, 70 free, 140 PLUS. That means drawing 60 new pages (6 per group, numbered 151 to 210). The free ones stay the first 7 by file number (cat, dog, bunny...); new pages go at the end, so they're all PLUS. Plan 03. |
| 3 | **What "3 objects per category in Play & Learn" means** | "Access up to 3 objects per category" | Play and Learn are two separate menu cards: 6 games and 6 topics. The games use pictures from 5 coloring groups; the topics have 10 to 26 cards each. | **Decided 2026-10-28: 7 whole games and 7 whole topics free, 15 more of each on PLUS** (first decided 2026-10-22 as 3 and 3). Play and Learn have 22 items each, after 12 new of each were added on 2026-10-28. Free games: Peekaboo, Where is it?, Xylophone, Colors, Feed me, Roads, Bubble pop. Free topics: Colors, Shapes, Counting, Animal sounds, ABC, My body, Food. Plan 03. |
| 4 | **Tools that are free today** | Rainbow, glitter, extra tools, stamps, stickers and saving are PLUS | All of these already exist and are free: magic crayon (rainbow, glitter, glow, mirror), 8 stamps, 12 stickers, 8 pattern crayons, Save to phone, Share | They all move to PLUS, as the brief says. Free keeps the crayon (12 colors, 3 sizes), eraser, undo, clean page, white and black paper, and tap-to-fill. Plan 04. |
| 5 | **Wording of the locked pop-up** | "tell mommy/daddy to buy it" | | **Decided 2026-10-22: "Ooh, that one's locked! Ask a grown-up."** The brief's wording could get the app rejected in Play review (see "Policy" below). Plan 02. |
| 6 | **The sideloaded APK** (`direct` flavor) | | Two flavors: `direct` (handed out by hand, has the donate page) and `play` | `direct` stays **fully unlocked, with no billing and the donate page kept**. Play Billing can't work outside Play anyway, and your own child's phone can keep running this build. Plan 01. |
| 7 | **"Save drawings"** | PLUS | Every drawing is saved inside the app automatically, and grown-ups can Save to phone or Share | In-app autosave stays free, since losing a child's drawing is never OK. **Save to phone and Share** become PLUS. Plan 04. |

## Free vs PLUS, mapped onto the app

| Area | FREE | PLUS |
|---|---|---|
| Free Draw | Crayon in 12 colors and 3 sizes, eraser, undo, clean page, white or black paper, up to 30 sheets kept | Magic crayon (rainbow, glitter, glow, mirror), stamps, stickers, pattern crayons |
| Color | All 10 groups, with the first 7 pictures of each (70 in all); tap-to-fill and crayon | All 210 pictures, plus the magic crayon and stamps on pages |
| Play | 7 complete games: Peekaboo, Where is it?, Xylophone, Colors, Feed me, Roads, Bubble pop | 15 more: Memory, Shape sorter, Jigsaw, Balloons, Drums, Funny faces, Fishing, Stars, Counting, Big and small, Shadows, Choo choo, Rub and see, Flowers, Blocks |
| Learn | 7 complete topics: Colors, Shapes, Counting, Animal sounds, ABC, Body, Food | 15 more: Sea animals, Bugs, Sky, Wild animals, Pets, Birds, Farm, Garden, Cars and trucks, Up and away, Fruits, Weather, Space, Toys, Beach |
| Grown-ups | Settings, bedtime timer, pinning, baby mode, Drawings (look and delete) | Save to phone, Share |
| Always | No ads, no tracking, no internet permission, no pop-ups that interrupt | Future pages and tools at no extra charge |

Baby mode, sounds, the voice, the bedtime timer and pinning stay free. They're about keeping kids safe and calm, not extras.

## Ground rules

From the brief, plus the app's own rules:

- **No ads, ever**, in the free version too. No analytics, no ad SDKs, and **never the INTERNET permission**.
- **No upgrade pop-ups the child didn't ask for.** The only place a child ever hears about PLUS is the locked pop-up, and only right after tapping something locked. The child never sees a price, a buy button or a store link.
- **Buying sits behind the grown-up gate:** hold the gear for 2 seconds, answer a sum, then confirm on Google Play's own purchase sheet (plan 05).
- **One payment through Google Play Billing, restored automatically** on reinstall with the same Google account. A Restore button is there as a backup (plan 06).
- **Never take away a child's drawing.** A page the child already colored stays open, even if it's in the PLUS set.

## The one exception to "no libraries"

Google Play Billing only works through Google's Play Billing Library. The old library-free route (AIDL) was removed years ago, and since 31 Aug 2026 Play only accepts new apps and updates built with **version 8 or newer**. So the **`play` flavor** adds that one library and the `com.android.vending.BILLING` permission. The library talks to the Play Store app on the phone, not to the internet, so the app still has no INTERNET permission. Plan 06 includes checking the merged manifest to confirm this. The `direct` flavor stays library-free and permission-free.

## Policy: why the pop-up wording matters

Just Doodles is aimed at children, so Google Play's **Families policy** applies. Its monetization rules cover "offers for in-app purchases", and Google rejects or removes kids' apps for overly aggressive commercial tactics. A voice telling a toddler to get their parents to buy something is the clearest example of that. EU consumer law goes further and bans telling children to buy something or to get their parents to buy it (Unfair Commercial Practices Directive, Annex I, no. 28). "Ask a grown-up", with a parental gate behind it, is the pattern well-known kids' apps use. It also covers families without a mommy and a daddy, and it matches the app's existing "For grown-ups" wording.

## Build order

1. `plus-01-plus-switch-and-flavors.md`: the one `Plus` switch every lock reads, and a `PlusStore` class per flavor. Small.
2. `plus-02-locked-items-and-popup.md`: lock badges, and what a child sees on tapping something locked.
3. `plus-03-color-play-learn-limits.md`: which coloring pages, games and Learn topics are free.
4. `plus-04-drawing-tools.md`: which tools are free, and Save/Share.
5. `plus-05-grownup-gate-and-plus-page.md`: the grown-ups' PLUS page behind the gate.
6. `plus-06-google-play-billing.md`: the actual purchase, restore and refunds.
7. `plus-07-play-console-and-release.md`: the Play product, policy forms, store text, tests and release.

Plans 02 to 05 don't need billing. They can all be tested on a sideloaded `play` build, where no billing means the free tier. Plan 06 can be built at the same time as 02 to 05 by a second session, as long as only one session edits `MainActivity.kt` at a time. This project has no git, so see the memory note on parallel sessions.

## Done when

- A fresh install from Play shows the free tier with small padlock badges, and the child never sees a pop-up they didn't trigger.
- A grown-up can buy PLUS behind the gate, and everything unlocks at once with no restart.
- After an uninstall and reinstall with the same Google account, PLUS comes back on its own.
- After a refund, the app goes back to the free tier the next time it opens, and no drawings are lost.
- The `direct` APK works exactly as it does today: everything on, plus the donate page.
- The README, the store listing and the grown-up screen no longer say "nothing to buy".
