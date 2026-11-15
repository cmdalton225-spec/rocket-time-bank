# Asher's Rocket — Android tablet package (v17 + Nov 11 additions)

This folder is the Samsung Galaxy Tab A11+ build. It wraps the **restored v17 Firebase cockpit** (bundled under `app/src/main/assets/www/`, copied from repo `public/`) in a thin Android shell for Clock timer, widget, landing, and ~72dp kid buttons.

The v1.0 native UI under repo `app/` is **not** used for the kid/parent screens — only engine patterns (Clock timer, widget layout).

## Build (machine with JDK 21 + Android SDK)

```bash
cd tablet-app
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Signed with the org debug keystore in `tablet-app/keystore/debug.keystore` (same cert as the v1.0 debug APK).

## Install

Copy the debug APK to the tablet, install, link the child device in Dad Control as with the web app, place the **Asher's Rocket** home-screen widget.

On first Play, choose **Clock** → Always when Android asks which timer app to use.