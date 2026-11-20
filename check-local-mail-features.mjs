// Local-only integration test; creates and removes its own uniquely named mailboxes.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {replyDraft,forwardDraft} from './frontend/src/mail-model.mjs';
import net from 'node:net';
const base=process.env.LOCAL_MAIL_TEST_URL||'http://127.0.0.1:8080';
const origin='http://127.0.0.1:5173';let checks=0;const created=[];
async function request(path,{cookie='',body,method='GET',status=200,requestOrigin=origin}={}){
  const response=await fetch(base+'/api'+path,{method,headers:{'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{}),...(requestOrigin?{Origin:requestOrigin}:{})},body:body?JSON.stringify(body):undefined});
  const data=await response.json().catch(()=>null);assert.equal(response.status,status,path+': '+JSON.stringify(data));checks++;
  return {data,cookie:response.headers.get('set-cookie')?.split(';')[0]||cookie};
}
async function smtpMessage(from,to,subject,text){
  const socket=net.createConnection({host:'127.0.0.1',port:1025});let buffer='',codes=[],waiters=[],failure;
  socket.setTimeout(5000,()=>socket.destroy(new Error('SMTP test timeout')));
  socket.on('error',error=>{failure=error;for(const waiter of waiters.splice(0))waiter.reject(error);});
  socket.on('data',chunk=>{buffer+=chunk;let newline;while((newline=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,newline).trimEnd();buffer=buffer.slice(newline+1);if(/^\d{3} /.test(line)){const code=Number(line.slice(0,3));const waiter=waiters.shift();if(waiter)waiter.resolve(code);else codes.push(code);}}});
  const next=()=>failure?Promise.reject(failure):codes.length?Promise.resolve(codes.shift()):new Promise((resolve,reject)=>waiters.push({resolve,reject}));
  async function command(value,expected){socket.write(value+'\r\n');assert.equal(await next(),expected);}
  try{assert.equal(await next(),220);await command('EHLO localhost',250);await command('MAIL FROM:<'+from+'>',250);await command('RCPT TO:<'+to+'>',250);await command('DATA',354);await command('From: '+from+'\r\nTo: '+to+'\r\nSubject: '+subject+'\r\n\r\n'+text+'\r\n.',250);}finally{socket.setTimeout(0);socket.end('QUIT\r\n');}
}
const prefix='qa'+randomUUID().replaceAll('-','').slice(0,10),password='LocalTestOnly@'+randomUUID();
let adminCookie='';
try{
  const admin=await request('/auth/login',{method:'POST',body:{email:process.env.LOCAL_MAIL_ADMIN_EMAIL||'admin@xpertsedge.com',password:process.env.LOCAL_MAIL_ADMIN_PASSWORD||'Admin@123'}});adminCookie=admin.cookie;
  const accounts=[];
  for(const suffix of ['a','b','c']){const local=prefix+suffix;const box=await request('/mail/mailboxes',{method:'POST',cookie:adminCookie,body:{address:local,displayName:'Local test '+local,password,quotaGb:1}});created.push(box.data.id);const login=await request('/webmail/login',{method:'POST',body:{email:local+'@xpertsedge.com',password}});assert.ok(login.cookie.startsWith('xe_mail_session='));accounts.push({...box.data,cookie:login.cookie,email:local+'@xpertsedge.com'});}
  const [a,b,c]=accounts;
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
  // Real multipart SMTP with To + Cc, and owner-scoped attachment downloads.
  const fileBytes=Buffer.from('Attachment contents ✓');
  const multipartBody={to:b.email,cc:c.email,subject:prefix+' multipart',body:'Multipart body search token 100%_literal',attachments:[{name:'report.txt',contentType:'text/plain',data:fileBytes.toString('base64')}]};
  const multipartDraft=(await request('/webmail/drafts',{method:'POST',cookie:a.cookie,body:multipartBody})).data;
  assert.equal(multipartDraft.attachments.length,1);checks++;
  const senderFile=multipartDraft.attachments[0];
  let download=await fetch(base+'/api/webmail/messages/'+multipartDraft.id+'/attachments/'+senderFile.id,{headers:{Cookie:a.cookie}});assert.equal(download.status,200);assert.equal(download.headers.get('content-type'),'application/octet-stream');assert.equal(download.headers.get('x-content-type-options'),'nosniff');assert.deepEqual(Buffer.from(await download.arrayBuffer()),fileBytes);checks+=4;
  await request('/webmail/messages/'+multipartDraft.id+'/attachments/'+senderFile.id,{cookie:b.cookie,status:404});
  await request('/webmail/send',{method:'POST',cookie:a.cookie,body:{id:multipartDraft.id,to:b.email,cc:c.email,subject:multipartBody.subject,body:multipartBody.body,attachmentIds:[senderFile.id]}});
  const bInbox=(await request('/webmail/messages?folder=Inbox',{cookie:b.cookie})).data.messages;
  const receivedMultipart=bInbox.find(m=>m.subject===multipartBody.subject);assert.ok(receivedMultipart);assert.equal(receivedMultipart.attachments.length,1);assert.equal(receivedMultipart.ccAddress,c.email);checks+=3;
  const cInbox=(await request('/webmail/messages?folder=Inbox',{cookie:c.cookie})).data.messages;assert.ok(cInbox.some(m=>m.subject===multipartBody.subject));checks++;
  const receivedFile=receivedMultipart.attachments[0];download=await fetch(base+'/api/webmail/messages/'+receivedMultipart.id+'/attachments/'+receivedFile.id,{headers:{Cookie:b.cookie}});assert.deepEqual(Buffer.from(await download.arrayBuffer()),fileBytes);checks++;
  // Reply all uses the exact frontend addressing logic and delivers to sender + Cc.
  const reply=replyDraft(receivedMultipart,b.email,true);assert.equal(reply.to,a.email);assert.equal(reply.cc,c.email);checks+=2;
  const replySaved=(await request('/webmail/drafts',{method:'POST',cookie:b.cookie,body:reply})).data;
  await request('/webmail/send',{method:'POST',cookie:b.cookie,body:{...reply,id:replySaved.id}});
  for(const account of [a,c])assert.ok((await request('/webmail/messages?folder=Inbox',{cookie:account.cookie})).data.messages.some(m=>m.subject===reply.subject));checks+=2;
  // Forward copies bytes into an independent draft; source files stay private.
  const forward=forwardDraft(receivedMultipart);forward.to=c.email;forward.forwardAttachmentIds=forward.attachments.map(file=>file.id);forward.attachments=[];
  const forwarded=(await request('/webmail/drafts',{method:'POST',cookie:b.cookie,body:forward})).data;assert.notEqual(forwarded.attachments[0].id,receivedFile.id);checks++;
  await request('/webmail/send',{method:'POST',cookie:b.cookie,body:{id:forwarded.id,to:c.email,subject:forward.subject,body:forward.body,attachmentIds:forwarded.attachments.map(file=>file.id)}});
  const cForward=(await request('/webmail/messages?folder=Inbox',{cookie:c.cookie})).data.messages.find(m=>m.subject===forward.subject);assert.ok(cForward);assert.equal(cForward.attachments.length,1);checks+=2;
  // Full-body search includes text beyond the first 255 characters; wildcards stay literal.
  await request('/webmail/drafts',{method:'POST',cookie:b.cookie,body:{to:a.email,subject:prefix+' search',body:'x'.repeat(500)+' tailneedle 100%_literal'}});
  assert.equal((await request('/webmail/messages?folder=Drafts&q=tailneedle',{cookie:b.cookie})).data.total,1);checks++;
  assert.equal((await request('/webmail/messages?q='+encodeURIComponent('100%_literal'),{cookie:b.cookie})).data.total,4);checks++;
  await request('/webmail/messages/'+receivedMultipart.id,{method:'PATCH',cookie:b.cookie,body:{seen:false}});
  assert.ok((await request('/webmail/messages?folder=Inbox',{cookie:b.cookie})).data.unread>0);checks++;
  await request('/webmail/messages/'+multipartDraft.id,{method:'PATCH',cookie:a.cookie,body:{folder:'Trash'}});
  assert.equal((await request('/webmail/messages/'+multipartDraft.id,{method:'PATCH',cookie:a.cookie,body:{folder:'Restore'}})).data.folder,'Sent');checks++;
  await request('/webmail/messages/'+receivedMultipart.id,{method:'PATCH',cookie:b.cookie,body:{folder:'Trash'}});
  assert.equal((await request('/webmail/messages/'+receivedMultipart.id,{method:'PATCH',cookie:b.cookie,body:{folder:'Restore'}})).data.folder,'Inbox');checks++;
  await request('/webmail/messages/'+receivedMultipart.id,{method:'PATCH',cookie:b.cookie,body:{folder:'Trash'}});
  await request('/webmail/messages/'+receivedMultipart.id,{method:'DELETE',cookie:b.cookie,status:204});
  assert.ok(!(await request('/webmail/messages',{cookie:b.cookie})).data.messages.some(m=>m.id===receivedMultipart.id||m.subject===multipartBody.subject),'Deleted capture must not reappear');checks++;
  await request('/webmail/messages/'+receivedMultipart.id+'/attachments/'+receivedFile.id,{cookie:b.cookie,status:404});
  const detached=await request('/webmail/drafts',{method:'POST',cookie:b.cookie,body:{to:a.email,subject:prefix+' remove file',body:'Draft',attachments:[{name:'remove.txt',data:Buffer.from('remove').toString('base64')}]}});
  assert.equal((await request('/webmail/drafts/'+detached.data.id,{method:'PUT',cookie:b.cookie,body:{to:a.email,subject:prefix+' remove file',body:'Draft',attachmentIds:[]}})).data.attachments.length,0);checks++;
  const incomingAlias=prefix+'incoming';
  const aliasRecord=(await request('/mail/aliases',{method:'POST',cookie:adminCookie,body:{alias:incomingAlias,target:c.address}})).data;
  await smtpMessage(a.email,incomingAlias+'@xpertsedge.com',prefix+' direct alias','Direct SMTP alias content');
  assert.ok((await request('/webmail/messages?folder=Inbox',{cookie:c.cookie})).data.messages.some(m=>m.subject===prefix+' direct alias'));checks++;
  await request('/mail/aliases/'+aliasRecord.id,{method:'PUT',cookie:adminCookie,body:{alias:incomingAlias,target:b.address}});
  assert.ok(!(await request('/webmail/messages?folder=Inbox',{cookie:b.cookie})).data.messages.some(m=>m.subject===prefix+' direct alias'),'Retarget must not expose historical alias mail');checks++;
  await smtpMessage(a.email,incomingAlias+'@xpertsedge.com',prefix+' retargeted alias','New alias owner');
  assert.ok((await request('/webmail/messages?folder=Inbox',{cookie:b.cookie})).data.messages.some(m=>m.subject===prefix+' retargeted alias'));checks++;
  const renewal=prefix+'renew';let renewalBox=(await request('/mail/mailboxes',{method:'POST',cookie:adminCookie,body:{address:renewal,password,quotaGb:1}})).data;created.push(renewalBox.id);
  await smtpMessage(a.email,renewal+'@xpertsedge.com',prefix+' old account','Must not appear in a recreated account');
  let renewalLogin=await request('/webmail/login',{method:'POST',body:{email:renewal+'@xpertsedge.com',password}});
  assert.ok((await request('/webmail/messages',{cookie:renewalLogin.cookie})).data.messages.some(m=>m.subject===prefix+' old account'));checks++;
  await request('/mail/mailboxes/'+renewalBox.id,{method:'DELETE',cookie:adminCookie,status:204});created.splice(created.indexOf(renewalBox.id),1);
  renewalBox=(await request('/mail/mailboxes',{method:'POST',cookie:adminCookie,body:{address:renewal,password,quotaGb:1}})).data;created.push(renewalBox.id);
  renewalLogin=await request('/webmail/login',{method:'POST',body:{email:renewal+'@xpertsedge.com',password}});
  assert.ok(!(await request('/webmail/messages',{cookie:renewalLogin.cookie})).data.messages.some(m=>m.subject===prefix+' old account'));checks++;
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
  console.log('PASS: '+checks+' local MySQL/mailbox-auth/SMTP, reply/forward, attachment, folder/search and isolation checks.');
}finally{
  for(const id of created)await request('/mail/mailboxes/'+id,{method:'DELETE',cookie:adminCookie,status:204});
  if(adminCookie){const notices=(await request('/notifications',{cookie:adminCookie})).data;for(const notice of notices)if(notice.body?.includes(prefix))await request('/notifications/'+notice.id,{method:'DELETE',cookie:adminCookie,status:204});}
  // Delete only SMTP captures belonging to this uniquely named disposable test run.
  const capture=await fetch('http://127.0.0.1:8025/api/v1/search?query='+encodeURIComponent(prefix)).then(r=>r.json());
  const ids=(capture.messages||[]).filter(m=>m.Subject?.includes(prefix)).map(m=>m.ID);
  if(ids.length)await fetch('http://127.0.0.1:8025/api/v1/messages',{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({IDs:ids})});
}
