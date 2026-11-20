/* Tablet shell bridge: Clock timer, widget sync, landing — v17 logic unchanged. */
(function () {
  const android = window.RocketTablet;
  if (!android) return;

  let clockSynced = false;
  let wasPlaying = false;
  let lastLandKey = '';

  // One Clock timer per flight, across page loads and app restarts: remember
  // the projected end time of the timer we set (it stays steady while a flight
  // runs). A flight whose end is within CLOCK_TOLERANCE_MS of it already has
  // its timer (Kit's review: Dad Control and back, or reopening the app, must
  // not start another one).
  const CLOCK_KEY = 'rocketTablet.clockEndMs';
  const CLOCK_TOLERANCE_MS = 15000;
  function clockEnd() {
    try { return Number(window.localStorage.getItem(CLOCK_KEY)) || 0; } catch (e) { return 0; }
  }
  function setClockEnd(ms) {
    try {
      if (ms) window.localStorage.setItem(CLOCK_KEY, String(ms));
      else window.localStorage.removeItem(CLOCK_KEY);
    } catch (e) { /* storage unavailable: page-local sync still applies */ }
  }

  function parseClockDigits(el) {
    if (!el) return 0;
    const parts = el.textContent.trim().split(':').map((n) => parseInt(n, 10) || 0);
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    return 0;
  }

  function pushState(raw, eff) {
    const bank = Math.max(0, Number(eff?.bankSeconds ?? raw?.bankSeconds) || 0);
    const session = Math.max(0, Number(eff?.sessionSeconds ?? raw?.sessionSeconds) || 0);
    const playing = !!raw?.running;
    // A new flight: forget the last landing, so an empty-bank landing
    // (always "0:0:idle") shows again next time (Kit's review).
    if (playing && !wasPlaying) lastLandKey = '';
    const payload = JSON.stringify({
      bankSeconds: bank,
      sessionSeconds: session,
      running: playing,
      sessionStatus: raw?.sessionStatus || 'idle',
    });
    android.onBankState(payload);

    const playSecs = parseClockDigits(document.getElementById('session-clock'));
    const secondsLeft = playSecs > 0 ? playSecs : session;

    if (playing) {
      if (!clockSynced && secondsLeft > 0) {
        const end = Date.now() + secondsLeft * 1000;
        if (Math.abs(clockEnd() - end) > CLOCK_TOLERANCE_MS) {
          android.startClockTimer(secondsLeft);
          setClockEnd(end);
        }
        clockSynced = true;
      }
    } else {
      // Only an OBSERVED stop/pause resets sync — never the request for one
      // (a refresh during a slow Pause still says running; Kit's review).
      clockSynced = false;
      if (wasPlaying) setClockEnd(0);
    }

    const playDone = session <= 0 && playSecs <= 0;
    const landed = wasPlaying && !playing && playDone && (raw?.sessionStatus || 'idle') === 'idle';
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

  // (No wrapper around asherFirebaseSession: sync follows the observed running
  // state above, so neither a fast Start nor a slow Pause can double a timer.)
})();