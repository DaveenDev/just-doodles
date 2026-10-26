# PLUS 07: Play Console, policy, text and release

Part of the PLUS plans; start at `plus-00-overview.md`. Size: medium, mostly forms and text. Steps 1 to 4 can be done any time; plan 06's testing needs them. The rest waits until plans 01 to 06 are done.

## Play Console

1. **Payments profile.** Selling anything needs a payments profile (a merchant account) linked to the developer account: Play Console → Setup → Payments profile. It has to exist before you can create a paid product.
2. **Create the product:** Monetize → Products → One-time products.
   - Product id: `plus_lifetime`. It must match `PlusStore.PRODUCT_ID`, and a product id can't be reused once deleted, so get it right first time.
   - Name: Just Doodles PLUS
   - Description: "Unlock every coloring page, game and Learn topic, plus magic crayons, stamps, stickers, patterns and saving. One payment, no subscription."
3. **Price:** set the PHP price to **₱79** (decided 2026-10-22). Let Play convert the other countries; round the main ones by hand if you like.
4. **License testers:** Setup → License testing. Add your Google account(s) for plan 06's tests.
5. **App content:**
   - **Target audience and content:** ages 5 and under, so the Families policy applies (it already did).
   - **Ads:** "No, my app does not contain ads".
   - **Data safety:** the app itself still collects and shares nothing. Check Google's guidance on what the Play Billing Library handles, and declare what it says (Google Play processes the purchases).
   - **In-app purchases:** the "Contains in-app purchases" label appears on the listing by itself once the product is active.
6. **Upload** a `play` AAB with `./gradlew bundlePlayRelease --console=plain </dev/null`, to Internal testing first. Run plan 06's tests, then move on to Closed testing or Production.

## Version

In `app/build.gradle.kts`: `versionCode = 3`, and `versionName = "1.2"` (or "2.0" to mark the PLUS release).

## Text to change

Everything that says "nothing to buy":

| Where | Now | Change to |
|---|---|---|
| `README.md`, first line | "No ads, no in-app purchases, no tracking, no internet." | "No ads, no tracking, no internet. One optional upgrade (PLUS) on Google Play." |
| `README.md`, store listing short description (80 characters max) | "Free drawing & coloring for toddlers. No ads, no internet, nothing to buy." | "Drawing & coloring for toddlers. No ads, no internet. One-time PLUS upgrade." (count it before pasting) |
| `README.md`, full description | "No ads, no in-app purchases…" and "It's free, made by a parent, and always will be." | Free to play, with no ads, no accounts and no internet. A one-time PLUS upgrade unlocks everything else. It's for grown-ups, behind a parent check, and kids never see ads or sales pop-ups. |
| `parent_about` (play flavor) | "…no in-app purchases…" | see plan 05 |
| `donate_message` | "free and always will be: no ads, no payments, nothing to unlock" | Still true for the `direct` build, which has everything unlocked. Leave it. |
| `README.md`: "What's in it", grown-up settings, code tour | | Add PLUS, `Plus.kt`, `PlusStore.kt` and `LockedView.kt`, and say which features are PLUS on Google Play. |
| `SENDOFF.md` | | Where PLUS stands, and how to test billing. |

- **Store listing rule:** Play doesn't allow price or promo words ("free", "no ads") in the **title**. The README already handles that. In the description, say plainly what's free and what PLUS adds, so no parent feels tricked.
- **Tell existing users.** Google recommends it when in-app purchases are added. Use the "What's new" text, for example: *"New: Just Doodles PLUS, a one-time upgrade for grown-ups. Still no ads, ever."*

## Release checklist

- [ ] The `playRelease` merged manifest adds only `com.android.vending.BILLING`, and has no INTERNET.
- [ ] `directRelease`: no new dependency, no new permission, everything unlocked, and the donate page works.
- [ ] Plan 06's eight billing tests pass on the realme, installed from the Internal testing track.
- [ ] A free-tier walk-through, tapping like a child: no pop-up unless something locked was tapped, and Back still never leaves the app.
- [ ] The bedtime timer and baby mode still work on top of locked screens and the pop-up.
- [ ] Listen to the spoken lock line with the phone's voice (and in Filipino, if a `values-fil` translation exists by then).
- [ ] The README, store text and What's new are updated.

## Ideas for "future premium content"

New groups (space, dinosaurs, home, weather, already listed in `SENDOFF.md`), a sticker pack per group, more pattern crayons, and seasonal pages. In each new group, keep the first 7 pages free and the rest PLUS, so free users get something new too.
