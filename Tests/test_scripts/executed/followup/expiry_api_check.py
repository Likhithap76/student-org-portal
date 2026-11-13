import urllib.request,urllib.error,json,time,pathlib,base64
R=pathlib.Path(__file__).resolve().parent
base='http://localhost:8189/api'
r=json.load(urllib.request.urlopen(urllib.request.Request(base+'/auth/login',data=json.dumps({'email':'qa-officer@example.test','password':'Regression2026!Pass'}).encode(),headers={'Content-Type':'application/json'})))
token=r['accessToken'];claims=json.loads(base64.urlsafe_b64decode(token.split('.')[1]+'=='))
def check():
 try:return urllib.request.urlopen(urllib.request.Request(base+'/events',headers={'Authorization':'Bearer '+token})).status
 except urllib.error.HTTPError as e:return e.code
before=check();time.sleep(21);after=check();result={'configuredLifetimeSeconds':claims['exp']-claims['iat'],'beforeExpiryStatus':before,'after21SecondsStatus':after,'passed':before==200 and after==401,'scope':'20-second runtime override on exact source; production two-hour waiting period not executed'}
(R/'pending-expiry-results.json').write_text(json.dumps(result,indent=2));print(result)
