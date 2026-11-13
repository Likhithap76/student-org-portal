import pathlib,os,subprocess,signal,time,json,urllib.request
R=pathlib.Path(__file__).resolve().parent;repo=R.parent/'student-org-portal';java=next((R/'runtime').glob('jdk-*/Contents/Home/bin/java'))
env=os.environ.copy();env.update(JWT_SECRET='regression-only-jwt-secret-2026-4a74d05-minimum-32',INTERNAL_SERVICE_TOKEN='regression-only-internal-token',SPRING_PROFILES_ACTIVE='local',ORGPORTAL_DATA_DIR=str(R/'data'),USER_SERVICE_URL='http://localhost:8181',CORS_ALLOWED_ORIGINS='http://localhost:5175')
ps=[]
def stop(*a):
 for p in ps:p.terminate()
 for p in ps:p.wait(timeout=15)
 raise SystemExit
signal.signal(signal.SIGINT,stop);signal.signal(signal.SIGTERM,stop)
for svc,port in [('user-service',8181),('gateway',8189)]:
 args=[str(java),'-Xmx256m','-jar',str(next((repo/'backend'/svc/'target').glob('*.jar'))),f'--server.port={port}']
 if svc=='user-service':args+=['--orgportal.security.jwt-expiry=20s']
 ps.append(subprocess.Popen(args,env=env,cwd=repo/'backend',stdout=open(R/'logs'/f'expiry-{svc}.log','w'),stderr=subprocess.STDOUT))
 for _ in range(50):
  try:
   if json.load(urllib.request.urlopen(f'http://localhost:{port}/actuator/health'))['status']=='UP':break
  except:time.sleep(1)
 print('READY',svc,flush=True)
while True:time.sleep(5)
