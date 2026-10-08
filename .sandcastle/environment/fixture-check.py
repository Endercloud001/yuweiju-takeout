import os,json,socket,subprocess,time,urllib.request,urllib.error
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
p=subprocess.Popen(['python3','/environment/fixture-server.py'])
try:
 for _ in range(50):
  try:opener.open('http://127.0.0.1:18081/health',timeout=1);break
  except Exception:time.sleep(.1)
 statuses={}
 for case in ['402','503','bad-output']:
  req=urllib.request.Request('http://127.0.0.1:18081/v1/chat/completions?case='+case,data=b'{}',method='POST')
  try:response=opener.open(req,timeout=2);code=response.status;body=response.read()
  except urllib.error.HTTPError as err:code=err.code;body=err.read()
  assert code==(int(case) if case in ['402','503'] else 200)
  statuses[case]=code
 try:opener.open(urllib.request.Request('http://127.0.0.1:18081/v1/chat/completions?case=timeout',data=b'{}'),timeout=.2);raise AssertionError('expected timeout')
 except (TimeoutError,socket.timeout):statuses['timeout']='observed'
 response=opener.open(urllib.request.Request('http://127.0.0.1:18082/sandbox-only/probe.txt',data=b'isolated storage fixture',method='PUT'),timeout=2)
 assert response.status==200 and opener.open('http://127.0.0.1:18082/sandbox-only/probe.txt').read()==b'isolated storage fixture'
 if os.environ.get('FIXTURE_JAVA_CLASSPATH'):
  result=subprocess.run(['java','-cp',os.environ['FIXTURE_JAVA_CLASSPATH'],'StorageProbe'])
  if result.returncode:raise RuntimeError('StorageProbe exit '+str(result.returncode))
 print(json.dumps({'localHttpCases':statuses,'isolatedStorageWriteRead':True,'externalCalls':False}))
finally:p.terminate();p.wait(timeout=10)
