import {test,beforeEach,afterEach} from 'node:test';
import assert from 'node:assert/strict';
const originalFetch=globalThis.fetch;
let api,expired;
beforeEach(async()=> {
  globalThis.window=new EventTarget();
  globalThis.sessionStorage={setItem(){}};
  expired=0;window.addEventListener('session-expired',()=>expired++);
  ({api}=await import('../src/api.js?test='+Math.random()));
});
afterEach(()=>{globalThis.fetch=originalFetch;delete globalThis.window;delete globalThis.sessionStorage;});
test('sends cookies and JSON body without browser bearer storage',async()=> {
  globalThis.fetch=async(url,opts)=>{assert.equal(url,'/api/mail/mailboxes');assert.equal(opts.credentials,'same-origin');assert.equal(opts.headers.Authorization,undefined);assert.deepEqual(JSON.parse(opts.body),{address:'test'});return Response.json({id:1});};
  assert.deepEqual(await api('/mail/mailboxes',{method:'POST',body:{address:'test'}}),{id:1});
});
test('stale unauthorized response cannot sign out a new login',async()=> {
  let release;globalThis.fetch=()=>new Promise(resolve=>{release=resolve;});
  const old=api('/notifications').catch(e=>e);
  globalThis.fetch=async()=>Response.json({user:{name:'Admin'}});
  await api('/auth/login',{method:'POST'});
  release(new Response('{}',{status:401}));assert.equal((await old).status,401);assert.equal(expired,0);
});
test('current unauthorized request emits session expiry',async()=> {
  globalThis.fetch=async()=>new Response('{}',{status:401});
  await assert.rejects(api('/mail/mailboxes'),e=>e.status===401);assert.equal(expired,1);
});
test('anonymous session restore does not report expired login',async()=> {
  globalThis.fetch=async()=>new Response('{}',{status:401});
  await assert.rejects(api('/me'),e=>e.status===401);assert.equal(expired,0);
});
test('failed login preserves generic backend validation message',async()=> {
  globalThis.fetch=async()=>Response.json({message:'Incorrect email or password.'},{status:401});
  await assert.rejects(api('/auth/login',{method:'POST'}),/Incorrect email or password/);assert.equal(expired,0);
});
test('validation and rate-limit messages reach the form',async()=> {
  for(const status of [400,429]) {globalThis.fetch=async()=>Response.json({message:'Please retry later'},{status});await assert.rejects(api('/mail/mailboxes'),/Please retry later/);}
});
test('offline requests produce a useful connection error',async()=> {
  globalThis.fetch=async()=>{throw new TypeError('offline');};await assert.rejects(api('/me'),/reach the server/);
});
test('logout accepts an empty 204 response',async()=> {
  globalThis.fetch=async()=>new Response(null,{status:204});assert.equal(await api('/auth/logout',{method:'POST'}),null);
});
