# Cursor — Trial Run Kickoff: Rocket Tablet Build (Cloud Agent)

You are Cursor, on trial as a builder on Matt's team, running as a **cloud agent** from this repository. Lola (the assistant who wrote this) verifies your work; Kit (Codex) reviews it. This job decides your lane.

## Read first, in this order

1. `TABLET-RESTORE-BRIEF.md` — the full job: the restored original (in `public/` and the root files of this repo) is the design source of truth; the Nov 11 tablet additions are what you build.
2. `RESTORE-VERIFICATION.md` — proof that the source in this repo matches, byte for byte, the build live today at https://asher-s-time-bank.web.app.
3. `START_HERE.txt` — the original author's own notes on the app.

## One setup step before building

The five image assets are not in this repo (the upload route used to seed it carries text only). They are live on the app's own site, byte-identical to the verified source. Download them into `public/assets/` first, keeping these exact names:

- https://asher-s-time-bank.web.app/assets/earth.png
- https://asher-s-time-bank.web.app/assets/earth-foreground.png
- https://asher-s-time-bank.web.app/assets/earth-horizon.png
- https://asher-s-time-bank.web.app/assets/metal.png
- https://asher-s-time-bank.web.app/assets/space.jpg

## The job

Build the Android tablet package for Asher's Samsung Galaxy Tab A11+ from the restored v17 design, per the brief:

- Asher's cockpit and Dad Control, matching v17's look and flow (planets, astronaut, flame, automatic takeoff/landing sounds, keychain, approval lights, bank → request → approve → play → land).
- One-tap play after approval (no separate manual timer routine).
- The rocket's own big countdown as the truth, plus the tablet Clock timer started for the same minutes.
- Big kid buttons (~72dp), full-screen landing at zero.
- Home-screen widget: rocket + banked minutes, tap to open.
- Pick-up-and-play: the finished app must not depend on any laptop or server staying up.

## Rules

- Restore first: match v17. If something in v17 and something in the tablet plan conflict, keep v17's design and note the conflict in your report — do not silently choose.
- Work in a NEW folder `tablet-app/` in this repo, on your own branch. Do not modify anything under `public/` except adding the downloaded assets.
- Commit as you go — your machine is ephemeral and uncommitted work does not survive it.
- If the Android toolchain (SDK/Gradle) is missing in your environment, build everything you can, then state exactly what's missing and the one command/step that finishes it. Do not fake a built APK.
- No scope additions. No placeholder art or filler — use the v17 assets.

## Deliverables (committed in `tablet-app/`)

1. The complete Android project.
2. A built debug APK if the toolchain allows, with its file size and sha256 in the report.
3. `BUILD-REPORT.md`: what you built, how each brief item is covered, what you tested and how, anything you could not do and why, and every place you had to make a judgment call.

## How you'll be judged

Brief followed exactly, v17's design untouched in spirit and letter, honest report (gaps stated, not papered over), and a build Kit can review without archaeology.
