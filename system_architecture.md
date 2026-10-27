# ReelScore architecture notes

## Layout
- Single `:app` module, Kotlin + Jetpack Compose, package `com.reelscore.app`.
- AGP 9 compiles Kotlin itself (no `kotlin-android` plugin in `app/`). The root `build.gradle.kts` declares KGP `apply false` only to pin the Kotlin version.
- Versions live in `gradle/libs.versions.toml`.

## Counting pipeline
`ReelCounterService` (AccessibilityService) → `RuleBasedDetector` (per app) → `GenericPagerDetector` (fallback) → `ReelTracker` (dedupe) → `ReelRepository.recordReel`.

- **Package filter:** `res/xml/reel_counter_service.xml` `packageNames` must match `DetectorRules.apps`. Android sends no events from other apps.
- **Detectors** return a `ReelSighting(pkg, fingerprint, creator)`, or null when the screen isn't a reels screen.
  - Fingerprint = creator + the first 40 characters of the longest caption-like text (`NodeText.fingerprint`).
  - The generic rule: a scrollable node at least 90% of screen width and 70% of screen height, whose visible child is at least 85% of the pager's height.
- **`ReelTracker` rules:**
  - A fingerprint change within 2 s of a scroll or window-change event starts a new reel. Without that, it's the same reel updating, and the pending reel takes the newer details.
  - A new reel is counted only if it's still pending after `MIN_WATCH_MS` (5 s). The service schedules `confirm()` on the main-thread `Handler`, and also checks that the active window still belongs to that app, because other apps send no events.
  - Skip any of the last 30 counted fingerprints. This list resets when the app changes.
  - "Navigation" means `TYPE_WINDOW_STATE_CHANGED`, or a `TYPE_VIEW_SCROLLED` whose source is at least half the screen tall. YouTube's sound and remix chip row auto-scrolls; before this filter, it kept restarting the timer and Shorts stopped counting.
- **Permanent dedupe:** `ReelRepository.recordReelOnce` inserts SHA-256(pkg + fingerprint) into `seen_reel` with `IGNORE`, and only writes a `reel_event` if that insert succeeded. A rewatched reel never counts again, and caption text is never stored.
  - Trade-off: two different reels with the same creator and the same first 40 caption characters count once.
  - DB v2 migration `MIGRATION_1_2` creates `seen_reel`. Its SQL must match `app/schemas/.../2.json`.
- **Detector rules** (`DetectorRules.kt`) are plain data so they can move to Firebase Remote Config. The Instagram and YouTube IDs are **unverified guesses**.

## Deep pause and counting hours
- `CounterSettings` (in `settings/AppSettings.kt`) holds the master switch (`enabled`) and one daily window (`scheduleOn`, `startMinute`, `endMinute`). The window can cross midnight; equal start and end means all day.
  - `AppSettings.observeCounter()` is a Flow built on a SharedPreferences listener. The flow holds the listener reference, because SharedPreferences only keeps listeners weakly.
- `ReelCounterService.applyState()`:
  - Running: restores the XML event types and starts the heartbeat.
  - Paused: sets `serviceInfo.eventTypes = 0` (Android delivers nothing), cancels the heartbeat, and drops pending confirms.
  - It's triggered by a settings change (prefs listener), the next schedule boundary (Handler), and `SCREEN_ON`/time changes (receiver). Handler delays stall in deep sleep; screen-on catches boundaries missed then.
  - `onAccessibilityEvent` also re-checks the window.
- "Turn off completely" calls `disableSelf()` through the static `instance`. Android doesn't let an app re-enable itself, so turning it back on needs Accessibility settings.
- `ReelCounterService.isEnabled()` reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, not the live bound list, which is briefly empty while Android reconnects after an update.
- **You tab:** counter card (the master switch, with a status like "On until 11:00 PM" or "Paused until 9:00 AM"), counting hours (toggle plus From/To M3 `TimePicker` dialogs), Phone rows, and debug calibration rows.
  - The Play-required disclosure lives in the ⓘ `ModalBottomSheet`. It also opens before first setup, with a "Continue to settings" button.
  - No explanatory paragraphs on screen.
- **Today** appends "· Paused" to the date while the counter is paused.

## Daily limit
- `LimitSettings` (`on`, `count`; 1 to 2,000, default 100) in `AppSettings`, observed with `observeLimit()`.
- The limit counts the whole day's reels, including those watched before it was turned on.
- After `recordReelOnce` succeeds, the service asks `LimitAlert.check(todayCount)`. It fires once per (day, limit) pair through `markLimitNotified`, so raising the limit can alert again the same day.
- The alert is a pop-up card (`res/layout/limit_popup.xml`) that the service adds as a `TYPE_ACCESSIBILITY_OVERLAY` window over the reels app. That needs no extra permission and no notifications. OK or a tap on the scrim closes it. Nothing is blocked; the tone stays neutral.

