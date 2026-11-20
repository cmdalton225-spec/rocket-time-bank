/* Tablet shell bridge: flight timer, widget sync, landing — v17 logic unchanged. */
(function () {
  const android = window.RocketTablet;
  if (!android) return;

  let wasPlaying = false;
  let timerSettled = false;   // has this page told the native timer about an observed state yet?
  let lastLandKey = '';
  const KID_PAGE = document.body?.dataset?.role === 'child';

  // 17.1 kept a Clock-timer end time here; the native flight timer owns that state now.
  try { window.localStorage.removeItem('rocketTablet.clockEndMs'); } catch (e) { /* ignore */ }

  function pushState(raw, eff) {
    const bank = Math.max(0, Number(eff?.bankSeconds ?? raw?.bankSeconds) || 0);
    const session = Math.max(0, Number(eff?.sessionSeconds ?? raw?.sessionSeconds) || 0);
    // Running until Firestore says it stopped (the final charge writes running:false).
    const playing = !!raw?.running;
    // A new flight: forget the last landing, so an empty-bank landing
    // (always "0:0:idle") shows again next time (Kit's review).
    if (playing && !wasPlaying) lastLandKey = '';
    android.onBankState(JSON.stringify({
      bankSeconds: bank,
      sessionSeconds: session,
      running: playing,
      sessionStatus: raw?.sessionStatus || 'idle',
    }));

    // Status-bar countdown + time's-up alarm. The native side keeps ONE timer per flight end
    // (reopening the app, Dad Control and back, or re-renders never stack a second one) and
    // moves it when Dad adds or removes time. Any observed pause, stop or landing cancels it.
    // Only Asher's cockpit sets it: its countdown runs on the server-calibrated clock
    // (kid-sync.js); Dad Control's display can be a few seconds off on this tablet.
    if (playing && KID_PAGE) {
      android.syncFlightTimer(Math.min(bank, session));
      timerSettled = true;
    } else if (!playing && (wasPlaying || !timerSettled)) {
      android.cancelFlightTimer();
      timerSettled = true;
    }

    const landed = wasPlaying && !playing && session <= 0 && (raw?.sessionStatus || 'idle') === 'idle';
    const landKey = `${bank}:${session}:${raw?.sessionStatus}`;
    if (landed && landKey !== lastLandKey) {
      lastLandKey = landKey;
      android.showLanding();
    }
    wasPlaying = playing;
  }

  function hookLive() {
    if (!window.asherLive?.setBank) return false;
    const orig = window.asherLive.setBank.bind(window.asherLive);
    window.asherLive.setBank = function (raw, eff) {
      orig(raw, eff);
      pushState(raw, eff);
    };
    return true;
  }

  const t = setInterval(() => {
    if (hookLive()) clearInterval(t);
  }, 150);

  // Widget Pause / Play: hand the native side this cockpit's own Firebase sign-in (the anonymous,
  // Dad-approved child account), so the widget runs the same session transaction under the same
  // Firestore rules. Same module URLs as kid-sync.js, so this is kid-sync's own Firebase app + auth.
  // Cockpit only: Dad Control signs in as Dad, and that account never leaves the page.
  const FIREBASE = 'https://www.gstatic.com/firebasejs/12.4.0/';
  if (KID_PAGE && android.onFirebaseAuth) {
    Promise.all([import(FIREBASE + 'firebase-app.js'), import(FIREBASE + 'firebase-auth.js')])
      .then(([fbApp, fbAuth]) => {
        const wait = setInterval(() => {
          let app;
          try { app = fbApp.getApp('asher-child'); } catch (e) { return; }   // kid-sync.js not up yet
          clearInterval(wait);
          fbAuth.onIdTokenChanged(fbAuth.getAuth(app), (user) => {
            if (!user || !user.isAnonymous || !user.refreshToken) return;
            android.onFirebaseAuth(JSON.stringify({
              apiKey: app.options.apiKey,
              projectId: app.options.projectId,
              uid: user.uid,
              refreshToken: user.refreshToken,
            }));
          });
        }, 200);
      })
      .catch(() => { /* offline at load: the next cockpit open hands it over */ });
  }
})();
