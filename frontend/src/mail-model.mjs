export const splitAddresses=value=>[...new Set((value||'').split(/[,;]/).map(email=>email.trim().toLowerCase()).filter(Boolean))];
export function replyDraft(message,ownEmail,replyAll=false){
  const own=ownEmail.toLowerCase(),sender=splitAddresses(message.replyToAddress||message.fromAddress||message.from);
  const primary=sender.every(email=>email===own)?splitAddresses(message.toAddress||message.to):sender;
  const to=replyAll?[...primary,...splitAddresses(message.toAddress||message.to)]:primary;
  const recipients=[...new Set(to)].filter(email=>email!==own);
  const cc=replyAll?splitAddresses(message.ccAddress||message.cc).filter(email=>email!==own&&!recipients.includes(email)):[];
  const result={to:recipients.join(', '),cc:cc.join(', '),subject:(/^re:/i.test(message.subject)?message.subject:'Re: '+(message.subject||'')).slice(0,255),body:'\n\nOn '+new Date(message.createdAt).toLocaleString()+', '+(message.fromAddress||message.from)+' wrote:\n'+(message.body||'').slice(0,15000).split('\n').map(line=>'> '+line).join('\n'),attachments:[]};result.body=result.body.slice(0,20000);return result;
}
export function forwardDraft(message){const result={to:'',cc:'',subject:(/^fwd:/i.test(message.subject)?message.subject:'Fwd: '+(message.subject||'')).slice(0,255),body:'\n\n---------- Forwarded message ----------\nFrom: '+(message.fromAddress||message.from)+'\nTo: '+(message.toAddress||message.to)+'\nSubject: '+(message.subject||'')+'\n\n'+(message.body||'').slice(0,18000),forwardFromId:message.id,attachments:(message.attachments||[]).map(file=>({...file,forward:true}))};result.body=result.body.slice(0,20000);return result;}
export function draftSignature(draft){return JSON.stringify([draft.to||'',draft.cc||'',draft.subject||'',draft.body||'',draft.tag||'',draft.forwardFromId||'',(draft.attachments||[]).map(file=>[file.id||'',file.name,file.size,file.data||'',!!file.forward])]);}
