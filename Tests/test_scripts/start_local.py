import os,sys,pathlib,subprocess,time,urllib.request,json,signal,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
repo=ROOT;out=ROOT/'Tests/test_results/reproduced';out.mkdir(parents=True,exist_ok=True);(out/'logs').mkdir(exist_ok=True)
java=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not java:raise SystemExit('Java 21 is required; set JAVA_HOME.')
env=os.environ.copy();env.update(JWT_SECRET='regression-only-jwt-secret-2026-4a74d05-minimum-32',INTERNAL_SERVICE_TOKEN='regression-only-internal-token',SEED_ADMIN_EMAIL='qa-officer@example.test',SEED_ADMIN_PASSWORD='Regression2026!Pass',SPRING_PROFILES_ACTIVE='local',ORGPORTAL_DATA_DIR=str(out/'data'),LLM_BASE_URL='http://127.0.0.1:11434',LLM_MODEL='llama3.2:3b')
services=['user-service','event-service','task-service','content-service','notification-service','reporting-service','ai-service','gateway'];processes=[]
def stop(*args):
 for p in processes:p.terminate()
 for p in processes:
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:p.kill()
 raise SystemExit
signal.signal(signal.SIGINT,stop);signal.signal(signal.SIGTERM,stop)
try:
 for svc,port in zip(services,[8081,8082,8083,8084,8085,8086,8087,8080]):
  jars=list((repo/'backend'/svc/'target').glob('*.jar'))
  if not jars:raise RuntimeError('Build backend first: '+svc)
  p=subprocess.Popen([java,'-Xmx256m','-jar',str(jars[0])],env=env,cwd=repo/'backend',stdout=open(out/'logs'/f'{svc}.log','w'),stderr=subprocess.STDOUT);processes.append(p)
  for attempt in range(90):
   if p.poll() is not None:raise RuntimeError(svc+' exited; inspect reproduced/logs')
   try:
    if json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=2))['status']=='UP':break
   except Exception:pass
   time.sleep(1)
  else:raise RuntimeError(svc+' health timeout')
  print('HEALTHY',svc,flush=True)
 print('READY. Ctrl+C stops this test stack.',flush=True)
 while True:time.sleep(5)
finally:
 for p in processes:
  if p.poll() is None:p.terminate()
