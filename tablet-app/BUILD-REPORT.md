# Rocket tablet build — report

**Branch:** `cursor-tablet-build`  
**Builder:** Cursor cloud agent  
**Date:** 2026-11-15  
**Target device:** Samsung Galaxy Tab A11+ (Asher)

## Approach (judgment call #1)

The brief's **design source of truth** is v17 in `public/` (Firebase cockpit + Dad Control). The repo's v1.0 `app/` is a **native Compose rewrite**, not the v17 look/flow — so this deliverable is a **WebView shell** in `tablet-app/` that bundles a byte-identical copy of v17 web assets and adds only the Nov 11 tablet integration pieces Android must own (Clock timer, widget, full-screen landing, ~72dp kid buttons).

`public/`, `app/`, and `docs/` were **not modified**.

## Toolchain on this machine

| Tool | Present? |
|------|----------|
| Java (JDK) | **No** — `java` not found |
| Gradle (system) | **No** |
| Android SDK | **No** |

**No debug APK was built here.** No APK size or sha256 is claimed.

### Commands to produce the APK (JDK 21 + Android SDK / platform 37)

```bash
cd tablet-app
./gradlew assembleDebug
```

Optional (match v1.0 CI):

```bash
cd tablet-app
./gradlew assembleDebug lintDebug
```

Expected output: `tablet-app/app/build/outputs/apk/debug/app-debug.apk`

Sha256 after build:

```bash
sha256sum tablet-app/app/build/outputs/apk/debug/app-debug.apk
```

## Brief coverage (TABLET-RESTORE-BRIEF + ROCKET-SPEC + CURSOR-KICKOFF)

### v17 must keep (non-negotiable)

| Requirement | How covered |
|-------------|-------------|
| Asher cockpit + Dad Control | Bundled `index.html` + `dad.html`, same `styles.css`, `live-ui.js`, sync scripts, `firebase.js`; navigation via v17 sitebar inside WebView |
| Shuttle cockpit, rocket/flame/planets | Unmodified `live-ui.js` animation |
| Astronaut on land | Unmodified v17 canvas logic |
| Auto takeoff/landing sounds | Unmodified `live-ui.js` audio |
| Keychain, lamps, approval lights | Unmodified HTML/CSS |
| Bank → request → approve → play → land | Unmodified `kid-sync.js` / `dad-sync.js` + Firestore |
| Dad: Firebase sign-in, PIN, approve/deny, bank ops, device link | Unmodified `dad-sync.js` |

**Asset fidelity:** `live-ui.js`, `styles.css`, and other v17 files in `assets/www/` were checked with `diff -q` against `public/` — match except `index.html` / `dad.html` (tablet-only script tags only).

### Tablet additions (Nov 11)

| # | Requirement | Implementation |
|---|-------------|----------------|
| 1 | One-tap play after approve — Matt does nothing after approve | v17 already exposes **▶ START PLAYING** when `sessionStatus` is approved; no extra parent step. Bridge does not add a second play path. |
| 2 | Rocket countdown = truth; status-bar countdown same duration | **17.2:** `tablet-bridge.js` calls `RocketTablet.syncFlightTimer(min(bank, session))` while a flight runs and `cancelFlightTimer()` on any observed pause / stop / landing. `FlightTimer.kt` owns the timer: an ongoing "Rocket is flying" countdown notification plus an exact time's-up alarm (`FlightTimeUpReceiver`), one per flight end (`FlightTimerPlan`, 3 s tolerance), moved when Dad changes the time. Replaces 17.1's Clock-app timer (`ACTION_SET_TIMER`), which no app can stop: `ACTION_DISMISS_TIMER` only dismisses timers already ringing. |
| 3 | Big kid buttons ~72dp | `tablet-tablet.css` on child role: `#start-session`, `#pause-session`, `#end-session`, `.action-key`, `.keypad button` min-height 72px. |
| 4 | Full-screen landing at zero | When play ends with session at 0, bridge calls `showLanding()` → `LandingActivity` (v17 stencil palette). Rocket still lands in WebView. **Judgment #2:** landing title uses stencil wording `LANDED ON EARTH` (not v1.0's plain "The rocket has landed") to stay closer to v17 badge text while still giving a full-screen kid moment per spec. |
| 5 | Home widget — rocket + banked minutes, tap opens app | `RocketWidget.kt` + copied `widget_rocket.xml`; state from `WidgetState` prefs updated by bridge on every `setBank`. Same Firebase bank as WebView (not a second bank). |
| 6 | Pick-up-and-play | App runs against **Firebase asher-s-time-bank** (same as v17 hosting). No laptop or local server. Needs normal **internet** for Firebase + CDN modules (permission declared). Offline/cached edge cases are the same as v17 web on a tablet browser. |

