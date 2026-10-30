# Publishing Just Doodles on Google Play: every screen, step by step

For a first-time publisher. It follows the order you'll meet things in. `PUBLISHING.md` is the short tick-list; this is the long one.

**Menu names:** Google moves things around. Where a menu path was checked against Google's help pages it's stated plainly. Where it wasn't, it says "(not confirmed)". The search bar at the top of Play Console finds any page by name.

**Facts you'll type again and again**

| | |
|---|---|
| App name | Just Doodles |
| Package name | `com.daveendev.justdoodles` (permanent) |
| Contact email | daveendev07@gmail.com |
| Privacy policy | the public link to `privacy-policy.html` (see "Hosting the privacy policy" at the bottom) |
| File to upload | `E:\DEVELOPER (WEB)\FREE DRAWING APP\app\build\outputs\bundle\playRelease\app-play-release.aab` |
| Version | `versionCode` 3, `versionName` 1.2 |
| In-app product | `plus_lifetime`, ₱79 |

---

## Step 0: Before you open Play Console

1. Host `privacy-policy.html` at a public web address and keep the link (bottom of this file).
2. Have these ready: a 512 × 512 icon, a 1024 × 500 feature graphic, and 2 to 8 phone screenshots. The phone's 2354 × 1080 shots need cropping to about 2160 × 1080 (no side more than twice the other).
3. Back up the `signing` folder (it has your upload key) off this PC. It isn't in git.

## Step 1: Payments profile (needed to sell PLUS)

1. Go to `play.google.com/console`, then open the page listing all your apps.
2. Left menu: **Settings** (the gear at the bottom).
3. Find **Payments profile** and fill in your details and bank account. (Not confirmed: older versions had it under "Setup".)

Without it you can't create the PLUS product. The rest of the setup doesn't depend on it, so don't get stuck here.

## Step 2: Create the app

1. On the all-apps page, click **Create app** (top right).
2. Fill in:
   - **App name:** Just Doodles
   - **Default language:** English (United States)
   - **App or game:** App
   - **Free or paid:** **Free**. PLUS is bought inside the app, so the app itself stays free. A free app can never be switched to paid.
   - **Contact email:** daveendev07@gmail.com
3. If the form asks for a package name, enter `com.daveendev.justdoodles`. If it doesn't, Play reads it from the first bundle you upload.
4. Tick the three declarations: **Developer Program Policies**, **US export laws**, and the **Play App Signing Terms of Service**. Click **Create app**.

You land on the **Dashboard**.

---

## Step 3: The Dashboard checklist ("Finish setting up your app")

The Dashboard shows a list of tasks, and the same list lives under **Monitor and improve → Policy and programs → App content**. That page has two tabs, **Needs attention** (still to do) and **Actioned** (done). A task has a **Start** or **Start declaration** button, and **Manage** once it's done. Whatever the heading says on your screen, do every item below, in this order. Several only need one click.

You can leave a form half-filled with **Save as draft** and come back.

### 3.1 Privacy policy
- App content → **Privacy policy** → **Start declaration** (or **Manage**).
- Paste the public link to your privacy policy and **Save**.
- Google needs this link on your store listing and inside the app. Because the app is for children, they check it.

### 3.2 App access (sign-in details)
- **App access** → **Start** or **Manage**.
- Choose **All functionality is available without special access**. Just Doodles has no sign-in. PLUS is an optional purchase, not a locked login, so this answer is right.
- You don't need to click **+ Add new instructions**.

### 3.3 Ads
- **Ads** → **Start declaration**.
- Choose **No, my app does not contain ads**. Save.

### 3.4 Content rating
- **Content ratings** → **Start questionnaire** (or **Start**).
- Enter your email (the one above).
- Pick the category. The choices usually include *Reference, News, or Educational*, *Utility, Productivity, Communication, or Other*, *Social Media*, *Game* and more. The closest for a drawing and Learn app is **Reference, News, or Educational** (or **Other** if Play offers nothing closer). Don't pick *Game* just because it has games; ask yourself what the app mainly is.
- Answer the questions honestly for this app: **no** to violence, blood, sexual content, nudity, bad language, drugs, gambling, and **no** to users interacting, sharing location, or buying digital goods with real money in unrestricted ways. If it asks about in-app purchases, the one PLUS purchase is behind a parent check, and the answer is whatever matches that.
- **Save**, then **Next**, then **Submit**. You get an age rating such as **3+ / Everyone**. This can't be reopened without redoing it, so read each answer once before submitting.

