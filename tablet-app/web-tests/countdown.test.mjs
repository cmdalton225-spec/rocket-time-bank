// Runs the REAL www/kid-sync.js in virtual time against a fake Firestore whose server clock
// differs from the tablet's, and checks Matt's Nov 20 countdown bug (video B: -5 s every
// second) is gone: the countdown steps one second at a time and Firestore charges real time.
// Usage: node countdown.test.mjs [path/to/kid-sync.js]
import fs from 'fs';
import path from 'path';
import assert from 'assert';
import { fileURLToPath } from 'url';
const here = path.dirname(fileURLToPath(import.meta.url));
const FILE = process.argv[2] || path.join(here, '../app/src/main/assets/www/kid-sync.js');

async function simulate({ skewMs, secs, rttMs = 120, mid = false, session = 1956, preElapsed: pre = 2.3 }) {
  let now = 1_800_000_000_000;                       // tablet clock
  const serverClock = () => now - skewMs;             // skewMs > 0: tablet runs fast
  const q = []; let seq = 0;
  const at = (t, fn) => { q.push({ t, fn, s: seq++ }); q.sort((a, b) => a.t - b.t || a.s - b.s); };
  const flush = () => new Promise((r) => setImmediate(r));
  const ts = (ms) => ({ toMillis: () => ms });
  const preElapsed = mid ? pre : 0;
  const doc = { bankSeconds: 3054, sessionSeconds: session, running: mid, sessionStatus: mid ? 'running' : 'approved',
    lastChargedAt: ts(serverClock() - preElapsed * 1000) };
  const listeners = [], SERVER_TS = Symbol('ts');
  const deliver = () => { const d = { ...doc }; at(now + rttMs / 2, () => listeners.forEach((l) => l(d))); };
  const shown = [];
  const fb = {
    initializeApp: () => ({}), getAuth: () => ({ currentUser: { isAnonymous: true, uid: 'kid' } }),
    onAuthStateChanged: (a, cb) => at(now + 1, () => cb({ isAnonymous: true, uid: 'kid' })), signInAnonymously: async () => {},
    getFirestore: () => ({}), collection: () => ({}), addDoc: async () => ({ id: 'r' }), serverTimestamp: () => SERVER_TS,
    doc: (db, c) => ({ kind: c === 'approvedChildren' ? 'approved' : 'bank' }),
    onSnapshot: (ref, cb) => {
      if (ref.kind === 'approved') { at(now + 1, () => cb({ exists: () => true })); return () => {}; }
      const l = (d) => cb({ exists: () => true, data: () => d }); listeners.push(l); at(now + rttMs / 2, () => l({ ...doc })); return () => {};
    },
    runTransaction: (db, fn) => new Promise((resolve) => {
      at(now + rttMs, async () => {
        const snapshot = { ...doc }; let pending = null;
        await fn({ get: async () => ({ exists: () => true, data: () => snapshot }), update: (r, u) => { pending = u; } });
        await flush();
        at(now + rttMs / 2, () => {
          if (pending) { const t = serverClock(); for (const k in pending) doc[k] = pending[k] === SERVER_TS ? ts(t) : pending[k]; deliver(); }
          at(now + rttMs / 2, resolve);
        });
      });
    }),
  };
  const el = () => ({ textContent: '', hidden: false, addEventListener() {} });
  const g = globalThis, saved = { setInterval: g.setInterval, setTimeout: g.setTimeout, now: Date.now };
  g.document = { getElementById: el };
  g.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
  g.window = g; g.__fb = fb;
  g.asherLive = { setBank: (raw, eff) => shown.push({ t: now, session: eff.sessionSeconds }), setLinked() {}, setPending() {}, toast() {} };
  Date.now = () => now;
  g.setInterval = (fn, ms) => { const tick = () => { fn(); at(now + ms, tick); }; at(now + ms, tick); return 1; };
  g.setTimeout = (fn, ms) => at(now + (ms || 0), fn);
  const src = fs.readFileSync(FILE, 'utf8')
    .replace(/^import \{firebaseConfig\}.*$/m, 'const firebaseConfig={};')
    .replace(/^import \{([^}]*)\} from '[^']+';$/gm, (m, names) => `const {${names}}=globalThis.__fb;`);
  const mod = path.join(fs.mkdtempSync(path.join(fs.realpathSync('/tmp'), 'kidsync-')), 'kid-sync.mjs');
  fs.writeFileSync(mod, src);
  try {
    await import(mod);
    const runUntil = async (tEnd) => { while (q.length && q[0].t <= tEnd) { const e = q.shift(); now = e.t; e.fn(); await flush(); await flush(); } now = tEnd; };
    const t0 = now;
    await runUntil(t0 + 3000);
    if (!mid) g.asherFirebaseSession('start');
    await runUntil(t0 + 3000 + secs * 1000);
    const changes = []; let prev = null;
    for (const s of shown) { if (prev === null || s.session !== prev) changes.push(s.session); prev = s.session; }
    const steps = changes.slice(1).map((v, i) => v - changes[i]);
    // real time played, on any one clock: since Play was pressed, or since the reopened flight's last charge
    const trueUsed = (now - t0 + (mid ? preElapsed * 1000 : -3000)) / 1000;
    return { steps, charged: session - doc.sessionSeconds, onScreen: shown.at(-1).session, trueLeft: session - trueUsed, played: trueUsed, doc };
  } finally {
    Object.assign(g, { setInterval: saved.setInterval, setTimeout: saved.setTimeout }); Date.now = saved.now;
  }
}

