# Test evidence: Rocket Time Bank 1.0

Run on 2026-11-11 in a cloud build container (Linux, JDK 21, Android SDK platform 37,
build-tools 37.0.0, AGP 9.5.0, Gradle 9.8.2).

```bash
./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

| Step | Result |
|---|---|
| `assembleDebug` | **BUILD SUCCESSFUL** → `releases/RocketTimeBank-1.0-debug.apk` |
| `lintDebug` | **0 errors**, 11 warnings (listed below) |
| `testDebugUnitTest` | **56 tests, 56 passed, 0 failed, 0 skipped** |

Raw evidence: `docs/evidence/gradle-build.log`, `docs/evidence/test-results/*.xml` (JUnit),
`docs/evidence/lint-results-debug.txt`, and screen renders in `docs/screenshots/`.

## No emulator, so Robolectric instead

This build machine has no hardware virtualization (`/dev/kvm` is missing), so an Android emulator
can't run here. Device-level behaviour is tested with **Robolectric** (Android 16 / API 36
runtime on the JVM) on a **Galaxy Tab A11+-sized screen: 1280×800 dp landscape, hdpi,
1920×1200 px**. These tests run the real activities, Compose UI, foreground service, widget
provider and Clock-timer intent. They do **not** replace the live test on the tablet (see
"Not verified" below).

## What is covered

| Suite | Tests | Covers |
|---|---|---|
| `BankTest` | 30 | Bank, request, approval and countdown rules: add time; request only when covered, one at a time, not mid-flight; approve moves minutes into a waiting flight; deny keeps the bank; no countdown until Play; play/pause/resume; screen-off pause + unlock resume (manual pause is not auto-resumed); Clock timer marked out of date after every pause; landing exactly at zero; Clear (cancel request / refund all before launch / refund unused mid-flight / refund frozen time while paused); recovery after the app wasn't running (time stops at the last heartbeat); the spec's full loop; countdown and minutes formatting |
| `SaveFormatTest` | 7 | Every state survives save/load; empty or broken save starts fresh; unknown history lines are skipped; no negative balance; history cap; PIN format; salted-hash PIN check |
| `DeviceTest` (Robolectric) | 11 | Bank survives a restart; PIN stored only as a hash; `ACTION_SET_TIMER` carries the exact seconds + `EXTRA_SKIP_UI` + new-task flag; **missing Clock app handled gracefully**; widget shows the same bank (banked → ready → flying minutes) and a tap opens the app; service goes foreground, lands at zero, opens the landing page and posts a full-screen-intent notification; screen-off broadcast pauses, unlock resumes with a fresh Clock timer for the time left; **full loop through the real screens** (set PIN twice → +30 → pick 25 → ask → wrong PIN refused → approve → Play starts both the countdown and the Clock timer (1500 s, skip-UI) and the flight service → pause/resume → land → "The rocket has landed" → OK → 5 min left); Clear mid-flight asks first then refunds; landing page returns to the rocket; no INTERNET permission |
| `ScreenshotTest` (Robolectric, native graphics) | 8 | Renders each screen at tablet size to `docs/screenshots/` (render only, no assertions) |

### Every test case

| Suite | Test | Result |
|---|---|---|
| BankTest | `tickRecordsAHeartbeatWhileFlying` | pass |
| BankTest | `playStartsTheCountdown` | pass |
| BankTest | `approveWithNothingPendingDoesNothing` | pass |
| BankTest | `requestNeedsEnoughInTheBank` | pass |
| BankTest | `countdownFormat` | pass |
| BankTest | `clearBeforeLaunchRefundsEverything` | pass |
| BankTest | `addTimeBanksMinutesAndLogsIt` | pass |
| BankTest | `recoverStopsTimeAtTheLastHeartbeat` | pass |
| BankTest | `pauseFreezesTheCountdownAndPlayResumesIt` | pass |
| BankTest | `screenOffPausesAndUnlockResumes` | pass |
| BankTest | `playTwiceDoesNotRestart` | pass |
| BankTest | `clearCancelsAPendingRequest` | pass |
| BankTest | `unlockDoesNotResumeAManualPause` | pass |
| BankTest | `presetsAreMattsChoice` | pass |
| BankTest | `flightRemainingNeverNegative` | pass |
| BankTest | `recoverLandsAFlightThatHadAlreadyRunOut` | pass |
| BankTest | `clearMidFlightRefundsTheUnusedTime` | pass |
| BankTest | `denyLeavesTheBankAlone` | pass |
| BankTest | `recoverLeavesAFreshFlightAlone` | pass |
| BankTest | `tickLandsTheRocketAtZero` | pass |
| BankTest | `approvedFlightDoesNotCountDownUntilPlay` | pass |
| BankTest | `approveMovesMinutesFromBankIntoAWaitingFlight` | pass |
| BankTest | `tickDoesNothingWhilePaused` | pass |
| BankTest | `fullLoopEarnRequestApprovePlayPauseLand` | pass |
| BankTest | `onlyOneRequestAtATimeAndNotDuringAFlight` | pass |
| BankTest | `clearWhilePausedRefundsTheFrozenTime` | pass |
| BankTest | `clearWithNothingGoingOnDoesNothing` | pass |
| BankTest | `clockTimerMustBeRestartedAfterEveryPause` | pass |
| BankTest | `minutesLeftRoundsUp` | pass |
| BankTest | `addTimeIgnoresZeroAndNegative` | pass |
| DeviceTest | `pinIsSavedHashedAndChecked` | pass |
| DeviceTest | `serviceLandsTheRocketAndOpensTheLandingScreen` | pass |
| DeviceTest | `missingClockAppIsHandledGracefully` | pass |
| DeviceTest | `screenOffPausesAndUnlockResumesWithAFreshClockTimer` | pass |
| DeviceTest | `clockTimerIntentCarriesTheExactTimeAndSkipsUi` | pass |
| DeviceTest | `noInternetPermission` | pass |
| DeviceTest | `widgetShowsTheSameBank` | pass |
| DeviceTest | `fullLoopThroughTheScreens` | pass |
| DeviceTest | `landingActivityShowsAndReturnsToTheRocket` | pass |
| DeviceTest | `bankSurvivesARestart` | pass |
| DeviceTest | `clearMidFlightAsksFirstThenRefunds` | pass |
| SaveFormatTest | `everyStateRoundTrips` | pass |
| SaveFormatTest | `unknownHistoryLinesAreSkippedNotFatal` | pass |
| SaveFormatTest | `pinMustBeFourDigits` | pass |
| SaveFormatTest | `historyIsCapped` | pass |
| SaveFormatTest | `negativeBalanceIsNeverLoaded` | pass |
| SaveFormatTest | `pinIsCheckedAgainstASaltedHash` | pass |
| SaveFormatTest | `emptyOrBrokenSaveStartsFresh` | pass |
| ScreenshotTest | `grownUpSide` | pass |
| ScreenshotTest | `flying` | pass |
| ScreenshotTest | `landed` | pass |
| ScreenshotTest | `pick` | pass |
| ScreenshotTest | `pausedScreenOff` | pass |
| ScreenshotTest | `empty` | pass |
| ScreenshotTest | `ready` | pass |
| ScreenshotTest | `waiting` | pass |

## Lint warnings (0 errors)

- `ApplySharedPref` ×2 and `UseKtx` ×3: deliberate. The bank is saved with `commit()` so a change
  is on disk before the app can be killed.
- `UnusedAttribute` ×3: widget attributes for Android 12+ (`previewLayout`, `targetCellWidth/Height`),
  ignored on older Android. Harmless.
- `ObsoleteSdkInt` ×1: the `mipmap-anydpi-v26` folder. Harmless.
- `NewerVersionAvailable` ×1: the test-only `org.json` library.
- One notice that a library's own lint checks were skipped.

## APK

| | |
|---|---|
| File | `releases/RocketTimeBank-1.0-debug.apk` |
| Package / version | `com.cmdalton.rocket` 1.0 (versionCode 1), minSdk 26, targetSdk 37 |
| Size | 11,979,385 bytes |
| SHA-256 | `3deb29c3ca21b4e9723ae4bdfac8b9e42a67a629c149d0005e86e5c247f70eed` |
| Signing | APK Signature Scheme v2, the org's permanent debug key (`keystore/debug.keystore`, same as adhd-app) |
| Cert SHA-256 | `8d:df:5f:18:a7:de:80:8b:b8:c8:5b:b6:6d:a6:49:84:89:c6:81:90:a5:1f:2f:ed:a9:11:a2:b9:d6:32:88:cd` |
| Permissions | POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, USE_FULL_SCREEN_INTENT, SYSTEM_ALERT_WINDOW, SET_ALARM (+ AndroidX's internal receiver permission). **No INTERNET.** |

## Not verified here (needs the live test on the Tab A11+)

- Whether Samsung Clock honours `EXTRA_SKIP_UI`, and how it handles a second timer after a resume.
- The landing page opening over another app (needs "Appear on top"; Android's real
  background-start rules are not simulated by Robolectric).
- Real screen-off/unlock broadcasts on One UI, and the foreground service on One UI's battery
  management.
- The widget on Samsung's launcher (size, look, tap).
- Install on a Family Link-supervised account.

## Build-environment note

In this container, Maven Central (`repo.maven.apache.org` / `repo1.maven.org`) answered HTTP 429
for a few large test jars. The run above used Google's mirror of Maven Central through a
container-only Gradle init script, plus the optional `ROBOLECTRIC_REPO_URL` environment variable
read by `app/build.gradle.kts`. Neither is needed on a normal network.
