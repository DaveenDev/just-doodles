# PLUS 05: the grown-up gate and the PLUS page

Part of the PLUS plans; start at `plus-00-overview.md`. Size: medium. Needs plan 01, and plan 06 for actual buying (the page can be built against the stub first).
Files: `ParentPanel.kt`, `MainActivity.kt` (the panel's Host), `strings.xml`, a new `app/src/play/res/values/strings.xml`.

## Goal

Grown-ups can see what PLUS adds and what it costs, buy it, and restore it. A child tapping around can't get there.

## The gate: three steps, all needed

1. **Hold the gear for 2 seconds.** This already exists (`HoldButton` with `GEAR_HOLD_MS`) and opens the grown-up settings.
2. **A sum, before the PLUS page opens.** Reuse `ParentPanel.showCheck()`, changed to `showCheck(next: () -> Unit)` so the donate page (`direct`) and the PLUS page (`play`) share it.
   - Make it harder for buying: one two-digit number (`11..19 + 3..9`) and **six** answers, so random tapping gets through about 1 time in 6. It would still have to get past step 3.
   - A wrong pick goes back to the settings, as it does today.
3. **Google Play's own purchase sheet.** It shows the price and asks for whatever the account requires (password, fingerprint, or Family Link approval).

The gate guards **buying**. Restore can't spend money, so it doesn't need its own gate, but it's on the same page, so it ends up behind the gate anyway.

## In the grown-up settings

- **`play` flavor:** a new pill under Drawings, where `direct` has the donate pill:
  - not owned: **"⭐ Just Doodles PLUS"**. No price here; the price is on the page, after the sum.
  - owned: **"⭐ PLUS is on. Thank you!"**. It opens the page, which then only shows Restore.
- **`direct` flavor:** no change. `PlusStore.available` is false, which hides the PLUS pill, and donate stays.
- **`parent_about`** currently says "no in-app purchases". Give the play flavor its own copy in `app/src/play/res/values/strings.xml`, for example: *"No ads, no tracking and no internet access. One optional upgrade, PLUS, for grown-ups to buy. Made by DaveenDev."*

## The PLUS page (play only)

Same card style as Drawings and donate: a white card, with Back in the header.

- **Title:** Just Doodles PLUS
- **One line:** *"One payment, yours to keep. No subscription, no ads, ever."*
- **What it adds**, as short lines (each one a string, so it can be translated):
  - All 210 coloring pages (140 more)
  - 7 more games and 7 more Learn topics
  - Magic crayon: rainbow, glitter, glow and mirror
  - Stamps, stickers and pattern crayons
  - Save drawings to the phone, and share them
  - New pictures and tools in future updates, at no extra cost
- **Big button:** "Get PLUS · {price}" ("Get PLUS · ₱79" in the Philippines), where the price is `PlusStore.price`: Play's own formatted price, so it's right in every country. Until the price arrives, the button is disabled and says "Loading price…".
- **Small text button:** Restore purchase.
- **A status line** under the buttons: waiting for payment, an error, or "PLUS is on" (the messages are in plan 06).
- **One line at the bottom:** *"Paid through Google Play. Future updates are included."*

## Shortcuts to the page

- **From a locked pop-up** (plan 02): holding its small grown-up button for 2 seconds opens the settings straight on the sum, then the PLUS page. The gate is the same; there are just fewer taps for the grown-up standing next to the child.
- **From Drawings** on the free tier: the Save and Share buttons show a small "PLUS" tag. Tapping one opens the sum, then the PLUS page. (The panel is already behind the gear hold.)

## Don'ts

- No mention of PLUS anywhere outside the grown-up panel, except the child's locked pop-up (plan 02).
- No countdowns, "sale" banners, reminders, or "you'll lose…" lines.
- The PLUS page and the sum never open by themselves: not on launch, not after finishing a page, not after some time has passed.

## Checks

- Short taps on the gear, the way a child taps, never open anything.
- A wrong sum answer goes back to the settings, never on to the PLUS page.
- The price shows as ₱79 on a Philippine account, and the button can't be pressed before it loads.
- After buying, the page switches to "PLUS is on" without leaving, and Back lands in an unlocked app.
- On a landscape phone (2354x1080), the page fits or scrolls, and the header's Back stays visible, the same way the settings card handles it.
