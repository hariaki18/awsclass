// Local-only integration test; creates and removes its own uniquely named mailboxes.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const base=process.env.LOCAL_MAIL_TEST_URL||'http://127.0.0.1:8080';
const origin='http://127.0.0.1:5173';let checks=0;const created=[];
async function request(path,{cookie='',body,method='GET',status=200,requestOrigin=origin}={}){
  const response=await fetch(base+'/api'+path,{method,headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{}),...(requestOrigin?{Origin:requestOrigin}:{})},body:body?JSON.stringify(body):undefined});
  const data=await response.json().catch(()=>null);assert.equal(response.status,status,path+': '+JSON.stringify(data));checks++;
  return {data,cookie:response.headers.get('set-cookie')?.split(';')[0]||cookie};
}
const prefix='qa'+randomUUID().replaceAll('-','').slice(0,10),password='LocalTestOnly@'+randomUUID();
let adminCookie='';
try{
  const admin=await request('/auth/login',{method:'POST',body:{email:process.env.LOCAL_MAIL_ADMIN_EMAIL||'admin@xpertsedge.com',password:process.env.LOCAL_MAIL_ADMIN_PASSWORD||'Admin@123'}});adminCookie=admin.cookie;
  const accounts=[];
  for(const suffix of ['a','b']){const local=prefix+suffix;const box=await request('/mail/mailboxes',{method:'POST',cookie:adminCookie,body:{address:local,displayName:'Local test '+local,password,quotaGb:1}});created.push(box.data.id);const login=await request('/webmail/login',{method:'POST',body:{email:local+'@xpertsedge.com',password}});assert.ok(login.cookie.startsWith('xe_mail_session='));accounts.push({...box.data,cookie:login.cookie,email:local+'@xpertsedge.com'});}
  const [a,b]=accounts;
  await request('/webmail/login',{method:'POST',body:{email:a.email,password:'wrong'},status:401});
  await request('/webmail/login',{method:'POST',requestOrigin:'https://attacker.invalid',body:{email:a.email,password},status:403});
  await request('/me',{cookie:a.cookie,status:401});
  await request('/mail/mailboxes',{cookie:a.cookie,status:401});
  await request('/webmail/me',{cookie:adminCookie,status:401});
  assert.equal((await request('/webmail/status',{cookie:a.cookie})).data.connected,true);
  const draft=await request('/webmail/drafts',{method:'POST',cookie:a.cookie,body:{to:b.email,subject:prefix+' SMTP roundtrip',body:'Local SMTP body ✓',tag:'Green'}});
  assert.equal((await request('/webmail/messages',{cookie:a.cookie})).data.messages.find(m=>m.id===draft.data.id).folder,'Drafts');
  await request('/webmail/drafts/'+draft.data.id,{method:'PUT',cookie:b.cookie,body:{to:a.email,subject:'forbidden',body:'forbidden'},status:404});
  await request('/webmail/send',{method:'POST',cookie:a.cookie,body:{id:draft.data.id,to:'outside@gmail.com',subject:'blocked',body:'No internet'},status:400});
  const sent=await request('/webmail/send',{method:'POST',cookie:a.cookie,body:{id:draft.data.id,to:b.email,subject:prefix+' SMTP roundtrip',body:'Local SMTP body ✓',tag:'Green'}});
  assert.equal(sent.data.folder,'Sent');
  await request('/webmail/send',{method:'POST',cookie:a.cookie,body:{id:draft.data.id,to:b.email,subject:'duplicate',body:'duplicate'},status:400});
  const inbox=(await request('/webmail/messages',{cookie:b.cookie})).data.messages;
  const received=inbox.find(m=>m.subject===prefix+' SMTP roundtrip');assert.ok(received,'SMTP message imported into recipient Inbox');assert.equal(received.body.trim(),'Local SMTP body ✓');assert.equal(received.folder,'Inbox');assert.equal(received.fromAddress,a.email);checks+=4;
  const repeated=(await request('/webmail/messages',{cookie:b.cookie})).data.messages;assert.equal(repeated.filter(m=>m.subject===received.subject).length,1);checks++;
  await request('/mail/aliases',{method:'POST',cookie:adminCookie,body:{alias:prefix+'alias',target:b.address}});
  await request('/webmail/send',{method:'POST',cookie:a.cookie,body:{to:prefix+'alias@xpertsedge.com',subject:prefix+' alias delivery',body:'Alias SMTP delivery'}});
  assert.ok((await request('/webmail/messages',{cookie:b.cookie})).data.messages.some(m=>m.subject===prefix+' alias delivery'));checks++;
  await request('/webmail/messages/'+received.id,{method:'PATCH',cookie:a.cookie,body:{seen:true},status:404});
  assert.equal((await request('/webmail/messages/'+received.id,{method:'PATCH',cookie:b.cookie,body:{seen:true}})).data.seen,true);
  assert.equal((await request('/webmail/messages/'+received.id,{method:'PATCH',cookie:b.cookie,body:{folder:'Spam'}})).data.folder,'Spam');
  await request('/webmail/messages/'+received.id,{method:'PATCH',cookie:b.cookie,body:{folder:'Inbox'}});
  await request('/mail/mailboxes/'+b.id+'/toggle',{method:'POST',cookie:adminCookie});
  await request('/webmail/me',{cookie:b.cookie,status:401});
  await request('/mail/mailboxes/'+b.id+'/toggle',{method:'POST',cookie:adminCookie});
  await request('/webmail/me',{cookie:b.cookie,status:401});
  await request('/mail/mailboxes/'+a.id+'/reset-password',{method:'POST',cookie:adminCookie,body:{password:password+'new'}});
  await request('/webmail/me',{cookie:a.cookie,status:401});
  const relogin=await request('/webmail/login',{method:'POST',body:{email:a.email,password:password+'new'}});
  assert.ok((await request('/webmail/messages',{cookie:relogin.cookie})).data.messages.some(m=>m.id===sent.data.id&&m.folder==='Sent'));checks++;
  await request('/webmail/logout',{method:'POST',cookie:relogin.cookie,status:204});
  await request('/webmail/me',{cookie:relogin.cookie,status:401});
  console.log('PASS: '+checks+' local MySQL/mailbox-auth/SMTP delivery/isolation/persistence checks.');
}finally{
  for(const id of created)await request('/mail/mailboxes/'+id,{method:'DELETE',cookie:adminCookie,status:204});
  if(adminCookie){const notices=(await request('/notifications',{cookie:adminCookie})).data;for(const notice of notices)if(notice.body?.includes(prefix))await request('/notifications/'+notice.id,{method:'DELETE',cookie:adminCookie,status:204});}
  // Delete only SMTP captures belonging to this uniquely named disposable test run.
  const capture=await fetch('http://127.0.0.1:8025/api/v1/search?query='+encodeURIComponent(prefix)).then(r=>r.json());
  const ids=(capture.messages||[]).filter(m=>m.Subject?.startsWith(prefix)).map(m=>m.ID);
  if(ids.length)await fetch('http://127.0.0.1:8025/api/v1/messages',{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({IDs:ids})});
}
