import json,urllib.request,urllib.error,time,datetime,pathlib,csv,io,sys
ROOT=pathlib.Path(__file__).resolve().parent
BASE='http://127.0.0.1:8080/api';results=[];group='BR-01';off=None;mem=None
PW='Regression2026!Pass'
def record(name,ok,detail=''):
 results.append(dict(group=group,name=name,status='PASS' if ok else 'FAIL',detail=detail));print(results[-1],flush=True)
 (ROOT/'api-results.json').write_text(json.dumps(results,indent=2))
def call(method,path,data=None,token=None,expected=200,name=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 t=time.perf_counter()
 try:
  with urllib.request.urlopen(urllib.request.Request(BASE+path,data=None if data is None else json.dumps(data).encode(),headers=h,method=method),timeout=35) as r:s=r.status;b=r.read().decode()
 except urllib.error.HTTPError as e:s=e.code;b=e.read().decode()
 try:v=json.loads(b)
 except:v=b
 if name:record(name,s==expected,f'HTTP {s}; expected {expected}; {round((time.perf_counter()-t)*1000)} ms'+(' '+str(v)[:250] if s!=expected else ''))
 return v
def check(name,ok,detail=''):record(name,bool(ok),detail)
def items(v):return v if isinstance(v,list) else v.get('content',v.get('items',[]))
def login(email,pw=PW):return call('POST','/auth/login',{'email':email,'password':pw})['accessToken']
def register(n,approve=True):
 u=call('POST','/auth/register',{'email':f'qa-member-{n}@example.test','displayName':f'QA Member {n}','password':PW},expected=201,name=f'Register member {n}')
 tok=login(u['email']);a=call('GET','/memberships/me',token=tok)
 if approve:call('POST',f'/memberships/{a["id"]}/approve',token=off,name=f'Approve member {n}');tok=login(u['email'])
 return u,tok,a
try:
 off=login('qa-officer@example.test');u,mem,a=register(1);uid=u['id'];u2,mem2,a2=register(2);reject,rej,ar=register(3,False)
 call('GET','/events',token=rej,expected=403,name='Pending applicant cannot read events')
 call('POST',f'/memberships/{ar["id"]}/reject',token=off,name='Reject membership')
 check('Rejection persists',call('GET','/memberships/me',token=rej)['decision']=='REJECTED')
 call('POST','/memberships/reapply',token=rej,expected=201,name='Rejected applicant can reapply')
 check('Approval persists after fresh login',call('GET','/auth/me',token=login(u['email']))['status']=='ACTIVE')
 group='BR-02'
 now=datetime.datetime.now(datetime.timezone.utc)
 def event(title,secs=3600):return dict(title=title,description='Synthetic regression event',location='QA room',startsAt=(now+datetime.timedelta(seconds=secs)).isoformat(),endsAt=(now+datetime.timedelta(seconds=secs+3600)).isoformat())
 evdata=event('QA Regression Event');ev=call('POST','/events',evdata,off,201,'Create event');eid=ev['id']
 evdata['title']='QA Edited Event';call('PUT',f'/events/{eid}',evdata,off,name='Edit event')
 check('Edited event persists',call('GET',f'/events/{eid}',token=mem)['title']=='QA Edited Event')
 for choice in ['GOING','NOT_GOING','GOING','GOING']:
  call('PUT',f'/events/{eid}/rsvp',{'response':choice},mem,name=f'RSVP {choice}')
  detail=call('GET',f'/events/{eid}',token=mem);check('RSVP state and count persisted',detail['myResponse']==choice and detail['goingCount']==(1 if choice=='GOING' else 0))
 rr=call('GET',f'/events/{eid}/rsvps',token=off);check('Repeated RSVP remains unique',len(rr)==1)
 soon=call('POST','/events',event('QA Attendance Event',15),off,201,'Create near-start attendance event');sid=soon['id']
 group='BR-03'
 com=call('POST','/committees',dict(name='QA Regression Committee',kind='COMMITTEE',description='Synthetic'),off,201,'Create committee');cid=com['id']
 for i in range(2):call('POST',f'/committees/{cid}/members',{'userIds':[uid,u2['id']]},off,name='Add committee members idempotently')
 check('Committee members unique',len(call('GET',f'/committees/{cid}',token=off)['members'])==2)
 task=call('POST','/tasks',dict(committeeId=cid,title='QA Regression Task',assigneeId=uid,dueDate=(now+datetime.timedelta(days=1)).date().isoformat()),off,201,'Assign committee task');tid=task['id']
 check('Assigned task appears for member',any(t['id']==tid for t in items(call('GET','/tasks/me',token=mem))))
 call('DELETE',f'/committees/{cid}/members/{uid}',token=off,expected=422,name='Cannot remove member with open task')
 for s in ['IN_PROGRESS','DONE','IN_PROGRESS','DONE']:
  r=call('PATCH',f'/tasks/{tid}/status',{'status':s},mem,name=f'Task transition {s}');check('Task status response',r.get('status')==s)
 call('DELETE',f'/committees/{cid}/members/{u2["id"]}',token=off,expected=204,name='Remove member without open task')
 check('Removal persists',all(m['userId']!=u2['id'] for m in call('GET',f'/committees/{cid}',token=off)['members']))
 group='BR-06'
 ann=call('POST','/announcements',{'title':'QA Regression Announcement','body':'Synthetic regression notice'},off,201,'Publish announcement')
 check('Member sees announcement',any(a['id']==ann['id'] for a in items(call('GET','/announcements',token=mem))))
 doc=call('POST','/documents',{'title':'QA Resource','category':'Regression','url':'https://example.com/qa'},off,201,'Create HTTPS document link')
 call('POST','/documents',{'title':'Unsafe','category':'Regression','url':'http://example.com'},off,422,'Reject HTTP document link')
 call('PUT',f'/documents/{doc["id"]}',{'title':'QA Resource Edited','category':'Regression','url':'https://example.com/edited'},off,name='Edit document link')
 check('Member sees edited link',any(x['title']=='QA Resource Edited' for x in items(call('GET','/documents?category=Regression',token=mem))))
 notices=items(call('GET','/notifications/me?size=100',token=mem));(ROOT/'notification-sample.json').write_text(json.dumps(notices,indent=2))
 check('Membership task and announcement notices delivered',len(notices)>=3,str([n.get('type') for n in notices]))
 if notices:
  read=call('PATCH',f'/notifications/{notices[0]["id"]}/read',token=mem,name='Mark notification read');check('Read state persists',read.get('read')==True)
 call('POST','/notifications/me/read-all',token=mem,name='Mark all notifications read')
 check('Unread count becomes zero',call('GET','/notifications/me/unread-count',token=mem)['count']==0)
 call('DELETE',f'/documents/{doc["id"]}',token=off,expected=204,name='Delete document link')
 call('DELETE',f'/announcements/{ann["id"]}',token=off,expected=204,name='Delete announcement')
 check('Deleted content absent',not any(x['id']==doc['id'] for x in items(call('GET','/documents',token=mem))) and not any(x['id']==ann['id'] for x in items(call('GET','/announcements',token=mem))))
 group='BR-01'
 remaining=(now+datetime.timedelta(seconds=16)-datetime.datetime.now(datetime.timezone.utc)).total_seconds()
 if remaining>0:time.sleep(remaining)
 for state in ['PRESENT','ABSENT','PRESENT']:
  call('PUT',f'/events/{sid}/attendance',[{'userId':uid,'status':state}],off,name=f'Attendance {state}')
 att=call('GET',f'/events/{sid}/attendance',token=off);(ROOT/'attendance-sample.json').write_text(json.dumps(att,indent=2))
 group='BR-04'
 dates=f'?from={now.date()}&to={(now+datetime.timedelta(days=1)).date()}'
 report=call('GET','/reports/engagement'+dates,token=off,name='Engagement valid date range');row=next(r for r in report['rows'] if r['userId']==uid)
 check('Engagement reconciles attendance and completed task',row['eventsAttended']==1 and row['tasksCompleted']==1,str(row))
 csvtext=call('GET','/reports/engagement.csv'+dates,token=off,name='Authenticated CSV export');csvrows=list(csv.DictReader(io.StringIO(csvtext)));check('CSV includes same member and counts',any(str(uid)==str(list(r.values())[0]) and '1' in r.values() for r in csvrows),str(csvrows[:2]))
 call('GET','/reports/engagement?from=2030-02-01&to=2030-01-01',token=off,expected=422,name='Invalid report date range rejected')
 old=call('GET','/reports/engagement?from=2020-01-01&to=2020-01-02',token=off,name='Alternate empty date range');check('Alternate range totals zero',all(r['eventsAttended']==0 and r['tasksCompleted']==0 for r in old['rows']))
 group='BR-05'
 call('PUT','/auth/me',{'displayName':'QA Updated Member'},mem,name='Edit profile')
 check('Profile persists on login',call('GET','/auth/me',token=login(u['email']))['displayName']=='QA Updated Member')
 newpw='Regression2026!Changed';call('PUT','/auth/me/password',{'currentPassword':PW,'newPassword':newpw},mem,204,'Change password')
 call('POST','/auth/login',{'email':u['email'],'password':PW},expected=401,name='Old password rejected');mem=login(u['email'],newpw)
 call('PUT',f'/users/{u2["id"]}/role',{'role':'OFFICER_ADMIN'},off,name='Promote member');promoted=login(u2['email']);call('GET','/users/roster',token=promoted,name='Promoted officer permitted')
 call('PUT',f'/users/{u2["id"]}/role',{'role':'MEMBER'},off,name='Demote officer');call('GET','/users/roster',token=promoted,expected=403,name='Demotion enforced with old token')
 call('POST',f'/users/{u2["id"]}/deactivate',token=off,name='Deactivate account');call('GET','/events',token=mem2,expected=403,name='Inactive member denied')
 call('POST',f'/users/{u2["id"]}/reactivate',token=off,name='Reactivate account');call('GET','/events',token=login(u2['email']),name='Reactivated member access')
 group='BR-08'
 for method,path,data,tok,expected,label in [
 ('GET','/events',None,None,401,'Unauthenticated request'),('GET','/events',None,'invalid',401,'Invalid token'),('GET','/users/roster',None,mem,403,'Member roster restriction'),('GET','/reports/engagement',None,mem,403,'Member reporting restriction'),('POST','/events',evdata,mem,403,'Member event creation restriction'),('PUT',f'/events/{sid}/attendance',[{'userId':uid,'status':'PRESENT'}],mem,403,'Member attendance write restriction'),('POST','/committees',{'name':'Not allowed','kind':'COMMITTEE'},mem,403,'Member committee restriction'),('POST','/announcements',{'title':'Not allowed','body':'Synthetic'},mem,403,'Member announcement restriction'),('POST','/ai/summaries',{'text':'Synthetic'},mem,403,'Member AI restriction'),('GET','/internal/users',None,off,403,'Gateway internal path restriction')]:call(method,path,data,tok,expected,label)
 group='BR-02';call('POST',f'/events/{eid}/cancel',token=off,name='Cancel event');check('Cancelled event persists',call('GET',f'/events/{eid}',token=off)['status']=='CANCELLED')
 group='BR-07'
 call('POST','/ai/summaries',{'text':''},off,422,'AI empty input rejected');call('POST','/ai/summaries',{'text':'x'*5001},off,422,'AI over-limit input rejected')
 call('POST','/ai/summaries',{'text':'QA_SYNTHETIC_NOTES_SENTINEL meeting decided to test the portal.'},off,503,'Unavailable model gives graceful 503')
 (ROOT/'fixture.json').write_text(json.dumps(dict(officerEmail='qa-officer@example.test',memberEmail=u['email'],memberPassword=newpw,memberId=uid,otherId=u2['id'],committeeId=cid,taskId=tid,eventId=eid,attendanceEventId=sid),indent=2))
except Exception as e:record('Harness interrupted',False,repr(e));raise
finally:
 print('SUMMARY',len(results),'checks',sum(r['status']=='PASS' for r in results),'passed',flush=True)