let pass = 0;
const CHARGE_SLACK = 6;   // charges land every 5 s; up to that much can still be riding in the carry
const ok = (c, m, extra = '') => { assert.ok(c, `${m} ${extra}`); pass++; console.log('  ok -', m); };
for (const [skewMs, label] of [[0, 'tablet clock right'], [5000, 'tablet clock 5 s fast (video B)'], [-5000, 'tablet clock 5 s slow'], [60000, 'tablet clock 1 min fast']]) {
  for (const mid of [false, true]) {
    const r = await simulate({ skewMs, secs: 60, mid, rttMs: mid ? 400 : 120 });
    const what = `${label}, ${mid ? 'app reopened mid-flight' : 'Play pressed'}`;
    ok(r.steps.every((s) => s === -1), `${what}: countdown ticks one second at a time`, JSON.stringify([...new Set(r.steps)]));
    ok(Math.abs(r.onScreen - r.trueLeft) <= 1.2, `${what}: on-screen time matches real time left`, `${r.onScreen} vs ${r.trueLeft}`);
    ok(r.charged <= r.played + 1 && r.charged >= r.played - CHARGE_SLACK, `${what}: Firestore charged the real play time`, `charged ${r.charged} of ${r.played}`);
  }
}
for (const skewMs of [0, 5000]) {
  // Kit's review: the app reopened 5 min after the last charge (Android killed the WebView mid-flight)
  const r = await simulate({ skewMs, secs: 60, mid: true, preElapsed: 300, rttMs: 400 });
  ok(r.played > 360 && r.charged >= r.played - CHARGE_SLACK && r.charged <= r.played + 1 && Math.abs(r.onScreen - r.trueLeft) <= 1.2,
    `reopened 5 min after the last charge (clock ${skewMs / 1000} s off): the whole gap is charged and shown`, `charged ${r.charged} of ${r.played}, ${r.onScreen} vs ${r.trueLeft}`);
}
const land = await simulate({ skewMs: 5000, secs: 40, session: 23 });
ok(land.doc.running === false && land.doc.sessionStatus === 'idle' && land.doc.sessionSeconds === 0 && land.doc.bankSeconds === 3054 - 23,
  'a 23 s flight on a fast-clock tablet lands with exactly 23 s taken from the bank', JSON.stringify(land.doc.bankSeconds));
console.log(`countdown: ${pass} passed`);
