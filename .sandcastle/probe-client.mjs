import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
const child=spawn('codex',['app-server','--stdio','--strict-config'],{stdio:['pipe','pipe','pipe']});
const pending=new Map(); let seq=0; let err='';
child.stderr.on('data',d=>{err+=d.toString();});
createInterface({input:child.stdout}).on('line',line=>{try{const msg=JSON.parse(line); if(msg.id!==undefined&&pending.has(msg.id)){pending.get(msg.id)(msg); pending.delete(msg.id);}}catch{}});
const request=(method,params)=>new Promise((resolve,reject)=>{const id=++seq;const timer=setTimeout(()=>{pending.delete(id);reject(new Error('RPC timeout: '+method));},45000);pending.set(id,msg=>{clearTimeout(timer);if(msg.error)reject(new Error(method+': '+JSON.stringify(msg.error)));else resolve(msg.result);});child.stdin.write(JSON.stringify({id,method,params})+'\n');});
try{
await request('initialize',{clientInfo:{name:'yuweiju-preparation',version:'1.0.0'},capabilities:{experimentalApi:true}});
child.stdin.write(JSON.stringify({method:'initialized',params:{}})+'\n');
const config=await request('config/read',{cwd:'/home/agent/workspace',includeLayers:false});
const catalog=await request('model/list',{includeHidden:true,limit:100});
const target=catalog.data.find(m=>m.model==='gpt-6.1-sol');
const skills=await request('skills/list',{cwds:['/home/agent/workspace'],forceReload:true});
console.log(JSON.stringify({config:{forced_login_method:config.config.forced_login_method,model:config.config.model,model_reasoning_effort:config.config.model_reasoning_effort,service_tier:config.config.service_tier},model:target?{id:target.id,model:target.model,efforts:target.supportedReasoningEfforts,tiers:target.serviceTiers,defaultTier:target.defaultServiceTier}:null,skills:skills.data.map(e=>({cwd:e.cwd,skills:e.skills.map(s=>({name:s.name,path:s.path,enabled:s.enabled})),errors:e.errors})),catalogNextCursor:catalog.nextCursor},null,2));
if(!target)throw new Error('Requested model absent from catalog');
if(!target.supportedReasoningEfforts.some(e=>e.reasoningEffort==='medium'))throw new Error('Medium unsupported');
}catch(e){console.error(e.message);process.exitCode=1;}finally{child.stdin.end();child.kill('SIGTERM');setTimeout(()=>child.kill('SIGKILL'),1000).unref();}