// Regression + optional live check for the Gmail API / OAuth integration.
//   Offline (default):  node check-gmail-api.mjs
//       Needs the backend running with the local-mail profile (same prerequisites as check-mail-modules.mjs).
//       Works whether or not the gmail-api profile is enabled; it never contacts Google and needs no Gmail account.
//   Live (real Gmail):  GMAIL_LIVE_EMAIL=name@xpertsedge.com GMAIL_LIVE_PASSWORD=... node check-gmail-api.mjs
//       The mailbox must already be connected through Settings > Connect Gmail. The check sends a message to the
//       connected Gmail address itself and waits for Gmail sync to deliver it to the app Inbox.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const base=process.env.LOCAL_MAIL_TEST_URL||'http://127.0.0.1:8080',origin='http://127.0.0.1:5173';
let checks=0;
async function raw(path,{cookie='',body,method='GET',redirect='follow'}={}){
  const r=await fetch(base+'/api'+path,{method,redirect,headers:{Origin:origin,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:body?JSON.stringify(body):undefined});
  const text=await r.text();let data=null;try{data=JSON.parse(text);}catch{}
  return {status:r.status,data,text,location:r.headers.get('location')||'',cookie:r.headers.get('set-cookie')?.split(';')[0]||cookie};
}
async function req(path,opts={},status=200){const r=await raw(path,opts);assert.equal(r.status,status,path+': '+r.text.slice(0,300));checks++;return r;}
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function offline(){
  const prefix='gm'+randomUUID().replaceAll('-','').slice(0,10),password='LocalOnly@'+randomUUID();let adminCookie='',id=null;
  try{
    adminCookie=(await req('/auth/login',{body:{email:process.env.LOCAL_MAIL_ADMIN_EMAIL||'admin@xpertsedge.com',password:process.env.LOCAL_MAIL_ADMIN_PASSWORD||'Admin@123'},method:'POST'})).cookie;
    id=(await req('/mail/mailboxes',{cookie:adminCookie,body:{address:prefix,displayName:'Gmail QA',password,quotaGb:1},method:'POST'})).data.id;
    const email=prefix+'@xpertsedge.com',cookie=(await req('/webmail/login',{body:{email,password},method:'POST'})).cookie;
    // Gmail endpoints require a mailbox session.
    for(const path of ['/webmail/gmail/status','/webmail/gmail/connect'])await req(path,{},401);
    await req('/webmail/gmail/disconnect',{method:'POST'},401);
    const status=(await req('/webmail/gmail/status',{cookie})).data;
    assert.equal(status.connected,false);assert.ok(!('refreshToken' in status)&&!JSON.stringify(status).includes('refresh'));
    // Connect: 503 when the profile is off, otherwise a Google URL with PKCE, offline access and no secret.
    if(!status.configured){await req('/webmail/gmail/connect',{cookie},503);}
    else{
      const url=new URL((await req('/webmail/gmail/connect',{cookie})).data.url);
      assert.equal(url.origin+url.pathname,'https://accounts.google.com/o/oauth2/v2/auth');
      assert.equal(url.searchParams.get('access_type'),'offline');assert.equal(url.searchParams.get('code_challenge_method'),'S256');
      assert.ok(url.searchParams.get('code_challenge'));assert.ok(url.searchParams.get('state').length>=40);
      const scopes=url.searchParams.get('scope').split(' ');assert.deepEqual(scopes.sort(),['https://www.googleapis.com/auth/gmail.readonly','https://www.googleapis.com/auth/gmail.send']);
      assert.ok(!url.search.includes('client_secret'));
      // A state is single-use and bound to a mailbox; a forged callback must be rejected without leaking details.
    }
    // Callback with unknown/forged state or a Google error redirects to the app and never echoes input.
    const forged=await raw('/webmail/gmail/callback?code=SECRETCODE123&state=forged-state',{redirect:'manual'});checks++;
    assert.equal(forged.status,302);assert.match(forged.location,/[?&]gmail=error#\/webmail$/);assert.ok(!forged.location.includes('SECRETCODE123'));
    const denied=await raw('/webmail/gmail/callback?error=access_denied',{redirect:'manual'});checks++;
    assert.equal(denied.status,302);assert.match(denied.location,/[?&]gmail=denied#\/webmail$/);
    // Nothing is linked, so sync is a 404 (or 503 when disabled) and disconnect is idempotent.
    await req('/webmail/gmail/sync',{cookie,method:'POST'},status.configured?404:503);
    await req('/webmail/gmail/disconnect',{cookie,method:'POST'},204);await req('/webmail/gmail/disconnect',{cookie,method:'POST'},204);
    // Without a connected Gmail (and without SMTP) external recipients are refused; local mail rules are unchanged.
    const mailStatus=(await req('/webmail/status',{cookie})).data;assert.equal(mailStatus.gmailConnected,false);
    if(!mailStatus.externalAllowed){const draft=await raw('/webmail/drafts',{cookie,method:'POST',body:{to:'someone@example.com',subject:'x',body:'y'}});checks++;assert.ok([200,400].includes(draft.status),'external draft: '+draft.status);} // 400 in local-only mode; 200 only if SMTP sender is enabled for other mailboxes
    const list=(await req('/webmail/messages?folder=Inbox',{cookie})).data;assert.equal(list.gmailConnected,false);
    console.log('PASS: '+checks+' offline Gmail OAuth checks (auth required, PKCE URL, forged/denied callback, idempotent disconnect, external guard).');
  }finally{
    if(id!==null&&adminCookie)await raw('/mail/mailboxes/'+id,{cookie:adminCookie,method:'DELETE'});
  }
}
async function live(){
  const email=process.env.GMAIL_LIVE_EMAIL,password=process.env.GMAIL_LIVE_PASSWORD;
  const cookie=(await req('/webmail/login',{body:{email,password,code:process.env.GMAIL_LIVE_CODE||undefined},method:'POST'})).cookie;
  const status=(await req('/webmail/gmail/status',{cookie})).data;
  assert.equal(status.configured,true,'Start the backend with the gmail-api profile');assert.equal(status.connected,true,'Connect Gmail in Settings first');assert.equal(status.needsReconnect,false,'Gmail needs to be reconnected');
  await req('/webmail/gmail/sync',{cookie,method:'POST'});
  const subject='XpertsEdge live check '+randomUUID().slice(0,8);
  // App -> Gmail: mail to the connected address itself exercises send (API) and receive (sync) with one message.
  const sent=(await req('/webmail/send',{cookie,method:'POST',body:{to:status.gmailAddress,subject,body:'Live Gmail API check. You can delete this message.'}})).data;
  assert.equal(sent.folder,'Sent');assert.equal(sent.deliveryStatus,'GMAIL_ACCEPTED');assert.equal(sent.fromAddress,status.gmailAddress);
  let received=null;
  for(let attempt=0;attempt<18&&!received;attempt++){
    await sleep(5000);await req('/webmail/gmail/sync',{cookie,method:'POST'});
    received=(await req('/webmail/messages?folder=Inbox&q='+encodeURIComponent(subject),{cookie})).data.messages.find(m=>m.subject===subject);
  }
  assert.ok(received,'Message sent to the connected Gmail did not reach the app Inbox within 90 seconds');
  assert.equal(received.deliveryStatus,'RECEIVED_GMAIL');
  // Repeated syncs must not duplicate it.
  await req('/webmail/gmail/sync',{cookie,method:'POST'});
  assert.equal((await req('/webmail/messages?folder=Inbox&q='+encodeURIComponent(subject),{cookie})).data.messages.filter(m=>m.subject===subject).length,1);
  for(const m of [sent,received]){await req('/webmail/messages/'+m.id,{cookie,method:'PATCH',body:{folder:'Trash'}});await req('/webmail/messages/'+m.id,{cookie,method:'DELETE'},204);}
  console.log('PASS: '+checks+' live Gmail send + receive checks for '+status.gmailAddress+'. Also confirm the Spam path manually (see GMAIL-OAUTH.md).');
}
await offline();
if(process.env.GMAIL_LIVE_EMAIL&&process.env.GMAIL_LIVE_PASSWORD)await live();
else console.log('Live Gmail check skipped (set GMAIL_LIVE_EMAIL and GMAIL_LIVE_PASSWORD to run it).');
