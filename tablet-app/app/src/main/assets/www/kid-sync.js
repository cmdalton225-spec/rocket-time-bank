/* Real child-device Firebase sync. Parent approval and time are enforced by Firestore rules. */
import {firebaseConfig} from './firebase.js';
import {initializeApp} from 'https://www.gstatic.com/firebasejs/12.4.0/firebase-app.js';
import {getAuth,onAuthStateChanged,signInAnonymously} from 'https://www.gstatic.com/firebasejs/12.4.0/firebase-auth.js';
import {getFirestore,doc,onSnapshot,collection,addDoc,serverTimestamp,runTransaction} from 'https://www.gstatic.com/firebasejs/12.4.0/firebase-firestore.js';

const childApp=initializeApp(firebaseConfig,'asher-child');
const auth=getAuth(childApp),db=getFirestore(childApp),bankRef=doc(db,'timeBank','main');
const pairing=document.getElementById('devicePairing');
const uidEl=document.getElementById('deviceUid');
const connection=document.getElementById('connectionNotice');
const live=window.asherLive;
let approved=false,bankData=null,bankUnsub=null,requestUnsub=null,approvalUnsub=null;
let busy=false,clockBusy=false;
// TABLET FIX (Nov 20): play time is measured on Firestore's clock, not the tablet's. lastChargedAt is a
// server timestamp, so Date.now()-lastChargedAt was off by the tablet's clock error: a tablet ~5 s fast
// saw 5 s "already elapsed" the moment each charge landed, charged again every tick, and drained the
// bank ~5x real time. clockOffsetMs is learned from our own commits (server time minus local time,
// lowest round trip wins). carrySec is the play time since the previous charge that the last write
// didn't cover (whole seconds are written; the remainder and the commit delay ride along), so every
// second is charged exactly once and the countdown is continuous across charges. It is never capped: after a
// long gap (Android closed the app mid-flight) the whole gap is still owed; each charge spends at most the balances.
let clockOffsetMs=0,offsetRttMs=Infinity,carrySec=0,carryAtMs=null,pendingCharge=null,shownKey='',lastChargeTry=0;
const CHARGE_EVERY=5;
let requestId=localStorage.getItem('asher_pending_request')||'';
const status=message=>live.toast(message);
function connected(){return approved&&auth.currentUser?.isAnonymous&&bankData!==null;}
function serverNow(){return Date.now()+clockOffsetMs;}
function offsetKnown(){return offsetRttMs<Infinity;}
function elapsedExact(data){const at=data?.running&&data.lastChargedAt?.toMillis?data.lastChargedAt.toMillis():null;if(at===null)return 0;return Math.max(0,(serverNow()-at)/1000+(at===carryAtMs?carrySec:0));}
function elapsed(data){return Math.floor(elapsedExact(data));}
// The commit happened between sending it and the first sign it landed (our snapshot or the ack, whichever came first).
function offsetSample(p){const back=Math.min(p.ackAt??Infinity,p.seenAt??Infinity);if(p.sampled||p.atMs==null||back===Infinity)return;p.sampled=true;const rtt=back-p.sentAt;if(rtt>=0&&rtt<offsetRttMs){offsetRttMs=rtt;clockOffsetMs=p.atMs-(p.sentAt+back)/2;}}
function settleCharge(data){const p=pendingCharge,at=data?.lastChargedAt?.toMillis?.();if(!p||p.settled||at==null||at===p.fromMs)return;p.settled=true;p.seenAt=Date.now();
 if(data.bankSeconds===p.bank&&data.sessionSeconds===p.session&&!!data.running===p.running){p.atMs=at;carryAtMs=at;carrySec=p.running&&!p.start&&p.fromMs!=null?(at-p.fromMs)/1000+p.carryIn-p.spent:0;offsetSample(p);}
 else{carryAtMs=null;carrySec=0;}outstandingChanged();}
