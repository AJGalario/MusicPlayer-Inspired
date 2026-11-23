/* HeadSet Widget: compact browser-app frontend for the dependency-free Java music server. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const audio = $('audio');
  const ui = {
    title:$('title'), artist:$('artist'), cover:$('cover'), fallback:$('artFallback'),
    seek:$('seek'), elapsed:$('elapsed'), duration:$('duration'),
    play:$('play'), playIcon:$('playIcon'), pauseIcon:$('pauseIcon'),
    shuffle:$('shuffle'), repeat:$('repeat'), repeatOne:$('repeatOne'),
    volume:$('volume'), mute:$('mute'), volumeIcon:$('volumeIcon'), mutedIcon:$('mutedIcon'),
    library:$('libraryPanel'), settings:$('settingsPanel'), songs:$('songs'), search:$('search'),
    fileInput:$('fileInput'), count:$('songCount'), libraryCount:$('libraryCount'),
    empty:$('emptyList'), speed:$('speed'), speedLabel:$('speedLabel'), toast:$('toast'),
    label:$('nowLabel'), add:$('addQuick')
  };
  const settings = {
    read(name, fallback) {try {const v = localStorage.getItem('hw:' + name);return v == null ? fallback : JSON.parse(v);}catch{return fallback;}},
    write(name, value) {try{localStorage.setItem('hw:' + name, JSON.stringify(value));}catch{}}
  };
  let tracks = [], current = -1, shuffle = settings.read('shuffle',false), repeat = settings.read('repeat',0);
  let playingOrder = [], seeking = false, toastTimer, currentObjectUrls = [];
  let audioContext, filters = [];
  const frequencies = [60,250,1000,4000,12000];
  const presets = {flat:[0,0,0,0,0],bass:[8,5,0,-2,-3],vocal:[-3,-1,4,5,2],pop:[-1,2,4,2,-1],rock:[5,3,-1,3,5]};
  let gains = settings.read('gains',[0,0,0,0,0]);
  if(!Array.isArray(gains)||gains.length!==5) gains=[0,0,0,0,0];
  const fmt = sec => Number.isFinite(sec) ? `${Math.floor(sec/60)}:${String(Math.floor(sec%60)).padStart(2,'0')}` : '0:00';
  const paint = el => el.style.setProperty('--fill',`${100*(+el.value-(+el.min||0))/((+el.max||100)-(+el.min||0))}%`);
  const getSrc = t => t.local ? t.src : '/audio/'+encodeURIComponent(t.id);
  const getCover = t => !t.local && t.cover ? '/cover/'+encodeURIComponent(t.id) : null;
  function toast(s){ui.toast.textContent=s;ui.toast.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>ui.toast.classList.remove('visible'),2300);}
  function panel(name){
    const library = name==='library', eq=name==='settings';
    ui.library.hidden=!library; ui.settings.hidden=!eq;
    $('libraryToggle').classList.toggle('active',library); $('settingsToggle').classList.toggle('active',eq);
    $('libraryToggle').setAttribute('aria-expanded',String(library)); $('settingsToggle').setAttribute('aria-expanded',String(eq));
    if(library)ui.search.focus();
    if(eq) initAudioContext();
  }
  $('libraryToggle').addEventListener('click',()=>panel(ui.library.hidden?'library':null));
  $('settingsToggle').addEventListener('click',()=>panel(ui.settings.hidden?'settings':null));
  $('queueShortcut').addEventListener('click',()=>panel('library'));
  document.querySelectorAll('[data-close]').forEach(el=>el.addEventListener('click',()=>panel(null)));
  ui.add.addEventListener('click',()=>ui.fileInput.click());
  $('uploadBtn').addEventListener('click',()=>ui.fileInput.click());
  $('emptyAdd').addEventListener('click',()=>ui.fileInput.click());
  $('refresh').addEventListener('click',()=>loadTracks(true));
  function rebuildOrder(){
    playingOrder=tracks.map((_,i)=>i);
    if(shuffle)for(let i=playingOrder.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[playingOrder[i],playingOrder[j]]=[playingOrder[j],playingOrder[i]];}
    if(current>=0){const n=playingOrder.indexOf(current);if(n>0){playingOrder.splice(n,1);playingOrder.unshift(current);}}
  }
  function updateModes(){
    ui.shuffle.classList.toggle('on',shuffle);ui.shuffle.setAttribute('aria-label',shuffle?'Shuffle on':'Shuffle off');
    ui.repeat.classList.toggle('on',repeat>0);ui.repeat.setAttribute('aria-label',`Repeat ${['off','all','one'][repeat]}`);
    ui.repeatOne.hidden=repeat!==2;
  }
  ui.shuffle.addEventListener('click',()=>{shuffle=!shuffle;settings.write('shuffle',shuffle);rebuildOrder();updateModes();toast(shuffle?'Shuffle on':'Shuffle off');});
  ui.repeat.addEventListener('click',()=>{repeat=(repeat+1)%3;settings.write('repeat',repeat);updateModes();toast(['Repeat off','Repeat all','Repeat one'][repeat]);});
  function updateMedia(t){
    if(!('mediaSession' in navigator))return;
    try{navigator.mediaSession.metadata=new MediaMetadata({title:t.title,artist:t.artist||'Local music',artwork:getCover(t)?[{src:getCover(t)}]:[]});}catch{}
  }
  function setTrack(i,autoplay=false){
    if(i<0||i>=tracks.length)return;
    const t=tracks[i]; current=i;
    audio.src=getSrc(t);audio.load();
    ui.title.textContent=t.title||'Untitled';ui.title.title=t.title||'';
    ui.artist.textContent=t.artist||(t.local?'Local file':'Unknown artist');
    ui.label.textContent='NOW PLAYING';
    const cover=getCover(t);ui.cover.hidden=!cover;ui.fallback.hidden=!!cover;
    if(cover){ui.cover.onload=()=>{try{const c=document.createElement('canvas');c.width=c.height=20;c.getContext('2d').drawImage(ui.cover,0,0,20,20);const d=c.getContext('2d').getImageData(10,10,1,1).data;document.documentElement.style.setProperty('--accent',`rgb(${Math.max(130,d[0])},${Math.max(116,d[1])},${Math.max(165,d[2])})`);}catch{}};ui.cover.src=cover;}
    else {ui.cover.removeAttribute('src');document.documentElement.style.setProperty('--accent','#c8b6ff');}
    ui.seek.value=0;paint(ui.seek);ui.elapsed.textContent='0:00';ui.duration.textContent='0:00';
    settings.write('last',t.id); renderList(); updateMedia(t); document.title=`${t.title} · HeadSet`;
    if(autoplay) playAudio();
  }
  function playAudio(){
    initAudioContext();
    if(audioContext&&audioContext.state==='suspended')audioContext.resume().catch(()=>{});
    audio.play().catch(()=>toast('Could not play this audio file'));
  }
  function toggle(){
    if(current<0){if(!tracks.length){panel('library');return;}setTrack(0,true);return;}
    if(audio.paused)playAudio();else audio.pause();
  }
  function step(direction,ended=false){
    if(!tracks.length)return;
    if(current<0){setTrack(0,true);return;}
    if(ended&&repeat===2){audio.currentTime=0;playAudio();return;}
    let idx=playingOrder.indexOf(current)+direction;
    if(idx>=playingOrder.length){if(ended&&repeat===0){audio.pause();audio.currentTime=0;return;}idx=0;}
    if(idx<0)idx=playingOrder.length-1;
    setTrack(playingOrder[idx],true);
  }
  ui.play.addEventListener('click',toggle);
  $('prev').addEventListener('click',()=>{if(audio.currentTime>3)audio.currentTime=0;else step(-1);});
  $('next').addEventListener('click',()=>step(1));
  audio.addEventListener('play',()=>{document.body.classList.add('playing');ui.playIcon.hidden=true;ui.pauseIcon.hidden=false;ui.play.setAttribute('aria-label','Pause');if('mediaSession' in navigator)navigator.mediaSession.playbackState='playing';});
  audio.addEventListener('pause',()=>{document.body.classList.remove('playing');ui.playIcon.hidden=false;ui.pauseIcon.hidden=true;ui.play.setAttribute('aria-label','Play');if('mediaSession' in navigator)navigator.mediaSession.playbackState='paused';});
  audio.addEventListener('ended',()=>step(1,true));
  audio.addEventListener('loadedmetadata',()=>ui.duration.textContent=fmt(audio.duration));
  audio.addEventListener('timeupdate',()=>{if(!seeking&&audio.duration>0&&Number.isFinite(audio.duration)){ui.seek.value=Math.round(audio.currentTime/audio.duration*1000);paint(ui.seek);}ui.elapsed.textContent=fmt(audio.currentTime);ui.duration.textContent=fmt(audio.duration);if('mediaSession' in navigator&&navigator.mediaSession.setPositionState&&Number.isFinite(audio.duration)&&audio.duration>0){try{navigator.mediaSession.setPositionState({duration:audio.duration,position:audio.currentTime,playbackRate:audio.playbackRate});}catch{}}});
  audio.addEventListener('error',()=>{if(audio.src)toast('This audio format could not be played');});
  ui.seek.addEventListener('input',()=>{seeking=true;paint(ui.seek);if(audio.duration>0)ui.elapsed.textContent=fmt(+ui.seek.value/1000*audio.duration);});
  ui.seek.addEventListener('change',()=>{if(audio.duration>0)audio.currentTime=+ui.seek.value/1000*audio.duration;seeking=false;});
  ui.volume.value=settings.read('volume',70);audio.volume=+ui.volume.value/100;paint(ui.volume);
  ui.volumeLabel=$('volumeLabel');ui.volumeLabel.textContent=ui.volume.value+'%';
  ui.volume.addEventListener('input',()=>{audio.volume=+ui.volume.value/100;audio.muted=false;settings.write('volume',+ui.volume.value);ui.volumeLabel.textContent=ui.volume.value+'%';paint(ui.volume);syncMute();});
  ui.mute.addEventListener('click',()=>{audio.muted=!audio.muted;syncMute();});
  function syncMute(){const muted=audio.muted||audio.volume===0;ui.volumeIcon.hidden=muted;ui.mutedIcon.hidden=!muted;ui.mute.setAttribute('aria-label',muted?'Unmute':'Mute');}
  function renderList(){
    const q=ui.search.value.toLowerCase().trim();ui.songs.textContent='';
    let visible=0;
    tracks.forEach((t,i)=>{
      if(q&&!`${t.title} ${t.artist}`.toLowerCase().includes(q))return;
      visible++;
      const li=document.createElement('li'), b=document.createElement('button');b.className='song-button'+(i===current?' active':'');b.type='button';
      const art=document.createElement('span');art.className='mini-art';
      const cover=getCover(t);
      if(cover){const img=document.createElement('img');img.src=cover;img.alt='';img.onerror=()=>{img.remove();art.textContent='♫';};art.append(img);}else art.textContent='♫';
      const meta=document.createElement('span');meta.className='list-meta';const title=document.createElement('strong');title.textContent=t.title||'Untitled';const artist=document.createElement('small');artist.textContent=t.artist||(t.local?'Local file':'Unknown artist');meta.append(title,artist);
      const arrow=document.createElement('span');arrow.className='song-arrow';arrow.textContent=i===current?'●':'▶';
      b.append(art,meta,arrow);b.addEventListener('click',()=>{setTrack(i,true);panel(null);});li.append(b);ui.songs.append(li);
    });
    ui.empty.hidden=visible>0;
    ui.empty.querySelector('strong').textContent=tracks.length&&q?'No matching songs':'No tracks here yet';
    ui.empty.querySelector('span').textContent=tracks.length&&q?'Try another search term.':'Add a music file to start listening.';
    ui.count.textContent=`${tracks.length} ${tracks.length===1?'song':'songs'}`;
    ui.libraryCount.textContent=tracks.length;
  }
  ui.search.addEventListener('input',renderList);
  async function loadTracks(notify=false){
    try{
      const res=await fetch('/api/tracks',{cache:'no-store'});if(!res.ok)throw Error('Server unavailable');
      const remote=await res.json();const keep=tracks[current]?.id??settings.read('last',null);
      const local=tracks.filter(t=>t.local);const playing=!audio.paused;
      tracks=[...remote,...local];
      if(current>=0&&playing){current=tracks.findIndex(t=>t.id===keep);if(current<0){audio.pause();current=-1;}}
      else{const idx=tracks.findIndex(t=>t.id===keep);if(idx>=0)setTrack(idx,false);else if(tracks.length)setTrack(0,false);else {current=-1;ui.title.textContent='Your music, your space.';ui.artist.textContent='Pick a song to begin';}}
      rebuildOrder();renderList();if(notify)toast('Library refreshed');
    }catch{renderList();if(notify)toast('Server not available');}
  }
  ui.fileInput.addEventListener('change',async()=>{
    const files=Array.from(ui.fileInput.files);if(!files.length)return;
    let uploaded=0, temporary=0;
    for(const file of files){
      if(!/\.(mp3|m4a|aac|wav|ogg|oga|opus|flac|webm)$/i.test(file.name))continue;
      try{
        const res=await fetch('/api/upload',{method:'POST',headers:{'X-Filename':encodeURIComponent(file.name)},body:file});
        if(!res.ok)throw Error('Upload unavailable');uploaded++;
      }catch{
        const url=URL.createObjectURL(file);currentObjectUrls.push(url);const base=file.name.replace(/\.[^.]+$/,'');const split=base.indexOf(' - ');const artist=split>0?base.slice(0,split):'';const title=split>0?base.slice(split+3):base;
        tracks.push({id:'local:'+url,title,artist,local:true,src:url,cover:false});temporary++;
      }
    }
    ui.fileInput.value='';
    if(uploaded)await loadTracks();
    rebuildOrder();renderList();
    if(current<0&&tracks.length)setTrack(0,false);
    toast(`${uploaded+temporary} ${uploaded+temporary===1?'song':'songs'} added${temporary?' (temporary in browser)':''}`);
  });
  function initAudioContext(){
    if(audioContext)return;
    try{
      audioContext=new (window.AudioContext||window.webkitAudioContext)();
      const src=audioContext.createMediaElementSource(audio);
      filters=frequencies.map((f,i)=>{const filter=audioContext.createBiquadFilter();filter.type=i===0?'lowshelf':i===4?'highshelf':'peaking';filter.frequency.value=f;filter.Q.value=1;filter.gain.value=gains[i];return filter;});
      let prev=src;filters.forEach(f=>{prev.connect(f);prev=f;});prev.connect(audioContext.destination);
    }catch(e){console.warn('Equalizer not supported',e);audioContext=null;}
  }
  function renderGains(){gains.forEach((g,i)=>{$('eq'+i).value=g;$('db'+i).textContent=(g>0?'+':'')+g;});}
  renderGains();
  function applyGains(values){gains=[...values];gains.forEach((g,i)=>{if(filters[i])filters[i].gain.value=g;});settings.write('gains',gains);renderGains();}
  document.querySelectorAll('.preset').forEach(btn=>btn.addEventListener('click',()=>{const preset=btn.dataset.preset;initAudioContext();applyGains(presets[preset]);settings.write('preset',preset);document.querySelectorAll('.preset').forEach(b=>b.classList.toggle('selected',b===btn));}));
  document.querySelectorAll('.eq-band input').forEach((input,i)=>input.addEventListener('input',()=>{initAudioContext();gains[i]=+input.value;applyGains(gains);document.querySelectorAll('.preset').forEach(b=>b.classList.remove('selected'));settings.write('preset','custom');}));
  document.querySelectorAll('.preset').forEach(b=>b.classList.toggle('selected',b.dataset.preset===settings.read('preset','flat')));
  ui.speed.value=settings.read('speed',1);audio.playbackRate=+ui.speed.value;ui.speedLabel.textContent=(+ui.speed.value).toFixed(2)+'×';paint(ui.speed);
  ui.speed.addEventListener('input',()=>{audio.playbackRate=+ui.speed.value;ui.speedLabel.textContent=(+ui.speed.value).toFixed(2)+'×';paint(ui.speed);settings.write('speed',+ui.speed.value);});
  if('mediaSession' in navigator){
    const handlers={play:playAudio,pause:()=>audio.pause(),previoustrack:()=>step(-1),nexttrack:()=>step(1),seekbackward:()=>audio.currentTime=Math.max(0,audio.currentTime-10),seekforward:()=>audio.currentTime=Math.min(audio.duration||0,audio.currentTime+10),seekto:({seekTime})=>audio.currentTime=seekTime};
    for(const [name,handler] of Object.entries(handlers)){try{navigator.mediaSession.setActionHandler(name,handler);}catch{}}
  }
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'){panel(null);return;}
    if(event.ctrlKey||event.metaKey||event.altKey||event.target.closest('input,button,textarea'))return;
    switch(event.key.toLowerCase()){
      case ' ':event.preventDefault();toggle();break;
      case 'arrowleft':audio.currentTime=Math.max(0,audio.currentTime-5);break;
      case 'arrowright':audio.currentTime=Math.min(audio.duration||0,audio.currentTime+5);break;
      case 'n':step(1);break;case 'p':step(-1);break;
      case 's':ui.shuffle.click();break;case 'r':ui.repeat.click();break;
      case 'm':ui.mute.click();break;case 'l':panel('library');break;case 'e':panel('settings');break;
    }
  });
  window.addEventListener('beforeunload',()=>currentObjectUrls.forEach(u=>URL.revokeObjectURL(u)));
  paint(ui.seek);updateModes();syncMute();loadTracks();
})();
