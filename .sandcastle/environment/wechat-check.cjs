const automator=require(process.env.WECHAT_AUTOMATOR_MODULE||'/usr/local/lib/node_modules/miniprogram-automator');
const fs=require('node:fs');
(async()=>{
 const endpoint=process.env.WECHAT_WS_ENDPOINT||'ws://127.0.0.1:9420';
 const timeout=setTimeout(()=>{console.error('WeChat probe deadline');process.exit(124)},60000);
 const mini=await automator.connect({wsEndpoint:endpoint});console.log("Connected to WeChat automation");
 try{
  const page=await mini.currentPage();console.log("Read current simulator page");
  if(!page||page.path!=='pages/probe/index')throw new Error('Only the isolated probe project is allowed');
  await mini.screenshot({path:(process.env.WECHAT_SCREENSHOT||'/evidence/wechat-probe.png')});
  console.log(JSON.stringify({connected:true,path:page.path,screenshot:true}));
 }finally{await mini.disconnect();clearTimeout(timeout);}
})().catch(e=>{console.error(e.message);process.exitCode=1});
