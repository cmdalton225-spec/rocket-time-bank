# Rocket — Tablet Restore + Integration Brief

Created: 2026-11-15, at Matt's reminder: "we have to get this onto his tablet with everything we were going to build."
Deadline: installed and live-tested on Asher's Samsung Galaxy Tab A11+ before Fri Nov 20, 2026.

## Base — non-negotiable

The restored original is the design source of truth: `~/workspace/rocket-app/restored-v17/Asher_Time_Bank_Final` (v17 Final, verified live byte-for-byte at https://asher-s-time-bank.web.app on 2026-11-15).

Restore first. Do NOT redesign the look, wording, or flow. The v1.0 Android rebuild is superseded as a design; only its engine pieces may be reused underneath the restored v17 design where they do not change what Asher or Matt sees.

## What v17 must keep, exactly

- Two sides: Asher's cockpit side and Dad Control on Matt's side.
- Shuttle cockpit screen; little rocket flying with flame to planets and landing.
- Astronaut comes out when the rocket stops/lands.
- Takeoff and landing sounds play automatically.
- The hanging rocket keychain in the cockpit.
- Cockpit lights, including the approval/status lights (LINKED / REQUEST / PLAYING on Dad side).
- Real bank flow: earn/save minutes → Asher picks an amount → request → Dad approves/denies → Asher plays → countdown/flight burns down → rocket returns/lands at zero.
- Dad Control: Firebase parent sign-in, PIN lock, approve/deny, add/remove minutes, clear bank, device link.

## Tablet build additions (the Nov 11 plan, still owed)

From `ROCKET-SPEC.md` — these get built ON the restored v17 design, not instead of it:

1. One-tap play collapse: after Dad approves, Asher presses the big Play button and the countdown starts by itself — Matt does nothing after approving; no separate manual timer routine.
2. Two countdowns, one truth: (a) the rocket's own big on-screen timer/flight is primary; (b) the tablet Clock timer is started via Android's set-timer request for the same minutes so the notification-bar countdown Asher watches is there too. The rocket's own countdown stays the truth if the Clock timer is dismissed/cancelled (Android cannot cancel the Clock app's timer from the app after pause/early landing).
3. Big kid buttons (~72dp targets): play/pause/clear and amount picks sized for Asher's motor ease.
4. Landing: at zero, the rocket lands — full-screen friendly landing moment, astronaut out.
5. Home-screen widget: rocket + banked minutes at a glance; tap opens the app to the request/play flow. Same bank, same state — a window, not a second app.
6. Pick-up-and-play: no laptop, no server babysitting; ordinary restarts/signal drops must not break Asher's use.

## Verification before it counts as done

- v17 code checks: JS syntax OK (checked 2026-11-15); Firebase project = asher-s-time-bank; live hosted files match the recovered source byte-for-byte.
- Still owed: live read-only load check of Asher + Dad pages; family-session flow test (sign-in, balance, request → approve → play → land) — needs the family's signed-in session/device.
- Tablet live test with Matt and Asher before Nov 20: earn → request → approve → play → Clock timer visible in the bar → land → widget shows the bank.
- Matt sees the tablet build before it is called done (restore rule).

## Build route

Brief first (this file). Route evidence (Lola's laptop check, 2026-11-15): Cursor is NOT installed on the laptop (not in any standard location, no Start Menu entry) — he cannot build until Matt installs/signs him in, which is Matt's step. Firebase CLI is also not on the laptop (node/npm are present, so it can run via npx; Firebase sign-in is Matt's account step). Mason is the proven builder but is mid-Butler-v2.5 and the Claude account was at 92% of its weekly cap (resets Tue Nov 17, 4:00 AM). Default route: Mason takes the tablet build after Butler v2.5, from the restored v17 copy + this brief, one task at a time, Kit reviews — unless Matt installs Cursor and wants him tested on it.
