# PLUS 06: Google Play Billing (play flavor only)

Part of the PLUS plans; start at `plus-00-overview.md`. Size: medium. Needs plan 01. The Play Console product setup is in plan 07, and real purchases can't be tested without it.
Files: `app/build.gradle.kts`, `app/src/play/java/com/daveendev/justdoodles/PlusStore.kt`, and `app/src/play/AndroidManifest.xml` if anything needs removing.

## Goal

Buy, unlock, restore, and re-lock after a refund, all through Google Play, for the one-time PLUS product. No server, no INTERNET permission, and nothing breaks when Play isn't there.

## Library and manifest

- In `app/build.gradle.kts`, add the library to the `play` flavor only:
  ```kotlin
  dependencies {
      "playImplementation"("com.android.billingclient:billing:<newest stable, 8.0 or later>")
  }
  ```
  - Use the plain Java artifact, not `billing-ktx`, which pulls in coroutines.
  - Look up the newest version in the Play Billing release notes when you start. **Nothing below 8:** since 31 Aug 2026, Play no longer accepts new apps or updates built with version 7. The method names below are from version 8; check the release notes if a newer one renamed anything.
- The library's manifest merges in three things; leave them alone:
  - the `com.android.vending.BILLING` permission;
  - a `<queries>` entry for the Play Store app;
  - a `com.google.android.play.billingclient.version` meta-data entry, which Play checks.
- **Check the merged manifest** (under `app/build/intermediates/merged_manifests/playRelease/`, or run `aapt2 dump permissions` on the APK):
  - there must be **no `android.permission.INTERNET`**, and nothing else unexpected;
  - if something extra shows up, remove it with `tools:node="remove"` in `app/src/play/AndroidManifest.xml`, then test a purchase again.
- R8 is on; the library ships its own keep rules. Make sure the release build can still buy.
- The `direct` flavor gets none of this. `./gradlew :app:dependencies --configuration directReleaseRuntimeClasspath` should still list nothing.

## The product

- One **one-time product** with the id `plus_lifetime`, held in a constant, `PlusStore.PRODUCT_ID`. It's never consumed.
- The price comes from Play Console (plan 07). The app never hard-codes a price; it shows the formatted price from `ProductDetails`, which Play localizes (₱ in the Philippines, the local currency elsewhere).

## How `PlusStore` (play) works

1. **`start(activity)`**, called in `onCreate`:
   - build the client: `BillingClient.newBuilder(context).setListener(::onPurchasesUpdated).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).enableAutoServiceReconnection().build()`;
   - `startConnection`; once it's set up, run `queryPurchases()` and then `queryProductDetails()`;
   - on `BILLING_UNAVAILABLE`, or with no Play Store: `state = UNAVAILABLE`, and the cached `Plus` value stays as it is.
2. **`queryPurchases()`** runs `queryPurchasesAsync` for in-app products. It's also how restore works:
   - result OK, and `plus_lifetime` is there with state `PURCHASED`: `Plus.set(true)`, and acknowledge it if it isn't acknowledged yet;
   - result OK, and it's there with state `PENDING`: `state = PENDING`, and stay locked;
   - result OK, and it's not there: `Plus.set(false)`. This is how refunds and revoked purchases re-lock;
   - any error (offline, Play busy): change nothing, and the cached value stands.
3. **`refresh()`**, called in `onResume`, runs `queryPurchases()` again. It catches a pending payment that went through, a purchase made on another device, and refunds. It's a local call to the Play Store app, so it's cheap.
4. **`queryProductDetails()`** asks for the one product, and keeps its `ProductDetails` and the one-time offer's formatted price for the PLUS page. If it fails, it tries again on the next `refresh()`.
5. **`buy(activity)`** is only reachable from the PLUS page, behind the gate (plan 05). It calls `launchBillingFlow` with the stored `ProductDetails`; if those haven't loaded yet, it queries first and then launches.
6. **`onPurchasesUpdated(result, purchases)`**:

   | Result | What happens |
   |---|---|
   | OK, state `PURCHASED` | Unlock at once with `Plus.set(true)`, then acknowledge (`acknowledgePurchase` with the purchase token). If acknowledging fails, `queryPurchases()` retries it next time. **Purchases not acknowledged within 3 days are refunded automatically**, so this matters. The PLUS page plays `Sounds.jingle()` and says thank you. |
   | OK, state `PENDING` | "Waiting for payment. PLUS turns on as soon as it goes through." |
   | `ITEM_ALREADY_OWNED` | Run `queryPurchases()`; it's really a restore. |
   | `USER_CANCELED` | Nothing. |
   | `SERVICE_UNAVAILABLE`, `NETWORK_ERROR`, `SERVICE_DISCONNECTED` | "Couldn't reach Google Play. Check the phone's internet and try again." (Buying needs the phone online; the app itself still doesn't use the internet.) |
   | `BILLING_UNAVAILABLE` | "Google Play purchases aren't available on this phone or account." |

7. **`restore(onDone)`**, for the PLUS page's Restore button: runs `queryPurchases()`, then says either "PLUS is back on!" or "No PLUS purchase found for the Google account signed in to the Play Store."
8. **`stop()`**, called in `onDestroy`: `endConnection()`.

## Restore after a reinstall

Nothing extra is needed. Play keeps one-time purchases on the Google account, so the `queryPurchases()` in `start()` turns PLUS back on during the first launch after a reinstall, with no tap needed. The Restore button covers the rare case where Play was slow or offline at that moment.

## Left out on purpose

- **No server check.** It would need internet access and a server. For a ₱79 upgrade, the local check is enough.
- **No local signature check in v1.** It could be added later with the Play Console license key, but it only stops someone editing the prefs on a rooted phone.
- **No consumables and no subscriptions.**

## Testing (needs plan 07's Play Console setup)

- Add your test Google account under **Play Console → Settings → License testing**. License testers get test payment methods and aren't charged.
- Upload a `play` AAB to **Internal testing** and install it on the realme from the tester link. That's the most reliable way. Google's test-purchase docs say whether sideloaded builds can buy too; check before relying on that for faster rounds.

**Cases:**

1. Fresh install: the free tier, and the price shows on the PLUS page.
2. Buy with "Test card, always approves": everything unlocks at once, the badges go, and Save and Share work.
3. "Test card, always declines": still free, with a friendly message.
4. "Slow test card": the waiting-for-payment message, then PLUS turns on by itself later (on resume), with no more taps.
5. Cancel Play's purchase sheet: nothing happens.
6. Uninstall and reinstall: PLUS is on at first launch. Then airplane mode and relaunch: still on, from the cache.
7. Refund in Play Console (Order management: refund, and tick **revoke**): the next time the app opens it's back on the free tier, every drawing is still there, and pages the child drew on still open.
8. A phone without the Play Store, or signed out of it: the PLUS page says it's not available, and nothing crashes.

Finally, compare the APK/AAB size before and after, and check the merged manifest once more.
