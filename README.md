# Rocket Time Bank

Asher's screen-time bank as one Android app (plus a home-screen widget) for his Samsung Galaxy
Tab A11+. Built to `ROCKET-SPEC.md` (v1, Lola, 2026-11-11). No ads, no accounts, no internet
permission, no networking.

**The fix the spec asks for:** collapse the steps. Matt approves once; Asher presses one big Play
button; the rocket's own countdown **and** the tablet's Clock timer both start by themselves.

## How it works

| Who | What |
|---|---|
| Asher | Sees his **Rocket bank** in minutes → taps an amount (5 / 10 / 15 / 20 / 25 / 30 min; amounts above his balance are greyed out) → **Ask for N min** |
| Matt | **Grown-ups** button → PIN → **Approve** or **Deny**. Also: **Add earned time** (+5 … +30), balance, history, tablet setup checks |
| Asher | **Play** (huge green button). The rocket's big on-screen countdown starts (the source of truth) **and** the Clock app's timer is set to the exact time via `AlarmClock.ACTION_SET_TIMER` with skip-UI. Matt does nothing more. |
| — | The rocket sinks toward Earth as time burns. **Pause** stops it. The countdown also shows in the notification bar (the rocket's own notification). |
| — | At zero the rocket **lands**: a full-screen *"The rocket has landed"* page with a big OK. Family Link stays the outer fence for hard limits. |

The **widget** shows the rocket and the banked minutes (or minutes left while flying). The whole
widget is one big tap target that opens the app. It reads the same bank as the app.

### Matt's decisions on the spec gaps (2026-11-11)

1. **PIN:** Matt sets his own 4-digit PIN the first time he opens Grown-ups (typed twice). Only a
   salted hash is stored. No PIN is built in.
2. **Amounts:** big preset buttons 5 / 10 / 15 / 20 / 25 / 30 min (the same steps for *Add earned time*).
3. **Clear:** before launch it clears the pick or cancels a pending request. During an approved or
   flying rocket it lands now (after a "Land the rocket now?" check) and the unused time goes back
   to the bank.
4. **Pause and the Clock timer:** the rocket also pauses by itself when the screen turns off, and
   resumes on unlock. On every resume a fresh Clock timer is set for the exact time left. Android
   can't pause or cancel another app's timer, so an older Clock timer may keep running. The
   rocket stays the truth.

## One-time tablet setup (~2 minutes)

1. Copy `releases/RocketTimeBank-1.0-debug.apk` to the tablet and open it to install. Allow
   "install unknown apps" for Files/My Files when asked. On a Family Link account, Matt may have
   to approve it.
2. Open **Rocket Time Bank** and allow notifications.
3. Tap **Grown-ups**, set the PIN, and add some earned time.
4. In Grown-ups → **Tablet setup**, tap **Turn on** for any item that isn't on:
   - **Appear on top**: lets the landing page open over other apps, and lets the Clock timer
     restart after unlock without opening the rocket app.
   - **Full-screen alerts** (Android 14+): the landing page when the tablet is locked.
5. Long-press the home screen → **Widgets** → **Rocket Time Bank** → place it.
6. The first time Play is pressed, if Android asks which Clock app to use, pick **Clock** → *Always*.

Usage access is **not** needed. "Burns only while playing" is done by pausing on screen-off.

## Live-test checklist (before Fri Nov 20, 2026)

- [ ] Earn: Grown-ups → +30 min → widget shows 30 min
- [ ] Request: Asher taps 25 min → Ask → "Waiting for a grown-up"
- [ ] Approve: Grown-ups → PIN → Approve → "25 min ready!"
- [ ] Play: Asher presses Play → big countdown runs **and** the Clock timer appears in the
      notification bar (note whether Samsung Clock opened its own screen or stayed hidden)
- [ ] Pause/Play with the big buttons; screen off → paused; unlock → resumes (+ fresh Clock timer)
- [ ] Clear mid-flight → unused minutes back in the bank
- [ ] Land: let a 5-minute flight run out while in another app → "The rocket has landed"

## Words

Every word on screen is in `app/src/main/res/values/strings.xml`, so wording can be changed in
one place. Fixed by the spec: "The rocket has landed". Everything else (button labels, "Rocket
bank", "Waiting for a grown-up to say OK", and so on) is plain placeholder wording for Matt to
approve or change.

## Known limits

- Built and tested without a tablet or emulator (see `TESTS.md`). The real-device behaviour of
  Samsung Clock (does it honour skip-UI, one timer or several), the over-other-apps landing page,
  and screen-off/unlock detection need the live test.
- Android has no API to pause or cancel the Clock app's timer. After a pause or Clear, the old
  Clock timer may still run and ring. The rocket's countdown is the truth.
- Without **Appear on top**, Android blocks opening screens from the background: the landing
  page then comes as a notification (full-screen when locked), and the Clock timer restarts the
  next time the rocket app is opened.
- Forgotten PIN: the only reset is Settings → Apps → Rocket Time Bank → Storage → Clear data,
  which also empties the bank.
- The bank survives restarts and app updates (same signing key). It survives an uninstall and
  reinstall only through Android's Google backup, if backup is on for the tablet.
- Changing the tablet's clock affects the countdown (wall-clock based).

## Build

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Android SDK platform 37, AGP 9.5, Gradle 9.8.2 (wrapper), JDK 21. Debug builds are signed with
the org's permanent debug key in `keystore/debug.keystore`, the same key adhd-app uses. Cert
SHA-256 `8D:DF:5F:18:A7:DE:80:8B:B8:C8:5B:B6:6D:A6:49:84:89:C6:81:90:A5:1F:2F:ED:A9:11:A2:B9:D6:32:88:CD`.
Debug only, not a release key.

Icons: `art/rocket-app-icon-approved.webp` (the approved icon) → `art/make_icons.py` → legacy,
round, adaptive (foreground + navy background) and themed-monochrome launcher icons at mdpi–xxxhdpi,
plus the in-app/widget rocket `res/drawable-nodpi/rocket.png`.

## Code map

- `bank/Bank.kt`: every rule (add, request, approve, deny, play, pause, auto-resume, clear,
  tick/land, recover). Pure Kotlin, unit-tested.
- `bank/StateJson.kt`, `bank/Pin.kt`: the save format and the PIN hash.
- `RocketRepo.kt`: the one bank on the tablet (SharedPreferences). App, service and widget share it.
- `FlightService.kt`: foreground service. Countdown, notification-bar chronometer, screen-off
  pause and unlock resume, landing.
- `ClockTimer.kt`: `ACTION_SET_TIMER` with the exact seconds and `EXTRA_SKIP_UI`. Handles a
  missing or refusing Clock app gracefully.
- `RocketWidget.kt` + `res/layout/widget_rocket.xml`: the home-screen widget.
- `ui/`: kid screen, rocket sky, PIN gate, grown-up panel, landing page.
