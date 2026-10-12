import {readFileSync} from 'node:fs';
import {test, before, after, beforeEach} from 'node:test';
import {initializeTestEnvironment, assertFails, assertSucceeds} from '@firebase/rules-unit-testing';
import {doc, collection, setDoc, getDoc, getDocs, updateDoc, deleteDoc, query, where, writeBatch, arrayUnion} from 'firebase/firestore';
let env;
const code = 'ABCDEFGHJKLM';
const seed = {name:'Trip', inviteCode:code, memberNames:{a:'Alice', guest:'Guest'}, memberClaims:{a:'alice'}, memberIds:['alice'], defaultCurrency:'UYU'};
const db = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
before(async () => {env=await initializeTestEnvironment({projectId:'demo-gastos', firestore:{host:'127.0.0.1', port:8185, rules:readFileSync('firestore.rules','utf8')}});});
after(async()=>{await env.cleanup();});
beforeEach(async()=>{await env.clearFirestore(); await env.withSecurityRulesDisabled(async ctx=>{
  const f=ctx.firestore(); await setDoc(doc(f,'groups/g'),seed);
  await setDoc(doc(f,`invitations/${code}`),{groupId:'g'});
  await setDoc(doc(f,'users/alice'),{name:'Alice', email:'alice@example.test'});
  await setDoc(doc(f,'groups/g/entries/e'),{amount:10});
});});
const access = async uid => assertSucceeds(setDoc(doc(db(uid),`users/${uid}/groupAccess/g`),{code}));
const claim = (uid,id='guest') => ({[`memberClaims.${id}`]:uid, memberIds:arrayUnion(uid), lastJoinedMemberId:id});
test('unauthenticated access denied',async()=>{
  await assertFails(getDoc(doc(db(),'groups/g')));
  await assertFails(getDoc(doc(db(),`invitations/${code}`)));
});
test('profiles are private',async()=>{
  await assertSucceeds(getDoc(doc(db('alice'),'users/alice')));
  await assertFails(getDoc(doc(db('bob'),'users/alice')));
  await assertFails(getDocs(collection(db('bob'),'users')));
});
test('outsider cannot list or read groups or expenses',async()=>{
  for(const path of ['groups/g','groups/g/entries/e']) await assertFails(getDoc(doc(db('bob'),path)));
  await assertFails(getDocs(collection(db('bob'),'groups')));
});
test('member query and existing content still work',async()=>{
  await assertSucceeds(getDocs(query(collection(db('alice'),'groups'),where('memberIds','array-contains','alice'))));
  await assertSucceeds(getDoc(doc(db('alice'),'groups/g/entries/e')));
  await assertSucceeds(updateDoc(doc(db('alice'),'groups/g'),{name:'Renamed', 'memberNames.new':'New guest'}));
  for(const sub of ['entries','notes','tasks']) await assertSucceeds(setDoc(doc(db('alice'),`groups/g/${sub}/new`),{text:'test'}));
});
test('invitation exact lookup allowed but enumeration denied',async()=>{
  await assertSucceeds(getDoc(doc(db('bob'),`invitations/${code}`)));
  await assertFails(getDocs(collection(db('bob'),'invitations')));
});
test('wrong code and grants for other accounts denied',async()=>{
  await assertFails(setDoc(doc(db('bob'),'users/bob/groupAccess/g'),{code:'WRONGCODEABC'}));
  await assertFails(setDoc(doc(db('bob'),'users/alice/groupAccess/g'),{code}));
});
test('valid code allows preview but not expenses until joining',async()=>{
  await access('bob');
  await assertSucceeds(getDoc(doc(db('bob'),'groups/g')));
  await assertFails(getDoc(doc(db('bob'),'groups/g/entries/e')));
});
test('claim guest grants membership and preserves existing expenses',async()=>{
  await access('bob'); await assertSucceeds(updateDoc(doc(db('bob'),'groups/g'),claim('bob')));
  await assertSucceeds(getDoc(doc(db('bob'),'groups/g/entries/e')));
});
test('joining as a new person works',async()=>{
  await access('bob'); await assertSucceeds(updateDoc(doc(db('bob'),'groups/g'),{...claim('bob','b'),'memberNames.b':'Bob'}));
});
test('joining without proof of invitation denied',async()=>{
  await assertFails(updateDoc(doc(db('bob'),'groups/g'),claim('bob')));
});
test('identity theft and double claims denied',async()=>{
  await access('bob'); await assertFails(updateDoc(doc(db('bob'),'groups/g'),claim('bob','a')));
  await assertSucceeds(updateDoc(doc(db('bob'),'groups/g'),claim('bob')));
  await access('carol'); await assertFails(updateDoc(doc(db('carol'),'groups/g'),claim('carol')));
});
test('join cannot alter group, other identities or add another account',async()=>{
  await access('bob');
  for(const extra of [{name:'Hacked'}, {'memberNames.a':'Hacked'}, {'memberClaims.a':'bob'}, {memberIds:['alice','bob','carol']}, {'memberNames.guest':'Hacked'}]) {
    await assertFails(updateDoc(doc(db('bob'),'groups/g'),{...claim('bob'),...extra}));
  }
});
test('members cannot inject new account claims',async()=>{
  await assertFails(updateDoc(doc(db('alice'),'groups/g'),{'memberClaims.guest':'carol',memberIds:arrayUnion('carol')}));
});
test('group creation and invitation are atomic',async()=>{
  const f=db('bob'), data={...seed,memberIds:['bob'],memberClaims:{b:'bob'},memberNames:{b:'Bob'},inviteCode:'NPQRSTUVWXYZ'};
  await assertFails(setDoc(doc(f,'groups/new'),data));
  const batch=writeBatch(f);batch.set(doc(f,'groups/new'),data);batch.set(doc(f,'invitations/NPQRSTUVWXYZ'),{groupId:'new'});
  await assertSucceeds(batch.commit());
});
test('existing invitation cannot be hijacked',async()=>{
  const f=db('bob'), batch=writeBatch(f);
  batch.set(doc(f,'groups/hijack'),{...seed,memberIds:['bob'],memberClaims:{b:'bob'},memberNames:{b:'Bob'}});
  batch.set(doc(f,`invitations/${code}`),{groupId:'hijack'});
  await assertFails(batch.commit());
});
test('member can delete own group content, outsider cannot',async()=>{
  await assertFails(deleteDoc(doc(db('bob'),'groups/g/entries/e')));
  await assertSucceeds(deleteDoc(doc(db('alice'),'groups/g/entries/e')));
  await assertSucceeds(deleteDoc(doc(db('alice'),'groups/g')));
});
test('rotating the code revokes previously granted preview',async()=>{
  await access('bob');
  await env.withSecurityRulesDisabled(ctx=>updateDoc(doc(ctx.firestore(),'groups/g'),{inviteCode:'NPQRSTUVWXYZ'}));
  await assertFails(getDoc(doc(db('bob'),'groups/g')));
});
