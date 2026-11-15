# Rocket v17 — Restore Verification

Date: 2026-11-15
Action: Restored by verification, not redeploy.

The live Firebase site https://asher-s-time-bank.web.app was compared file-by-file against the recovered v17 source (`Asher_Time_Bank_Final`, inside `Asher_Time_Bank_v17_Rocket_Resume_Fix`).

Result: every public file matched byte-for-byte.

Files verified live against the recovered v17 source:
- /index.html — MATCH — sha256 bf76fbcd2f473223ef7510576b4a33cdda9b456507b0bae9b4f706dd9c66abba
- /live-ui.js — MATCH — sha256 24ce88b7327034877aad2bba9fa9a55ad81e83c7bdffc19683118c00e7cc920f
- /styles.css — MATCH
- /dad.html — MATCH — sha256 33e810b408b45856cc8e19ac8689cb7e89a254f57d4a39da07b8612ae4dce078
- /kid-sync.js — MATCH
- /dad-sync.js — MATCH
- /firebase.js — MATCH
- /404.html — MATCH
- /assets/earth.png — MATCH
- /assets/earth-horizon.png — MATCH
- /assets/earth-foreground.png — MATCH
- /assets/metal.png — MATCH
- /assets/space.jpg — MATCH

Conclusion: the live site is already serving the exact v17 Final build. No redeploy was needed and no app files were changed. A clean canonical copy is kept here as `Asher_Time_Bank_Final`.

Not verified: signing in with the family Firebase accounts and exercising the live bank balance/request flow. That requires the family's signed-in device/session; the hosted files themselves are proven to be v17.
