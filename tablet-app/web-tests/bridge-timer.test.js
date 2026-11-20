// Runs the REAL www/tablet-bridge.js against a model of the native flight timer
// (FlightTimerPlan's 3 s tolerance). One "device" = one native timer; a "page" = a fresh
// script context (navigation or app restart). Usage: node bridge-timer.test.js
const fs = require('fs'), vm = require('vm'), path = require('path'), assert = require('assert');
const src = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/www/tablet-bridge.js'), 'utf8');
let now = 0;
function device() {
  const d = { end: 0, scheduled: 0, cancels: 0, landings: 0, store: {} };
  d.native = {
    onBankState() {},
    syncFlightTimer(s) { if (s > 0 && (d.end <= 0 || Math.abs(d.end - (now + s * 1000)) > 3000)) { d.end = now + s * 1000; d.scheduled++; } },
    cancelFlightTimer() { d.end = 0; d.cancels++; },
    showLanding() { d.landings++; },
  };
  return d;
}
function page(d, role = 'child') {
  const timers = [];
  const ctx = { JSON, Math, Number, String,
    document: { body: { dataset: { role } } },
    setInterval: (fn) => { timers.push(fn); return timers.length; }, clearInterval: () => {} };
  ctx.window = { RocketTablet: d.native, asherLive: { setBank() {} },
    localStorage: { getItem: (k) => d.store[k] ?? null, setItem: (k, v) => { d.store[k] = String(v); }, removeItem: (k) => { delete d.store[k]; } } };
  vm.createContext(ctx); vm.runInContext(src, ctx); timers.forEach((f) => f());
  return (raw, eff = raw) => ctx.window.asherLive.setBank(raw, eff);
}
const run = (b, s) => ({ bankSeconds: b, sessionSeconds: s, running: true, sessionStatus: 'running' });
const idle = (b, s, st) => ({ bankSeconds: b, sessionSeconds: s, running: false, sessionStatus: st });
function tick(show, b, s, secs) { for (let i = 0; i < secs; i++) { now += 1000; show(run(b - i - 1, s - i - 1)); } }
let pass = 0; const ok = (c, m) => { assert.ok(c, m); pass++; console.log('  ok -', m); };

let d = device(), kid = page(d);
kid(idle(600, 300, 'approved'));
ok(d.cancels === 1 && d.end === 0, 'first observed idle state clears any stale timer');
kid(run(600, 300)); tick(kid, 600, 300, 20);
ok(d.scheduled === 1 && d.end === 300_000, 'Play: one timer, ending when the session ends; 20 re-renders add none');
now += 1000; kid(idle(579, 279, 'paused'));
ok(d.end === 0 && d.cancels === 2, 'Pause cancels the timer (Bug 1)');
now += 30_000; kid(run(579, 279));
ok(d.scheduled === 2 && d.end === now + 279_000, 'Resume sets one fresh timer for what is left');
now += 1000; page(d, 'dad')(run(578, 278)); now += 1000; kid = page(d); kid(run(577, 277));
ok(d.scheduled === 2 && d.end > 0, 'Dad Control and back mid-flight: no cancel, no second timer');
now += 1000; kid(run(576 + 600, 276 + 600));
ok(d.scheduled === 3 && d.end === now + 876_000, 'Dad adds 10 min mid-flight: the timer moves to the new end');
now += 1000; kid(idle(1175, 0, 'idle'));
ok(d.end === 0 && d.landings === 1, 'Dad stops the flight: timer cancelled');
d.end = 99; now += 1000; kid = page(d); kid(idle(1175, 0, 'idle'));
ok(d.end === 0, 'Reopening the app while idle clears a leftover timer');
// natural landing, twice (empty bank both times)
d = device(); kid = page(d);
for (let i = 0; i < 2; i++) { kid(idle(60, 60, 'approved')); kid(run(60, 60)); tick(kid, 60, 60, 59); now += 1000; kid(run(0, 0)); kid(idle(0, 0, 'idle')); }
ok(d.landings === 2 && d.end === 0 && d.scheduled === 2, 'Flight runs out: landing shows and the timer is cleared, every flight');
ok(!('rocketTablet.clockEndMs' in d.store), '17.1 Clock-timer key is cleaned up');
console.log(`bridge-timer: ${pass} passed`);
