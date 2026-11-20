import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {java} from './java-runtime.mjs';
const jar='backend/target/xpertsedge-hrms-backend-1.0.0.jar';
const common=['-jar',jar,'--spring.profiles.active=prod','--server.port=0','--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--spring.flyway.locations=classpath:db/migration','--spring.datasource.url=jdbc:h2:mem:authguard',
  '--hrms.admin.email=admin@example.invalid','--hrms.admin.password=DisposableTestOnly@2026','--hrms.auth.origins=https://app.example.invalid'];
for(const [name,extra,expected] of [
  ['missing signing secret',['--hrms.jwt.secret='],'JWT_SECRET is required in production'],
  ['known signing secret',['--hrms.jwt.secret=change-this-secret-change-this-secret-32chars'],'Use a random JWT_SECRET'],
  ['demo admin password',['--hrms.jwt.secret='+randomBytes(48).toString('base64'),'--hrms.admin.password=Admin@123'],'Production ADMIN_PASSWORD'],
  ['insecure origin',['--hrms.jwt.secret='+randomBytes(48).toString('base64'),'--hrms.auth.origins=http://app.example.invalid'],'production requires HTTPS']
]) {
  const properties=new Map([...common.slice(2),...extra].map(arg=>[arg.split('=')[0],arg]));
  const result=spawnSync(java,[...common.slice(0,2),...properties.values()],{encoding:'utf8',timeout:60000,windowsHide:true});
  assert.ok(!result.error,name+' process must finish without timeout');
  const log=(result.stdout||'')+(result.stderr||'');
  assert.notEqual(result.status,0,name); assert.ok(log.includes(expected),name+' did not fail for the expected reason');
  console.log('PASS: production rejects '+name);
}