### CURSOR-KICKOFF deliverables

| Deliverable | Status |
|-------------|--------|
| Complete Android project in `tablet-app/` | Yes |
| Debug APK + sha256 | **Not built** (no JDK/SDK here) |
| `BUILD-REPORT.md` | This file |

## What was tested here

| Test | How | Result |
|------|-----|--------|
| `tablet-bridge.js` syntax | `node --check` | Pass |
| `live-ui.js` syntax | `node --check` | Pass |
| v17 JS/CSS copy vs `public/` | `diff -q` on `live-ui.js`, `styles.css` | Identical |
| Gradle assembleDebug | Not run | N/A — no Java |
| Firebase sign-in / family flow | Not run | Needs family device + internet |
| Tab A11+ Clock skip-UI / widget / landing | Not run | Needs physical tablet |

## Judgment calls & conflicts

1. **WebView wrapper vs reusing v1.0 Compose UI** — Brief: restore v17 design; v1.0 UI is superseded for design. Chose WebView + bridge. Reused v1.0 **Clock timer**, **widget layout**, **debug keystore pattern** only.
2. **Landing copy** — ROCKET-SPEC mentions "The rocket has landed"; v17 uses cockpit/badge language. Used `LANDED ON EARTH` + subtitle referencing astronaut; full-screen is native Compose, not a redesign of the cockpit.
3. **72dp on entire keypad** — Makes BANK/SPEND/amount keys meet motor-ease spec; layout may feel dense on small widths — only `tablet-tablet.css` in assets (not `public/styles.css`).
4. **`applicationId` `com.cmdalton.rocket.tablet`** — Separate from v1.0 `com.cmdalton.rocket` so both can install for comparison; same debug signing key for org consistency.
5. **Landing detection** — Fires when play stops, session seconds and play clock are 0, and `sessionStatus` is `idle` (bank may still hold earned minutes after a SPEND session). Matches "play time at zero" not "empty bank."
6. **No v1.0 FlightService** — Countdown truth lives in v17 Firebase + animation; shell does not duplicate a second countdown service. **Gap:** landing overlay may not appear if WebView is killed mid-flight before bridge fires; resume behavior follows v17 resume fix in `live-ui.js`.
7. **INTERNET permission** — Required for v17 Firebase CDN; conflicts with v1.0 README's "no internet" line — **v17 wins** per restore-first rule.

## Known limits (honest)

- 17.2: the Clock app is no longer used (Android gives apps no way to stop a running Clock timer); the app's own countdown notification + alarm is cancelled on pause / stop / landing. Needs notifications allowed; a toast says so if they're off.
- Samsung Clock may ignore `EXTRA_SKIP_UI` and flash its UI — same as v1.0 README.
- Dad Control in the same app uses parent Google sign-in in WebView (may need Chrome custom tab / account chooser behavior tested on device).
- ES modules load from `gstatic.com`; WebView must allow network (tested on device still owed).
- Live family session, widget on home screen, and Matt sign-off before Nov 20 — **still owed** (per brief).

## File map

| Path | Role |
|------|------|
| `app/src/main/assets/www/` | v17 bundle + `tablet-bridge.js` + `tablet-tablet.css` |
| `app/src/main/java/.../ui/MainActivity.kt` | WebView host |
| `app/src/main/java/.../RocketBridge.kt` | JS ↔ Android |
| `app/src/main/java/.../ClockTimer.kt` | `ACTION_SET_TIMER` |
| `app/src/main/java/.../RocketWidget.kt` | Home widget |
| `app/src/main/java/.../ui/LandingActivity.kt` | Full-screen land |

## Next steps for Matt

1. Run `./gradlew assembleDebug` on a machine with JDK 21 + SDK 37.
2. Install APK on Tab A11+, link child device in Dad Control.
3. Place widget; run brief checklist: earn → request → approve → Play once → Clock in notification bar → land → widget balance.
4. Review landing wording and 72dp layout; tweak `strings.xml` / `tablet-tablet.css` only in `tablet-app/` if needed.
## 17.2 fix round (Nov 20, Matt's live test on the Tab A11+)

