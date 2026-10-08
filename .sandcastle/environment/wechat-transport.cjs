const WebSocket=require(process.env.WECHAT_WS_MODULE||'/usr/local/lib/node_modules/miniprogram-automator/node_modules/ws');
const endpoint=process.env.WECHAT_WS_ENDPOINT||'ws://127.0.0.1:9420';
const socket=new WebSocket(endpoint);const timer=setTimeout(()=>{console.error('transport timeout');socket.terminate();process.exitCode=124},10000);
socket.on('error',e=>{console.error(e.message);clearTimeout(timer);process.exitCode=1});
socket.on('open',()=>{console.log('WebSocket open');socket.send(JSON.stringify({id:'environment-probe',method:'Tool.getInfo',params:{}}));});
socket.on('message',raw=>{const event=JSON.parse(raw);if(event.id==='environment-probe'){console.log(JSON.stringify({sdkVersion:event.result?.SDKVersion,error:event.error?.message}));clearTimeout(timer);socket.close();}});
