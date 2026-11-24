/**
 * HeadSet Player — app.js
 *
 * Features:
 *  - Dynamic hue-shifting background (color transitions from album art via canvas ColorThief-style sampling)
 *  - Playlist from Java server (/api/tracks) + local file upload (Add Music)
 *  - Web Audio API 5-band Equalizer (Sub-Bass, Bass, Mid, Presence, Brilliance)
 *  - Tempo / Playback Rate slider
 *  - EQ presets (Flat, Bass Boost, Vocal, Rock, Pop, Jazz)
 *  - Persist settings via localStorage
 *  - MediaSession (lock screen controls, headphone buttons)
 *  - Full keyboard shortcuts
 */
(() => {
  'use strict';

  /* ======================================================= DOM refs */
  const $ = (id) => document.getElementById(id);
  const audio        = $('audio');
  const seek         = $('seek');
  const vol          = $('vol');
  const playBtn      = $('play');
  const iconPlay     = $('iconPlay');
  const iconPause    = $('iconPause');
  const shuffleBtn   = $('shuffle');
  const repeatBtn    = $('repeat');
  const muteBtn      = $('mute');
  const titleEl      = $('title');
  const artistEl     = $('artistAlbum');
  const coverEl      = $('cover');
  const fallbackEl   = $('artFallback');
  const timeElapsed  = $('timeElapsed');
  const timeDuration = $('timeDuration');
  const drawer       = $('drawer');
  const scrim        = $('scrim');
  const menuBtn      = $('menuBtn');
  const listEl       = $('list');
  const emptyEl      = $('empty');
  const searchEl     = $('search');
  const addMusicBtn  = $('addMusicBtn');
  const addMusicMenu = $('addMusicMenu');
  const fileInput    = $('fileInput');
  const refreshBtn   = $('refreshBtn');
  const eqBtn        = $('eqBtn');
  const eqPanel      = $('eqPanel');
  const eqScrim      = $('eqScrim');
  const eqCloseBtn   = $('eqCloseBtn');
  const moreBtn      = $('moreBtn');
  const moreMenu     = $('moreMenu');
  const eqMenuBtn    = $('eqMenuBtn');
  const tempoSlider  = $('tempo');
  const tempoValEl   = $('tempoVal');
  const bgGradient   = $('bgGradient');
  const repeatOneBadge = $('repeatOneBadge');

  /* ======================================================= LocalStorage helper */
  const store = {
    get(k, d) {
      try { const v = localStorage.getItem('hs:' + k); return v === null ? d : JSON.parse(v); } catch { return d; }
    },
    set(k, v) {
      try { localStorage.setItem('hs:' + k, JSON.stringify(v)); } catch { /* private mode */ }
    }
  };

  /* ======================================================= State */
  let tracks     = [];   // [{id, title, artist, cover, local?, src?}]
  let order      = [];   // play order = indices into tracks[]
  let cur        = -1;   // current index into tracks[]
  let shuffleOn  = store.get('shuffle', false);
  let repeatMode = store.get('repeat', 0);  // 0=off 1=all 2=one
  let scrubbing  = false;
  let errorStreak = 0;
  let localObjectURLs = []; // track created object URLs for cleanup

  /* ======================================================= Dynamic Color */
  /**
   * Samples dominant hue from the cover image using an offscreen canvas.
   * Falls back to smoothly cycling through hues when no cover is available.
   */
  let hueTarget  = 258;
  let hueCurrent = 258;
  let hueAnimId  = null;
  let colorCycleId = null;
  let colorCycleHue = 258;

  function sampleDominantHue(imgEl) {
    try {
      const c = document.createElement('canvas');
      c.width = 40; c.height = 40;
      const ctx = c.getContext('2d');
      ctx.drawImage(imgEl, 0, 0, 40, 40);
      const data = ctx.getImageData(0, 0, 40, 40).data;
      let totalH = 0, count = 0;
      for (let i = 0; i < data.length; i += 16) {
        const r = data[i] / 255, g = data[i+1] / 255, b = data[i+2] / 255;
        const max = Math.max(r,g,b), min = Math.min(r,g,b);
        const delta = max - min;
        if (delta < 0.15) continue; // skip near-grey pixels
        let h = 0;
        if (max === r) h = ((g - b) / delta) % 6;
        else if (max === g) h = (b - r) / delta + 2;
        else h = (r - g) / delta + 4;
        h = Math.round(h * 60);
        if (h < 0) h += 360;
        totalH += h; count++;
      }
      return count > 0 ? totalH / count : null;
    } catch { return null; }
  }

  function animateHue(target) {
    if (hueAnimId) cancelAnimationFrame(hueAnimId);
    if (colorCycleId) { clearInterval(colorCycleId); colorCycleId = null; }
    function step() {
      const diff = target - hueCurrent;
      const shortDiff = ((diff % 360) + 540) % 360 - 180; // shortest arc
      if (Math.abs(shortDiff) < 0.5) { hueCurrent = target; applyHue(); return; }
      hueCurrent += shortDiff * 0.06;
      applyHue();
      hueAnimId = requestAnimationFrame(step);
    }
    step();
  }

  function applyHue() {
    const root = document.documentElement;
    root.style.setProperty('--hue', Math.round(hueCurrent));
    // Update theme-color meta
    const meta = $('themeColor');
    if (meta) meta.setAttribute('content', `hsl(${Math.round(hueCurrent)},48%,60%)`);
  }

  function startColorCycle() {
    if (colorCycleId) return;
    // Slowly cycle hue when no cover art
    colorCycleId = setInterval(() => {
      colorCycleHue = (colorCycleHue + 0.4) % 360;
      animateHue(colorCycleHue);
    }, 80);
  }

  function onCoverLoad() {
    const hue = sampleDominantHue(coverEl);
    if (hue !== null) {
      hueTarget = hue;
      animateHue(hueTarget);
    } else {
      startColorCycle();
    }
  }

  /* ======================================================= Web Audio API Equalizer */
  let audioCtx = null;
  let sourceNode = null;
  let filters = [];
  // [freq, type, Q]
  const BANDS = [
    [60,   'lowshelf',  0.8],
    [250,  'peaking',   1.0],
    [1000, 'peaking',   1.0],
    [4000, 'peaking',   1.0],
    [12000,'highshelf', 0.8],
  ];
  const BAND_IDS = ['eq60', 'eq250', 'eq1k', 'eq4k', 'eq12k'];
  const BAND_VAL_IDS = ['val60', 'val250', 'val1k', 'val4k', 'val12k'];

  const EQ_PRESETS = {
    flat:  [0, 0, 0, 0, 0],
    bass:  [8, 5, 0, -2, -3],
    vocal: [-2, -1, 4, 5, 2],
    rock:  [5, 3, -1, 3, 5],
    pop:   [-1, 2, 4, 2, -1],
    jazz:  [4, 2, -1, 2, 4],
  };

  function initAudioContext() {
    if (audioCtx) return;
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      sourceNode = audioCtx.createMediaElementSource(audio);
      // Create filter chain
      filters = BANDS.map(([freq, type, Q]) => {
        const f = audioCtx.createBiquadFilter();
        f.type = type;
        f.frequency.value = freq;
        f.Q.value = Q;
        f.gain.value = 0;
        return f;
      });
      // Connect: source -> f0 -> f1 -> f2 -> f3 -> f4 -> destination
      let prev = sourceNode;
      for (const f of filters) { prev.connect(f); prev = f; }
      prev.connect(audioCtx.destination);

      // Restore saved EQ values
      const saved = store.get('eq', null);
      if (saved && Array.isArray(saved) && saved.length === 5) {
        saved.forEach((gain, i) => applyEqBand(i, gain));
      }
    } catch (e) {
      console.warn('Web Audio API not available:', e);
    }
  }

  function applyEqBand(bandIndex, gain) {
    if (filters[bandIndex]) filters[bandIndex].gain.value = gain;
    const slider = $(BAND_IDS[bandIndex]);
    const valEl  = $(BAND_VAL_IDS[bandIndex]);
    if (slider) {
      slider.value = gain;
      updateEqSliderFill(slider);
    }
    if (valEl) valEl.textContent = (gain >= 0 ? '+' : '') + gain.toFixed(1) + ' dB';
  }

  function updateEqSliderFill(slider) {
    // Map -15..+15 to 0..100% for the visual fill from center
    const pct = ((+slider.value + 15) / 30) * 100;
    slider.style.setProperty('--p', pct.toFixed(1) + '%');
  }

  function saveEqState() {
    const vals = BAND_IDS.map(id => +$(id).value);
    store.set('eq', vals);
  }

  // Wire up EQ band sliders
  BAND_IDS.forEach((id, i) => {
    const slider = $(id);
    const valEl  = $(BAND_VAL_IDS[i]);
    slider.addEventListener('input', () => {
      const gain = +slider.value;
      if (filters[i]) filters[i].gain.value = gain;
      valEl.textContent = (gain >= 0 ? '+' : '') + gain.toFixed(1) + ' dB';
      updateEqSliderFill(slider);
      saveEqState();
      clearPresetActive();
    });
    updateEqSliderFill(slider);
  });

  // EQ Presets
  document.querySelectorAll('.preset-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const preset = EQ_PRESETS[btn.dataset.preset];
      if (!preset) return;
      preset.forEach((gain, i) => applyEqBand(i, gain));
      saveEqState();
      document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      store.set('eqPreset', btn.dataset.preset);
    });
  });

  function clearPresetActive() {
    document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));
  }

  // Restore preset
  const savedPreset = store.get('eqPreset', 'flat');
  const presetBtnToActivate = document.querySelector(`.preset-btn[data-preset="${savedPreset}"]`);
  if (presetBtnToActivate) presetBtnToActivate.classList.add('active');

  /* ======================================================= Tempo (Playback Rate) */
  const savedTempo = store.get('tempo', 1.0);
  tempoSlider.value = savedTempo;
  audio.playbackRate = savedTempo;
  tempoValEl.textContent = savedTempo.toFixed(2).replace(/\.?0+$/, '') + '×';
  updateTempoFill();

  tempoSlider.addEventListener('input', () => {
    const rate = +tempoSlider.value;
    audio.playbackRate = rate;
    tempoValEl.textContent = rate.toFixed(2).replace(/\.?0+$/, '') + '×';
    store.set('tempo', rate);
    updateTempoFill();
  });

  function updateTempoFill() {
    // 0.5 to 2.0 -> 0% to 100%
    const pct = ((+tempoSlider.value - 0.5) / 1.5) * 100;
    tempoSlider.style.setProperty('--p', pct.toFixed(1) + '%');
  }

  /* ======================================================= Equalizer Panel UI */
  function openEq(open) {
    eqPanel.classList.toggle('open', open);
    eqScrim.classList.toggle('show', open);
    eqPanel.inert = !open;
    eqBtn.classList.toggle('on', open);
    if (open) {
      initAudioContext();
      if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
      eqCloseBtn.focus();
    } else {
      eqBtn.focus();
    }
  }

  eqBtn.addEventListener('click', () => openEq(!eqPanel.classList.contains('open')));
  eqCloseBtn.addEventListener('click', () => openEq(false));
  eqScrim.addEventListener('click', () => openEq(false));
  eqMenuBtn.addEventListener('click', () => { closeMoreMenu(); openEq(true); });

  /* ======================================================= More Menu */
  function closeMoreMenu() { moreMenu.hidden = true; }
  moreBtn.addEventListener('click', (e) => {
    moreMenu.hidden = !moreMenu.hidden;
    e.stopPropagation();
  });
  document.addEventListener('click', (e) => {
    if (!moreMenu.hidden && !moreMenu.contains(e.target) && e.target !== moreBtn) closeMoreMenu();
  });

  /* ======================================================= Add Music */
  function triggerFileInput() { fileInput.click(); }
  addMusicBtn.addEventListener('click', triggerFileInput);
  addMusicMenu.addEventListener('click', () => { closeMoreMenu(); triggerFileInput(); });

  fileInput.addEventListener('change', () => {
    const files = Array.from(fileInput.files);
    if (!files.length) return;

    const newTracks = files.map(file => {
      const url = URL.createObjectURL(file);
      localObjectURLs.push(url);
      const name = file.name.replace(/\.[^.]+$/, ''); // strip extension
      let artist = '', title = name;
      const sep = name.indexOf(' - ');
      if (sep > 0) { artist = name.substring(0, sep).trim(); title = name.substring(sep + 3).trim(); }
      return { id: 'local:' + url, title, artist, cover: false, local: true, src: url };
    });

    // Add to tracks, avoiding duplicates by src
    const existingSrcs = new Set(tracks.filter(t => t.local).map(t => t.src));
    const fresh = newTracks.filter(t => !existingSrcs.has(t.src));
    if (!fresh.length) return;

    const startIndex = tracks.length;
    tracks.push(...fresh);
    renderList();
    buildOrder();

    // Auto-play first new track if nothing is playing
    if (cur < 0) setTrack(startIndex, false);

    fileInput.value = ''; // reset for re-selection
  });

  /* ======================================================= Helpers */
  const fmt = (s) => {
    if (!isFinite(s)) return '0:00';
    s = Math.floor(s);
    return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  };
  const label = (t) => (t.artist ? `${t.artist} — ${t.title}` : t.title);

  function paintSeek() {
    const pct = seek.value / 10;  // seek is 0..1000
    seek.style.setProperty('--p', pct + '%');
  }
  function paintVol() {
    vol.style.setProperty('--p', vol.value + '%');
  }

  function buildOrder() {
    order = tracks.map((_, i) => i);
    if (!shuffleOn) return;
    for (let i = order.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [order[i], order[j]] = [order[j], order[i]];
    }
    const p = order.indexOf(cur);
    if (p > 0) { order.splice(p, 1); order.unshift(cur); }
  }

  /* ======================================================= Playback */
  function setTrack(i, autoplay) {
    if (i < 0 || i >= tracks.length) return;
    cur = i;
    const t = tracks[i];

    // Set audio source
    if (t.local && t.src) {
      audio.src = t.src;
    } else {
      audio.src = '/audio/' + t.id;
    }

    titleEl.textContent = t.title || 'Unknown';
    artistEl.textContent = t.artist || (t.local ? 'Local File' : '—');
    document.title = label(t) + ' · HeadSet';

    // Cover art
    if (!t.local && t.cover) {
      coverEl.src = '/cover/' + t.id;
      coverEl.hidden = false;
      fallbackEl.hidden = true;
      coverEl.onload = onCoverLoad;
    } else {
      coverEl.hidden = true;
      coverEl.removeAttribute('src');
      fallbackEl.hidden = false;
      startColorCycle();
    }

    seek.value = 0;
    paintSeek();
    timeElapsed.textContent = '0:00';
    timeDuration.textContent = '0:00';
    markActive();
    store.set('last', t.id);
    updateMediaSession(t);

    if (autoplay) {
      if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
      audio.play().catch(() => {/* blocked until user tap */});
    }
  }

  function step(dir, auto) {
    if (!tracks.length) return;
    let p = order.indexOf(cur) + dir;
    if (p >= order.length) {
      if (auto && repeatMode === 0) {
        audio.pause();
        setTrack(order[0], false);
        return;
      }
      p = 0;
    }
    if (p < 0) p = order.length - 1;
    setTrack(order[p], true);
  }

  function prev() {
    if (audio.currentTime > 3) audio.currentTime = 0;
    else step(-1, false);
  }

  function togglePlay() {
    if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
    if (cur < 0 && tracks.length) setTrack(0, true);
    else if (audio.paused) audio.play().catch(() => {});
    else audio.pause();
  }

  /* ======================================================= Audio events */
  audio.addEventListener('play', syncPlayIcon);
  audio.addEventListener('pause', syncPlayIcon);
  audio.addEventListener('playing', () => { errorStreak = 0; });
  audio.addEventListener('ended', () => {
    if (repeatMode === 2) { audio.currentTime = 0; audio.play().catch(() => {}); }
    else step(1, true);
  });
  audio.addEventListener('error', () => {
    if (!audio.src) return;
    errorStreak++;
    titleEl.textContent = "Can't play this file";
    artistEl.textContent = '';
    if (errorStreak < tracks.length) setTimeout(() => step(1, false), 900);
  });
  audio.addEventListener('timeupdate', () => {
    if (!scrubbing && isFinite(audio.duration) && audio.duration > 0) {
      seek.value = (audio.currentTime / audio.duration) * 1000;
      paintSeek();
    }
    timeElapsed.textContent = fmt(audio.currentTime);
    timeDuration.textContent = fmt(audio.duration);
    seek.setAttribute('aria-valuetext', `${fmt(audio.currentTime)} of ${fmt(audio.duration)}`);
    updatePosition();
  });
  audio.addEventListener('volumechange', syncVolumeIcon);

  function syncPlayIcon() {
    const playing = !audio.paused;
    iconPlay.hidden = playing;
    iconPause.hidden = !playing;
    playBtn.setAttribute('aria-label', playing ? 'Pause' : 'Play');
    document.body.classList.toggle('is-playing', playing);
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = playing ? 'playing' : 'paused';
  }

  /* ======================================================= Seek & Volume */
  seek.addEventListener('input', () => { scrubbing = true; paintSeek(); });
  seek.addEventListener('change', () => {
    if (isFinite(audio.duration)) audio.currentTime = (seek.value / 1000) * audio.duration;
    scrubbing = false;
  });

  vol.value = store.get('vol', 70);
  audio.volume = vol.value / 100;
  vol.addEventListener('input', () => {
    audio.volume = vol.value / 100;
    audio.muted = false;
    store.set('vol', +vol.value);
    paintVol();
  });
  muteBtn.addEventListener('click', () => { audio.muted = !audio.muted; });

  function syncVolumeIcon() {
    const off = audio.muted || audio.volume === 0;
    $('iconVol').hidden = off;
    $('iconMute').hidden = !off;
    muteBtn.setAttribute('aria-label', audio.muted ? 'Unmute' : 'Mute');
  }

  // iOS: hide volume slider if volume control is locked to hardware
  { const old = audio.volume; audio.volume = 0.6; if (audio.volume !== 0.6) $('volumeRow').hidden = true; audio.volume = old; }

  /* ======================================================= Shuffle / Repeat */
  function syncModes() {
    shuffleBtn.classList.toggle('on', shuffleOn);
    shuffleBtn.setAttribute('aria-pressed', shuffleOn);
    repeatBtn.classList.toggle('on', repeatMode > 0);
    repeatBtn.dataset.mode = repeatMode;
    repeatBtn.setAttribute('aria-label', 'Repeat: ' + ['off','all','one'][repeatMode]);
    repeatOneBadge.hidden = repeatMode !== 2;
  }

  shuffleBtn.addEventListener('click', () => { shuffleOn = !shuffleOn; store.set('shuffle', shuffleOn); buildOrder(); syncModes(); });
  repeatBtn.addEventListener('click', () => { repeatMode = (repeatMode + 1) % 3; store.set('repeat', repeatMode); syncModes(); });
  playBtn.addEventListener('click', togglePlay);
  $('next').addEventListener('click', () => step(1, false));
  $('prev').addEventListener('click', prev);

  /* ======================================================= Library Drawer */
  function openDrawer(open) {
    drawer.classList.toggle('open', open);
    scrim.classList.toggle('show', open);
    drawer.inert = !open;
    menuBtn.setAttribute('aria-expanded', open);
    if (open) $('closeBtn').focus();
    else menuBtn.focus();
  }

  menuBtn.addEventListener('click', () => openDrawer(true));
  $('closeBtn').addEventListener('click', () => openDrawer(false));
  scrim.addEventListener('click', () => openDrawer(false));
  refreshBtn.addEventListener('click', loadTracks);

  function renderList() {
    listEl.textContent = '';
    emptyEl.hidden = tracks.length > 0;

    tracks.forEach((t, i) => {
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'track-item';
      btn.dataset.i = i;
      btn.dataset.q = (t.artist + ' ' + t.title).toLowerCase();

      const titleSpan = document.createElement('span');
      titleSpan.className = 'track-item-title';
      titleSpan.textContent = t.title;

      if (t.local) {
        const badge = document.createElement('span');
        badge.className = 'track-badge';
        badge.textContent = 'local';
        titleSpan.appendChild(badge);
      }

      const artistSpan = document.createElement('span');
      artistSpan.className = 'track-item-artist';
      artistSpan.textContent = t.artist || (t.local ? 'Local File' : '—');

      btn.append(titleSpan, artistSpan);
      li.append(btn);
      listEl.append(li);
    });

    markActive();
  }

  function markActive() {
    listEl.querySelectorAll('.track-item').forEach(b => b.classList.toggle('active', +b.dataset.i === cur));
  }

  listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('.track-item');
    if (!btn) return;
    setTrack(+btn.dataset.i, true);
    if (matchMedia('(max-width: 700px)').matches) openDrawer(false);
  });

  searchEl.addEventListener('input', () => {
    const q = searchEl.value.toLowerCase().trim();
    listEl.querySelectorAll('.track-item').forEach(b => {
      b.parentElement.hidden = q && !b.dataset.q.includes(q);
    });
  });

  /* ======================================================= Load from Server */
  async function loadTracks() {
    try {
      const res = await fetch('/api/tracks', { cache: 'no-store' });
      if (!res.ok) throw new Error(res.status);
      const serverTracks = await res.json();

      // Merge: keep existing local tracks, replace server tracks
      const localTracks = tracks.filter(t => t.local);
      const keepId = cur >= 0 ? tracks[cur]?.id : store.get('last', null);
      const wasPlaying = cur >= 0 && !audio.paused;

      tracks = [...serverTracks, ...localTracks];
      renderList();

      if (!tracks.length) { cur = -1; titleEl.textContent = 'No songs found'; artistEl.textContent = 'Add music using + button'; buildOrder(); return; }

      const i = tracks.findIndex(t => t.id === keepId);
      if (cur >= 0 && i >= 0) cur = i;    // refresh while playing: don't interrupt
      else setTrack(i >= 0 ? i : 0, false);
      buildOrder(); markActive();
    } catch {
      // Server not available — show local tracks only or empty state
      if (!tracks.length) {
        titleEl.textContent = 'HeadSet Player';
        artistEl.textContent = 'Add music using the + button';
        emptyEl.hidden = false;
      }
    }
  }

  /* ======================================================= MediaSession */
  function updateMediaSession(t) {
    if (!('mediaSession' in navigator)) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: t.title, artist: t.artist || '',
      artwork: (!t.local && t.cover) ? [{ src: '/cover/' + t.id }] : []
    });
  }

  function updatePosition() {
    if (!('mediaSession' in navigator) || !isFinite(audio.duration) || !navigator.mediaSession.setPositionState) return;
    try {
      navigator.mediaSession.setPositionState({ duration: audio.duration, position: audio.currentTime, playbackRate: audio.playbackRate });
    } catch { /* ignore */ }
  }

  if ('mediaSession' in navigator) {
    const handlers = {
      play: () => audio.play(),
      pause: () => audio.pause(),
      previoustrack: prev,
      nexttrack: () => step(1, false),
      seekbackward: () => { audio.currentTime = Math.max(0, audio.currentTime - 10); },
      seekforward: () => { audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 10); },
      seekto: (d) => { audio.currentTime = d.seekTime; }
    };
    for (const [k, fn] of Object.entries(handlers)) {
      try { navigator.mediaSession.setActionHandler(k, fn); } catch { /* unsupported action */ }
    }
  }

  /* ======================================================= Keyboard Shortcuts */
  document.addEventListener('keydown', (e) => {
    // Escape closes panels
    if (e.key === 'Escape') {
      if (eqPanel.classList.contains('open')) { openEq(false); return; }
      if (drawer.classList.contains('open')) { openDrawer(false); return; }
      if (!moreMenu.hidden) { closeMoreMenu(); return; }
    }
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.target.closest('input, textarea, button, select')) return;
    switch (e.key.toLowerCase()) {
      case ' ':           e.preventDefault(); togglePlay(); break;
      case 'arrowright':  audio.currentTime += 5; break;
      case 'arrowleft':   audio.currentTime = Math.max(0, audio.currentTime - 5); break;
      case 'arrowup':     vol.value = Math.min(100, +vol.value + 5); vol.dispatchEvent(new Event('input')); break;
      case 'arrowdown':   vol.value = Math.max(0, +vol.value - 5); vol.dispatchEvent(new Event('input')); break;
      case 'n':           step(1, false); break;
      case 'p':           prev(); break;
      case 's':           shuffleBtn.click(); break;
      case 'r':           repeatBtn.click(); break;
      case 'm':           muteBtn.click(); break;
      case 'e':           openEq(!eqPanel.classList.contains('open')); break;
      case 'l':           openDrawer(!drawer.classList.contains('open')); break;
    }
  });

  /* ======================================================= PWA install */
  let deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
  });

  /* ======================================================= Service Worker (optional offline) */
  if ('serviceWorker' in navigator) {
    // register only if sw.js exists (graceful)
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }

  /* ======================================================= Init */
  syncModes();
  syncPlayIcon();
  syncVolumeIcon();
  paintSeek();
  paintVol();

  // Start cycling color immediately
  startColorCycle();

  // Load tracks from server (non-fatal if server offline)
  loadTracks();

})();
