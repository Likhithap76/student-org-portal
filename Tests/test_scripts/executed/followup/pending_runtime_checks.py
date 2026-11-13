import pathlib,os,subprocess,signal,json,time,urllib.request,urllib.error,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
R=pathlib.Path(__file__).resolve().parent;repo=R.parent/'student-org-portal';java=next((R/'runtime').glob('jdk-*/Contents/Home/bin/java'))
env=os.environ.copy();env.update(JWT_SECRET='regression-only-jwt-secret-2026-4a74d05-minimum-32',INTERNAL_SERVICE_TOKEN='regression-only-internal-token',SPRING_PROFILES_ACTIVE='local',ORGPORTAL_DATA_DIR=str(R/'data'),LLM_BASE_URL='http://127.0.0.1:11435',LLM_MODEL='delayed-test-double')
class Delay(BaseHTTPRequestHandler):
 def do_POST(self):
  self.rfile.read(int(self.headers.get('Content-Length','0')));time.sleep(75)
  try:self.send_response(200);self.end_headers();self.wfile.write(b'{}')
  except:pass
 def log_message(self,*a):pass
server=ThreadingHTTPServer(('127.0.0.1',11435),Delay);threading.Thread(target=server.serve_forever,daemon=True).start()
def request(path,data,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(('http://127.0.0.1:8180/api' if path.startswith('/ai/') else 'http://127.0.0.1:8080/api')+path,data=json.dumps(data).encode(),headers=headers),timeout=90) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
token=request('/auth/login',{'email':'qa-officer@example.test','password':'Regression2026!Pass'})[1]['accessToken']
env['AI_SERVICE_URL']='http://localhost:8187'
gateway=subprocess.Popen([str(java),'-Xmx256m','-jar',str(next((repo/'backend/gateway/target').glob('*.jar'))),'--server.port=8180'],env=env,cwd=repo/'backend',stdout=open(R/'logs/timeout-gateway.log','w'),stderr=subprocess.STDOUT)
results=[]
for seconds in [30,60]:
 jar=next((repo/'backend/ai-service/target').glob('*.jar'));log=open(R/'logs'/f'ai-timeout-{seconds}.log','w')
 p=subprocess.Popen([str(java),'-Xmx256m','-jar',str(jar),'--server.port=8187',f'--orgportal.ai.timeout={seconds}s'],env=env,cwd=repo/'backend',stdout=log,stderr=subprocess.STDOUT)
 try:
  for _ in range(60):
   try:
    if json.load(urllib.request.urlopen('http://127.0.0.1:8187/actuator/health',timeout=1))['status']=='UP':break
   except:time.sleep(1)
  start=time.monotonic();status,body=request('/ai/summaries',{'text':'Synthetic timeout boundary test.'},token);elapsed=time.monotonic()-start
  results.append(dict(timeoutSeconds=seconds,status=status,elapsedSeconds=round(elapsed,3),response=body,passed=status==503 and seconds-2<=elapsed<=seconds+5,scope='Local delayed provider; 30s release default, 60s runtime override'))
  (R/'pending-timeout-results.json').write_text(json.dumps(results,indent=2));print(results[-1],flush=True)
 finally:p.terminate();p.wait(timeout=15)
server.shutdown();gateway.terminate();gateway.wait(timeout=15)
