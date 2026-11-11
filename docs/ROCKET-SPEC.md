# Rocket App — integration spec (v1, Lola, 2026-11-11)
Target: Asher's Samsung Galaxy Tab A11+. Deadline: integrated and live-tested BEFORE Asher's birthday, Fri Nov 20, 2026 (Matt's direction, voice call 2026-11-11).

## What exists
The family's Time Bank rocket app (from Matt's Sept 2026 Python project): Asher banks earned screen time, visualized as a rocket flight; when the rocket lands on Earth, time is zero. Approved icon: ~/workspace/imagine_media/media-generation-rocket-app-icon-0-4a714927-aac0-4422-9d8d-308bc1ac8785.webp. Current real workflow: Asher checks saved time → picks an amount (e.g. 25 min) → sends request → Matt approves → Asher presses play when ready; Matt ALSO sets a separate tablet timer and Asher presses play on both — the double routine is the problem (Asher delegates to Dad because it's fewer steps, NOT a reading problem — Matt's correction).

## The integration (the fix: collapse the steps)
1. One app keeps the bank: earned minutes saved, balance carries across installs/restarts.
2. Asher picks an amount → request → Matt approves (parent side, PIN/grown-up gate).
3. On approval + Asher pressing the BIG play button, the rocket starts the countdown BY ITSELF — Matt does nothing after approving (Matt's words 2026-11-11: "I don't have to do anything anymore, just put in his time, he pushes play and it goes from there"). TWO visible countdowns, one truth: (a) the rocket's OWN big on-screen timer/flight — the primary one Asher watches (Matt: "it will bring up its own specific timer... it will have its own little thing going"); (b) the tablet's Clock timer started via Android AlarmClock ACTION_SET_TIMER with the exact minutes (skip-UI where Samsung's Clock honors it) so the notification-bar countdown he loves is also there. The rocket's internal countdown is the source of truth — a cancelled Clock timer never breaks the flight.
4. Countdown burns only while playing (usage-aware where feasible); pause exists (big button).
5. At zero: the rocket LANDS — full-screen friendly landing screen ("The rocket has landed") — light-touch ending, Family Link stays the outer fence for hard limits (no app can plug into Digital Wellbeing/Family Link limits; no API exists).
6. Kid side: BIG start/pause/clear buttons (~72dp targets) — motor ease, independence. Parent side: approve/deny, add time, see balance/history.

## NEW — home-screen WIDGET (Matt, 2026-11-11)
The rocket also ships as an Android home-screen widget Matt can place on the tablet: shows the rocket + banked minutes at a glance; tapping opens the app (request/play flow). Widget is a window, not a second app — same bank, same state. Keep it glanceable and big-tap friendly; no tiny text controls on the widget itself.

## Setup & test
~2-minute one-time setup on the Tab A11+ (install, grant usage access if used, place widget). Live test with Asher before Nov 20: full loop — earn, request, approve, play, watch the system timer in the bar, land. Icon as approved. No ads, no accounts, no internet needed.
