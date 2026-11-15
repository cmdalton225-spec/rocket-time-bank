/* Firebase-connected retro cockpit. Bank authority is Firestore; this script controls only UI and animation. */
(() => {
'use strict';
const ROLE=document.body.dataset.role;
const $=id=>document.getElementById(id);
const targets=[{name:'MOON',minutes:40},{name:'MARS',minutes:120},{name:'JUPITER',minutes:240},{name:'SATURN',minutes:400},{name:'URANUS',minutes:600},{name:'NEPTUNE',minutes:850},{name:'PLUTO',minutes:1100}];
const state={bankSeconds:0,sessionSeconds:0,earnedMinutes:0,lifetimeEarnedMinutes:0,session:'idle',request:null,flightMode:'hold',flightHold:true,returnGoal:0,returnFrom:0,returnSpeed:0,returnStartedAt:0};
let bankSeen=false,lastBank=0,lastEarned=0,lastRunning=false,linked=false,authorized=false,enteredDigits='005',freshEntry=true;
let soundEnabled=true,audioContext,toastTimeout;
function fmt(s,full=false){s=Math.max(0,Math.ceil(s||0));const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sec=s%60;return full?`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`:`${String(m+h*60).padStart(2,'0')}:${String(sec).padStart(2,'0')}`}
function destination(){
 const target=targets.find(t=>state.earnedMinutes<t.minutes)||{name:'DEEP SPACE',minutes:1500};
 const previous=targets.filter(t=>t.minutes<=state.earnedMinutes).at(-1)?.minutes||0;
 // Keep the remaining duration in SECONDS, not fractional minutes.
 // e.g. Mars at 120:00 minus 48:56 banked = 71:04 remaining.
 const remainingSeconds=Math.max(0,Math.ceil(target.minutes*60-state.bankSeconds));
 return {...target,remainingSeconds,progress:Math.min(1,Math.max(0,(state.earnedMinutes-previous)/(target.minutes-previous)))};
}
function formatDestinationRemaining(seconds,forDad=false){
 const mins=Math.floor(seconds/60),secs=seconds%60;
 const duration=secs?`${mins} MIN ${String(secs).padStart(2,'0')} SEC`:`${mins} MIN`;
 return forDad?duration:`${duration} TO GO`;
}
// Describe the direction the rocket is actually travelling. Bank minutes are
// NOT a return-flight ETA: never show the outward countdown during descent.
function returnDisplay(){
 const returning=state.flightMode==='home'||state.flightMode==='return';
 // Dad Control has no local rocket canvas. While Asher is playing, show the
 // return route inferred from the bank, rather than the outward destination.
 const dadPlaying=ROLE==='dad'&&state.session==='playing';
 const dadHome=ROLE==='dad'&&state.bankSeconds<=0;
 if(!returning&&!dadPlaying&&!dadHome)return null;
 const goal=returning?Math.max(0,Number(state.returnGoal)||0):
   (dadHome?0:([0,...targets.map(t=>t.minutes)].filter(m=>m<state.earnedMinutes-.025).at(-1)??0));
 const name=goal===0?'EARTH':targets.find(t=>t.minutes===goal)?.name||'EARTH';
 const position=window.__asherFlight?.position?.();
 const start=Number(state.returnFrom)||0;
 const progress=returning&&Number.isFinite(position)&&start>goal+.001?
   Math.max(0,Math.min(1,(start-position)/(start-goal))):0;
 return {name,progress,caption:'RETURNING TO',remaining:'DESCENT IN PROGRESS'};
}
function toast(message){const el=$('toast');if(!el)return;el.textContent=message;el.classList.add('visible');clearTimeout(toastTimeout);toastTimeout=setTimeout(()=>el.classList.remove('visible'),3600)}
function persist(){render()} // Flight UI only. No Firestore writes here.
function entered(){return Number(enteredDigits)||0}
function beginHome(){const pos=window.__asherFlight?.position?.() ?? state.earnedMinutes;if(pos<.025){state.flightMode='hold';state.flightHold=true;return;}state.returnFrom=pos;state.returnGoal=0;state.returnStartedAt=Date.now();state.returnSpeed=pos/Math.min(120,Math.max(25,25+pos*.14));state.flightMode='home';state.flightHold=false;toast('Time bank empty • rocket returning to Earth')}
function beginBankAdjustmentReturn(goal){
 const pos=window.__asherFlight?.position?.() ?? goal;
 if(pos<=goal+.025){state.flightMode='hold';state.flightHold=true;return;}
 state.returnFrom=pos;state.returnGoal=goal;state.returnStartedAt=Date.now();
 state.returnSpeed=(pos-goal)/Math.max(15,Math.min(75,18+(pos-goal)*.35));
 state.flightMode='return';state.flightHold=false;
}
function beginSessionReturn(seconds){if(state.flightMode==='home')return;const pos=window.__asherFlight?.position?.() ?? state.earnedMinutes;const stops=[0,...targets.map(t=>t.minutes)];const goal=stops.filter(m=>m<pos-.025).at(-1)??0;if(pos<=goal+.025)return;state.returnFrom=pos;state.returnGoal=goal;state.returnStartedAt=Date.now();state.returnSpeed=(pos-goal)/Math.max(25,Math.min(240,seconds*.92));state.flightMode='return';state.flightHold=false;}
function setBank(raw,effective){
 const bank=Math.max(0,Number(effective.bankSeconds)||0),session=Math.max(0,Number(effective.sessionSeconds)||0);
 // IMPORTANT: Flight distance is the CURRENT saved balance, not lifetime
 // earnings. Dad's CLEAR BANK keeps history, but the next 5 minutes should
 // still travel only 5/40 of the way to the Moon.
 const earned=bank/60;
 const lifetimeEarned=Math.max(0,Number(raw.earnedMinutesTotal)||0);
 const playing=!!raw.running&&bank>0&&session>0;
 state.bankSeconds=bank;state.sessionSeconds=session;state.earnedMinutes=earned;state.lifetimeEarnedMinutes=lifetimeEarned;state.session=playing?'playing':bank<=0?'idle':raw.sessionStatus==='approved'?'ready':raw.sessionStatus==='paused'?'paused':'idle';
 if(!bankSeen){
  bankSeen=true;
  // The saved camera position is not the destination. Restore it and resume any
  // earned journey that has not actually been animated yet.
  const restored=window.__asherFlight?.restoreFromBank?.(earned,bank);
  const position=window.__asherFlight?.position?.() ?? earned;
  state.flightMode='hold';state.flightHold=true;
  // If Asher closed Silk during a zero-bank trip home, finish that trip even
  // when Dad added more minutes while the tablet was closed.
  if(restored?.savedMode==='home'&&position>.03){
   state.returnFrom=Math.max(position,Number(restored.savedReturnFrom)||position);
   state.returnGoal=0;state.returnStartedAt=Date.now();
   state.returnSpeed=Math.max(.08,Number(restored.savedReturnSpeed)||position/60);
   state.flightMode='home';state.flightHold=false;
  }
  else if(bank<=0&&position>0.03)beginHome();
  else if(playing&&bank>0)beginSessionReturn(session);
  else if(bank>0&&earned>position+.01){state.flightMode='outbound';state.flightHold=false;}
  else if(bank>0&&position>earned+.01)beginBankAdjustmentReturn(earned);
 }
 else if(bank<=0&&lastBank>0){beginHome()}
 else if(bank>0&&earned>lastEarned+.001){
  // An approved BANK request moves the rocket toward the new saved-time
  // destination. If it is already heading home, finish that trip first.
  if(state.flightMode!=='home'){
   const pos=window.__asherFlight?.position?.() ?? 0;
   if(pos>earned+.01)beginBankAdjustmentReturn(earned);
   else{state.flightMode='outbound';state.flightHold=false;}
  }
 }
 else if(bank>0&&earned<lastEarned-.001&&!playing&&!lastRunning){
  // Dad removed minutes while idle: gently move back toward the correct
  // position instead of leaving the rocket near an old planet.
  const pos=window.__asherFlight?.position?.() ?? 0;
  if(pos>earned+.01)beginBankAdjustmentReturn(earned);
  else if(pos<earned-.01){state.flightMode='outbound';state.flightHold=false;}
  else{state.flightMode='hold';state.flightHold=true;}
 }
 if(!lastRunning&&playing&&bank>0){beginSessionReturn(session)}
 if(lastRunning&&!playing&&bank>0&&state.flightMode==='return'){state.flightMode='hold';state.flightHold=true;}
 if(bank<=0&&state.flightMode!=='home'&&window.__asherFlight?.position?.()>0.03)beginHome();
 lastBank=bank;lastEarned=earned;lastRunning=playing;render();
}
function setPending(p){state.request=p?{pending:true}:null;render()}
function setLinked(v){linked=!!v;render()}
function setAuthorized(v){authorized=!!v;render()}
function render(){
 const d=destination(),returnRoute=returnDisplay();if($('bank-clock'))$('bank-clock').textContent=fmt(state.bankSeconds,true);
 if($('session-clock'))$('session-clock').textContent=fmt(state.sessionSeconds);
 if($('entered-minutes'))$('entered-minutes').textContent=enteredDigits;
 if($('destination-caption'))$('destination-caption').textContent=returnRoute?.caption||'NEXT DESTINATION';
 if($('destination-name'))$('destination-name').textContent=returnRoute?.name||d.name;
 if($('destination-remaining'))$('destination-remaining').textContent=returnRoute?.remaining||formatDestinationRemaining(d.remainingSeconds);
 if($('gauge-needle'))$('gauge-needle').setAttribute('transform',`rotate(${-65+130*(returnRoute?.progress??d.progress)} 110 103)`);
 const pending=!!state.request,playing=state.session==='playing';
 $('lamp-ready')?.classList.toggle('lit',!pending&&!playing);
 $('lamp-pending')?.classList.toggle('lit',pending);
 $('lamp-playing')?.classList.toggle('lit',playing);
 if(ROLE==='child'){
  $('start-session').hidden=!(state.session==='ready'||state.session==='paused');
  $('pause-session').hidden=!playing;$('end-session').hidden=state.session==='idle';
  $('child-status').textContent=!linked?'● WAITING FOR DEVICE LINK':pending?'● REQUEST TRANSMITTED • AWAITING DAD':playing?'● PLAY TIME RUNNING':state.session==='ready'?'● APPROVED • READY TO PLAY':'● LINKED TO DAD • LIVE';
  document.querySelectorAll('#bank-button,#spend-button,.keypad [data-key],#start-session,#pause-session,#end-session').forEach(b=>{b.disabled=!linked});
 }else{
  $('dad-status').textContent=!authorized?'● SIGN-IN REQUIRED':pending?'● NEW TRANSMISSION • APPROVAL REQUIRED':playing?'● ASHER IS PLAYING • SESSION LIVE':'● DAD CONTROL • LIVE FIREBASE';
  $('map-destination-caption').textContent=returnRoute?'RETURN ROUTE':'CURRENT DESTINATION';
  $('map-remaining-caption').textContent=returnRoute?'RETURN STATUS':'TIME STILL NEEDED';
  $('map-destination').textContent=returnRoute?.name||d.name;
  $('map-remaining').textContent=returnRoute?'DESCENDING':formatDestinationRemaining(d.remainingSeconds,true);
  $('map-flight').textContent=returnRoute?`RETURNING TO ${returnRoute.name}`:state.flightMode==='outbound'?'EXPLORING':'HOLDING POSITION';
 }
}
function keypad(){document.querySelectorAll('[data-key]').forEach(btn=>btn.addEventListener('click',()=>{if(ROLE==='child')unlockEngine();audioClick();const k=btn.dataset.key;let s=enteredDigits||'0';if(k==='C'){s='0';freshEntry=false}else if(k==='back'){s=s.slice(0,-1)||'0';freshEntry=false}else{if(freshEntry)s='0';s=(s==='0'?'':s)+k;freshEntry=false}enteredDigits=String(Math.min(240,Number(s)||0)).padStart(3,'0');render()}))}
function request(type){if(!linked)return toast('Dad must link this device first');const n=entered();if(n<5||n>240||n%5!==0)return toast('Choose 5–240 minutes in steps of five.');if(state.request)return toast('Wait for Dad to answer the previous request.');if(type==='spend'&&n*60>state.bankSeconds)return toast('Not enough saved time.');if(type==='spend'&&state.session!=='idle')return toast('Finish the current session first.');audioClick('heavy');unlockEngine();freshEntry=true;window.asherFirebaseRequest?.(type==='bank'?'bank_change':'session_change',n)}
function wire(){keypad();$('sound-toggle')?.addEventListener('click',()=>{soundEnabled=!soundEnabled;$('sound-toggle').textContent=soundEnabled?'♪ SOUND ON':'♪ MUTED';if(soundEnabled)unlockEngine();engineVolume(engineLevel);audioClick()});
 if(ROLE==='child'){
  $('bank-button').addEventListener('click',()=>request('bank'));$('spend-button').addEventListener('click',()=>request('spend'));
  $('start-session').addEventListener('click',()=>{audioClick('heavy');unlockEngine();window.asherFirebaseSession?.('start')});
  $('pause-session').addEventListener('click',()=>{audioClick('heavy');window.asherFirebaseSession?.('pause')});
  $('end-session').addEventListener('click',()=>{audioClick('heavy');window.asherFirebaseSession?.('end')});
  document.addEventListener('pointerdown',()=>unlockEngine(),{once:true});
 }else{
  $('add-button').addEventListener('click',()=>{audioClick('heavy');window.dadFirebaseAdjust?.(entered())});
  $('remove-button').addEventListener('click',()=>{audioClick('heavy');window.dadFirebaseAdjust?.(-entered())});
  $('stop-session').addEventListener('click',()=>{audioClick('heavy');window.dadFirebaseStop?.()});
 }
 render();}
window.asherLive={setBank,setPending,setLinked,setAuthorized,toast,render,position:()=>window.__asherFlight?.position?.()??0};
function audioClick(kind='key'){
 if(!soundEnabled)return;
 try{
  audioContext??=new (window.AudioContext||window.webkitAudioContext)();const ctx=audioContext;if(ctx.state==='suspended')ctx.resume();const t=ctx.currentTime;
  // A short metallic key impact plus low relay thunk, not a synthetic beep.
  const len=kind==='heavy'?.16:.075;const sampleRate=ctx.sampleRate;const buf=ctx.createBuffer(1,Math.ceil(sampleRate*len),sampleRate);const data=buf.getChannelData(0);
  for(let i=0;i<data.length;i++){const x=i/sampleRate;const env=Math.exp(-x*(kind==='heavy'?35:75));data[i]=(Math.random()*2-1)*env*(kind==='heavy'?.68:.36)}
  const source=ctx.createBufferSource();source.buffer=buf;const filter=ctx.createBiquadFilter();filter.type='bandpass';filter.frequency.value=kind==='heavy'?690:1300;filter.Q.value=.6;const gain=ctx.createGain();gain.gain.value=.35;source.connect(filter);filter.connect(gain);gain.connect(ctx.destination);source.start(t);
  const osc=ctx.createOscillator(),g=ctx.createGain();osc.type='triangle';osc.frequency.setValueAtTime(kind==='heavy'?105:190,t);osc.frequency.exponentialRampToValueAtTime(kind==='heavy'?48:110,t+.085);g.gain.setValueAtTime(.07,t);g.gain.exponentialRampToValueAtTime(.001,t+.10);osc.connect(g);g.connect(ctx.destination);osc.start(t);osc.stop(t+.105);
 }catch{}
}
// Gentle, continuous engine rumble. It is synthesized locally; no audio downloads.
// Audio is unlocked only by a real button tap, as required by phone browsers.
let engineNoise, engineTone, engineNoiseGain, engineToneGain, engineFilter;
let engineUnlocked=false, engineLevel=0;
function unlockEngine(){
 if(ROLE!=='child'||!soundEnabled)return;
 try{
  audioContext??=new (window.AudioContext||window.webkitAudioContext)();
  const ctx=audioContext;
  if(ctx.state==='suspended')ctx.resume();
  if(engineUnlocked)return;
  const n=ctx.sampleRate*2,buf=ctx.createBuffer(1,n,ctx.sampleRate),arr=buf.getChannelData(0);
  // Brown noise: soft low-frequency air/engine texture, not a sharp electronic buzz.
  let prev=0;
  for(let i=0;i<n;i++){prev=(prev+.035*(Math.random()*2-1))*.9993;arr[i]=Math.max(-1,Math.min(1,prev*4));}
  engineNoise=ctx.createBufferSource();engineNoise.buffer=buf;engineNoise.loop=true;
  engineFilter=ctx.createBiquadFilter();engineFilter.type='lowpass';engineFilter.frequency.value=190;
  engineNoiseGain=ctx.createGain();engineNoiseGain.gain.value=0;
  engineNoise.connect(engineFilter);engineFilter.connect(engineNoiseGain);engineNoiseGain.connect(ctx.destination);
  engineTone=ctx.createOscillator();engineTone.type='sine';engineTone.frequency.value=57;
  engineToneGain=ctx.createGain();engineToneGain.gain.value=0;
  engineTone.connect(engineToneGain);engineToneGain.connect(ctx.destination);
  engineNoise.start();engineTone.start();engineUnlocked=true;
 }catch(e){console.warn('Audio is unavailable:',e)}
}
function engineVolume(level){
 engineLevel=level;
 if(!engineUnlocked||!audioContext)return;
 const now=audioContext.currentTime;
 const active=soundEnabled&&!document.hidden;
 const v=active?Math.max(0,Math.min(1,level)):0;
 // Maximum ~0.065 on the noise bus; very soft cruise sound.
 engineNoiseGain.gain.setTargetAtTime(v*.15,now,.18);
 engineToneGain.gain.setTargetAtTime(v*.035,now,.18);
 engineTone.frequency.setTargetAtTime(52+v*17,now,.20);
 engineFilter.frequency.setTargetAtTime(145+v*215,now,.25);
}
let lastIgnitionAt=0;
function ignitionSound(){
 if(Date.now()-lastIgnitionAt<650)return;lastIgnitionAt=Date.now();
 unlockEngine();
 if(!engineUnlocked||!soundEnabled)return;
 const ctx=audioContext,t=ctx.currentTime;
 // One soft filtered "fwoosh" that swells rather than abruptly blasting.
 const b=ctx.createBuffer(1,Math.round(ctx.sampleRate*1.5),ctx.sampleRate),a=b.getChannelData(0);
 for(let i=0;i<a.length;i++)a[i]=Math.random()*2-1;
 const source=ctx.createBufferSource();source.buffer=b;
 const low=ctx.createBiquadFilter();low.type='lowpass';low.frequency.setValueAtTime(180,t);
 low.frequency.exponentialRampToValueAtTime(780,t+.85);
 const g=ctx.createGain();g.gain.setValueAtTime(.0001,t);
 g.gain.exponentialRampToValueAtTime(.095,t+.32);
 g.gain.exponentialRampToValueAtTime(.0001,t+1.45);
 source.connect(low);low.connect(g);g.connect(ctx.destination);
 source.start(t);source.stop(t+1.48);
}
document.addEventListener('visibilitychange',()=>engineVolume(engineLevel));
function initRocket(){
 const viewport=document.querySelector('.viewport'),canvas=$('trail-canvas'),oldRocket=$('rocket');
 if(!viewport||!canvas||!oldRocket)return;
 const ctx=canvas.getContext('2d');if(!ctx)return;
 const svg=oldRocket.querySelector('svg').cloneNode(true);
 svg.querySelectorAll('.flame,.flame-inner').forEach(n=>n.remove());
 svg.setAttribute('xmlns','http://www.w3.org/2000/svg');
 const rocketImg=new Image();rocketImg.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(new XMLSerializer().serializeToString(svg));
 const earthImg=new Image();earthImg.src='assets/earth.png';
 oldRocket.style.display='none';$('earth-globe').style.display='none';
 viewport.querySelector('.moon')?.style.setProperty('display','none');
 viewport.querySelector('.space-background')?.style.setProperty('display','none');
 canvas.style.zIndex='3';canvas.style.width='100%';canvas.style.height='100%';
 const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
 const smooth=v=>{v=clamp(v);return v*v*(3-2*v)};
 const lerp=(a,b,t)=>a+(b-a)*t;
 const milestones=[0,...targets.map(t=>t.minutes)];
 const names=['EARTH',...targets.map(t=>t.name)];
 const palette=[['#1d73a2','#8bd5e3'],['#818789','#d9d5c8'],['#803728','#e99d67'],['#9b785d','#e0c09c'],['#b49464','#e4c892'],['#588a93','#b3e7db'],['#245184','#8dbde2'],['#776b6c','#b6adb2']];
 const STORAGE='asherRealFlightPosition_v14';
 // Read saved flight progress before the first animation frame. Firebase sign-in
 // may take several seconds on Silk; never overwrite progress during that wait.
 function readSavedFlight(){
  try{const data=JSON.parse(localStorage.getItem(STORAGE)||'null');
   return data&&typeof data.shown==='number'&&Number.isFinite(data.shown)&&data.shown>=0?data:null;
  }catch{return null}
 }
 let savedFlight=readSavedFlight(),flightReady=false;
 let W=0,H=0,DPR=1,points=[],stars=[],shown=0,target=0;
 let cam={x:0,y:0,zoom:1},last=performance.now(),lastSave=0;
 let smoke=[],smokeClock=0,launchTime=0,ignitionHoldUntil=0,landingPause=0,visualAngle=0;
 let lastBadge='',ufoActive=false,lastSurprise=performance.now(),surpriseCount=0;
 let stoppedSince=performance.now(),wasMoving=false,flightHoldLast=!!state.flightHold;
 let previousRequested=false,retractStart=0,retractUntil=0,previousMode='hold';
 // Reopen where the rocket last was. Until Firebase confirms the bank,
 // keep this position unchanged rather than restarting from Earth.
 shown=savedFlight?.shown??0;
 target=state.earnedMinutes;
 const smokeSprite=document.createElement('canvas');smokeSprite.width=smokeSprite.height=72;
 const sc=smokeSprite.getContext('2d'),sg=sc.createRadialGradient(36,36,2,36,36,36);
 sg.addColorStop(0,'rgba(244,245,239,.82)');sg.addColorStop(.30,'rgba(221,230,232,.53)');sg.addColorStop(.65,'rgba(179,202,210,.15)');sg.addColorStop(1,'rgba(175,200,210,0)');sc.fillStyle=sg;sc.fillRect(0,0,72,72);
 function seeded(n){const x=Math.sin(n*127.1+3.21)*43758.5453;return x-Math.floor(x)}
 function legFor(minutes){let leg=0;while(leg<targets.length&&minutes>=milestones[leg+1]-.00001)leg++;return Math.min(leg,targets.length)}
 function landingBodyOffset(){const rocketHeight=Math.max(27,Math.min(40,Math.min(W,H)*.13));return rocketHeight*.39}
 function where(minutes){
  const leg=legFor(minutes);
  if(leg>=targets.length){const p=points[points.length-1];return {x:p.x,y:p.y-p.r-landingBodyOffset()+3,angle:0,leg,progress:1,landed:true}}
  const a=points[leg],b=points[leg+1];
  const t=clamp((minutes-milestones[leg])/(milestones[leg+1]-milestones[leg]));
  const at=u=>({x:lerp(a.x,b.x,u)+Math.sin(u*Math.PI)*W*.095*(leg%2?1:-1),y:lerp(a.y-a.r-landingBodyOffset()+3,b.y-b.r-landingBodyOffset()+3,smooth(u))});
  const p=at(t),p0=at(clamp(t-.004)),p1=at(clamp(t+.004));
  const angle=clamp(Math.atan2(p1.x-p0.x,-(p1.y-p0.y))*180/Math.PI,-28,28);
  const lift=smooth(t/.18),approach=1-smooth((t-.80)/.20);
  return {...p,angle:angle*lift*approach,leg,progress:t,landed:t<.00001};
 }
 function rebuild(){
  const b=viewport.getBoundingClientRect();if(!b.width||!b.height)return;
  W=b.width;H=b.height;DPR=Math.min(2,window.devicePixelRatio||1);
  canvas.width=Math.round(W*DPR);canvas.height=Math.round(H*DPR);
  points=names.map((name,i)=>({name,x:i===0?0:W*(i%2?.43:-.40),y:i===0?H*.57:H*(.57-i*1.17),r:Math.min(W,H)*[.18,.09,.135,.20,.18,.13,.12,.08][i]}));
  stars=Array.from({length:620},(_,i)=>({x:(seeded(i*2+1)-.5)*W*3.7,y:H*(.9-seeded(i*2+2)*10.5),r:.35+seeded(i+2000)*1.05,a:.28+seeded(i+900)*.7}));
  const pos=where(shown);cam.x=pos.x;cam.y=pos.y+H*.06;cam.zoom=shown<.001?2.3:shown<8?lerp(2.3,1.02,smooth(shown/8)):1.04;
 }
 if('ResizeObserver' in window)new ResizeObserver(rebuild).observe(viewport);
 window.addEventListener('resize',rebuild);rebuild();
 function paintPlanet(p,i){
  const [dark,light]=palette[i];ctx.save();ctx.translate(p.x,p.y);
  if(i===4){ctx.save();ctx.rotate(-.28);ctx.strokeStyle='#b9a68b99';ctx.lineWidth=p.r*.32;ctx.beginPath();ctx.ellipse(0,0,p.r*1.65,p.r*.45,0,0,Math.PI*2);ctx.stroke();ctx.restore()}
  const grad=ctx.createRadialGradient(-p.r*.32,-p.r*.38,p.r*.03,p.r*.2,p.r*.1,p.r*1.15);
  grad.addColorStop(0,light);grad.addColorStop(.52,dark);grad.addColorStop(1,'#080f1a');
  ctx.beginPath();ctx.arc(0,0,p.r,0,Math.PI*2);ctx.fillStyle=grad;ctx.fill();
  ctx.save();ctx.clip();
  if(i===0&&earthImg.complete&&earthImg.naturalWidth){ctx.globalAlpha=.62;ctx.drawImage(earthImg,-p.r,-p.r,p.r*2,p.r*2)}
  if(i===1||i===7){for(let c=0;c<7;c++){ctx.fillStyle='#242a3142';ctx.beginPath();ctx.arc((seeded(i*20+c)-.5)*p.r*1.55,(seeded(i*21+c+5)-.5)*p.r*1.6,p.r*(.07+seeded(c+11)*.12),0,Math.PI*2);ctx.fill()}}
  if(i===3||i===4){for(let k=0;k<6;k++){ctx.fillStyle=k%2?'#e4c09a29':'#442f2d55';ctx.fillRect(-p.r,-p.r+k*p.r*.38,p.r*2,p.r*.13)}}
  ctx.restore();ctx.strokeStyle='#b8d1d522';ctx.lineWidth=2;ctx.beginPath();ctx.arc(0,0,p.r,0,Math.PI*2);ctx.stroke();
  // A tiny physical launch platform sits exactly on Earth's curved horizon.
  if(i===0){ctx.save();ctx.fillStyle='#65717a';ctx.strokeStyle='#a7afb0';ctx.lineWidth=1.1;ctx.fillRect(-13,-p.r+1,26,3);ctx.strokeRect(-13,-p.r+1,26,3);ctx.fillStyle='#f5a260';ctx.fillRect(-12,-p.r,2.4,1.5);ctx.fillRect(9.6,-p.r,2.4,1.5);ctx.restore()}
  if(i===4){ctx.save();ctx.rotate(-.28);ctx.strokeStyle='#d4b78a80';ctx.lineWidth=p.r*.15;ctx.beginPath();ctx.ellipse(0,0,p.r*1.67,p.r*.45,0,0,Math.PI);ctx.stroke();ctx.restore()}
  ctx.font=`${Math.max(8,Math.min(12,W*.023))}px monospace`;ctx.textAlign='center';ctx.fillStyle='#e0c6a5aa';ctx.fillText(p.name,0,p.r+16);ctx.restore();
 }
 function drawRocket(p,angle,thrust){
  ctx.save();ctx.translate(p.x,p.y);ctx.rotate(angle*Math.PI/180);
  const rh=Math.max(27,Math.min(40,Math.min(W,H)*.13)),rw=rh*100/210;
  if(thrust>.02){const length=rh*(.22+.34*thrust),fg=ctx.createLinearGradient(0,rh*.38,0,rh*.4+length);
   fg.addColorStop(0,'#fff3c9');fg.addColorStop(.4,'#ffa24b');fg.addColorStop(1,'#f06e2a00');ctx.fillStyle=fg;
   ctx.beginPath();ctx.moveTo(-rw*.17,rh*.37);ctx.quadraticCurveTo(-rw*.23,rh*.55+length*.5,0,rh*.42+length);ctx.quadraticCurveTo(rw*.23,rh*.55+length*.5,rw*.17,rh*.37);ctx.fill()}
  if(rocketImg.complete&&rocketImg.naturalWidth)ctx.drawImage(rocketImg,-rw/2,-rh/2,rw,rh);
  else{ctx.fillStyle='#d9d4c9';ctx.beginPath();ctx.moveTo(0,-rh*.5);ctx.lineTo(-rw*.48,rh*.37);ctx.lineTo(rw*.48,rh*.37);ctx.closePath();ctx.fill()}
  ctx.restore();return {x:p.x-Math.sin(angle*Math.PI/180)*rh*.43,y:p.y+Math.cos(angle*Math.PI/180)*rh*.43,rh};
 }
 function addSmoke(n,angle,dt,thrust){
  smokeClock+=dt*(thrust>.45?21:thrust>.1?12:4);const r=angle*Math.PI/180;
  while(smokeClock>=1){smokeClock--;const spread=(Math.random()-.5)*4;
   smoke.push({x:n.x+spread,y:n.y+spread,vx:-Math.sin(r)*(6+Math.random()*5)+spread*.3,vy:Math.cos(r)*(6+Math.random()*5)-2,age:0,life:3.3+Math.random()*3.2,r:2+Math.random()*2.5,alpha:.38+Math.random()*.22})}
  if(smoke.length>240)smoke.splice(0,smoke.length-240);
 }
 function paintSmoke(dt){
  for(const s of smoke){s.age+=dt;s.x+=s.vx*dt;s.y+=s.vy*dt;const life=clamp(1-s.age/s.life);if(!life)continue;
   const r=s.r+s.age*.85;ctx.globalAlpha=s.alpha*Math.pow(life,1.4);ctx.drawImage(smokeSprite,s.x-r*1.7,s.y-r*1.7,r*3.4,r*3.4)}
  ctx.globalAlpha=1;smoke=smoke.filter(s=>s.age<s.life);
 }
 function drawAstronaut(p,nozzle,now,progress){
  // Hatch first, then a real exit along the tether; no one-frame pop-in.
  const e=smooth(progress);
  const hatchX=p.x+nozzle.rh*.18,hatchY=p.y-nozzle.rh*.07;
  const bob=Math.sin(now/1500)*3.3*e,sway=Math.cos(now/2300)*3.8*e;
  const x=hatchX+nozzle.rh*.73*e+sway,y=hatchY-nozzle.rh*.02*e+bob;
  ctx.save();ctx.lineCap='round';ctx.lineJoin='round';
  ctx.fillStyle='#1a242a';ctx.strokeStyle='#9e9583';ctx.lineWidth=1.1;
  ctx.beginPath();ctx.ellipse(hatchX,hatchY,4.2,6,0,0,Math.PI*2);ctx.fill();ctx.stroke();
  // Hatch door rotates away before the astronaut emerges.
  ctx.save();ctx.translate(hatchX,hatchY);ctx.rotate(-.95*e);
  ctx.fillStyle='#a9aba4';ctx.beginPath();ctx.ellipse(-2.5,0,2.5,5.5,0,0,Math.PI*2);ctx.fill();ctx.restore();
  if(e<.025){ctx.restore();return}
  ctx.strokeStyle='#c8b8a5';ctx.lineWidth=1.25;ctx.beginPath();ctx.moveTo(hatchX,hatchY);
  ctx.bezierCurveTo(hatchX+nozzle.rh*.30*e,hatchY+nozzle.rh*.60*e,x-nozzle.rh*.22*e,y+nozzle.rh*.40*e,x-2*e,y+6*e);ctx.stroke();
  ctx.translate(x,y);ctx.rotate(Math.sin(now/1900)*.16*e);ctx.scale(.16+.84*e,.16+.84*e);
  ctx.fillStyle='#c7c7b9';ctx.strokeStyle='#57616a';ctx.lineWidth=.8;
  ctx.fillRect(-3.5,0,7,9);ctx.strokeRect(-3.5,0,7,9);
  ctx.fillStyle='#858f92';ctx.fillRect(-5.5,1,2.5,6);
  ctx.beginPath();ctx.moveTo(-3,3);ctx.lineTo(-7,7);ctx.moveTo(3,3);ctx.lineTo(7,6);ctx.moveTo(-2,9);ctx.lineTo(-4,14);ctx.moveTo(2,9);ctx.lineTo(4,14);ctx.strokeStyle='#d4d1c7';ctx.lineWidth=2.2;ctx.stroke();
  ctx.fillStyle='#d9d5c7';ctx.beginPath();ctx.arc(0,-4.8,5.8,0,Math.PI*2);ctx.fill();ctx.strokeStyle='#53626b';ctx.lineWidth=1;ctx.stroke();
  const helmet=ctx.createLinearGradient(-4,-7,4,-1);helmet.addColorStop(0,'#6b9cb0');helmet.addColorStop(1,'#0d2637');ctx.fillStyle=helmet;
  ctx.beginPath();ctx.ellipse(0,-4.9,4.3,3.6,0,0,Math.PI*2);ctx.fill();ctx.restore();
 }
 function spawnUfo(){
  if(ufoActive)return;ufoActive=true;
  const el=document.createElement('div');el.className='original-ufo';el.setAttribute('aria-hidden','true');
  el.innerHTML='<svg viewBox="0 0 92 45"><path d="M23 25Q26 5 46 5Q65 5 69 25Z" fill="#657c85" stroke="#c2cac6" stroke-width="2"/><ellipse cx="46" cy="28" rx="43" ry="12" fill="#a5a8a2" stroke="#293a42" stroke-width="3"/><circle cx="20" cy="31" r="3" fill="#f6b36c"/><circle cx="46" cy="34" r="3" fill="#ec7762"/><circle cx="72" cy="31" r="3" fill="#83d3cb"/></svg>';
  el.style.top=`${18+Math.random()*36}%`;viewport.appendChild(el);
  const from=Math.random()>.5?-90:W+90,to=from<0?W+90:-90;
  const anim=el.animate([{transform:`translate(${from}px,0)`},{transform:`translate(${to}px,-14px)`}],{duration:8500,easing:'linear'});
  const done=()=>{el.remove();ufoActive=false};anim.onfinish=done;anim.oncancel=done;
 }
 function spawnOther(kind){
  const el=document.createElement('div');el.setAttribute('aria-hidden','true');el.className='space-surprise '+kind;
  if(kind==='satellite')el.textContent='✦';viewport.appendChild(el);
  const from=kind==='meteor'?W+30:-40,to=kind==='meteor'?-80:W+40;
  const anim=el.animate([{transform:`translate(${from}px,${H*.19}px) rotate(-24deg)`,opacity:0},{opacity:.85,offset:.2},{transform:`translate(${to}px,${H*.55}px) rotate(-24deg)`,opacity:0}],{duration:kind==='meteor'?1700:9600,easing:'linear'});
  anim.onfinish=()=>el.remove();anim.oncancel=()=>el.remove();
 }
 function surprise(){const n=surpriseCount++%3;if(n===0)spawnUfo();else spawnOther(n===1?'satellite':'meteor')}
 
 function frame(now){
  const dt=Math.min(.05,Math.max(0,(now-last)/1000));last=now;
  if(!W||!H||!flightReady){requestAnimationFrame(frame);return}
  if(state.earnedMinutes!==target){target=state.earnedMinutes;}
  // A spending session runs backwards along the SAME continuous path.
  // It stops at the previous landing site or when the session expires; never loops.
  const returning=state.flightMode==='return'||state.flightMode==='home';
  const goal=Math.max(0,Math.min(shown,Number(state.returnGoal)||0));
  const outbound=state.flightMode==='outbound';
  const hold=!!state.flightHold||state.flightMode==='hold';
  const requested=!hold&&((returning&&shown>goal+.0001)||(outbound&&shown<target-.0001));
  if(requested&&!previousRequested){
   // Engines ignite while still on the launchpad; the rocket doesn't jump off Earth.
   if(outbound&&where(shown).landed){ignitionHoldUntil=now+1550;launchTime=now;ignitionSound()}
   // If an EVA is underway, bring the astronaut back in before firing thrusters.
   const at=where(shown);
   if(!at.landed&&now-stoppedSince>2700){retractStart=now;retractUntil=now+1350}
   else{retractUntil=0;retractStart=0}
  }
  previousRequested=requested;
  const retracting=requested&&now<retractUntil;
  if((hold&&!flightHoldLast)||(!requested&&wasMoving))stoppedSince=now;
  flightHoldLast=hold;
  let moving=false;
  if(requested&&!retracting&&!((outbound)&&(now<ignitionHoldUntil))){
   if(returning){
    const before=shown;
    // Gently accelerate the return; decelerate as the landing site approaches.
    const elapsed=(Date.now()-state.returnStartedAt)/1000;
    const ramp=smooth(elapsed/2.4);
    const dist=shown-goal;
    const brake=dist<2?(.28+.72*smooth(dist/2)):1;
    const speed=Math.max(.08,Number(state.returnSpeed)||.35)*Math.max(.12,ramp)*brake;
    shown=Math.max(goal,shown-speed*dt);
    moving=shown<before-.0000001;
    if(shown<=goal+.0001){shown=goal;moving=false;if(state.flightMode==='home'&&state.bankSeconds>0&&state.earnedMinutes>0){state.flightMode='outbound';state.flightHold=false;}else{state.flightMode='hold';state.flightHold=true;}persist()}
   }else{
    const nextMilestone=milestones.find(m=>m>shown+.00001);
    if(landingPause>0&&milestones.some(m=>m>0&&Math.abs(shown-m)<.01))landingPause-=dt;
    else{
     const previous=shown;
     const speed=Math.max(.28,Math.min(2.1,(target-shown)/28));
     shown=Math.min(target,shown+speed*dt);moving=shown>previous+.0000001;
     if(nextMilestone&&shown>=nextMilestone-.00001){shown=nextMilestone;landingPause=2.8}
     if(previous>0&&milestones.some(m=>Math.abs(previous-m)<.01)&&shown>previous+.001){
      launchTime=now;ignitionSound();
     }
    }
   }
  }
  if(outbound&&!hold&&Math.abs(shown-target)<.0001)shown=target;
  if(moving)stoppedSince=now;
  wasMoving=moving;
  const pos=where(shown),parked=pos.landed&&!moving;
  const returnRange=Math.max(.01,(Number(state.returnFrom)||shown)-goal);
  const nearLanding=returning?smooth((Math.max(0,returnRange*.20-(shown-goal)))/(returnRange*.20)):0;
  const targetAngle=returning&&moving?lerp(180+pos.angle,0,nearLanding):pos.angle;
  const diff=((targetAngle-visualAngle+540)%360)-180;
  visualAngle+=diff*(1-Math.exp(-dt*2.0));
  const igniting=outbound&&requested&&now<ignitionHoldUntil;
  const justLaunched=(moving||igniting)&&now-launchTime<3300;
  const thrust=igniting?.82:moving?(justLaunched?.87:returning?.17:.24):0;
  const zoomTarget=shown<.001?2.3:shown<8?lerp(2.3,1.02,smooth(shown/8)):parked?1.22:1.02;
  const follow=1-Math.exp(-dt*1.25);
  cam.x=lerp(cam.x,pos.x,follow);
  cam.y=lerp(cam.y,pos.y+(shown<8?H*.06:H*.035),follow);
  cam.zoom=lerp(cam.zoom,zoomTarget,1-Math.exp(-dt*.65));
  ctx.setTransform(DPR,0,0,DPR,0,0);ctx.clearRect(0,0,W,H);
  ctx.fillStyle='#e8e5d7';for(const s of stars){const sx=(s.x-cam.x)*cam.zoom+W/2,sy=(s.y-cam.y)*cam.zoom+H/2;if(sx<0||sy<0||sx>W||sy>H)continue;ctx.globalAlpha=s.a;ctx.beginPath();ctx.arc(sx,sy,s.r,0,Math.PI*2);ctx.fill()}ctx.globalAlpha=1;
  ctx.save();ctx.translate(W/2,H/2);ctx.scale(cam.zoom,cam.zoom);ctx.translate(-cam.x,-cam.y);
  for(let i=0;i<points.length;i++){const p=points[i];if(Math.abs(p.y-cam.y)<H*1.5)paintPlanet(p,i)}
  paintSmoke(dt);
  const nozzle=drawRocket(pos,visualAngle,thrust);
  if(moving||igniting)addSmoke(nozzle,visualAngle,dt,thrust);
  const evaTime=now-stoppedSince;
  const evaProgress=retracting?clamp(1-(now-retractStart)/1350):clamp((evaTime-650)/1950);
  const astronautVisible=!parked&&shown>.15&&(!moving||retracting)&&evaProgress>0;
  if(astronautVisible)drawAstronaut(pos,nozzle,now,evaProgress);
  ctx.restore();
  if(now-lastSave>850){lastSave=now;saveFlight()}
  if(now-lastSurprise>33000){lastSurprise=now;if(Math.random()<.75)surprise()}
  const nextName=targets[Math.min(pos.leg,targets.length-1)]?.name||'DEEP SPACE';
  const label=parked?`LANDED ON ${points[Math.min(pos.leg,points.length-1)].name} • READY FOR NEXT LAUNCH`:
    moving?(returning?`RETURNING TO ${state.flightMode==='home'?'EARTH':points[Math.max(0,pos.leg)].name} • SMOOTH DESCENT`:`EN ROUTE TO ${nextName} • ${Math.round(shown)} FLIGHT MIN`):
    astronautVisible?`HOLDING IN SPACE • ASTRONAUT EVA • ${Math.round(shown)} FLIGHT MIN`:
    `HOLDING POSITION • ${Math.round(shown)} FLIGHT MIN`;
  if(label!==lastBadge){lastBadge=label;if($('flight-badge'))$('flight-badge').textContent=label}
  if(returning){
   const start=Number(state.returnFrom)||0;
   const progress=start>goal+.001?clamp((start-shown)/(start-goal)):1;
   if($('gauge-needle'))$('gauge-needle').setAttribute('transform',`rotate(${-65+130*progress} 110 103)`);
  }
  engineVolume(igniting?.65:moving?(justLaunched?.70:returning?.20:.27):0);
  requestAnimationFrame(frame);
 }
 function saveFlight(){
  if(!flightReady)return;
  try{localStorage.setItem(STORAGE,JSON.stringify({
   shown,bankMinutes:state.earnedMinutes,mode:state.flightMode,
   returnGoal:state.returnGoal,returnFrom:state.returnFrom,
   returnSpeed:state.returnSpeed,savedAt:Date.now()
  }))}catch{}
 }
 // Save immediately when Silk moves the page to the background. The regular
 // animation loop also checkpoints every 850ms while the page is visible.
 window.addEventListener('pagehide',saveFlight);
 document.addEventListener('visibilitychange',()=>{if(document.hidden)saveFlight()});
 window.__asherFlight={restoreFromBank:(earned,bank)=>{
  // Read once, BEFORE the first frame can save anything. Old v14 files only
  // stored {shown}; a legacy zero with a positive bank is likely the bugged
  // reset, so use the authoritative bank position rather than replay Earth.
  const saved=savedFlight;
  const hasBankSnapshot=typeof saved?.bankMinutes==='number'&&Number.isFinite(saved.bankMinutes);
  const legacyProgress=typeof saved?.shown==='number'&&saved.shown>.025;
  shown=hasBankSnapshot?Math.max(0,saved.shown):legacyProgress?saved.shown:earned;
  target=earned;smoke=[];rebuild();flightReady=true;
  // The first normal animation frame will save the resolved flight mode.
  return {position:shown,restored:hasBankSnapshot||legacyProgress,
   savedMode:hasBankSnapshot?saved.mode:null,
   savedReturnGoal:hasBankSnapshot?saved.returnGoal:null,
   savedReturnFrom:hasBankSnapshot?saved.returnFrom:null,
   savedReturnSpeed:hasBankSnapshot?saved.returnSpeed:null};
 },position:()=>shown,target:()=>target,location:()=>where(shown),smokeCount:()=>smoke.length,astronaut:()=>!wasMoving&&!where(shown).landed&&shown>.15,hold:()=>!!state.flightHold,mode:()=>state.flightMode,launching:()=>performance.now()<ignitionHoldUntil};
 requestAnimationFrame(frame);
}

wire();if(ROLE==='child')initRocket();
})();
