import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { task } from './task-config.mts';
import { boundedRun, repo, evidence, quote } from './common.mts';
import { resultSummary } from './result-summary.mts';
import type { AgentProvider } from '@ai-hero/sandcastle';
assert.ok(task);
const expectedPrompt='Configured no-model prompt fixture.';
const js=`const fs=require('node:fs'),cp=require('node:child_process'),assert=require('node:assert/strict');
assert.equal(fs.readFileSync('/home/agent/task-input/probe.txt','utf8'),'Configured readonly input fixture.');
assert.ok(fs.existsSync('/opt/java/openjdk/bin/java'));
assert.equal(process.env.LANG,'C.UTF-8');assert.equal(process.env.LC_ALL,'C.UTF-8');
const iteration=fs.existsSync('.sandcastle/config-fixture-progress.md')?2:1;
fs.mkdirSync('/home/agent/.m2',{recursive:true});fs.mkdirSync('/home/agent/.npm',{recursive:true});
for(const directory of ['/home/agent/.m2','/home/agent/.npm']){const marker=directory+'/fixture-cache.txt';if(iteration===2)assert.equal(fs.readFileSync(marker,'utf8'),'cache retained');else fs.writeFileSync(marker,'cache retained');}
cp.execFileSync('java',['-XshowSettings:properties','-version'],{encoding:'utf8',stdio:['ignore','ignore','pipe']});
fs.writeFileSync('中文路径.txt','UTF-8 native path');

fs.mkdirSync('.sandcastle',{recursive:true});fs.writeFileSync('.sandcastle/config-fixture-progress.md','Configured progress retained');
fs.writeFileSync('config-fixture-committed.txt','No-model configured task '+iteration);
cp.execFileSync('git',['add','--','config-fixture-committed.txt']);cp.execFileSync('git',['commit','-m','test: configured no-model fixture']);
console.log(JSON.stringify({type:'text',text:iteration===2?'<promise>COMPLETE</promise>':'Local fixture first iteration incomplete'}));`;
const provider:AgentProvider={name:'configured-local-fixture',env:{},captureSessions:false,
 buildPrintCommand(options){assert.equal(options.prompt,expectedPrompt);return {command:'node -e '+quote(js)};},
 parseStreamLine(line){const e=JSON.parse(line);return e.type==='text'?[{type:'text',text:e.text}]:[];}};
const {result,resourceFile}=await boundedRun(provider,task.branch,true,{prompt:undefined,promptFile:task.promptFile},30000);
const summary=resultSummary(result,resourceFile,task.progressFile);assert.equal(summary.status,'agent-reported-complete-awaiting-independent-review');
const resource=JSON.parse(readFileSync(resourceFile,'utf8'));assert.equal(resource.stopped,true);
assert.equal(resource.iterations.length,2);assert.ok(resource.iterations.every((entry:any)=>entry.stopped));
assert.equal(new Set(resource.iterations.map((entry:any)=>entry.containerId)).size,2);
assert.ok(resource.iterations.every((entry:any)=>entry.environment.locale.includes('LC_ALL=C.UTF-8')));
assert.ok(resource.mounts.some((entry:any)=>entry.destination==='/home/agent/.m2'&&entry.source.startsWith(evidence)));
assert.ok(resource.mounts.some((entry:any)=>entry.destination==='/home/agent/.npm'&&entry.source.startsWith(evidence)));
assert.equal(resource.mounts.some((m:any)=>m.destination==='/home/agent/.codex'),false);
assert.equal(resource.mounts.some((m:any)=>m.destination==='/home/agent/task-input'&&!m.writable),true);
const parent=execFileSync('git',['-C',repo,'rev-parse',task.branch+'~2'],{encoding:'utf8'}).trim();
const configured=execFileSync('git',['-C',repo,'rev-parse',task.startCommit],{encoding:'utf8'}).trim();assert.equal(parent,configured);
assert.equal(readFileSync(join(resource.worktree,task.progressFile!), 'utf8'),'Configured progress retained');
writeFileSync(join(evidence,'config-fixture-result.json'),JSON.stringify({passed:true,authMounted:false,modelCalled:false,startCommit:parent,...summary},null,2));
console.log(JSON.stringify({passed:true,startCommit:parent,resourceFile,progressFile:summary.progressFile}));