## Scroll distance
- `distance/ScrollDistance`: one reel is a quarter of the panel's physical height (`Display.mode.physicalHeight / ydpi`; the "display size" setting doesn't affect it), clamped to a 8 to 40 cm screen with a 16 cm fallback. About 4 cm per reel on the RMX3853.
- `DistanceFormat`: metric, except imperial for US, LR and MM by locale country.
- `distance/Comparisons`: 38 `Thing`s. Each has `tall` (compared by height, otherwise length), `aspect` (drawing width / height) and `art`. `compare()` always uses the smallest thing still bigger than the distance ("62% of a bus") and moves on to the next once it's passed. Past the biggest (the Moon trip), it counts multiples. Things of equal size take turns by epoch day.
- `distance/Silhouettes`: flat silhouettes drawn in code through `Sketch`, using 0..1 coordinates with the ground at y = 1. `ink` is the silhouette and `cut` is the card colour, for windows and lines. The debug-only Settings → "Silhouette gallery" shows them all to check they read; the auto-rickshaw, cricket pitch, Statue of Unity and bat were redrawn after review.
- Distance shows **only on Trends** (`DistanceSection`): Today and All time figures, a toggle, then `ScaleComparison`.
  - Tall things stand on a ground line with a lime line rising beside them; long things sit above a lime line.
  - Both share one scale fitted to the box, with dashed guides at the thing's end.