### 3.5 Target audience and content
- **Target audience and content** → **Start** or **Manage**.
- **Target age group:** tick **5 and under** (and only that; ticking older groups too makes the policy much stricter and makes the app look like it's aimed at adults and children both).
- **App details / store listing appeal:** answer **Yes** if it asks whether the app appeals to children. It does.
- Because children are in the audience, Google's **Families policy** applies. It means: no ads (you have none), no sending data to outside services, no advertising ID, and a privacy policy. Just Doodles already meets these.
- **Save**, then review the summary and submit.

### 3.6 Data safety
- **Data safety** → **Start**.
- **Overview:** read, **Next**.
- **Data collection and security:** the form asks whether the app **collects or shares any of the required user data types**. Choose **No**, because Just Doodles has no internet permission, no accounts and no analytics. The form then skips the long lists of data types.
- **Payments through Google Play Billing:** you don't need to declare the payment data Google Play handles itself, because the app never sees it and Google collects it directly from the user.
- **Store listing preview:** check it shows "No data collected" and "No data shared".
- **Submit** (or **Save as draft**).
- On the **Security practices** part of the form there may be an option to opt in to the **"Committed to follow the Play Families Policy"** badge. Tick it only after you've read the Families policy; it's accurate for this app.
- Internal-testing-only apps are exempt from this form, but you need it before Closed testing and Production.

### 3.7 Advertising ID
- **Advertising ID** (may be listed as **Ads → Advertising ID declaration**, or under **App content**).
- Answer **No**, the app doesn't use the advertising ID. It declares no `AD_ID` permission, and you checked this when you saw the release manifest.

### 3.8 Government apps
- **Government apps** → **Start**.
- Answer **No**, Just Doodles isn't made by or for a government.

### 3.9 Financial features
- **Financial features** → **Start**.
- Tick **My app doesn't provide any financial features** (the exact wording may differ), then **Save**. Google requires this declaration from every developer, even for test tracks.
- A one-time in-app purchase for digital content isn't a "financial feature" in this sense. That heading covers loans, banking, crypto and payments between people.

### 3.10 Health
- **Health** (may be called **Health apps declaration**) → **Start**.
- Choose that the app has **none** of the health features listed. Just Doodles isn't a medical, fitness or health-tracking app.

### 3.11 News apps
- **News apps** → **Start**.
- Choose **My app is not a news app**. Since Aug 2025 news and magazine apps must submit a declaration; apps that aren't news or magazines just say no.

### 3.12 Public health, other policy declarations
- You may also see **COVID-19 contact tracing and status apps** (answer: not a public-health-authority app), **Photo and video permissions** and **Permissions declaration form** (not needed: the app asks for no sensitive permissions, and for saving drawings it uses Android's own "save as" screen), and **Foreground services** (not used). Answer **No / Not applicable** to anything that's offered, and don't tick features the app doesn't have.
- Anything marked required that you can't make sense of: choose the honest "this doesn't apply" answer rather than guessing. The app genuinely does very little.

### 3.13 Set the app category and contact details
- Left menu **Grow users → Store presence → Store settings** (not confirmed).
- **App category:** choose **Education** (or **Art & Design**). **Tags:** pick the few that fit, like *Drawing*, *Kids*, *Education*.
- **Contact details:** the support email is required. A website and phone number are optional.

### 3.14 Main store listing
- Left menu **Grow users → Store presence → Store listings**, open the default listing.
- **App name** (30 characters max): `Just Doodles: Kids Drawing`. Google doesn't allow words like "free" or "no ads" in the name.
- **Short description** (80 characters max): `Drawing & coloring for toddlers. No ads, no internet. One-time PLUS upgrade.`
- **Full description** (4,000 characters max): paste the text under "Full description" in `README.md`.
- **Graphics:**
  - **App icon:** 512 × 512 PNG.
  - **Feature graphic:** 1024 × 500.
  - **Phone screenshots:** 2 to 8.
- **Save**. The Dashboard task for the store listing then turns green.

---

## Step 4: Upload to Internal testing

1. Left menu **Test and release → Testing → Internal testing**.
2. **Testers** tab → **Create email list**: name it, add your own Google email (and any family members who'll test), **Save changes**, then **Create**. Tick the list in the table and **Save changes**.
3. **Releases** tab → **Create new release**.
4. In **App bundles**, upload `app-play-release.aab` from the path in the table at the top.
5. If Play asks you to opt in to **Play App Signing**, say yes.
6. **Release name** is an internal label (default is fine, e.g. `3 (1.2)`). **Release notes** are what testers or users read; type between the `<en-US>` tags:
   `Welcome to Just Doodles! Drawing, coloring, games and Learn topics for little ones. No ads, no accounts, no internet. Grown-ups can unlock 140 more coloring pages, 15 more games and 15 more Learn topics with the one-time PLUS upgrade.`
7. **Next**, fix any errors it lists, **Save**, then roll out.
8. Back on the **Testers** tab, copy the **opt-in link** and open it on your phone, signed in with the tester account. Install from there.

A warning that no deobfuscation file was uploaded is optional; ignore it.

## Step 5: Create the PLUS product

Only possible after the bundle (with the billing permission) is uploaded.

1. Left menu **Monetize with Play → Products → One-time products**, then **Create one-time product**.
2. Enter:
   - **Product ID:** `plus_lifetime` (it must match the app's code, and can't be changed or reused).
   - **Name:** Just Doodles PLUS
   - **Description:** Unlock every coloring page, game and Learn topic, plus magic crayons, stamps, stickers, patterns and saving. One payment, no subscription.
3. Fill in the **Tax, compliance, and programs** section, then **Next**.
4. **Purchase option:** enter an ID such as `plus-buy`, choose **Buy**.
5. **Availability and pricing:** available in all regions by default. Set the Philippines price to **₱79** (click a region's price to edit it, or **Set prices → Bulk edit pricing**), and let Play convert the rest.
6. Click **Activate**.

## Step 6: License testers (so you can test buying without being charged)

1. Left menu **Settings → License testing**.
2. Add your Google account (an email list or Google Group), **Save changes**.
3. Install from the Internal testing link and try: buy (*always approves*), decline (*always declines*), the slow test card (PLUS turns on by itself later), cancel, uninstall and reinstall (PLUS comes back), airplane mode (PLUS stays on), and a refund with **revoke** in Order management (back to free on the next open, drawings kept).

## Step 7: Closed testing (required for new personal accounts)

A personal account created after 13 Nov 2023 needs a closed test with **at least 12 testers who stay opted in for 14 days in a row**. Anyone who opts out and back in resets that person's count.

1. Left menu **Test and release → Testing → Closed testing**, then **Manage track**.
2. **Testers** tab: **Create email list** (12 or more people with Google accounts), or choose **Google Groups** and enter `yourgroup@googlegroups.com`.
3. Add a feedback email, copy the **opt-in link**, **Save changes**, and send the link to the testers. They must tap it, tap **Become a tester**, and install the app.
4. **Releases** tab: **Create new release**, add the bundle (it can be the same one), **Next**, **Save**, roll out.
5. Wait 14 days with the testers still opted in.

## Step 8: Production

1. On the **Dashboard**, click **Apply for production**. It has three sections: about your closed test, about your app, and production readiness. Answer plainly and honestly.
2. Review usually takes up to 7 days, sometimes longer.
3. When approved: **Test and release → Production → Create new release**, upload the bundle, add release notes (for example *New: Just Doodles PLUS, a one-time upgrade for grown-ups with 140 more coloring pages, 15 more games and 15 more Learn topics, magic crayons, stamps, stickers and saving. Still no ads, ever.*), **Next**, **Save**.
4. Open **Publishing overview** (left menu) and click **Send changes for review**. The app goes live when review finishes.

**If you update the app later:** raise `versionCode` (and `versionName`) in `app/build.gradle.kts`, run `./gradlew bundlePlayRelease --console=plain </dev/null` in the Bash tool, and upload the new `.aab` as a new release.

---

## Hosting the privacy policy

Play Console only takes a link, so the file has to live on a website first.

**Easiest: Netlify Drop**
1. Copy `privacy-policy.html` into a new empty folder and rename the copy to `index.html`.
2. Go to `app.netlify.com/drop` and drag the folder onto the page. You get an address like `https://something-random.netlify.app`.
3. Open it to confirm the policy shows, copy the address, and create a free account so it isn't deleted.

**Or GitHub Pages**
1. Make a public repository (say `justdoodles-privacy`) and upload `privacy-policy.html`.
2. Repository **Settings → Pages → Build and deployment → Source: Deploy from a branch → main / (root) → Save**.
3. After a minute the address is `https://<your-username>.github.io/justdoodles-privacy/privacy-policy.html`.

The link must open in a normal browser without signing in, and stay online. The same link goes in Play Console (3.1) and ideally in the app's settings.

---

## Quick troubleshooting

- **"Needs attention" won't clear:** open the item, read the red text, and fix exactly that.
- **The product page says you need a bundle with the billing permission:** you haven't uploaded the bundle yet (Step 4), or you uploaded the `direct` build by mistake. Only `app-play-release.aab` works.
- **Upload error about the package name or signing:** you must upload the bundle built with the `play` flavor, signed with `signing/justdoodles-upload.jks`. Never regenerate that key.
- **Rejected for Families policy:** the usual causes are an ad SDK, the advertising ID, a missing privacy policy, or wrong age groups. Just Doodles has none of those; reply to the rejection email explaining that.
