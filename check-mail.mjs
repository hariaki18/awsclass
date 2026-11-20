import assert from 'node:assert/strict';
const base=process.env.AUTH_ROTATION_BASE;
assert.ok(base,'Run node run-password-rotation.mjs mail');
let cookie, count=0;
const origin='https://app.example.invalid';
async function call(path,method='GET',body,status=200) {
  const r=await fetch(base+path,{method,headers:{Origin:origin,'Content-Type':'application/json',...(cookie?{Cookie:cookie}:{})},body:body?JSON.stringify(body):undefined});
  assert.equal(r.status,status,`${method} ${path}`);count++;
  if(path==='/auth/login' && r.ok) cookie=r.headers.get('set-cookie').split(';')[0];
  return status===204?null:r.json();
}
await call('/me','GET',undefined,401);
await call('/auth/login','POST',{email:'admin@example.invalid',password:'DisposableTestOnly@2026'});
assert.deepEqual(await call('/notifications'),[]);
const seed={address:'first',displayName:'First',quotaGb:2,password:'LocalOnly@123'};
const first=await call('/mail/mailboxes','POST',seed);
const second=await call('/mail/mailboxes','POST',{...seed,address:'second'});
assert.equal(first.passwordHash,undefined);
let alias=await call('/mail/aliases','POST',{alias:'sales',target:'first'});
alias=await call('/mail/aliases/'+alias.id,'PUT',{alias:' SALES.NEW ',target:' SECOND '});
assert.equal(alias.alias,'sales.new'); assert.equal(alias.target,'second');
await call('/mail/aliases/'+alias.id,'PUT',{alias:'first',target:'second'},400);
await call('/mail/aliases/'+alias.id,'PUT',{alias:'sales.new',target:'missing'},400);
for(const displayName of [null,12,{},'x'.repeat(201),'bad\nname']) {
  await call('/mail/mailboxes','POST',{...seed,address:'invalid',displayName},400);
  await call('/mail/mailboxes/'+first.id,'PUT',{displayName},400);
}
for(const quotaGb of [null,'',true,-1,1.2,1001,'oops']) {
  await call('/mail/mailboxes','POST',{...seed,address:'invalid',quotaGb},400);
  await call('/mail/mailboxes/'+first.id,'PUT',{quotaGb},400);
}
for(const address of [null,123,true,[],{},'.bad','bad..name','x'.repeat(65)])
  await call('/mail/mailboxes','POST',{...seed,address},400);
assert.equal((await call('/notifications/summary')).unreadCount,4,'Failed operations must not notify');
const current=(await call('/mail/mailboxes')).find(b=>b.id===first.id);
assert.equal(current.displayName,'First');assert.equal(current.quotaGb,2);
await call('/mail/mailboxes/'+first.id,'PUT',{displayName:'Edited',quotaGb:3});
await call('/mail/mailboxes/'+first.id+'/reset-password','POST',{password:'short'},400);
await call('/mail/mailboxes/'+first.id+'/reset-password','POST',{password:'UpdatedLocal@123'});
assert.equal((await call('/mail/mailboxes/'+first.id+'/toggle','POST')).active,false);
assert.equal((await call('/mail/mailboxes/'+first.id+'/toggle','POST')).active,true);
const notices=await call('/notifications');
await call('/notifications/'+notices[0].id+'/read','POST');
await call('/notifications/'+notices[0].id+'/read','POST');
assert.equal((await call('/notifications/summary')).unreadCount,7);
await call('/notifications/read-all','POST');
assert.equal((await call('/notifications/summary')).unreadCount,0);
await call('/notifications/'+notices[0].id,'DELETE',undefined,204);
await call('/notifications/'+notices[0].id,'DELETE',undefined,404);
const requests=[['/mail/mailboxes',{...seed,address:'race'}],['/mail/aliases',{alias:'race',target:'first'}]];
const race=await Promise.all(requests.map(([path,body])=>fetch(base+path,{method:'POST',headers:{Origin:origin,'Content-Type':'application/json',Cookie:cookie},body:JSON.stringify(body)})));
assert.deepEqual(race.map(r=>r.status).sort(),[200,400]);count+=2;
for(let i=0;i<103;i++) await call('/mail/mailboxes/'+first.id,'PUT',{displayName:'Updated '+i});
assert.equal((await call('/notifications')).length,100);
assert.equal((await call('/notifications/summary')).unreadCount,104,'Count must include notifications older than latest 100');
await call('/notifications/read-all','POST');
assert.equal((await call('/notifications/summary')).unreadCount,0);
await call('/mail/mailboxes/'+second.id,'DELETE',undefined,204);
assert.ok(!(await call('/mail/aliases')).some(a=>a.id===alias.id));
const spare=await call('/mail/aliases','POST',{alias:'spare',target:'first'});
await call('/mail/aliases/'+spare.id,'DELETE',undefined,204);
await call('/mail/aliases/'+spare.id,'DELETE',undefined,404);
await call('/mail/aliases/'+spare.id,'PUT',{alias:'gone',target:'first'},404);
for(const b of await call('/mail/mailboxes')) await call('/mail/mailboxes/'+b.id,'DELETE',undefined,204);
assert.deepEqual(await call('/mail/mailboxes'),[]);
assert.deepEqual(await call('/mail/aliases'),[]);
await call('/auth/logout','POST',undefined,204);
await call('/me','GET',undefined,401);
console.log(`PASS: ${count} isolated CRUD/validation/notification checks, including concurrency and >100 unread items.`);
