import {spawnSync} from 'node:child_process';
import {join} from 'node:path';
// Use the real executable, bypassing Windows Java launchers that spawn an untracked child.
const probe=spawnSync('java',['-XshowSettings:properties','-version'],{encoding:'utf8',windowsHide:true,timeout:10000});
const home=((probe.stderr||'')+(probe.stdout||'')).match(/^\s*java.home\s*=\s*(.+)$/m)?.[1].trim();
if(!home) throw new Error('A working Java installation is required');
export const java=join(home,'bin',process.platform==='win32'?'java.exe':'java');
