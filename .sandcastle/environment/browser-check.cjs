const {chromium}=require('/usr/local/lib/node_modules/playwright');
const assert=require('node:assert/strict');
const WebSocket=require('/usr/local/lib/node_modules/miniprogram-automator/node_modules/ws');
function wsPing(token){return new Promise((resolve,reject)=>{
 const socket=new WebSocket('ws://127.0.0.1:8080/ws/customer-service/admin'+(token?'?token='+encodeURIComponent(token):''));
 const timer=setTimeout(()=>{socket.terminate();reject(new Error('isolated WS deadline'))},10000);
 socket.on('open',()=>socket.send(JSON.stringify({type:'PING'})));
 socket.on('message',raw=>{const message=JSON.parse(raw);if(message.type==='PONG'){clearTimeout(timer);socket.close();resolve('PONG')}});
 socket.on('close',()=>{clearTimeout(timer);resolve('CLOSED')});
 socket.on('error',()=>{clearTimeout(timer);reject(new Error('isolated WS transport failed'))});
});}
(async()=>{
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:5173/',{waitUntil:'networkidle'});
  await page.getByPlaceholder('用户名').waitFor();
  await page.screenshot({path:'/evidence/admin-login.png',fullPage:true});
  await page.getByPlaceholder('用户名').fill('sandbox_admin');
  await page.getByPlaceholder('密码',{exact:true}).fill('sandbox-only-login');
  const login=page.waitForResponse(r=>r.url().endsWith('/admin/employee/login')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'登录'}).click();
  const reply=await(await login).json();assert.equal(reply.code,1);
  await page.waitForURL(url=>!url.pathname.includes('login'));
  await page.waitForTimeout(1000);
  await page.screenshot({path:'/evidence/admin-dashboard.png',fullPage:true});
  assert.equal(await wsPing(reply.data.token),'PONG','actual password-authenticated admin WebSocket');
  assert.equal(await wsPing(), 'CLOSED','unauthenticated WebSocket rejected');
  await page.goto('http://127.0.0.1:5173/inform',{waitUntil:'networkidle'});
  await page.screenshot({path:'/evidence/admin-customer-service.png',fullPage:true});
  assert.equal(errors.length,0,'browser page errors');
  console.log(JSON.stringify({normalFixturePasswordLogin:true,routes:['/dashboard',new URL(page.url()).pathname],authenticatedAdminWsPing:true,unauthenticatedWsRejected:true,screenshots:['admin-login.png','admin-dashboard.png','admin-customer-service.png'],pageErrors:errors.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1});
