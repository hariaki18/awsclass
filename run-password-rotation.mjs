import {spawn} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {java} from './java-runtime.mjs';
const testSecret=randomBytes(48).toString('base64');
const child=spawn(java,['-jar','backend/target/xpertsedge-hrms-backend-1.0.0.jar','--spring.profiles.active=prod','--server.port=0',
  '--spring.datasource.driver-class-name=org.h2.Driver','--spring.datasource.username=sa','--spring.datasource.password=','--spring.flyway.locations=classpath:db/migration','--spring.datasource.url=jdbc:h2:mem:rotation','--hrms.jwt.secret='+testSecret,
  '--hrms.admin.email=admin@example.invalid','--hrms.admin.password=DisposableTestOnly@2026','--hrms.auth.origins=https://app.example.invalid'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
try {
  const port=await new Promise((resolve,reject)=> {
    let log=''; const timeout=setTimeout(()=>reject(new Error('Test backend startup timed out')),60000);
    child.on('error',reject);
    child.on('exit',()=>{clearTimeout(timeout);reject(new Error('Test backend exited before checks'));});
    child.stdout.on('data',chunk=> {
      log+=chunk.toString();
      if(log.includes('Administrator bootstrap verified')) {
        const match=log.match(/Tomcat started on port (\d+)/);
        if(match){clearTimeout(timeout);resolve(match[1]);}
      }
    });
    child.stderr.resume();
  });
  process.env.AUTH_ROTATION_BASE=`http://127.0.0.1:${port}/api`;
  if(process.argv[2]==='mail') await import('./check-mail.mjs');
  else if(process.argv[2]==='basic') await import('./check-basic-modules.mjs');
  else if(process.argv[2]==='auth') {
    process.env.AUTH_TEST_BASE=process.env.AUTH_ROTATION_BASE;
    process.env.AUTH_TEST_ORIGIN='https://app.example.invalid';
    process.env.AUTH_TEST_EMAIL='admin@example.invalid';
    process.env.AUTH_TEST_PASSWORD='DisposableTestOnly@2026';
    process.env.AUTH_TEST_SECRET=testSecret;
    await import('./check-auth.mjs');
  }
  else await import('./check-password-rotation.mjs');
} finally {
  if(child.exitCode===null) { child.kill(); await new Promise(resolve=>child.once('exit',resolve)); }
}
