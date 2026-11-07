import test from 'node:test';
import assert from 'node:assert/strict';
import { validateRequest, validateResult, buildUpstream, callPlanner, AppError } from '../planner.mjs';
import {createService,configureProvider} from '../server.mjs';
import {mkdtempSync,readFileSync,rmSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const context=()=>({now:'2026-10-29T18:00:00+08:00',today:'2026-10-29',timeZone:'Asia/Shanghai',defaultDailyMinutes:30,customPrompt:'不要在周日安排任务。',existingSteps:[],messages:[{role:'user',content:'下周五交一份三页的报告，每天30分钟，已有资料。',at:'2026-10-29T18:00:00+08:00'}]});
const ready=()=>({reply:'已生成可检查的每日草案。',status:'ready',task:{title:'三页课程报告',deliverable:'三页完整报告',deadline:'2026-11-06',deadlineTime:'18:00',dailyMinutes:30,progress:'已有资料',constraints:[]},questions:[],steps:[{date:'2026-10-29',title:'从已有资料提取三个核心结论',minutes:20,definitionOfDone:'写出三条结论并标注各自的资料来源。'},{date:'2026-11-05',title:'核对报告格式并导出提交版本',minutes:30,definitionOfDone:'导出一个三页PDF，逐条检查交付要求。'}],warnings:[]});

test('accepts complete multi-field messages without a fixed questionnaire',()=>assert.equal(validateRequest(context()).messages.length,1));
test('validates leap dates and rejects an impossible date',()=>{const body=context();body.today='2026-02-30';assert.throws(()=>validateRequest(body),AppError)});
test('keeps relative-date message timestamps and custom prompt in the model request',()=>{
  const body=context();body.messages.push({role:'user',content:'调整一下',at:'2026-10-30T09:00:00+08:00'});
  const request=buildUpstream({provider:'deepseek',model:'deepseek-flash'},body);
  assert.equal(request.payload.response_format.type,'json_object');
  assert.equal(request.payload.thinking.type,'disabled');
  assert.match(request.payload.messages[1].content,/不要在周日/);
  assert.match(request.payload.messages[2].content,/2026-10-29T18:00/);
  assert.match(request.payload.messages[3].content,/2026-10-30T09:00/);
});
test('ready plans receive a unique ID and retain completion criteria',()=>{const a=validateResult(ready(),context()),b=validateResult(ready(),context());assert.notEqual(a.planId,b.planId);assert.equal(a.steps[0].minutes,20);assert.match(a.steps[0].definitionOfDone,/资料来源/)});
test('missing deadline cannot produce an actionable plan',()=>{const r=ready();r.task.deadline=null;assert.throws(()=>validateResult(r,context()),/尚未齐全/)});
test('clarification returns selectable questions with no board actions',()=>{
  const r=ready();r.status='clarify';r.task.deadline=null;r.steps=[];r.questions=[{id:'deadline',prompt:'哪一天交付？',kind:'date',options:[{label:'明天',value:'2026-10-30'}]}];
  const parsed=validateResult(r,context());assert.equal(parsed.planId,null);assert.equal(parsed.questions[0].options[0].value,'2026-10-30');
});
test('rejects past dates, dates after deadline and out-of-order steps',()=>{
  for(const dates of [['2026-10-28','2026-11-05'],['2026-10-29','2026-11-07'],['2026-11-05','2026-10-29']]){const r=ready();r.steps.forEach((s,i)=>s.date=dates[i]);assert.throws(()=>validateResult(r,context()),AppError)}
});
test('overbooking with existing board tasks converts ready to infeasible',()=>{const c=context();c.existingSteps=[{date:'2026-10-29',title:'已有任务',minutes:20}];const r=validateResult(ready(),c);assert.equal(r.status,'infeasible');assert.equal(r.steps.length,0);assert.equal(r.planId,null);assert.match(r.reply,/40 分钟/)});
test('rejects implausible step duration and missing completion criteria',()=>{let r=ready();r.steps[0].minutes=0;assert.throws(()=>validateResult(r,context()),AppError);r=ready();r.steps[0].definitionOfDone='';assert.throws(()=>validateResult(r,context()),AppError)});
test('a same-day deadline already passed cannot be confirmed',()=>{const r=ready();r.task.deadline='2026-10-29';r.task.deadlineTime='17:30';r.steps=r.steps.slice(0,1);assert.equal(validateResult(r,context()).status,'infeasible')});
test('returns actionable configuration and quota errors without leaking upstream details',async()=>{
  await assert.rejects(()=>callPlanner({apiKey:''},context()),e=>e.code==='AI_NOT_CONFIGURED');
  await assert.rejects(()=>callPlanner({provider:'deepseek',apiKey:'test-only',model:'x',baseUrl:'https://example.test'},context(),async()=>({ok:false,status:401})),e=>e.code==='UPSTREAM_ERROR'&&!e.message.includes('test-only'));
});
test('DeepSeek HTTP adapter parses real API-shaped JSON and rejects truncated or malformed output',async()=>{
  const config={provider:'deepseek',apiKey:'test-only',model:'deepseek-flash',baseUrl:'https://example.test'};
  let sent;
  const fetcher=async(url,init)=>{sent={url,body:JSON.parse(init.body)};return {ok:true,json:async()=>({choices:[{finish_reason:'stop',message:{content:JSON.stringify(ready())}}]})}};
  assert.equal((await callPlanner(config,context(),fetcher)).status,'ready');
  assert.equal(sent.url,'https://example.test/chat/completions');
  assert.equal(sent.body.model,'deepseek-flash');
  for(const choice of [{finish_reason:'length',message:{content:'{}'}},{finish_reason:'stop',message:{content:''}}]) await assert.rejects(()=>callPlanner(config,context(),async()=>({ok:true,json:async()=>({choices:[choice]})})),AppError);
});
test('OpenAI adapter uses structured Responses and handles a refusal',async()=>{
  const config={provider:'openai',apiKey:'test-only',model:'gpt-5-mini',baseUrl:'https://example.test'};
  const request=buildUpstream(config,context());assert.equal(request.path,'/responses');assert.equal(request.payload.store,false);assert.equal(request.payload.text.format.strict,true);
  assert.equal((await callPlanner(config,context(),async()=>({ok:true,json:async()=>({output:[{content:[{type:'output_text',text:JSON.stringify(ready())}]}]})}))).status,'ready');
  await assert.rejects(()=>callPlanner(config,context(),async()=>({ok:true,json:async()=>({output:[{content:[{type:'refusal'}]}]})})),e=>e.code==='REFUSAL');
});
test('HTTP service requires its connection code and supports configuration without returning a secret',async()=>{
  let planned=0,configured;
  const server=createService({token:'local-test-token',getConfig:()=>({apiKey:'test-only',provider:'deepseek',model:'deepseek-flash'}),planner:async()=>{planned++;return validateResult(ready(),context())},configure:body=>{configured=body;return {configured:true,provider:body.provider,model:body.model}}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  const headers={Authorization:'Bearer local-test-token','Content-Type':'application/json'};
  try {
    assert.equal((await fetch(base+'/health')).status,401);
    assert.equal((await (await fetch(base+'/health',{headers})).json()).configured,true);
    assert.equal((await fetch(base+'/plan',{method:'POST',headers,body:'bad'})).status,400);
    const r=await fetch(base+'/plan',{method:'POST',headers,body:JSON.stringify(context())});assert.equal(r.status,200);assert.equal((await r.json()).status,'ready');assert.equal(planned,1);
    const config=await fetch(base+'/configuration',{method:'POST',headers,body:JSON.stringify({provider:'deepseek',model:'deepseek-flash',apiKey:'test-secret'})});assert.equal(config.status,200);assert.equal(configured.apiKey,'test-secret');assert.equal((await config.text()).includes('test-secret'),false);
  } finally {await new Promise(resolve=>server.close(resolve));}
});
test('provider settings persist keys server-side, preserve a key on blank input and reject newline injection',()=>{
  const dir=mkdtempSync(path.join(os.tmpdir(),'routine-config-test-'));
  const file=path.join(dir,'.env');
  try {
    const r=configureProvider({provider:'deepseek',model:'deepseek-flash',apiKey:'test-secret'},file);
    assert.equal(JSON.stringify(r).includes('test-secret'),false);
    assert.match(readFileSync(file,'utf8'),/DEEPSEEK_API_KEY=test-secret/);
    configureProvider({provider:'deepseek',model:'deepseek-v4-pro',apiKey:''},file);
    assert.match(readFileSync(file,'utf8'),/DEEPSEEK_API_KEY=test-secret/);
    assert.match(readFileSync(file,'utf8'),/AI_MODEL=deepseek-v4-pro/);
    assert.throws(()=>configureProvider({provider:'deepseek',model:'x\nPORT=80',apiKey:'test'},file),AppError);
    assert.throws(()=>configureProvider({provider:'deepseek',model:'x',apiKey:'test\nPORT=80'},file),AppError);
  }finally{rmSync(dir,{recursive:true,force:true})}
});
