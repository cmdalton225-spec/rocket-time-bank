/* Real parent dashboard: authenticated Firebase account + local convenience PIN. */
import {auth,db,doc,getDoc,onAuthStateChanged,signInWithEmailAndPassword,signOut,onSnapshot,collection,query,where,serverTimestamp,setDoc} from './firebase.js';
import {runTransaction} from 'https://www.gstatic.com/firebasejs/12.4.0/firebase-firestore.js';
const $=id=>document.getElementById(id),live=window.asherLive;
const bankRef=doc(db,'timeBank','main');
const authOverlay=$('dad-auth-overlay'),authMessage=$('dad-auth-message'),pinOverlay=$('dad-pin-lock'),pinDigits=$('dad-pin-digits'),pinHint=$('dad-pin-hint');
const linkOverlay=$('dad-link-overlay');
let currentBank=null,pending=[],bankUnsub=null,requestsUnsub=null,bankClockInterval=null,authorized=false,unlocked=false;
let pin='',setupStep=0,firstPin='';
const PIN_KEY='asherDadPinHash_v11';
function setStatus(message){live.toast(message);}
function elapsed(data){return data?.running&&data.lastChargedAt?.toMillis?Math.max(0,Math.floor((Date.now()-data.lastChargedAt.toMillis())/1000)):0;}
function effective(data){const spent=Math.min(elapsed(data),Math.max(0,Number(data.bankSeconds)||0),Math.max(0,Number(data.sessionSeconds)||0));return {...data,bankSeconds:Math.max(0,(Number(data.bankSeconds)||0)-spent),sessionSeconds:Math.max(0,(Number(data.sessionSeconds)||0)-spent)};}
function refresh(){if(currentBank)live.setBank(currentBank,effective(currentBank));}
function showRequest(){const p=pending[0];$('request-body').textContent=p?`${p.data.type==='bank_change'?'BANK':p.data.type==='session_change'?'SPEND':p.data.type.replace('_',' ').toUpperCase()} • ${p.data.minutes} MIN`:'NO PENDING REQUESTS';$('approve-request').disabled=!p||!unlocked;$('deny-request').disabled=!p||!unlocked;live.setPending(!!p);}
function clearWatch(){bankUnsub?.();requestsUnsub?.();clearInterval(bankClockInterval);bankClockInterval=null;bankUnsub=requestsUnsub=null;currentBank=null;pending=[];live.setAuthorized(false);showRequest();}
function watch(){clearWatch();bankUnsub=onSnapshot(bankRef,snap=>{if(!snap.exists()){setStatus('Time Bank missing. Open the original working dashboard to initialize it.');return;}currentBank=snap.data({serverTimestamps:'estimate'});refresh();},err=>{console.error(err);setStatus('Could not read bank: '+err.message)});
 requestsUnsub=onSnapshot(query(collection(db,'requests'),where('status','==','pending')),snap=>{pending=snap.docs.map(d=>({id:d.id,data:d.data()})).sort((a,b)=>(a.data.createdAt?.seconds||0)-(b.data.createdAt?.seconds||0));showRequest();},err=>{console.error(err);setStatus('Could not load requests: '+err.message)});
 live.setAuthorized(true);bankClockInterval=setInterval(refresh,1000);
}
function resetLock(){unlocked=false;pin='';firstPin='';setupStep=0;pinOverlay.hidden=false;document.body.classList.add('dad-locked');updatePinDisplay();showRequest();}
function updatePinDisplay(){pinDigits.textContent='●'.repeat(pin.length)+'○'.repeat(4-pin.length);pinHint.textContent=localStorage.getItem(PIN_KEY)?'ENTER YOUR PIN':setupStep?'RE-ENTER PIN TO CONFIRM':'SET YOUR PIN • THEN CONFIRM IT';}
async function pinHash(salt,p){const bytes=new TextEncoder().encode(salt+':'+p);const digest=await crypto.subtle.digest('SHA-256',bytes);return Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');}
async function enterPin(k){if(!authorized)return;if(k==='C')pin='';else if(k==='back')pin=pin.slice(0,-1);else if(pin.length<4&&/^\d$/.test(k))pin+=k;updatePinDisplay();if(pin.length!==4)return;
 const value=pin;pin='';
 const stored=localStorage.getItem(PIN_KEY);
 if(!stored){if(!setupStep){firstPin=value;setupStep=1;pinHint.textContent='RE-ENTER PIN TO CONFIRM';setTimeout(updatePinDisplay,350);return;}
  if(value!==firstPin){setupStep=0;firstPin='';pinHint.textContent='CODES DID NOT MATCH • TRY AGAIN';setTimeout(updatePinDisplay,1200);return;}
  const salt=Array.from(crypto.getRandomValues(new Uint8Array(16)),n=>n.toString(16).padStart(2,'0')).join('');localStorage.setItem(PIN_KEY,JSON.stringify({salt,hash:await pinHash(salt,value)}));setupStep=0;
 }else{let item;try{item=JSON.parse(stored)}catch{localStorage.removeItem(PIN_KEY);updatePinDisplay();return;}
  if(await pinHash(item.salt,value)!==item.hash){pinHint.textContent='WRONG PIN • TRY AGAIN';setTimeout(updatePinDisplay,1000);return;}
 }
 unlocked=true;pinOverlay.hidden=true;document.body.classList.remove('dad-locked');showRequest();setStatus('Dad Control unlocked.');
}
pinOverlay.querySelectorAll('[data-pin-key]').forEach(b=>b.addEventListener('click',()=>enterPin(b.dataset.pinKey)));
$('dad-lock').addEventListener('click',resetLock);
window.addEventListener('pageshow',e=>{if(e.persisted&&authorized)resetLock()});
$('dad-login-form').addEventListener('submit',async e=>{e.preventDefault();authMessage.textContent='Signing in…';try{await signInWithEmailAndPassword(auth,$('dad-email').value,$('dad-password').value);$('dad-password').value='';}catch(err){authMessage.textContent='Sign-in failed. Check your email and password.';console.error(err);}});
async function dadSignOut(){resetLock();await signOut(auth);setStatus('Signed out.')}
$('dad-signout').addEventListener('click',dadSignOut);$('dad-signout-footer').addEventListener('click',dadSignOut);
onAuthStateChanged(auth,async user=>{clearWatch();authorized=false;unlocked=false;pinOverlay.hidden=false;authOverlay.hidden=false;
 if(!user){authMessage.textContent='Sign in to access Dad Control.';return;}
 if(user.isAnonymous){authMessage.textContent='This is a child account. Sign in as Dad.';return;}
 try{await getDoc(bankRef);if(auth.currentUser?.uid!==user.uid)return;authorized=true;authOverlay.hidden=true;resetLock();watch();}catch(e){console.error(e);authMessage.textContent='Parent access denied. Check Firebase permissions.';}
});
async function resolve(approved){if(!authorized||!unlocked||!pending[0])return;const {id}=pending[0],ref=doc(db,'requests',id),historyRef=doc(collection(db,'history'));$('approve-request').disabled=true;$('deny-request').disabled=true;
 try{await runTransaction(db,async tx=>{
  const rs=await tx.get(ref),bs=await tx.get(bankRef);if(!rs.exists()||rs.data().status!=='pending')throw Error('Request already handled.');if(!bs.exists())throw Error('Time Bank missing.');
  const req=rs.data(),bank=bs.data(),m=Number(req.minutes),changes={};
  if(approved){
   if(req.type==='bank_change'){
    if(!Number.isInteger(m)||m===0)throw Error('Invalid bank request.');if(bank.running)throw Error('Pause playtime before changing bank minutes.');
    const next=(Number(bank.bankSeconds)||0)+m*60;if(next<0)throw Error('Not enough banked minutes.');changes.bankSeconds=next;
    if(m>0)changes.earnedMinutesTotal=(Number(bank.earnedMinutesTotal)||0)+m;
   }else if(req.type==='session_change'){
    if(!Number.isInteger(m)||m===0)throw Error('Invalid SPEND request.');if(bank.running)throw Error('Pause playtime before changing session.');
    const next=Math.max(0,(Number(bank.sessionSeconds)||0)+m*60);if(next>(Number(bank.bankSeconds)||0))throw Error('Not enough banked minutes.');
    changes.sessionSeconds=next;changes.sessionStatus=next>0?'approved':'idle';
   }else if(req.type==='reset_session'){if(bank.running)throw Error('Stop the session first.');Object.assign(changes,{sessionSeconds:0,sessionStatus:'idle',running:false});}
   else if(req.type==='start_session'){if(bank.running)throw Error('Already playing.');if((Number(bank.bankSeconds)||0)<=0||(Number(bank.sessionSeconds)||0)<=0)throw Error('No time to play.');Object.assign(changes,{sessionStatus:'approved',running:false});}
   else throw Error('Unsupported request type.');
  }
  tx.update(ref,{status:approved?'approved':'denied',resolvedAt:serverTimestamp(),resolvedByUid:auth.currentUser.uid});
  if(approved)tx.update(bankRef,{...changes,updatedAt:serverTimestamp()});
  tx.set(historyRef,{description:(approved?'Approved: ':'Denied: ')+req.type+' '+m+' min',createdAt:serverTimestamp()});
 });setStatus(approved?'Request approved!':'Request denied.');}catch(e){console.error(e);setStatus('Could not process request: '+e.message);}finally{showRequest();}
}
$('approve-request').addEventListener('click',()=>resolve(true));$('deny-request').addEventListener('click',()=>resolve(false));
window.dadFirebaseAdjust=async minutes=>{if(!authorized||!unlocked)return;if(!Number.isInteger(minutes)||minutes===0||Math.abs(minutes)>240||Math.abs(minutes)%5!==0)return setStatus('Enter 5–240 minutes in steps of five.');
 try{await runTransaction(db,async tx=>{const s=await tx.get(bankRef);if(!s.exists())throw Error('Bank missing.');const d=s.data();if(d.running)throw Error('Stop playtime before adjusting the bank.');const next=(Number(d.bankSeconds)||0)+minutes*60;if(next<0)throw Error('Not enough banked minutes.');const changes={bankSeconds:next,updatedAt:serverTimestamp()};if(minutes>0)changes.earnedMinutesTotal=(Number(d.earnedMinutesTotal)||0)+minutes;if(next===0&&d.sessionStatus==='approved')Object.assign(changes,{sessionSeconds:0,sessionStatus:'idle'});tx.update(bankRef,changes);});setStatus(minutes>0?'Bank time added.':'Bank time removed.');}catch(e){console.error(e);setStatus('Could not adjust time: '+e.message);}
};
// Clear bank is a deliberate Dad-only action, independent of keypad limits.
// It does NOT erase earnedMinutesTotal: the rocket returns home when bankSeconds
// reaches zero, and the earned-journey milestones remain recorded.
let clearingBank=false;
$('clear-bank').addEventListener('click',async()=>{
 if(!authorized||!unlocked||clearingBank)return;
 const remaining=currentBank?effective(currentBank).bankSeconds:0;
 const minutes=Math.floor(remaining/60),seconds=Math.floor(remaining%60);
 const detail=`${minutes} minute${minutes===1?'':'s'}${seconds?' and '+seconds+' seconds':''}`;
 if(!confirm(`CLEAR ASHER'S TIME BANK?\n\nThis will remove ${detail} of saved time and end any current play session. The rocket will return to Earth.\n\nThis cannot be undone. Continue?`))return;
 clearingBank=true;
 const button=$('clear-bank');button.disabled=true;
 try{
  await runTransaction(db,async tx=>{
   const snap=await tx.get(bankRef);
   if(!snap.exists())throw Error('Time Bank missing.');
   tx.update(bankRef,{
    bankSeconds:0,
    sessionSeconds:0,
    running:false,
    sessionStatus:'idle',
    lastChargedAt:serverTimestamp(),
    updatedAt:serverTimestamp()
   });
  });
  setStatus('Time Bank cleared. Rocket returning to Earth.');
 }catch(error){console.error(error);setStatus('Could not clear bank: '+error.message)}
 finally{clearingBank=false;button.disabled=false}
});
window.dadFirebaseStop=async()=>{if(!authorized||!unlocked)return;if(!confirm('Stop Asher\'s current session?'))return;try{await runTransaction(db,async tx=>{const s=await tx.get(bankRef);if(!s.exists())return;const d=s.data(),bank=Math.max(0,Number(d.bankSeconds)||0),session=Math.max(0,Number(d.sessionSeconds)||0),spent=Math.min(elapsed(d),bank,session);tx.update(bankRef,{bankSeconds:Math.floor(bank-spent),sessionSeconds:0,running:false,sessionStatus:'idle',lastChargedAt:serverTimestamp(),updatedAt:serverTimestamp()});});setStatus('Session stopped.');}catch(e){console.error(e);setStatus('Could not stop: '+e.message);}};
function openLink(){if(!unlocked)return;linkOverlay.hidden=false;}
$('link-device-open').addEventListener('click',openLink);$('dad-link-footer').addEventListener('click',openLink);$('link-device-close').addEventListener('click',()=>linkOverlay.hidden=true);
$('linkDevice').addEventListener('click',async()=>{if(!authorized||!unlocked)return;const uid=$('childUidInput').value.trim();if(!/^[A-Za-z0-9]{20,128}$/.test(uid)){$('linkMessage').textContent='Enter the full Device ID.';return;}try{await setDoc(doc(db,'approvedChildren',uid),{label:"Asher's device",linkedByUid:auth.currentUser.uid,linkedAt:serverTimestamp()});$('linkMessage').textContent='Device linked! Asher should connect automatically.';}catch(e){console.error(e);$('linkMessage').textContent='Could not link: '+e.message;}});