- Today shows `JourneyRings` only while a journey is active: an Overall ring (percentage, done of total) and a Today ring (today's share of the whole journey).
- With a journey, `BarChart(journeyShare = ...)` pairs each day's reels bar with a lime bar for that day's journey percentage. Each series scales to its own peak, with a legend. `ReelRepository.dailyCounts(days, notBefore)` provides the journey series.

## Journeys
- The place list is `assets/geo/{countries,states,cities}.tsv`, built by `design/build_geo.py` from GeoNames cities15000 (CC BY 4.0; credited in Settings). 244 countries, 2,764 states, 31,777 cities, about 1.1 MB. `journey/Geo` loads it once on IO.
- `JourneyScreen`: From and To groups, each Country, then City underneath. **India only** adds State between them (`STATE_COUNTRY`). Searchable full-screen `Picker` dialogs. Changing a country or state clears the choices below it. No reel estimate is shown.
  - **Different countries:** Start appears with just both countries. State and City are marked "Optional". A country-only end point is measured from its capital (`GeoData.countryPoint`; `countries.tsv` has a capital column, falling back to the biggest city) and stored as a `Place` whose `city` is the country name.
  - **Same country:** both cities are required; the card says "Pick both cities…". Identical points are blocked.
- One active journey lives in prefs (`JourneyStore`), with place display names and coordinates. Distance is a straight-line great circle at real scale. Progress is `countSince(startedAt) * metersPerReel`.
- On arrival, the service's `checkJourney()` (after each counted reel) clears the journey, inserts `completed_journey` (DB v3, for future achievements) and shows the shared `milestone_popup` overlay ("Arrived"). The same overlay is used for the daily limit.

## Calibration
- `CalibrationDumper` is turned on by a switch in Setup, debug builds only, and the setting is saved in `AppSettings`.
- It writes the screen tree (class, view ID, bounds, flags, hasText; never the text itself) to `Android/data/com.reelscore.app/files/calibration/`.
- At most one dump every 4 s, 800 nodes each, and 60 per service run.
- Dumps run on a background thread, because each node is an IPC call. On the main thread, a dump stalled event handling for seconds and broke counting.
- **YouTube (calibrated 2026-10-21):** `reel_recycler` is the pager, with one page per Short. The channel handle has no view ID; it's the `@handle` text.
- To inspect the on-device DB: `adb exec-out run-as com.reelscore.app cat databases/reelscore.db > file`. Use cmd.exe redirection; PowerShell `>` corrupts binary output.
- Pull the files with `adb pull /sdcard/Android/data/com.reelscore.app/files/calibration/`.

## Storage (Room, `reelscore.db`)
- `reel_event(pkg, creator, timestamp)`: one row per reel. Aggregation is done in SQL (`ReelDao`). Creator names never leave the phone.
- `tracking_ping(timestamp)`: written every 5 minutes while the service runs and kept for 35 days. Gaps between pings mean tracking was off; this will feed the "tracking was off" note on shared scores.

## UI
- **Navigation** (`AppNav`): the NavHost has `tabs`, `app/{pkg}`, `settings` and `help`.
  - The five tabs (today, trends, apps, friends, you) live inside the `tabs` destination in `TabHost`, with `selectedTab` held in `rememberSaveable`.
  - `TabHost` composes each tab once and keeps it. Hidden tabs are measured but not placed, so they don't draw or take touches. Unvisited tabs are composed 400 ms after launch. A switch is a GPU-only alpha and scale crossfade.
  - **Don't move tabs back into separate NavHost destinations.** Rebuilding a tab on every switch caused the lag: on a Realme RMX3853, janky frames went from 4.7% to 0.5% after this change.
  - Back on a non-Today tab returns to Today. The `FloatingNavBar` overlays everything and hides off the `tabs` route.
- **FloatingNavBar:** each tab is its own pill, driven by one `progress` float (0 = icon only, 1 = selected) that sets its width, lime fill, label fade and icon colour.
  - Target widths come from the labels measured once with `TextMeasurer`, and the row has a fixed width, so nothing tracks measured positions.
  - `progress` is only read in `layout` and draw lambdas, never in composition.
  - Don't bring back a separate sliding indicator that follows `onPlaced` bounds: it chased moving targets and wobbled.
  - `SharedScopes` is `remember`ed in `AppNav`; a fresh instance per recomposition re-ran all four tabs on every switch.
- **Performance testing:** `design/measure_tabs.ps1` taps through the tabs and prints `gfxinfo`.
  - Measure the **release** build (`assembleRelease`: R8, `profileinstaller`, then `cmd package compile -m speed-profile -f`). Debug builds are much slower and not representative.
  - The release build is signed with the debug key for local installs only; swap in an upload key before publishing.
  - **Never force-stop the app**: Android then switches off its Accessibility permission.
- **Settings page** (gear on the You tab): Accessibility (system settings; Android requires it), "Turn off completely" (`disableSelf`, with a confirmation), Help, and calibration (debug builds only).
  - **Help** is an accordion of task-based answers in `ui/settings/HelpScreen.kt` (`topics`).
- **Battery popup** (`BatteryPrompt`): `AppSettings.recordServiceStart()` flags a kill when the previous service run never reached `onDestroy`, with the same boot and the same install time.
  - When ReelScore next resumes and isn't already battery-exempt, a dialog offers Android's `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` popup, which needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  - Play may question that permission; the justification is that it's only requested after a detected kill.
- **App morph:** a `SharedTransitionLayout` wraps the NavHost. `SharedScopes` carries the shared-transition and animated-visibility scopes into screens.
  - `sharedAppContainer` (key `app-<pkg>`) links an `AppRow` to the detail header; `sharedAppIcon` (key `icon-<pkg>`) links the icons.
  - Today and Apps rows use the same keys. Only one of those screens is composed at a time, so they don't clash.
- **Theme** (`ui/theme`): fixed brand colors with no dynamic color, following the system dark/light setting.
  - Accent `Volt` `#C8F25A`. In light theme, `primary` is olive `#4A6600` for text contrast, and `primaryContainer` stays lime.
  - `LocalBrandColors` holds the nav bar colors, which stay graphite in both themes.
  - Type: Bricolage Grotesque (variable, `res/font`) in `DisplayFamily` (opsz 96) and `TitleFamily` (opsz 24); Roboto for body text. `scoreStyle(digits)` shrinks the Today number as it gains digits.
  - Shapes: 20 dp large, 12 dp small, pills for nav and chips. `groupShape()` gives grouped-list rows rounded ends and 6 dp joins.
- **Components** (`ui/components`): `RollingNumber` (per-digit `AnimatedContent`, keyed from the right), `AppIcon` (real launcher icon via PackageManager, falls back to the initial letter), `AppRow`, `BarChart` (Canvas; today's bar is the accent; bars grow with an ease-out-expo on range change).
- Screens read Flows from `ReelRepository` directly; there are no ViewModels yet.
- All wording stays neutral: counts are never called good or bad, and the comparison with yesterday only states the difference.
- `res/values(-night)/themes.xml` sets the window background to match Compose, so there's no white flash at launch.
- **Launcher icon:** adaptive icon in `mipmap-anydpi/ic_launcher.xml`.
  - Foreground: `mipmap-*/ic_launcher_foreground.png`, generated by `design/make_icon.ps1` from a square 3D render (the Canva "tilted infinity", source in `design/icon-concepts/`). The script scales the render to 72/108 of the canvas, feathers its edge into the background, and writes every density. Re-run it with `-Source <file>` to swap renders.
  - `launcher_background` (`#0D0D0F`) is the colour sampled from the render's corners.
  - Monochrome (themed icons): `drawable/ic_launcher_monochrome.xml`, a flat infinity stroke tilted -28° to match.

## Accounts and friends (phase two)
- **Firebase project** `reelscore-eb0f0` on the free **Spark** plan, owned by the personal Gmail (not the work account). Firestore in `asia-south1`, production mode. Analytics and Gemini off.
  - `app/google-services.json` is applied by the `com.google.gms.google-services` plugin, which generates `R.string.default_web_client_id`.
  - Google sign-in needs each signing key's SHA-1 in Project settings. Only the **debug** key is added. Add the Play upload key and the Play app-signing key before publishing.
  - **No Cloud Functions** (Blaze-only). All logic runs on the client; `firestore.rules` (published by pasting into the console's Rules tab) is the real access control. Edit the file and re-publish together.
- **Signing in is optional.** `account/Account.kt` handles sign-in (Credential Manager `GetSignInWithGoogleOption` → `GoogleAuthProvider` → Firebase Auth), username claim, privacy, sign-out and delete.
  - The username is claimed in one transaction (`usernames/{name}` + `users/{uid}`) and is immutable.
  - Delete reauthenticates first, then removes friends on both sides, requests, the public copy, the username and the profile, then the login.
- **Data online:**
  - `users/{uid}` holds the username, privacy (public/friends/private), `today`/`todayDate`, `week`/`weekStart`, `allTime` and `gapToday`.
  - `public/{uid}` is a copy kept only while the account is Public. It backs the Everyone leaderboard: a single-field `orderBy` needs no composite index.
  - `users/{uid}/friends/{f}` holds `{name, since}` and is written on both sides when a request is accepted.
  - `requests/{from}_{to}` holds a friend request.
  - Nothing per-reel, per-app or per-journey leaves the phone.
- **Uploads** (`sync/ScoreUpload.kt`, WorkManager):
  - Runs every 6 hours, plus once on app open, only when online.
  - Skips the upload when the totals and privacy are unchanged (a fingerprint in prefs).
  - About 2 writes per upload. This keeps Spark's 20K writes/day enough for thousands of daily users.
- **Honesty note:** `AppSettings.hadGapToday()` is true if the counter switch was off, set hours are on, or the service stopped or was killed today (`gap_day` pref). It's shown as "Counter was off for part of the day" on Today rows.
- **Leaderboards** (`account/Friends.kt`, `ui/friends/FriendsScreen.kt`, 5th tab):
  - **Friends:** you plus your friends. Your own line uses live local counts. Friends' profiles are fetched only when the friend list changes, to save reads.
  - **Everyone:** top 30 public accounts; Today and This week drop stale dates.
  - Sort "Most first" or "Fewest first", with no crowns. Private friends appear without a score.
- **Settings → Account:** username, privacy picker (`PrivacyChoice`), sign out and delete account.
- **Privacy changes** (`Account.changePrivacy` → `ScoreUpload.writePrivacy`):
  - They run in an app-wide scope, so leaving Settings doesn't stop them.
  - They're saved at once. The rules exempt a privacy-only update from the 10-second score throttle.
  - `Account.pendingPrivacy` shows the choice while it saves, and `privacyFailures` reports a failure after the choice is undone.
- **Score writes** (`ScoreUpload.write`) are serialised by a mutex. A write waits out the 11.5 s spacing on the phone rather than being refused.

## Redesign (2026-10-23)
- **4 tabs:** Home (`ui/home`), Insights (`ui/insights`, which replaces Trends and Apps), Friends and You.
- **Distance** is its own page (`ui/distance`, route `distance`). The old `DistanceSection` is gone; `ScaleComparison` remains in `DistanceSection.kt`.
- **Colours are unchanged** (the user asked to keep them). Only layout, components and motion changed.
- **Shared pieces:**
  - `ui/components/Design.kt`: `Card` (22 dp, press-scale), `enter(order)` staggered entry, `PillTabs` (black pill on white), `StatusTag`, `SectionHeader`, `Avatar`, `StatTile`, `ProgressLine`, `TileRow`.
  - `ui/components/Charts.kt`: `DayLine`, `HeatStrip`, `HourHeatmap`, `CalendarHeatmap` and `Sparkline`.
  - All motion checks `reducedMotion()`.
- **Data:** `ReelDao.hourlyCounts`, `weekdayHourCounts`, `dailyCountsForApp` and `countsByAppBetween`, exposed through `ReelRepository.todayByHour`, `weekHourGrid`, `dailyCountsForApp` and `countsByAppOn`.
- **Status bar:** a translucent backdrop in `AppNav` keeps scrolled content off the clock.
- **Jank:** `design/measure_tabs.ps1` now taps Home > Insights > Friends > You. After the redesign it measured 3.1% janky frames (it was 0.5% before), mostly first composition of chart-heavy tabs.

## Not built yet
Share cards, Remote Config rules, achievements, linking social accounts.
