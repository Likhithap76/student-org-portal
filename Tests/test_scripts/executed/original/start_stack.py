import os,subprocess,pathlib,time,urllib.request,json,signal
root=pathlib.Path(__file__).resolve().parent
repo=root.parent/'student-org-portal'
java=next((root/'runtime').glob('jdk-*/Contents/Home/bin/java'))
env=os.environ.copy();env.update(JWT_SECRET='regression-only-jwt-secret-2026-4a74d05-minimum-32',INTERNAL_SERVICE_TOKEN='regression-only-internal-token',SEED_ADMIN_EMAIL='qa-officer@example.test',SEED_ADMIN_PASSWORD='Regression2026!Pass',SPRING_PROFILES_ACTIVE='local',ORGPORTAL_DATA_DIR=str(root/'data'),LLM_BASE_URL='http://127.0.0.1:11434',LLM_MODEL='llama3.2:3b')
services=['user-service','event-service','task-service','content-service','notification-service','reporting-service','ai-service','gateway']
processes=[]
def stop(*a):
 for p in processes:p.terminate()
 for p in processes:
  try:p.wait(timeout=15)
  except: p.kill()
 raise SystemExit
signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
for svc,port in zip(services,[8081,8082,8083,8084,8085,8086,8087,8080]):
 jar=next((repo/'backend'/svc/'target').glob('*.jar'))
 log=open(root/'logs'/f'{svc}.log','w')
 p=subprocess.Popen([str(java),'-Xmx256m','-jar',str(jar)],env=env,cwd=repo/'backend',stdout=log,stderr=subprocess.STDOUT);processes.append(p)
 for _ in range(90):
  if p.poll() is not None: print('START FAILED',svc,flush=True);stop()
  try:
   with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=2) as r:
    if json.load(r)['status']=='UP':break
  except Exception:pass
  time.sleep(1)
 else:print('HEALTH TIMEOUT',svc,flush=True);stop()
 print('HEALTHY',svc,port,flush=True)
(root/'stack-pids.json').write_text(json.dumps([p.pid for p in processes]))
print('STACK READY',flush=True)
while True:time.sleep(5)
