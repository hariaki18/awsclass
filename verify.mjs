import {spawnSync} from 'node:child_process';
import {mkdirSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
const root=fileURLToPath(new URL('.',import.meta.url));
const results=[];mkdirSync(join(root,'.verification'),{recursive:true});
const shell=(windows,unix)=>process.platform==='win32'?['cmd.exe',['/d','/s','/c',windows]]:[unix[0],unix.slice(1)];
const steps=[
  ['frontend-tests','frontend',...shell('npm.cmd test',['npm','test'])],
  ['frontend-build','frontend',...shell('npm.cmd run build',['npm','run','build'])],
  ['backend-tests-build','backend',...shell('mvnw.cmd -q clean verify',['./mvnw','-q','clean','verify'])],
  ['mail-regression','.',process.execPath,['run-password-rotation.mjs','mail']],
  ['basic-modules-regression','.',process.execPath,['run-password-rotation.mjs','basic']],
  ['auth-regression','.',process.execPath,['run-password-rotation.mjs','auth']],
  ['password-rotation','.',process.execPath,['run-password-rotation.mjs']],
  ['production-guards','.',process.execPath,['check-production-auth.mjs']]
];
const report=()=> {
  const failed=results.some(r=>!r.passed), complete=results.length===steps.length;
  writeFileSync(join(root,'verification-results.json'),JSON.stringify({
    updatedAt:new Date().toISOString(),status:failed?'failed':complete?'passed':'running',
    passed:complete && !failed,results
  },null,2));
};
report();
for(const [name,folder,command,args] of steps) {
  console.log('RUN '+name);const start=Date.now();
  const r=spawnSync(command,args,{cwd:join(root,folder),encoding:'utf8',windowsHide:true,timeout:240000,maxBuffer:10*1024*1024});
  const output=(r.stdout||'')+(r.stderr||'');writeFileSync(join(root,'.verification',name+'.log'),output);
  const passed=r.status===0 && !r.error;
  results.push({name,passed,durationMs:Date.now()-start,exitCode:r.status});
  report();
  if(!passed) {console.error(output.slice(-6000));console.error(r.error?.message||'Verification failed; see .verification/'+name+'.log');process.exit(1);}
  console.log('PASS '+name);
}
console.log('PASS: all tests, regressions and production builds. See verification-results.json.');
