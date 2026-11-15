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
| 2 | Rocket countdown = truth; Clock timer same duration | `tablet-bridge.js` hooks `asherLive.setBank` and calls `RocketTablet.startClockTimer(seconds)` on play using session-clock / `sessionSeconds`. `ClockTimer.kt` uses `AlarmClock.ACTION_SET_TIMER` + `EXTRA_SKIP_UI`. Toast if Clock refuses (`R.string.clock_failed`). Resets clock sync flag on pause / new start so resume can set a fresh timer (Android cannot cancel Clock — documented below). |
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

- Clock timer cannot be paused/cancelled from the app (Android limitation); rocket + Firestore session remain truth.
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