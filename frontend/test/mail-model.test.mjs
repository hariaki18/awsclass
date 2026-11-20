import {test} from 'node:test';
import assert from 'node:assert/strict';
import {replyDraft,forwardDraft,splitAddresses,draftSignature} from '../src/mail-model.mjs';
const message={id:'source',fromAddress:'alice@xpertsedge.com',toAddress:'bob@xpertsedge.com, carol@xpertsedge.com',ccAddress:'dave@xpertsedge.com, bob@xpertsedge.com',subject:'Project',body:'Hello\nTeam',createdAt:'2026-11-06T00:00:00Z',attachments:[{id:'file',name:'notes.txt',size:4}]};
test('reply targets sender without copying all recipients or attachments',()=>{const draft=replyDraft(message,'bob@xpertsedge.com');assert.equal(draft.to,'alice@xpertsedge.com');assert.equal(draft.cc,'');assert.equal(draft.subject,'Re: Project');assert.match(draft.body,/> Hello\n> Team/);assert.deepEqual(draft.attachments,[]);});
test('reply all excludes self and deduplicates To and Cc',()=>{const draft=replyDraft(message,'bob@xpertsedge.com',true);assert.equal(draft.to,'alice@xpertsedge.com, carol@xpertsedge.com');assert.equal(draft.cc,'dave@xpertsedge.com');});
test('reply honors Reply-To header and avoids duplicate Re prefix',()=>{assert.equal(replyDraft({...message,replyToAddress:'support@xpertsedge.com',subject:'Re: Project'},'bob@xpertsedge.com').to,'support@xpertsedge.com');assert.equal(replyDraft({...message,subject:'Re: Project'},'bob@xpertsedge.com').subject,'Re: Project');});
test('forward references owned source attachments without embedding their bytes',()=>{const draft=forwardDraft(message);assert.equal(draft.forwardFromId,'source');assert.equal(draft.to,'');assert.equal(draft.attachments[0].forward,true);assert.equal(draft.attachments[0].data,undefined);assert.match(draft.body,/Forwarded message/);});
test('dirty signature ignores server read/time changes and detects attachment edits',()=>{assert.equal(draftSignature(message),draftSignature({...message,seen:true,updatedAt:'later'}));assert.notEqual(draftSignature(message),draftSignature({...message,attachments:[]}));assert.deepEqual(splitAddresses('A@x.com; a@x.com, b@x.com'),['a@x.com','b@x.com']);});

test('reply from Sent targets original recipients instead of the sender itself',()=>{assert.equal(replyDraft(message,'alice@xpertsedge.com').to,'bob@xpertsedge.com, carol@xpertsedge.com');});


test('quoted replies and forwards stay within the message body limit',()=>{const long={...message,body:'a\n'.repeat(10000),toAddress:'x'.repeat(1900)};assert.ok(replyDraft(long,'bob@xpertsedge.com').body.length<=20000);assert.ok(forwardDraft(long).body.length<=20000);});

