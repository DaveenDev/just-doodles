# Publishing Just Doodles on Google Play

Tick these off in order. The long version, with the reasons, is "Publishing on Google Play" in `README.md`.

**The file to upload:** `app/build/outputs/bundle/playRelease/app-play-release.aab`, from `./gradlew bundlePlayRelease --console=plain </dev/null` (run it in the Bash tool, not PowerShell). Signed with `signing/justdoodles-upload.jks`: back that folder up, and never regenerate or delete it.
**Version:** `versionCode = 3`, `versionName = "1.2"` in `app/build.gradle.kts`. Raise `versionCode` for every later upload.

## Before Play Console

- [ ] Developer account verified (fee paid, identity checked).
- [ ] Host `privacy-policy.html` (already updated for PLUS on 28 Oct) at a public address (GitHub Pages, Netlify, Vercel...). Write the URL down.
- [ ] Back up `signing/` somewhere safe, off this PC.
- [ ] Make the store pictures:
  - [ ] 512 x 512 icon (`ICON_BRIEF.md`)
  - [ ] 1024 x 500 feature graphic
  - [ ] 2 to 8 phone screenshots. The phone's 2354 x 1080 shots need cropping to about 2160 x 1080 (no side more than twice the other).

## In Play Console

- [ ] **Setup → Payments profile:** create one and link it to the developer account. Selling PLUS needs it.
- [ ] **Create app:** name "Just Doodles", language, App (not game), Free. Accept Play App Signing.
- [ ] **Internal testing:** upload the AAB and add your Google account as a tester. Play needs an upload with the billing permission before you can create a product.
- [ ] **Monetize → Products → One-time products:** create `plus_lifetime`, exactly that id (it must match `PlusStore.PRODUCT_ID` and can't be reused once deleted).
  - Name: Just Doodles PLUS
  - Description: Unlock every coloring page, game and Learn topic, plus magic crayons, stamps, stickers, patterns and saving. One payment, no subscription.
  - Price: PHP 79, let Play convert the other countries. Activate it.
- [ ] **Setup → License testing:** add your own Google account(s).
- [ ] **App content:**
  - [ ] Privacy policy URL
  - [ ] Target audience: under 5 (the Families policy applies)
  - [ ] Ads: no
  - [ ] App access: all features without login
  - [ ] Content rating questionnaire
  - [ ] Data safety: the app collects and shares nothing; declare what Google's guidance says about the Play Billing Library
- [ ] **Store listing:** paste the text from `README.md` ("Store listing"). Title `Just Doodles: Kids Drawing`. No "free" or "no ads" in the title; those go in the short description. Category: Education or Art & Design. Add the pictures from above.

## Test the billing (from the internal testing build, on the realme)

- [ ] Buy (*always approves*): everything unlocks at once.
- [ ] Decline (*always declines*): stays free.
- [ ] Slow test card: waits for payment, then PLUS turns on by itself.
- [ ] Cancel the purchase sheet: nothing happens.
- [ ] Uninstall and reinstall: PLUS comes back by itself.
- [ ] Airplane mode: PLUS stays on.
- [ ] Refund with **revoke** in Order management: back to free on the next open, drawings kept.
- [ ] Free-tier walk-through, tapping like a child: no pop-up unless something locked was tapped, and Back never leaves the app.

## Release checks

- [ ] The Play build's permissions are exactly `com.android.vending.BILLING`, `android.permission.ACCESS_NETWORK_STATE` and the app's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **Never `android.permission.INTERNET`.** Check with `aapt2 dump permissions app/build/outputs/apk/play/release/app-play-release.apk`.
- [ ] The `direct` build still has no new library or permission and everything unlocked.

## Going to production

- [ ] **Closed testing:** a new personal account needs 12 testers opted in for 14 days in a row before Production unlocks. Start this early. Play changes these numbers, so check Play Console.
- [ ] **What's new:** *New: Just Doodles PLUS, a one-time upgrade for grown-ups with 140 more coloring pages, 15 more games and 15 more Learn topics, magic crayons, stamps, stickers and saving. Still no ads, ever.*
- [ ] Apply for production access, create the Production release, **send for review**. First reviews for children's apps can take days.
