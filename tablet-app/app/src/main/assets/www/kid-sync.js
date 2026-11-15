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
let requestId=localStorage.getItem('asher_pending_request')||'';
const status=message=>live.toast(message);
function connected(){return approved&&auth.currentUser?.isAnonymous&&bankData!==null;}
function elapsed(data){return data?.running&&data.lastChargedAt?.toMillis?Math.max(0,Math.floor((Date.now()-data.lastChargedAt.toMillis())/1000)):0;}
function effective(data){const spent=Math.min(elapsed(data),Math.max(0,Number(data.bankSeconds)||0),Math.max(0,Number(data.sessionSeconds)||0));return {...data,bankSeconds:Math.max(0,(Number(data.bankSeconds)||0)-spent),sessionSeconds:Math.max(0,(Number(data.sessionSeconds)||0)-spent)};}
function refresh(){if(bankData)live.setBank(bankData,effective(bankData));}
function watchBank(){bankUnsub?.();bankUnsub=onSnapshot(bankRef,snap=>{if(!snap.exists()){bankData=null;live.setLinked(false);connection.textContent='Dad must create the Time Bank first.';return;}bankData=snap.data({serverTimestamps:'estimate'});live.setLinked(true);connection.textContent='Connected to Dad';refresh();},err=>{console.error(err);bankData=null;live.setLinked(false);connection.textContent='Could not read the Time Bank.';});}
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
async function updateSession(action){if(!connected()||clockBusy)return;clockBusy=true;
 try{await runTransaction(db,async tx=>{
  const snap=await tx.get(bankRef);if(!snap.exists())throw Error('Time Bank missing.');
  const data=snap.data(),bank=Math.max(0,Math.floor(Number(data.bankSeconds)||0)),session=Math.max(0,Math.floor(Number(data.sessionSeconds)||0));
  const state=data.sessionStatus||'idle';
  if(action==='start'){
   if(data.running)return;
   if(!['approved','paused'].includes(state)||bank<=0||session<=0)throw Error('Dad must approve SPEND time first.');
   tx.update(bankRef,{running:true,sessionStatus:'running',lastChargedAt:serverTimestamp(),updatedAt:serverTimestamp()});return;
  }
  if(!data.running&&(action==='pause'||action==='charge'))return;
  if(!data.running&&action==='end'&&state==='idle')return;
  const spent=data.running?Math.min(elapsed(data),bank,session):0;
  const remainingBank=bank-spent,remainingSession=session-spent;
  const finished=remainingBank<=0||remainingSession<=0,ending=action==='end'||finished;
  tx.update(bankRef,{bankSeconds:remainingBank,sessionSeconds:ending?0:remainingSession,running:action==='charge'&&!finished,sessionStatus:ending?'idle':action==='pause'?'paused':'running',lastChargedAt:serverTimestamp(),updatedAt:serverTimestamp()});
 });if(action!=='charge')status(action==='start'?'Playing!':action==='pause'?'Session paused.':'Session ended.');}
 catch(e){console.error(e);status('Session update failed: '+e.message);}finally{clockBusy=false;}
}
window.asherFirebaseSession=updateSession;
setInterval(()=>{if(!connected())return;refresh();if(bankData.running&&!clockBusy&&elapsed(bankData)>=5)updateSession('charge');},1000);
