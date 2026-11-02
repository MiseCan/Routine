import http from 'node:http';
import { existsSync, readFileSync, writeFileSync, renameSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { AppError, validateRequest, callPlanner } from './planner.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
export function loadConfig() {
  const envPath = path.join(here,'.env');
  // Reload the private config for every request; edits do not require a restart.
  const values = existsSync(envPath) ? requireEnv(envPath) : {};
  const env = {...process.env,...values};
  const provider = env.AI_PROVIDER || 'deepseek';
  if(!['deepseek','openai'].includes(provider)) throw new Error('AI_PROVIDER must be deepseek or openai');
  const baseUrl = env.AI_BASE_URL || (provider === 'openai' ? 'https://api.openai.com/v1' : 'https://api.deepseek.com');
  if(new URL(baseUrl).protocol !== 'https:') throw new Error('AI_BASE_URL must use HTTPS');
  return {provider, baseUrl, model:env.AI_MODEL || (provider==='openai'?'gpt-5-mini':'deepseek-flash'), apiKey:provider==='openai'?env.OPENAI_API_KEY:env.DEEPSEEK_API_KEY, port:Number(env.PORT || 8787)};
}
function requireEnv(file) {
  const out = {};
  for(const line of readFileSync(file,'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z_][A-Z_0-9]*)\s*=\s*(.*?)\s*$/);
    if(match) out[match[1]] = match[2].replace(/^(["'])(.*)\1$/, '$2');
  }
  return out;
}
export function configureProvider(input, file=path.join(here,'.env')) {
  if(!input || !['deepseek','openai'].includes(input.provider) || typeof input.model!=='string' || !/^[a-zA-Z0-9._:/-]{1,100}$/.test(input.model) || (input.apiKey!==undefined && (typeof input.apiKey!=='string' || input.apiKey.length>512 || /\s/.test(input.apiKey)))) throw new AppError(400,'BAD_CONFIG','服务商、模型或密钥格式无效。');
  const values=existsSync(file)?requireEnv(file):{};
  const keyName=input.provider==='openai'?'OPENAI_API_KEY':'DEEPSEEK_API_KEY';
  if(input.apiKey) values[keyName]=input.apiKey;
  if(!values[keyName] && !process.env[keyName]) throw new AppError(400,'KEY_REQUIRED','请先填写该服务商的 API 密钥。');
  values.AI_PROVIDER=input.provider;
  values.AI_MODEL=input.model;
  values.AI_BASE_URL=input.provider==='openai'?'https://api.openai.com/v1':'https://api.deepseek.com';
  const temp=file+'.tmp';
  writeFileSync(temp,Object.entries(values).map(([k,v])=>`${k}=${v}`).join('\n')+'\n',{mode:0o600});
  renameSync(temp,file);
  return {configured:true,provider:input.provider,model:input.model};
}
export function createService({getConfig=loadConfig, token, planner=callPlanner, configure=configureProvider}={}) {
  const recent = [];
  let active = 0;
  const send = (res,status,data) => {res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'}); res.end(JSON.stringify(data));};
  return http.createServer(async (req,res) => {
    const supplied = Buffer.from(req.headers.authorization ?? '');
    const expected = Buffer.from(`Bearer ${token}`);
    if(!token || supplied.length !== expected.length || !timingSafeEqual(supplied,expected)) return send(res,401,{error:{code:'UNAUTHORIZED',message:'连接码不正确，请在设置中重新配置连接。'}});
    try {
      const config = getConfig();
      if(req.method==='GET' && req.url==='/health') return send(res,200,{configured:!!config.apiKey,provider:config.provider,model:config.model});
      if(req.method!=='POST' || !['/plan','/configuration'].includes(req.url)) return send(res,404,{error:{code:'NOT_FOUND',message:'接口不存在。'}});
      if(!(req.headers['content-type'] ?? '').includes('application/json')) throw new AppError(415,'CONTENT_TYPE','请求必须使用 JSON。');
      const now=Date.now();
      while(recent.length && recent[0]<now-60000) recent.shift();
      if(recent.length>=12 || active>=2) throw new AppError(429,'RATE_LIMIT','请求过于频繁，请等当前规划结束。');
      const chunks=[]; let size=0;
      for await(const chunk of req) {size+=chunk.length; if(size>256000) throw new AppError(413,'TOO_LARGE','对话内容过长，请新建规划。'); chunks.push(chunk);}
      const raw=Buffer.concat(chunks).toString('utf8');
      let body;
      try {body=JSON.parse(raw);} catch {throw new AppError(400,'BAD_JSON','请求内容格式无效。');}
      if(req.url==='/configuration') return send(res,200,configure(body));
      validateRequest(body);
      recent.push(now); active++;
      try {return send(res,200,await planner(config,body));} finally {active--;}
    } catch(e) {
      // Never log prompts, upstream payloads, Authorization headers, or API errors containing secrets.
      send(res,e instanceof AppError ? e.status:500,{error:{code:e instanceof AppError?e.code:'SERVER_ERROR',message:e instanceof AppError?e.message:'服务配置或处理异常，请检查电脑端配置。'}});
    }
  });
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const tokenPath=path.join(here,'.client-token');
  if(!existsSync(tokenPath)) writeFileSync(tokenPath,randomBytes(32).toString('hex'),{mode:0o600});
  const token=readFileSync(tokenPath,'utf8').trim();
  const config=loadConfig();
  writeFileSync(path.join(here,'connection.json'),JSON.stringify({url:`http://127.0.0.1:${config.port}`,token}),{mode:0o600});
  const server=createService({token});
  server.requestTimeout=115000; server.headersTimeout=10000;
  server.listen(config.port,'127.0.0.1',()=>console.log(`Daystep AI service: 127.0.0.1:${config.port}; provider=${config.provider}; key=${config.apiKey?'configured':'missing'}`));
}
