import json,urllib.request,urllib.error,time,pathlib
R=pathlib.Path(__file__).resolve().parent
base='http://127.0.0.1:8080/api'
def req(path,data,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=json.dumps(data).encode(),headers=h),timeout=100) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:
  return e.code,json.load(e)
token=req('/auth/login',{'email':'qa-officer@example.test','password':'Regression2026!Pass'})[1]['accessToken']
cases=[('cold','The committee decided to hold a volunteer fair on December 5, 2026. Alex will book the room by November 20, 2026. Jamie will prepare the announcement by November 22, 2026.'),('warm1','The committee decided to hold a volunteer fair on December 5, 2026. Alex will book the room by November 20, 2026. Jamie will prepare the announcement by November 22, 2026.'),('warm2','The committee decided to hold a volunteer fair on December 5, 2026. Alex will book the room by November 20, 2026. Jamie will prepare the announcement by November 22, 2026.'),('no_tasks','The group discussed possible event ideas. No decisions were made and no tasks were assigned.'),('missing_year','The group agreed to host a workshop. Sam will book the room by November 10. Lee will prepare slides by November 15.'),('relative_date','The group agreed to review the budget. Jordan will prepare a draft next week.'),('five_payload_audit','The committee decided to organize a reading group. Taylor will draft the agenda by December 10, 2026.')]
results=[]
for label,notes in cases:
 start=time.perf_counter();status,body=req('/ai/summaries',{'text':notes},token);elapsed=round(time.perf_counter()-start,3)
 results.append({'case':label,'notes':notes,'status':status,'seconds':elapsed,'response':body})
 (R/'pending-ai-results.json').write_text(json.dumps(results,indent=2));print(label,status,elapsed,flush=True)
