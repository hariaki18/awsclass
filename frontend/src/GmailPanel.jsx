import {useEffect,useState} from 'react';
const noticeFor={connected:'Company Gmail connected. External sending is now available for Webmail users.',denied:'Gmail access was not granted, so nothing was connected.',error:'Gmail could not be connected. Please try again.'};
export function gmailNotice(code){return noticeFor[code]||'';}
export default function GmailPanel({api}){
 const [state,setState]=useState(null),[notice,setNotice]=useState(()=>{try{const value=sessionStorage.getItem('wm_gmail_notice')||'';sessionStorage.removeItem('wm_gmail_notice');return value;}catch{return '';}}),[busy,setBusy]=useState(false);
 const load=()=>api('/gmail/status').then(setState).catch(error=>setNotice(error.message));
 useEffect(()=>{load();},[]);
 async function connect(){setBusy(true);setNotice('');try{const result=await api('/gmail/connect');window.location.assign(result.url);}catch(error){setNotice(error.message);setBusy(false);}}
 async function syncNow(){setBusy(true);setNotice('');try{const result=await api('/gmail/sync',{method:'POST'});setState(result);setNotice(result.imported?'Imported '+result.imported+' new message(s).':'Already up to date.');}catch(error){setNotice(error.message);load();}finally{setBusy(false);}}
 async function disconnect(){if(!window.confirm('Disconnect Gmail? Messages already imported stay in your Inbox, but new Gmail mail will stop arriving and external sending will stop.'))return;setBusy(true);setNotice('');try{await api('/gmail/disconnect',{method:'POST'});setNotice('Gmail disconnected.');await load();}catch(error){setNotice(error.message);}finally{setBusy(false);}}
 const lastSynced=state?.lastSyncAt?new Date(state.lastSyncAt).toLocaleString():'not yet';
 return <section className="wm-security" aria-label="Gmail connection"><h2>Gmail connection</h2>
  {!state?<p>Loading…</p>:!state.configured?<p>Gmail sending and receiving is not enabled on this server. An administrator must start the backend with the <code>gmail-api</code> profile and Google OAuth credentials.</p>
  :state.connected?<>
    <p>Connected as <strong>{state.gmailAddress}</strong> · Last synced: {lastSynced}</p>
    {state.needsReconnect&&<p role="alert">Google access expired or was revoked. Disconnect, then connect Gmail again.</p>}
    {state.syncFailed&&<p role="alert">The last sync failed. It will retry automatically.</p>}
    <p>Mail sent to external addresses goes out from this company Gmail account. Replies to Webmail messages return to the original sender's Inbox; unthreaded Gmail Inbox and Spam mail goes to the company Inbox.</p>
    {state.canManage&&<div><button className="btn" disabled={busy} onClick={syncNow}>Sync now</button> <button className="btn" disabled={busy} onClick={disconnect}>Disconnect Gmail</button></div>}
  </>:<>
    {state.canManage?<><p>Not connected. Link the one company Gmail or Google Workspace account. All Webmail users can then send external mail through it and replies sync into this workspace. You will approve access on Google’s own page; this app never sees your Google password.</p><button className="wm-primary" disabled={busy} onClick={connect}>{busy?'Opening Google…':'Connect company Gmail'}</button></>:<p>Company Gmail is not connected yet. The configured Webmail administrator must connect it once; users do not connect personal Gmail accounts.</p>}
  </>}
  {notice&&<p role="status">{notice}</p>}</section>;
}
