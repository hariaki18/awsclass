import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHmac} from 'node:crypto';
const base=process.env.AUTH_TEST_BASE || 'http://127.0.0.1:5173/api';
const origin=process.env.AUTH_TEST_ORIGIN || 'http://127.0.0.1:5173';
const adminEmail=process.env.AUTH_TEST_EMAIL || 'admin@xpertsedge.com';
const adminPassword=process.env.AUTH_TEST_PASSWORD || 'Admin@123';
let count=0;
async function request(path,{status=200,method='GET',body,cookie,token,requestOrigin=origin}={}) {
  const headers={'Content-Type':'application/json'};
  if(requestOrigin) headers.Origin=requestOrigin;
  if(cookie) headers.Cookie=cookie;
  if(token) headers.Authorization='Bearer '+token;
  const r=await fetch(base+path,{method,headers,body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});
  assert.equal(r.status,status,`${method} ${path}`); count++; return r;
}
await request('/me',{status:401});
await request('/auth/logout',{status:401,method:'POST'});
await request('/auth/unknown',{status:401});
await request('/auth/login',{status:403,method:'POST',requestOrigin:'https://attacker.invalid',body:{email:'admin@xpertsedge.com',password:'Admin@123'}});
await request('/auth/login',{status:401,method:'POST',body:{email:null,password:null}});
await request('/auth/login',{status:401,method:'POST',body:{email:'admin@xpertsedge.com',password:'x'.repeat(100)}});
const login=await request('/auth/login',{method:'POST',body:{email:' '+adminEmail.toUpperCase()+' ',password:adminPassword}});
const header=login.headers.get('set-cookie');
assert.match(header,/HttpOnly/i); assert.match(header,/SameSite=Strict/i); assert.match(header,/Path=\/api/i);
assert.match(login.headers.get('cache-control'),/no-store/);
const data=await login.json(); assert.equal(data.user.name,'Admin'); assert.equal(data.token,undefined); assert.equal(data.expiresIn,3600);
const cookie=header.split(';')[0], token=cookie.slice(cookie.indexOf('=')+1);
await request('/me',{cookie});
await request('/me',{token});
await request('/mail/mailboxes',{method:'POST',cookie,requestOrigin:'https://attacker.invalid',status:403,body:{}});
await request('/mail/mailboxes',{method:'POST',cookie,requestOrigin:null,status:403,body:{}});
await request('/me',{token:token.slice(0,-10)+'tampered123',status:401});
const parts=token.split('.'), original=JSON.parse(Buffer.from(parts[1],'base64url'));
const algorithm={HS256:'sha256',HS384:'sha384',HS512:'sha512'}[JSON.parse(Buffer.from(parts[0],'base64url')).alg];
assert.ok(algorithm,'Supported HMAC signing algorithm');
const secret=process.env.AUTH_TEST_SECRET || (await readFile(new URL('./backend/data/.jwt-secret',import.meta.url),'utf8')).trim();
function altered(changes) {
  const claims={...original,...changes};
  for(const [key,value] of Object.entries(claims)) if(value===null) delete claims[key];
  const input=parts[0]+'.'+Buffer.from(JSON.stringify(claims)).toString('base64url');
  return input+'.'+createHmac(algorithm,secret).update(input).digest('base64url');
}
await request('/me',{token:altered({})}); // Prove the test signer is valid before testing altered claims.
for(const changes of [{exp:1},{iss:'wrong'},{aud:['wrong']},{jti:'missing-session'},{exp:null},{iat:null},{sub:'missing@example.invalid'}])
  await request('/me',{token:altered(changes),status:401});
const logout=await request('/auth/logout',{method:'POST',cookie,status:204}); assert.match(logout.headers.get('set-cookie'),/Max-Age=0/);
await request('/me',{token,status:401});
await request('/me',{cookie,status:401});
const email='lockout-'+Date.now()+'@example.invalid';
for(let i=0;i<5;i++) await request('/auth/login',{method:'POST',status:401,body:{email,password:'wrong'}});
await request('/auth/login',{method:'POST',status:429,body:{email,password:'wrong'}});
// Unknown test identities do not lock the real administrator.
const again=await request('/auth/login',{method:'POST',body:{email:adminEmail,password:adminPassword}});
await request('/auth/logout',{method:'POST',cookie:again.headers.get('set-cookie').split(';')[0],status:204});
console.log(`PASS: ${count} auth checks (cookies, claims, CSRF, expiry, revocation, lockout). No credentials/tokens printed.`);
