exec(open(__file__.replace('followup.py','api_regression.py')).read().split('\ntry:\n')[0].replace("'api-results.json'","'followup-results.json'"))
from zoneinfo import ZoneInfo
f=json.loads((ROOT/'fixture.json').read_text());off=login(f['officerEmail']);mem=login(f['memberEmail'],f['memberPassword']);uid=f['memberId'];cid=f['committeeId'];tid=f['taskId'];sid=f['attendanceEventId']
group='BR-04'
today=datetime.datetime.now(ZoneInfo('America/Chicago')).date();dates=f'?from={today}&to={today}'
report=call('GET','/reports/engagement'+dates,token=off,name='Report uses organization-local date')
row=next(r for r in report['rows'] if r['userId']==uid);check('Attendance and completed task reconcile on Central date',row['eventsAttended']==1 and row['tasksCompleted']==1,str(row))
csvtext=call('GET','/reports/engagement.csv'+dates,token=off,name='CSV for same Central date');csvrows=list(csv.DictReader(io.StringIO(csvtext)))
check('Every CSV row matches JSON counts',len(csvrows)==len(report['rows']) and all(any(int(c['user_id'])==r['userId'] and int(c['events_attended'])==r['eventsAttended'] and int(c['tasks_completed'])==r['tasksCompleted'] for c in csvrows) for r in report['rows']))
group='BR-01';att=call('GET',f'/events/{sid}/attendance',token=off);check('Attendance remains unique and PRESENT',len(att['entries'])==1 and att['entries'][0]['status']=='PRESENT' and att['presentCount']==1)
group='BR-06';notices=items(call('GET','/notifications/me?size=100',token=mem));check('All three notification kinds delivered',{'application_decision','task_assigned','announcement'}.issubset({n['kind'] for n in notices}))
group='BR-08'
for n in range(4,22):register(n)
now=datetime.datetime.now(datetime.timezone.utc)
for n in range(8):call('POST','/events',dict(title=f'QA Fixture Event {n}',description='Timing fixture',location='QA',startsAt=(now+datetime.timedelta(days=n+1)).isoformat(),endsAt=(now+datetime.timedelta(days=n+1,hours=1)).isoformat()),off,201,f'Create timing event {n}')
for n in range(29):call('POST','/tasks',dict(committeeId=cid,title=f'QA Fixture Task {n}',assigneeId=uid,dueDate=(today+datetime.timedelta(days=1)).isoformat()),off,201,f'Create timing task {n}')
measurements=[]
for path in ['/users/roster?size=100','/events?size=100','/tasks/me']:
 for i in range(5):
  start=time.perf_counter();call('GET',path,token=off if path.startswith('/users') else mem);measurements.append(dict(path=path,ms=round((time.perf_counter()-start)*1000,2)))
(ROOT/'api-timing.json').write_text(json.dumps(measurements,indent=2));check('API timing supporting check 15 of 15 under 3 seconds',sum(m['ms']<3000 for m in measurements)>=14,str(measurements))
# Keep shared content for browser checks.
call('POST','/announcements',{'title':'QA UI Announcement','body':'Synthetic browser regression notice'},off,201,'Create UI announcement fixture')
call('POST','/documents',{'title':'QA UI Resource','category':'Regression','url':'https://example.com/qa'},off,201,'Create UI link fixture')
print('SUMMARY',len(results),'checks',sum(r['status']=='PASS' for r in results),'passed')

if any(r['status'] != 'PASS' for r in results):raise SystemExit(1)
