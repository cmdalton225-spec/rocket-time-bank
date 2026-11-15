/* Tablet shell bridge: Clock timer, widget sync, landing — v17 logic unchanged. */
(function () {
  const android = window.RocketTablet;
  if (!android) return;

  let clockSynced = false;
  let wasPlaying = false;
  let lastLandKey = '';

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
        android.startClockTimer(secondsLeft);
        clockSynced = true;
      }
    } else {
      clockSynced = false;
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

  const sess = setInterval(() => {
    if (!window.asherFirebaseSession) return;
    clearInterval(sess);
    const orig = window.asherFirebaseSession;
    window.asherFirebaseSession = async function (action) {
      const out = await orig(action);
      if (action === 'start') clockSynced = false;
      if (action === 'pause') clockSynced = false;
      return out;
    };
  }, 150);
})();