- **Countdown skipping / bank draining ~5x (video B):** `www/kid-sync.js` measured play time as the tablet's
  `Date.now()` minus Firestore's server `lastChargedAt`. The tablet clock runs ~5 s ahead of Google's, so each
  charge saw 5 s "already elapsed" the moment it landed and charged again on the next 1 s tick: -5 s per second,
  in Firestore, not just on screen. Fix (tablet copy of kid-sync.js only; `public/` untouched): elapsed time is
  measured on the server's clock (offset learned from the tablet's own commits), the part of each interval a
  charge doesn't write rides along to the next one (so every second is charged exactly once), and the
  countdown re-renders on a 200 ms loop only when a number changes. `live-ui.js`, `dad-sync.js`,
  `firebase.js`, `styles.css` stay byte-identical to `public/`.
- **Clock timer never stopped:** see row 2 above.
- **Tests:** `web-tests/countdown.test.mjs` runs the real kid-sync.js against a fake Firestore with the
  tablet clock right, 5 s fast/slow and 1 min fast, and reopened 5 min after the last charge (27 checks; 17.1 fails them);
  `web-tests/bridge-timer.test.js` runs the real tablet-bridge.js (10 checks); `FlightTimerPlanTest` (JVM).
  Run: `node web-tests/countdown.test.mjs && node web-tests/bridge-timer.test.js && ./gradlew testDebugUnitTest`.

## 17.4 widget round (Nov 20): full countdown + Pause / Play on the widget

- **Countdown:** the widget shows the full clock: `MM:SS` under an hour, `H:MM:SS` from an hour. While a flight
  runs, it is an Android countdown `Chronometer` that ticks every second on its own. It is based so it shows
  what the cockpit's session clock shows (the cockpit rounds up, the Chronometer down: base = end + 999 ms).
  A sync from the cockpit moves it only when it is a second or more off (`WidgetPlan.base`), so there's no
  visible jump. Paused = frozen time left with a Play button; ready to play = the session with Play; idle = the
  bank in the same format.
- **Pause / Play:** the round button runs the cockpit's own session transaction (`SessionRules.kt`, a port of
  `updateSession` in kid-sync.js) on Firestore through its REST API (`FirestoreSession.kt`). That means the
  same fields, the same `firestore.rules`, and lastChargedAt / updatedAt set to the server's request time.
  Flown time is measured on the server's clock (the batchGet readTime), never the tablet's. It signs in as the
  cockpit's own anonymous, Dad-approved child account: the cockpit page hands its Firebase refresh token over
  (`tablet-bridge.js` → `RocketBridge.onFirebaseAuth`, cockpit page only, never Dad's account), and it's kept in
  no-backup app storage. After the commit, the widget shows what Firestore holds and the flight timer follows:
  Pause cancels the countdown notification + time's-up alarm, Play sets them for exactly the time left. An open
  cockpit sees the change through its own Firestore listener. No widget-only state.
- **Widget truth with the app closed:** the widget re-reads the bank from Firestore on its update / flight tick
  (Kit's open widget-refresh thread).
- **First use:** the cockpit must be opened once on 17.4 so it can hand over the sign-in; until then the
  button says so.
- **Tests:** JVM: `WidgetSessionTest` (7; the real REST client against `FakeFirestore`, a local server
  enforcing a port of firestore.rules, with the tablet clock 5 s fast), `WidgetActionsTest` (4: the timer
  follows), `WidgetPlanTest` (9: the format; a Chronometer vs the cockpit every 100 ms over two minutes; keep
  within a second), `WidgetStateTest` (4), `FlightTimerPlanTest` (5). Node: `countdown.test.mjs` (35, including
  widget Pause / Play while the cockpit is open), `bridge-timer.test.js` (14, including the sign-in handover).

### 17.5 (Kit's review of 6825319)

- **Stale widget reads:** all widget Firestore work runs on one queue, in order. A background read is dropped
  if anything newer happened after it started (a widget Pause / Play, or a push from the open cockpit), so it
  can't undo a Pause or re-arm / cancel the flight timer.
- **The cockpit's unwritten carry:** kid-sync.js publishes it the moment it changes
  (`window.asherOutstandingChanged` → `RocketBridge.onOutstanding`). That covers a charge about to commit
  (its starting lastChargedAt, carry-in, spent, and the balances it writes) and a charge that landed (its
  carry). The widget's Pause adds what is still owed (`Carry.owedFor`, the cockpit's settleCharge formula), so
  a Pause right after a late reopen's 0 s calibration charge still charges the whole gap.
- Tests: JVM 35, countdown 37, bridge 14.