function effective(data){const spent=Math.min(elapsed(data),Math.max(0,Number(data.bankSeconds)||0),Math.max(0,Number(data.sessionSeconds)||0));return {...data,bankSeconds:Math.max(0,(Number(data.bankSeconds)||0)-spent),sessionSeconds:Math.max(0,(Number(data.sessionSeconds)||0)-spent)};}
// Re-render only when a shown number changes; the 200 ms loop below makes each second tick on time.
// A running flight isn't drawn until this tablet's clock offset is known (well under a second after the app opens), so a
// wrong tablet clock never flashes a wrong countdown; if the calibration write can't land, draw anyway after 3 s.
let uncalibratedSince=0;
function refresh(force){if(!bankData)return;if(bankData.running&&!offsetKnown()){uncalibratedSince||=Date.now();if(Date.now()-uncalibratedSince<3000)return;}const eff=effective(bankData),key=eff.bankSeconds+'|'+eff.sessionSeconds+'|'+!!bankData.running+'|'+bankData.sessionStatus;if(!force&&key===shownKey)return;shownKey=key;live.setBank(bankData,eff);}
function watchBank(){bankUnsub?.();bankUnsub=onSnapshot(bankRef,snap=>{if(!snap.exists()){bankData=null;live.setLinked(false);connection.textContent='Dad must create the Time Bank first.';return;}bankData=snap.data({serverTimestamps:'estimate'});settleCharge(bankData);live.setLinked(true);connection.textContent='Connected to Dad';refresh(true);},err=>{console.error(err);bankData=null;live.setLinked(false);connection.textContent='Could not read the Time Bank.';});}
function watchRequest(id){requestUnsub?.();requestUnsub=null;if(!id){live.setPending(false);return;}requestUnsub=onSnapshot(doc(db,'requests',id),snap=>{if(!snap.exists()){requestId='';localStorage.removeItem('asher_pending_request');live.setPending(false);return;}const s=snap.data().status;if(s==='approved'||s==='denied'){requestId='';localStorage.removeItem('asher_pending_request');requestUnsub?.();requestUnsub=null;live.setPending(false);status(s==='approved'?'Dad approved your request!':'Dad denied that request.');}else{live.setPending(true);}},err=>{console.error('Request watch',err);status('Unable to check request status.');});}
onAuthStateChanged(auth,async user=>{
 approvalUnsub?.();bankUnsub?.();bankUnsub=null;bankData=null;approved=false;live.setLinked(false);
 if(!user){try{await signInAnonymously(auth)}catch(e){console.error(e);pairing.hidden=false;connection.textContent='Child sign-in failed. Check internet.';}return;}
 if(!user.isAnonymous){pairing.hidden=false;connection.textContent='This device needs a child account.';return;}
 uidEl.textContent=user.uid;localStorage.setItem('asher_child_uid',user.uid);
 connection.textContent='Checking device approval…';pairing.hidden=false;
 approvalUnsub=onSnapshot(doc(db,'approvedChildren',user.uid),snap=>{
  approved=snap.exists();pairing.hidden=approved;
  if(approved){connection.textContent='Device linked to Dad';watchBank();if(requestId)watchRequest(requestId)}
  else{bankUnsub?.();bankUnsub=null;bankData=null;live.setLinked(false);connection.textContent='Dad needs to link this device once.';}
 },err=>{console.error(err);pairing.hidden=false;connection.textContent='Could not check device approval.';});
});
document.getElementById('copyDeviceUid').addEventListener('click',async()=>{try{await navigator.clipboard.writeText(uidEl.textContent);status('Device ID copied.')}catch{status('Select the Device ID and copy it manually.')}});
window.asherFirebaseRequest=async(type,minutes)=>{
 if(!connected())return status('Waiting for Dad to link this device.');
 if(busy||requestId)return status('Wait for Dad to answer the previous request.');
 if(!['bank_change','session_change'].includes(type)||!Number.isInteger(minutes)||minutes<5||minutes>240||minutes%5!==0)return status('Choose 5–240 minutes in steps of five.');
 if(type==='session_change'&&(effective(bankData).bankSeconds<minutes*60||bankData.sessionStatus!=='idle'))return status('Not enough saved time, or a session is already prepared.');
 busy=true;try{const ref=await addDoc(collection(db,'requests'),{childUid:auth.currentUser.uid,type,minutes,status:'pending',createdAt:serverTimestamp()});requestId=ref.id;localStorage.setItem('asher_pending_request',requestId);watchRequest(requestId);status('Request sent to Dad.');}catch(e){console.error(e);status('Request failed: '+e.message);}finally{busy=false;}
};
async function updateSession(action){if(!connected()||clockBusy)return;clockBusy=true;let mine=null;
 try{await runTransaction(db,async tx=>{
  const snap=await tx.get(bankRef);if(!snap.exists())throw Error('Time Bank missing.');
  const data=snap.data(),bank=Math.max(0,Math.floor(Number(data.bankSeconds)||0)),session=Math.max(0,Math.floor(Number(data.sessionSeconds)||0));
  const state=data.sessionStatus||'idle';
  if(action==='start'){
   if(data.running)return;
   if(!['approved','paused'].includes(state)||bank<=0||session<=0)throw Error('Dad must approve SPEND time first.');
   mine=pendingCharge={start:true,fromMs:null,carryIn:0,spent:0,bank,session,running:true,sentAt:Date.now()};
   tx.update(bankRef,{running:true,sessionStatus:'running',lastChargedAt:serverTimestamp(),updatedAt:serverTimestamp()});return;
  }
  if(!data.running&&(action==='pause'||action==='charge'))return;
  if(!data.running&&action==='end'&&state==='idle')return;
  // Until this tablet has measured its clock offset, a charge writes 0 s: it only calibrates the clock and
  // starts the carry, so a wrong tablet clock can never spend Asher's time.
  const fromMs=data.lastChargedAt?.toMillis?.()??null,carryIn=fromMs!==null&&fromMs===carryAtMs?carrySec:0;
  const spent=data.running?Math.min(action==='charge'&&!offsetKnown()?0:elapsed(data),bank,session):0;
  const remainingBank=bank-spent,remainingSession=session-spent;
  const finished=remainingBank<=0||remainingSession<=0,ending=action==='end'||finished;
  mine=pendingCharge={start:false,fromMs:data.running?fromMs:null,carryIn,spent,bank:remainingBank,session:ending?0:remainingSession,running:action==='charge'&&!finished,sentAt:Date.now()};outstandingChanged();
  tx.update(bankRef,{bankSeconds:remainingBank,sessionSeconds:ending?0:remainingSession,running:action==='charge'&&!finished,sessionStatus:ending?'idle':action==='pause'?'paused':'running',lastChargedAt:serverTimestamp(),updatedAt:serverTimestamp()});
 });if(mine){mine.ackAt=Date.now();offsetSample(mine);}if(action!=='charge')status(action==='start'?'Playing!':action==='pause'?'Session paused.':'Session ended.');}
 catch(e){console.error(e);status('Session update failed: '+e.message);}finally{clockBusy=false;}
}
window.asherFirebaseSession=updateSession;
// Flown time not yet written (keyed to the lastChargedAt it follows), for the widget's Pause on the tablet:
// after a late reopen the calibration charge leaves the whole gap here, and a widget Pause must still charge it.
window.asherOutstanding=()=>({atMs:carryAtMs,sec:carrySec,pending:pendingCharge&&!pendingCharge.settled&&!pendingCharge.start&&pendingCharge.running&&pendingCharge.fromMs!==null?
 {fromMs:pendingCharge.fromMs,carryIn:pendingCharge.carryIn,spent:pendingCharge.spent,bank:pendingCharge.bank,session:pendingCharge.session}:null});
// ...published the moment it changes: a charge about to commit (if it lands, the widget can still work out what it
// left owed) and a charge that landed (its carry).
function outstandingChanged(){try{window.asherOutstandingChanged?.()}catch(e){}}
setInterval(()=>{if(!connected())return;refresh();if(!bankData.running||clockBusy||Date.now()-lastChargeTry<1000)return;
 const e=elapsed(bankData),left=Math.min(Number(bankData.bankSeconds)||0,Number(bankData.sessionSeconds)||0);
 if(!offsetKnown()||e>=CHARGE_EVERY||(e>0&&e>=left)){lastChargeTry=Date.now();updateSession('charge');}},200);
