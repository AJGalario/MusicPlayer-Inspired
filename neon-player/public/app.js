(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const audio = $('audio'), seek = $('seek'), vol = $('vol');
  const playBtn = $('play'), iconPlay = $('iconPlay'), iconPause = $('iconPause');
  const shuffleBtn = $('shuffle'), repeatBtn = $('repeat'), muteBtn = $('mute');
  const titleEl = $('title'), coverEl = $('cover'), fallbackEl = $('artFallback');
  const drawer = $('drawer'), scrim = $('scrim'), menuBtn = $('menuBtn');
  const listEl = $('list'), emptyEl = $('empty'), searchEl = $('search');

  const store = {
    get(k, d) { try { const v = localStorage.getItem('np:' + k); return v === null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem('np:' + k, JSON.stringify(v)); } catch { /* private mode */ } }
  };

  let tracks = [];          // [{id, title, artist, cover}]
  let order = [];           // play order = indexes into tracks (shuffled or natural)
  let cur = -1;             // index into tracks
  let shuffleOn = store.get('shuffle', false);
  let repeatMode = store.get('repeat', 0);      // 0 off, 1 all, 2 one
  let scrubbing = false;
  let errorStreak = 0;

  /* ------------------------------------------------------------ helpers */
  const fmt = (s) => {
    if (!isFinite(s)) return '0:00';
    s = Math.floor(s);
    return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  };
  const label = (t) => (t.artist ? `${t.artist} - ${t.title}` : t.title);

  function paint() {
    seek.style.setProperty('--p', seek.value / 10 + '%');
    vol.style.setProperty('--p', vol.value + '%');
  }

  function buildOrder() {
    order = tracks.map((_, i) => i);
    if (!shuffleOn) return;
    for (let i = order.length - 1; i > 0; i--) {            // Fisher-Yates
      const j = Math.floor(Math.random() * (i + 1));
      [order[i], order[j]] = [order[j], order[i]];
    }
    const p = order.indexOf(cur);                           // keep the current song first
    if (p > 0) { order.splice(p, 1); order.unshift(cur); }
  }

  /* ------------------------------------------------------------ playback */
  function setTrack(i, autoplay) {
    if (i < 0 || i >= tracks.length) return;
    cur = i;
    const t = tracks[i];
    audio.src = '/audio/' + t.id;
    titleEl.textContent = label(t);
    document.title = label(t) + ' · Neon Player';

    if (t.cover) {
      coverEl.src = '/cover/' + t.id;
      coverEl.hidden = false; fallbackEl.hidden = true;
    } else {
      coverEl.hidden = true; coverEl.removeAttribute('src'); fallbackEl.hidden = false;
    }

    seek.value = 0; paint();
    markActive();
    store.set('last', t.id);
    updateMediaSession(t);
    if (autoplay) audio.play().catch(() => { /* blocked until the user taps */ });
  }

  function step(dir, auto) {
    if (!tracks.length) return;
    let p = order.indexOf(cur) + dir;
    if (p >= order.length) {
      if (auto && repeatMode === 0) {                       // end of queue, repeat off: stop on the first song
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
    if (audio.currentTime > 3) audio.currentTime = 0;       // like most players: restart first
    else step(-1, false);
  }

  function togglePlay() {
    if (cur < 0 && tracks.length) setTrack(0, true);
    else if (audio.paused) audio.play().catch(() => {});
    else audio.pause();
  }

  audio.addEventListener('play', () => syncPlayIcon());
  audio.addEventListener('pause', () => syncPlayIcon());
  audio.addEventListener('playing', () => { errorStreak = 0; });
  audio.addEventListener('ended', () => {
    if (repeatMode === 2) { audio.currentTime = 0; audio.play().catch(() => {}); }
    else step(1, true);
  });
  audio.addEventListener('error', () => {
    if (!audio.src) return;
    errorStreak++;
    titleEl.textContent = "Can't play this file";
    if (errorStreak < tracks.length) setTimeout(() => step(1, false), 900);   // skip broken file, but never loop forever
  });
  audio.addEventListener('timeupdate', () => {
    if (!scrubbing && isFinite(audio.duration) && audio.duration > 0) {
      seek.value = (audio.currentTime / audio.duration) * 1000;
      paint();
    }
    seek.setAttribute('aria-valuetext', `${fmt(audio.currentTime)} of ${fmt(audio.duration)}`);
    updatePosition();
  });
  audio.addEventListener('volumechange', syncVolumeIcon);

  function syncPlayIcon() {
    const playing = !audio.paused;
    iconPlay.hidden = playing; iconPause.hidden = !playing;
    playBtn.setAttribute('aria-label', playing ? 'Pause' : 'Play');
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = playing ? 'playing' : 'paused';
  }

  /* ------------------------------------------------------------ seek / volume */
  seek.addEventListener('input', () => { scrubbing = true; paint(); });
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
    paint();
  });
  muteBtn.addEventListener('click', () => { audio.muted = !audio.muted; });
  function syncVolumeIcon() {
    const off = audio.muted || audio.volume === 0;
    $('iconVol').hidden = off; $('iconMute').hidden = !off;
    muteBtn.setAttribute('aria-label', audio.muted ? 'Unmute' : 'Mute');
  }
  // iOS ignores audio.volume (hardware buttons only): hide the slider there
  { const old = audio.volume; audio.volume = 0.5; if (audio.volume !== 0.5) $('volumeRow').hidden = true; audio.volume = old; }

  /* ------------------------------------------------------------ shuffle / repeat */
  function syncModes() {
    shuffleBtn.classList.toggle('on', shuffleOn);
    shuffleBtn.setAttribute('aria-pressed', shuffleOn);
    repeatBtn.classList.toggle('on', repeatMode > 0);
    repeatBtn.dataset.mode = repeatMode;
    repeatBtn.setAttribute('aria-label', 'Repeat: ' + ['off', 'all', 'one'][repeatMode]);
  }
  shuffleBtn.addEventListener('click', () => { shuffleOn = !shuffleOn; store.set('shuffle', shuffleOn); buildOrder(); syncModes(); });
  repeatBtn.addEventListener('click', () => { repeatMode = (repeatMode + 1) % 3; store.set('repeat', repeatMode); syncModes(); });

  $('play').addEventListener('click', togglePlay);
  $('next').addEventListener('click', () => step(1, false));
  $('prev').addEventListener('click', prev);

  /* ------------------------------------------------------------ playlist drawer */
  function openDrawer(open) {
    drawer.classList.toggle('open', open);
    scrim.classList.toggle('show', open);
    drawer.inert = !open;
    menuBtn.setAttribute('aria-expanded', open);
    if (open) $('closeBtn').focus(); else menuBtn.focus();
  }
  menuBtn.addEventListener('click', () => openDrawer(true));
  $('closeBtn').addEventListener('click', () => openDrawer(false));
  scrim.addEventListener('click', () => openDrawer(false));
  $('refreshBtn').addEventListener('click', loadTracks);

  function renderList() {
    listEl.textContent = '';
    emptyEl.hidden = tracks.length > 0;
    tracks.forEach((t, i) => {
      const li = document.createElement('li');
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'track'; b.dataset.i = i;
      b.dataset.q = (t.artist + ' ' + t.title).toLowerCase();
      const a = document.createElement('span'); a.className = 't-title'; a.textContent = t.title;
      const c = document.createElement('span'); c.className = 't-artist'; c.textContent = t.artist;
      b.append(a, c); li.append(b); listEl.append(li);
    });
    markActive();
  }
  function markActive() {
    listEl.querySelectorAll('.track').forEach((b) => b.classList.toggle('active', +b.dataset.i === cur));
  }
  listEl.addEventListener('click', (e) => {
    const b = e.target.closest('.track');
    if (!b) return;
    setTrack(+b.dataset.i, true);
    if (matchMedia('(max-width: 700px)').matches) openDrawer(false);
  });
  searchEl.addEventListener('input', () => {
    const q = searchEl.value.toLowerCase().trim();
    listEl.querySelectorAll('.track').forEach((b) => { b.parentElement.hidden = q && !b.dataset.q.includes(q); });
  });

  /* ------------------------------------------------------------ library from the Java server */
  async function loadTracks() {
    try {
      const res = await fetch('/api/tracks', { cache: 'no-store' });
      if (!res.ok) throw new Error(res.status);
      const keepId = cur >= 0 ? tracks[cur]?.id : store.get('last', null);
      const hadTrack = cur >= 0;
      tracks = await res.json();
      renderList();
      if (!tracks.length) { cur = -1; titleEl.textContent = 'No songs found'; buildOrder(); return; }
      const i = tracks.findIndex((t) => t.id === keepId);
      if (hadTrack && i >= 0) cur = i;                      // refresh while playing: don't interrupt
      else setTrack(i >= 0 ? i : 0, false);
      buildOrder(); markActive();
    } catch {
      titleEl.textContent = 'Cannot reach the server';
    }
  }

  /* ------------------------------------------------------------ lock screen / headphone controls */
  function updateMediaSession(t) {
    if (!('mediaSession' in navigator)) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: t.title, artist: t.artist || '',
      artwork: t.cover ? [{ src: '/cover/' + t.id }] : []
    });
  }
  function updatePosition() {
    if (!('mediaSession' in navigator) || !isFinite(audio.duration) || !navigator.mediaSession.setPositionState) return;
    try {
      navigator.mediaSession.setPositionState({ duration: audio.duration, position: audio.currentTime, playbackRate: audio.playbackRate });
    } catch { /* ignore */ }
  }
  if ('mediaSession' in navigator) {
    const h = {
      play: () => audio.play(), pause: () => audio.pause(),
      previoustrack: prev, nexttrack: () => step(1, false),
      seekbackward: () => { audio.currentTime = Math.max(0, audio.currentTime - 10); },
      seekforward: () => { audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 10); },
      seekto: (d) => { audio.currentTime = d.seekTime; }
    };
    for (const [k, fn] of Object.entries(h)) { try { navigator.mediaSession.setActionHandler(k, fn); } catch { /* unsupported */ } }
  }

  /* ------------------------------------------------------------ keyboard (PC) */
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && drawer.classList.contains('open')) { openDrawer(false); return; }
    if (e.ctrlKey || e.metaKey || e.altKey || e.target.closest('input, textarea, button')) return;
    switch (e.key.toLowerCase()) {
      case ' ': e.preventDefault(); togglePlay(); break;
      case 'arrowright': audio.currentTime += 5; break;
      case 'arrowleft': audio.currentTime = Math.max(0, audio.currentTime - 5); break;
      case 'arrowup': vol.value = Math.min(100, +vol.value + 5); vol.dispatchEvent(new Event('input')); break;
      case 'arrowdown': vol.value = Math.max(0, +vol.value - 5); vol.dispatchEvent(new Event('input')); break;
      case 'n': step(1, false); break;
      case 'p': prev(); break;
      case 's': shuffleBtn.click(); break;
      case 'r': repeatBtn.click(); break;
      case 'm': muteBtn.click(); break;
    }
  });

  /* ------------------------------------------------------------ background + rain */
  const bg = new Image();
  bg.onload = () => document.body.classList.add('has-bg');   // user supplied public/bg.jpg: hide the CSS neon
  bg.src = 'bg.jpg';

  (function rain() {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const c = $('rain'), ctx = c.getContext('2d');
    let w = 0, h = 0, drops = [];
    const make = (anywhere) => ({
      x: Math.random() * (w + 100) - 50, y: anywhere ? Math.random() * h : -20,
      l: 12 + Math.random() * 22, v: 10 + Math.random() * 12, o: 0.15 + Math.random() * 0.35
    });
    function resize() {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = innerWidth; h = innerHeight;
      c.width = w * dpr; c.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      drops = Array.from({ length: Math.min(260, Math.floor((w * h) / 7000)) }, () => make(true));
    }
    function frame() {
      if (!document.hidden) {
        ctx.clearRect(0, 0, w, h);
        ctx.lineWidth = 1;
        for (const d of drops) {
          ctx.strokeStyle = `rgba(210,200,255,${d.o})`;
          ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(d.x - d.l * 0.12, d.y + d.l); ctx.stroke();
          d.y += d.v; d.x -= d.v * 0.12;
          if (d.y > h) Object.assign(d, make(false));
        }
      }
      requestAnimationFrame(frame);
    }
    addEventListener('resize', resize);
    resize(); frame();
  })();

  /* ------------------------------------------------------------ go */
  syncModes(); syncPlayIcon(); syncVolumeIcon(); paint();
  loadTracks();
})();
