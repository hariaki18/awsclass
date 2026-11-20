import assert from 'node:assert/strict';
const base=process.env.AUTH_ROTATION_BASE;
assert.ok(base,'Run through run-password-rotation.mjs against its isolated test process');
const origin='https://app.example.invalid';
let count=0;
async function call(path,method='GET',body,token,status=200) {
  const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',Origin:origin,...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined});
  assert.equal(r.status,status,method+' '+path);count++;return r;
}
const login=async password=> {
  const r=await call('/auth/login','POST',{email:'admin@example.invalid',password});
  const header=r.headers.get('set-cookie'); assert.match(header,/Secure/i);assert.match(header,/HttpOnly/i);
  return header.split(';')[0].split('=')[1];
};
const old='DisposableTestOnly@2026', next='RotatedTestOnly@2026';
const first=await login(old), second=await login(old);
await call('/auth/change-password','POST',{currentPassword:old,password:'short'},first,400);
await call('/auth/change-password','POST',{currentPassword:old,password:next},first,204);
await call('/me','GET',undefined,first,401);
await call('/me','GET',undefined,second,401);
await call('/auth/login','POST',{email:'admin@example.invalid',password:old},undefined,401);
const fresh=await login(next);
await call('/me','GET',undefined,fresh);
await call('/auth/logout','POST',undefined,fresh,204);
console.log(`PASS: ${count} isolated production-profile checks; password rotation revoked both sessions and Secure cookies are set.`);
