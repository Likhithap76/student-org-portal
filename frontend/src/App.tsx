import { FormEvent, useEffect, useState } from 'react';
import { api, Application, del, isOfficer, list, LoginResponse, Page, post, put, patch, RosterEntry, setToken, User } from './api';
type Tab = 'overview' | 'members' | 'events' | 'tasks' | 'content' | 'notifications' | 'reports' | 'ai';
type Event = {id:number;title:string;description?:string;location?:string;startsAt:string;endsAt:string;status:string;goingCount:number;myResponse?:string};
type Task = {id:number;title:string;status:string;dueDate:string;assigneeId:number;committeeId:number;overdue?:boolean};
type Committee = {id:number;name:string;kind:string;memberCount:number};
type Announcement = {id:number;title:string;body:string;publishedAt:string};
type DocumentLink = {id:number;title:string;category:string;url:string};
type Notice = {id:number;message:string;read:boolean;createdAt:string};
type Summary = {activeMembers:number;pendingApplications:number;upcomingEvents:number;openTasks:number;overdueTasks:number};

function errorMessage(e:unknown){return e instanceof Error ? e.message : 'An unexpected error occurred.';}
function formatDate(value?:string){return value?new Date(value).toLocaleString(): '—';}
function Card({title,children,subtitle}:{title:string;children:React.ReactNode;subtitle?:string}){
 return <section className="card"><div className="card-heading"><h2>{title}</h2>{subtitle&&<span className="muted">{subtitle}</span>}</div>{children}</section>;
}
function Alert({message,success=false}:{message:string;success?:boolean}){return <div role="status" className={`alert ${success?'success':''}`}>{message}</div>;}
function Button({children,busy=false,...props}:React.ButtonHTMLAttributes<HTMLButtonElement>&{busy?:boolean}){
 return <button {...props} disabled={busy||props.disabled}>{busy?'Working…':children}</button>;
}
function Auth({onLogin}:{onLogin:(value:LoginResponse)=>void}){
 const [register,setRegister]=useState(false);
 const [email,setEmail]=useState('');const [password,setPassword]=useState('');const [name,setName]=useState('');
 const [busy,setBusy]=useState(false);const [message,setMessage]=useState('');const [error,setError]=useState('');
 async function submit(e:FormEvent){
  e.preventDefault();setBusy(true);setError('');setMessage('');
  try{
   if(register){
    if(password.length<8||password.length>72||!/[a-zA-Z]/.test(password)||!/[0-9]/.test(password))throw new Error('Password must be 8–72 characters with a letter and number.');
    await post<User>('/auth/register',{email,displayName:name,password});
    setRegister(false);setMessage('Registration submitted. Your membership application is pending officer approval. You can now log in.');setPassword('');
   }else{
    const result=await post<LoginResponse>('/auth/login',{email,password});onLogin(result);
   }
  }catch(e){setError(errorMessage(e));}finally{setBusy(false);}
 }
 return <main className="auth-shell"><div className="auth-hero"><div className="logo">SO</div><h1>Student Organization Portal</h1><p>One place for membership, events, committees, updates and meeting notes.</p><div className="hero-pills"><span>Membership</span><span>Events</span><span>Teamwork</span></div></div><Card title={register?'Create your account':'Welcome back'} subtitle={register?'An officer will review your application':'Sign in to your organization workspace'}>
 <form onSubmit={submit} className="stack">{register&&<label>Full name<input required maxLength={100} value={name} onChange={e=>setName(e.target.value)} autoComplete="name"/></label>}
 <label>Email<input required type="email" value={email} onChange={e=>setEmail(e.target.value)} autoComplete="email"/></label>
 <label>Password<input required type="password" value={password} onChange={e=>setPassword(e.target.value)} autoComplete={register?'new-password':'current-password'}/></label>
 {error&&<Alert message={error}/ >}{message&&<Alert message={message} success/>}
 <Button busy={busy} type="submit" className="primary full">{register?'Register':'Log in'}</Button>
 <button className="link-btn" type="button" onClick={()=>{setRegister(!register);setError('');setMessage('');}}>{register?'Already registered? Log in':'New here? Register for membership'}</button></form>
 </Card></main>;
}

function Overview({user}:{user:User}){
 const [summary,setSummary]=useState<Summary|null>(null);const [error,setError]=useState('');
 useEffect(()=>{if(isOfficer(user)){api<Summary>('/reports/summary').then(setSummary).catch(e=>setError(errorMessage(e)));}},[user]);
 return <><div className="welcome"><div><span className="eyebrow">YOUR WORKSPACE</span><h1>Hello, {user.displayName.split(' ')[0]} 👋</h1><p>Manage your organization's activities in one place.</p></div><span className="status-pill">{user.role} · {user.status}</span></div>
 {user.status!=='ACTIVE'&&<Alert message="Your account is awaiting approval. An officer must approve your membership before you can access member features."/>}
 {summary&&<div className="stat-grid">{([['Active members',summary.activeMembers],['Pending applications',summary.pendingApplications],['Upcoming events',summary.upcomingEvents],['Open tasks',summary.openTasks],['Overdue tasks',summary.overdueTasks]] as const).map(([key,value])=><div className="stat" key={key}><span>{key}</span><strong>{value}</strong></div>)}</div>}
 {error&&<Alert message={error}/>}
 <Card title="Start here" subtitle="First integrated sprint workflow"><ol className="steps"><li>Register a member account.</li><li>Log in as an officer and review the pending application.</li><li>Approve it, then check that the member appears in the roster.</li><li>Refresh the page to verify persistence in PostgreSQL.</li></ol></Card></>;
}

function Members({user}:{user:User}){
 const officer=isOfficer(user);const [queue,setQueue]=useState<Application[]>([]);const [roster,setRoster]=useState<RosterEntry[]>([]);
 const [mine,setMine]=useState<Application|null>(null);const [error,setError]=useState('');const [message,setMessage]=useState('');const [busy,setBusy]=useState<number|null>(null);const [refreshing,setRefreshing]=useState(false);
 async function load(){setRefreshing(true);setError('');try{
  if(officer)setQueue(list(await api<Page<Application>>('/memberships?decision=PENDING&size=100')));
  if(officer)setRoster(list(await api<Page<RosterEntry>>('/users/roster?size=100')));
  else setMine(await api<Application>('/memberships/me'));
 }catch(e){setError(errorMessage(e));}finally{setRefreshing(false);}}
 useEffect(()=>{void load();},[user.id,user.status,user.role]);
 async function decide(id:number,action:'approve'|'reject'){
  setBusy(id);setMessage('');setError('');try{await post<void>(`/memberships/${id}/${action}`);setMessage(`Application ${action==='approve'?'approved':'rejected'} successfully.`);await load();}
  catch(e){setError(errorMessage(e));}finally{setBusy(null);}
 }
 async function reapply(){setError('');setMessage('');try{await post<void>('/memberships/reapply');setMessage('Your application was resubmitted.');await load();}catch(e){setError(errorMessage(e));}}
 return <><div className="page-header"><div><h1>Membership</h1><p>Applications and the active organization roster.</p></div><Button onClick={load} busy={refreshing}>Refresh</Button></div>
 {error&&<Alert message={error}/ >}{message&&<Alert message={message} success/>}
 {user.status==='PENDING'&&<Card title="Your application"><p>Your account is pending approval.</p>{mine&&<p><b>Status:</b> {mine.decision} · Submitted {formatDate(mine.submittedAt)}</p>}{mine?.decision==='REJECTED'&&<Button onClick={reapply}>Reapply</Button>}</Card>}
 {officer&&<Card title="Pending applications" subtitle={`${queue.length} awaiting review`}><div className="table-wrap"><table><thead><tr><th>Applicant</th><th>Email</th><th>Submitted</th><th>Decision</th></tr></thead><tbody>{queue.map(a=><tr key={a.id}><td>{a.applicantName}</td><td>{a.applicantEmail}</td><td>{formatDate(a.submittedAt)}</td><td className="actions"><Button className="small primary" busy={busy===a.id} onClick={()=>decide(a.id,'approve')}>Approve</Button><Button className="small subtle" busy={busy===a.id} onClick={()=>decide(a.id,'reject')}>Reject</Button></td></tr>)}{!queue.length&&<tr><td colSpan={4} className="empty">No pending applications.</td></tr>}</tbody></table></div></Card>}
 {officer&&<Card title="Active member roster" subtitle={`${roster.length} shown`}><div className="table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th></tr></thead><tbody>{roster.map(member=><tr key={member.id}><td>{member.displayName}</td><td>{member.email||'—'}</td><td>{member.role||'MEMBER'}</td></tr>)}{!roster.length&&<tr><td colSpan={3} className="empty">No roster entries returned.</td></tr>}</tbody></table></div></Card>}</>;
}

function Events({user}:{user:User}){
 const [items,setItems]=useState<Event[]>([]);const [error,setError]=useState('');const [message,setMessage]=useState('');
 const [title,setTitle]=useState('');const [location,setLocation]=useState('');const [start,setStart]=useState('');const [end,setEnd]=useState('');
 async function load(){try{setItems(list(await api<Page<Event>>('/events?size=100')));}catch(e){setError(errorMessage(e));}}
 useEffect(()=>{void load();},[]);
 async function submit(e:FormEvent){e.preventDefault();setError('');try{await post<Event>('/events',{title,location,description:'',startsAt:new Date(start).toISOString(),endsAt:new Date(end).toISOString()});setMessage('Event created.');setTitle('');await load();}catch(e){setError(errorMessage(e));}}
 async function rsvp(eventId:number,response:string){setError('');try{await put(`/events/${eventId}/rsvp`,{response});setMessage('RSVP saved.');await load();}catch(e){setError(errorMessage(e));}}
 return <><div className="page-header"><div><h1>Events</h1><p>Browse upcoming activities and update your RSVP.</p></div></div>{error&&<Alert message={error}/>}{message&&<Alert message={message} success/>}
 {isOfficer(user)&&<Card title="Schedule event"><form onSubmit={submit} className="form-grid"><label>Title<input required value={title} onChange={e=>setTitle(e.target.value)}/></label><label>Location<input value={location} onChange={e=>setLocation(e.target.value)}/></label><label>Starts<input type="datetime-local" required value={start} onChange={e=>setStart(e.target.value)}/></label><label>Ends<input type="datetime-local" required value={end} onChange={e=>setEnd(e.target.value)}/></label><Button className="primary" type="submit">Create event</Button></form></Card>}
 <div className="tile-grid">{items.map(item=><article className="card event-card" key={item.id}><span className="eyebrow">{item.status}</span><h2>{item.title}</h2><p>{formatDate(item.startsAt)}</p><p>{item.location||'Location pending'}</p><p>{item.goingCount||0} attending</p><div className="actions"><select aria-label={`RSVP for ${item.title}`} value={item.myResponse||''} onChange={e=>rsvp(item.id,e.target.value)}><option value="" disabled>Choose RSVP</option><option value="GOING">Going</option><option value="NOT_GOING">Not going</option></select></div>{isOfficer(user)&&<AttendancePanel eventId={item.id} startsAt={item.startsAt}/>}</article>)}{!items.length&&<Card title="No events yet"><p>Events published by officers will appear here.</p></Card>}</div></>;
}


type AttendanceStatus = 'PRESENT' | 'ABSENT';

type AttendanceEntry = {
  userId: number;
  displayName: string;
  status: AttendanceStatus;
  recordedAt: string;
};

type AttendanceResponse = {
  eventId: number;
  entries: AttendanceEntry[];
  presentCount: number;
  goingCount: number;
  rate: number | null;
};

function AttendancePanel({eventId,startsAt}:{eventId:number;startsAt:string}){
 const [open,setOpen]=useState(false);
 const [roster,setRoster]=useState<RosterEntry[]>([]);
 const [records,setRecords]=useState<AttendanceEntry[]>([]);
 const [memberId,setMemberId]=useState('');
 const [status,setStatus]=useState<AttendanceStatus>('PRESENT');
 const [loading,setLoading]=useState(false);
 const [saving,setSaving]=useState(false);
 const [error,setError]=useState('');
 const [message,setMessage]=useState('');
 const eventStarted=Date.parse(startsAt)<=Date.now();

 async function refresh(){
  const [members,attendance]=await Promise.all([
   api<Page<RosterEntry>>('/users/roster?size=100'),
   api<AttendanceResponse>(`/events/${eventId}/attendance`)
  ]);
  setRoster(list(members));
  setRecords(attendance.entries||[]);
 }

 async function toggle(){
  if(open){setOpen(false);return;}
  setOpen(true);
  setLoading(true);
  setError('');
  try{await refresh();}
  catch(e){setError(errorMessage(e));}
  finally{setLoading(false);}
 }

 async function save(e:FormEvent){
  e.preventDefault();
  if(!memberId||!eventStarted)return;
  setSaving(true);
  setError('');
  setMessage('');
  try{
   await put<AttendanceResponse>(
    `/events/${eventId}/attendance`,
    [{userId:Number(memberId),status}]
   );
   await refresh();
   setMessage('Attendance saved successfully.');
  }catch(e){setError(errorMessage(e));}
  finally{setSaving(false);}
 }

 return <div className="stack">
  <Button type="button" onClick={()=>void toggle()}>
   {open?'Hide attendance':'Manage attendance'}
  </Button>
  {open&&<>
   <h3>Event attendance</h3>
   {error&&<Alert message={error}/>}
   {message&&<Alert message={message} success/>}
   {loading?<p>Loading attendance...</p>:<>
    {!eventStarted&&<p className="muted">Attendance can only be marked after the event starts.</p>}
    <form className="stack" onSubmit={save}>
     <label>Member
      <select required value={memberId} onChange={e=>{
       const id=e.target.value;
       setMemberId(id);
       setStatus(records.find(r=>r.userId===Number(id))?.status||'PRESENT');
      }}>
       <option value="">Select member</option>
       {roster.map(m=><option key={m.id} value={m.id}>{m.displayName}</option>)}
      </select>
     </label>
     <label>Attendance status
      <select value={status} onChange={e=>setStatus(e.target.value as AttendanceStatus)}>
       <option value="PRESENT">Present</option>
       <option value="ABSENT">Absent</option>
      </select>
     </label>
     <Button className="primary" type="submit" busy={saving}
      disabled={!eventStarted||!memberId}>
      Save attendance
     </Button>
    </form>
    <h3>Saved attendance records</h3>
    <div className="table-wrap">
     <table>
      <thead><tr><th>Member</th><th>Status</th></tr></thead>
      <tbody>
       {records.map(r=><tr key={r.userId}>
        <td>{r.displayName||`User ${r.userId}`}</td>
        <td>{r.status==='PRESENT'?'Present':'Absent'}</td>
       </tr>)}
       {!records.length&&<tr><td colSpan={2}>No attendance recorded yet.</td></tr>}
      </tbody>
     </table>
    </div>
   </>}
  </>}
 </div>;
}


type CommitteeDetail = Committee & {
  members: { userId: number; displayName: string; addedAt: string }[];
};

function Tasks({user}:{user:User}){
 const [tasks,setTasks]=useState<Task[]>([]);
 const [committees,setCommittees]=useState<Committee[]>([]);
 const [error,setError]=useState('');
 const [message,setMessage]=useState('');

 const [committee,setCommittee]=useState('');
 const [title,setTitle]=useState('');
 const [assignee,setAssignee]=useState('');
 const [due,setDue]=useState('');
 const [newCommittee,setNewCommittee]=useState('');

 // Committee membership management
 const [selectedCommittee,setSelectedCommittee]=useState('');
 const [memberUserId,setMemberUserId]=useState('');
 const [committeeDetail,setCommitteeDetail]=useState<CommitteeDetail|null>(null);
 const [myCommittees, setMyCommittees] = useState<Committee[]>([]);
 const [myCommitteesLoading, setMyCommitteesLoading] = useState(false);
 const [myCommitteesError, setMyCommitteesError] = useState('');
 const [busy,setBusy]=useState(false);

 async function load(){
  try{
   const [t,c]=await Promise.all([
    api<Page<Task>>('/tasks/me?size=100'),
    api<Page<Committee>>('/committees?size=100')
   ]);
   setTasks(list(t));
   setCommittees(list(c));
  }catch(e){setError(errorMessage(e));}
 }

 useEffect(()=>{void load();},[]);
 useEffect(() => {
  if (isOfficer(user) || committees.length === 0) return;

  let cancelled = false;

  async function loadMyCommittees() {
    setMyCommitteesLoading(true);
    setMyCommitteesError('');

    const results = await Promise.allSettled(
      committees.map(c =>
        api<CommitteeDetail>(`/committees/${c.id}`)
      )
    );

    if (cancelled) return;

    const mine = committees.filter((_, index) => {
      const result = results[index];

      return result.status === 'fulfilled' &&
        result.value.members.some(m => m.userId === user.id);
    });

    setMyCommittees(mine);

    if (results.some(r => r.status === 'rejected')) {
      setMyCommitteesError(
        'Some committee details could not be loaded.'
      );
    }

    setMyCommitteesLoading(false);
  }

  void loadMyCommittees();

  return () => {
    cancelled = true;
  };
}, [committees, user.id, user.role]);

 async function loadCommittee(id:string){
  setSelectedCommittee(id);
  setCommitteeDetail(null);
  setError('');
  if(!id)return;
  try{
   const detail=await api<CommitteeDetail>(`/committees/${id}`);
   setCommitteeDetail(detail);
  }catch(e){setError(errorMessage(e));}
 }

 async function createCommittee(e:FormEvent){
  e.preventDefault();setError('');setMessage('');
  try{
   await post('/committees',{
    name:newCommittee,description:'',kind:'COMMITTEE'
   });
   setMessage('Committee created.');
   setNewCommittee('');
   await load();
  }catch(e){setError(errorMessage(e));}
 }

 async function addMember(e:FormEvent){
  e.preventDefault();
  if(!selectedCommittee||!memberUserId)return;
  setBusy(true);setError('');setMessage('');
  try{
   await post(`/committees/${selectedCommittee}/members`,{
    userIds:[Number(memberUserId)]
   });
   setMessage('Member added to committee.');
   setMemberUserId('');
   await load();
   await loadCommittee(selectedCommittee);
  }catch(e){setError(errorMessage(e));}
  finally{setBusy(false);}
 }

 async function removeMember(userId:number){
  if(!selectedCommittee)return;
  setBusy(true);setError('');setMessage('');
  try{
   await del(`/committees/${selectedCommittee}/members/${userId}`);
   setMessage('Member removed from committee.');
   await load();
   await loadCommittee(selectedCommittee);
  }catch(e){setError(errorMessage(e));}
  finally{setBusy(false);}
 }

 async function createTask(e:FormEvent){
  e.preventDefault();setError('');setMessage('');
  try{
   await post('/tasks',{
    title,
    committeeId:Number(committee),
    assigneeId:Number(assignee),
    dueDate:due
   });
   setMessage('Task created.');
   setTitle('');
   await load();
  }catch(e){setError(errorMessage(e));}
 }

 async function update(id:number,status:string){
  setError('');
  try{
   await patch(`/tasks/${id}/status`,{status});
   setMessage('Task status updated.');
   await load();
  }catch(e){setError(errorMessage(e));}
 }

 return <>
  <div className="page-header">
   <div>
    <h1>Committees & tasks</h1>
    <p>Coordinate responsibilities and track progress.</p>
   </div>
  </div>

  {error&&<Alert message={error}/>}
  {message&&<Alert message={message} success/>}

  {isOfficer(user)&&<>
   <div className="tile-grid">
    <Card title="Create committee">
     <form className="stack" onSubmit={createCommittee}>
      <label>Committee name
       <input required value={newCommittee}
        onChange={e=>setNewCommittee(e.target.value)}/>
      </label>
      <Button className="primary" type="submit">Create</Button>
     </form>
    </Card>

    <Card title="Assign task">
     <form className="stack" onSubmit={createTask}>
      <label>Task title
       <input required value={title}
        onChange={e=>setTitle(e.target.value)}/>
      </label>
      <label>Committee
       <select required value={committee}
        onChange={e=>setCommittee(e.target.value)}>
        <option value="">Select</option>
        {committees.map(c=>
         <option value={c.id} key={c.id}>{c.name}</option>
        )}
       </select>
      </label>
      <label>Assignee user ID
       <input type="number" min="1" required value={assignee}
        onChange={e=>setAssignee(e.target.value)}/>
      </label>
      <label>Due date
       <input type="date" required value={due}
        onChange={e=>setDue(e.target.value)}/>
      </label>
      <p className="muted">
       The assignee must already belong to the committee.
      </p>
      <Button className="primary" type="submit">Assign</Button>
     </form>
    </Card>
   </div>

   <Card title="Manage committee members">
    <div className="stack">
     <label>Select committee
      <select value={selectedCommittee}
       onChange={e=>void loadCommittee(e.target.value)}>
       <option value="">Select a committee</option>
       {committees.map(c=>
        <option key={c.id} value={c.id}>
         {c.name} ({c.memberCount} members)
        </option>
       )}
      </select>
     </label>

     {selectedCommittee&&<>
      <form className="stack" onSubmit={addMember}>
       <label>Member user ID
        <input type="number" min="1" required
         value={memberUserId}
         onChange={e=>setMemberUserId(e.target.value)}
         placeholder="Enter an approved member's user ID"/>
       </label>
       <Button className="primary" type="submit" busy={busy}>
        Add member
       </Button>
      </form>

      <h3>Current committee members</h3>
      {committeeDetail?(
       committeeDetail.members.length?(
        <div className="table-wrap">
         <table>
          <thead><tr>
           <th>Member</th><th>User ID</th><th>Action</th>
          </tr></thead>
          <tbody>
           {committeeDetail.members.map(m=>
            <tr key={m.userId}>
             <td>{m.displayName||'Member'}</td>
             <td>{m.userId}</td>
             <td>
              <Button className="small" type="button"
               disabled={busy}
               onClick={()=>void removeMember(m.userId)}>
               Remove
              </Button>
             </td>
            </tr>
           )}
          </tbody>
         </table>
        </div>
       ):<p className="empty">No members assigned yet.</p>
      ):<p className="muted">Loading committee members…</p>}
     </>}
    </div>
   </Card>
  </>}
  {!isOfficer(user) && (
  <Card title="My committees">
    {myCommitteesLoading ? (
      <p className="muted">Loading your committees…</p>
    ) : myCommitteesError ? (
      <Alert message={myCommitteesError} />
    ) : myCommittees.length === 0 ? (
      <p className="empty">
        You are not assigned to any committees yet.
      </p>
    ) : (
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Committee</th>
              <th>Membership</th>
            </tr>
          </thead>
          <tbody>
            {myCommittees.map(c => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>Member</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )}
  </Card>
)}
  <Card title="My tasks">
   <div className="table-wrap">
    <table>
     <thead><tr>
      <th>Task</th><th>Due</th><th>Status</th>
      <th>Change status</th>
     </tr></thead>
     <tbody>
      {tasks.map(t=>
       <tr key={t.id}>
        <td>{t.title}</td>
        <td>{t.dueDate||'—'} {t.overdue&&'⚠️'}</td>
        <td>{t.status}</td>
        <td>
         {(t.status==='TODO'||t.status==='IN_PROGRESS'||t.status==='DONE')&&
          <Button className="small"
           onClick={()=>update(
            t.id,
            t.status==='TODO'?'IN_PROGRESS':
            t.status==='IN_PROGRESS'?'DONE':'IN_PROGRESS'
           )}>
           {t.status==='TODO'?'Start':
            t.status==='IN_PROGRESS'?'Complete':'Reopen'}
          </Button>}
        </td>
       </tr>
      )}
      {!tasks.length&&<tr>
       <td colSpan={4} className="empty">No tasks assigned.</td>
      </tr>}
     </tbody>
    </table>
   </div>
  </Card>
 </>;
}


function Content({user}:{user:User}){
 const [announcements,setAnnouncements]=useState<Announcement[]>([]);const [links,setLinks]=useState<DocumentLink[]>([]);const [error,setError]=useState('');const [message,setMessage]=useState('');
 const [title,setTitle]=useState('');const [body,setBody]=useState('');const [linkTitle,setLinkTitle]=useState('');const [category,setCategory]=useState('General');const [url,setUrl]=useState('');
 async function load(){try{const [a,d]=await Promise.all([api<Page<Announcement>>('/announcements?size=100'),api<Page<DocumentLink>>('/documents?size=100')]);setAnnouncements(list(a));setLinks(list(d));}catch(e){setError(errorMessage(e));}}
 useEffect(()=>{void load();},[]);
 async function publish(e:FormEvent){e.preventDefault();setError('');try{await post('/announcements',{title,body});setMessage('Announcement published.');setTitle('');setBody('');await load();}catch(e){setError(errorMessage(e));}}
 async function saveLink(e:FormEvent){e.preventDefault();setError('');try{await post('/documents',{title:linkTitle,category,url});setMessage('Document link saved.');setLinkTitle('');setUrl('');await load();}catch(e){setError(errorMessage(e));}}
 return <><div className="page-header"><div><h1>Announcements & resources</h1><p>Keep everyone informed and share links.</p></div></div>{error&&<Alert message={error}/>}{message&&<Alert message={message} success/>}
 {isOfficer(user)&&<div className="tile-grid"><Card title="Publish announcement"><form className="stack" onSubmit={publish}><label>Title<input required value={title} onChange={e=>setTitle(e.target.value)}/></label><label>Message<textarea required rows={3} value={body} onChange={e=>setBody(e.target.value)}/></label><Button type="submit" className="primary">Publish</Button></form></Card><Card title="Add resource link"><form className="stack" onSubmit={saveLink}><label>Title<input required value={linkTitle} onChange={e=>setLinkTitle(e.target.value)}/></label><label>Category<input required value={category} onChange={e=>setCategory(e.target.value)}/></label><label>HTTPS URL<input required type="url" pattern="https://.*" value={url} onChange={e=>setUrl(e.target.value)}/></label><Button type="submit" className="primary">Save link</Button></form></Card></div>}
 <Card title="Latest announcements">{announcements.length?announcements.map(a=><div className="item" key={a.id}><strong>{a.title}</strong><span className="muted">{formatDate(a.publishedAt)}</span><p>{a.body}</p></div>):<p className="empty">Nothing published yet.</p>}</Card><Card title="Document library">{links.length?links.map(d=><div className="item" key={d.id}><strong>{d.title}</strong><span className="muted">{d.category}</span><a target="_blank" rel="noopener noreferrer" href={d.url}>{d.url}</a></div>):<p className="empty">No links yet.</p>}</Card></>;
}

function Notifications(){
 const [items,setItems]=useState<Notice[]>([]);const [error,setError]=useState('');const [message,setMessage]=useState('');
 async function load(){try{setItems(list(await api<Page<Notice>>('/notifications/me?size=100')));}catch(e){setError(errorMessage(e));}}
 useEffect(()=>{void load();},[]);
 async function read(id:number){try{await patch(`/notifications/${id}/read`);setMessage('Marked as read.');await load();}catch(e){setError(errorMessage(e));}}
 return <><div className="page-header"><div><h1>Notifications</h1><p>Updates about your organization.</p></div><Button onClick={async()=>{try{await post('/notifications/me/read-all');await load();}catch(e){setError(errorMessage(e));}}}>Mark all read</Button></div>{error&&<Alert message={error}/>}{message&&<Alert message={message} success/>}<Card title="Inbox">{items.length?items.map(n=><div className={`item ${!n.read?'unread':''}`} key={n.id}><p>{n.message}</p><span className="muted">{formatDate(n.createdAt)}</span>{!n.read&&<Button className="small" onClick={()=>read(n.id)}>Mark read</Button>}</div>):<p className="empty">No notifications.</p>}</Card></>;
}

function Reports(){
 const [summary,setSummary]=useState<Summary|null>(null);const [error,setError]=useState('');
 useEffect(()=>{api<Summary>('/reports/summary').then(setSummary).catch(e=>setError(errorMessage(e)));},[]);
 return <><div className="page-header"><div><h1>Participation reports</h1><p>Officer-only organization statistics.</p></div></div>{error&&<Alert message={error}/>}{summary&&<div className="stat-grid">{Object.entries(summary).filter(([,v])=>typeof v==='number').map(([k,v])=><div className="stat" key={k}><span>{k.replace(/([A-Z])/g,' $1')}</span><strong>{v}</strong></div>)}</div>}<Card title="Engagement report"><p className="muted">The engagement API and CSV export are marked in progress by Likhitha. Use them after backend tests confirm they work.</p></Card></>;
}

function AI(){
  type ActionItem = {
    description: string;
    assigneeName: string | null;
    dueDate: string | null;
    suggestedUserId?: number | null;
  };

  type AiResult = {
    summary: string;
    decisions: string[];
    actionItems: ActionItem[];
  };

  const [notes,setNotes] = useState('');
  const [summary,setSummary] = useState('');
  const [decisions,setDecisions] = useState<string[]>([]);
  const [actionItems,setActionItems] = useState<ActionItem[]>([]);
  const [error,setError] = useState('');
  const [busy,setBusy] = useState(false);
  const [copied,setCopied] = useState(false);

  async function generate(){
    if(!notes.trim() || busy) return;

    setBusy(true);
    setError('');
    setCopied(false);

    try {
      const result = await post<AiResult>('/ai/summaries', {
        text: notes
      });

      setSummary(result.summary || '');
      setDecisions(result.decisions || []);
      setActionItems((result.actionItems || []).map(item => {
        const name = item.assigneeName?.trim();
        return {
          ...item,
          assigneeName: !name || ['null', 'none', 'unassigned'].includes(name.toLowerCase())
            ? null : name
        };
      }));
    } catch(e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  function updateItem(index:number, changes:Partial<ActionItem>){
    setActionItems(previous =>
      previous.map((item,i) =>
        i === index ? {...item,...changes} : item
      )
    );
    setCopied(false);
  }

  async function copyAll(){
    const text = [
      'Meeting Summary',
      summary,
      '',
      'Decisions',
      ...decisions.map((d,i) => `${i+1}. ${d}`),
      '',
      'Action Items',
      ...actionItems.map((item,i) =>
        `${i+1}. ${item.description} | Owner: ${item.assigneeName || 'Unassigned'} | Due: ${item.dueDate || 'Unspecified'}`
      )
    ].join('\n');

    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setError('');
    } catch {
      setError('Clipboard access denied. Please copy the text manually.');
    }
  }

  function discard(){
    setSummary('');
    setDecisions([]);
    setActionItems([]);
    setCopied(false);
    setError('');
  }

  return <>
    <div className="page-header">
      <div>
        <h1>Meeting notes AI</h1>
        <p>Generate and review meeting summaries and tasks.</p>
      </div>
      <span className="status-pill">Live AI integration</span>
    </div>

    <Card title="Meeting notes" subtitle="Enter meeting notes to summarize using Ollama">
      <label>
        Paste notes
        <textarea
          rows={9}
          maxLength={3000}
          value={notes}
          onChange={e=>setNotes(e.target.value)}
          placeholder="Meeting date, decisions, action items, owners and deadlines..."
        />
      </label>

      <div className="toolbar">
        <span className="muted">{notes.length} / 3000 characters</span>
        <Button
          className="primary"
          disabled={!notes.trim() || busy}
          busy={busy}
          onClick={generate}
        >
          Generate summary
        </Button>
      </div>

      {error && <Alert message={error}/>}
    </Card>

    <Card title="Editable summary">
      <label>
        Review and edit
        <textarea
          rows={7}
          value={summary}
          onChange={e=>{setSummary(e.target.value);setCopied(false);}}
          placeholder="Your generated summary will appear here."
        />
      </label>
    </Card>

    <Card title="Decisions">
      {decisions.length === 0 && <p className="muted">No decisions generated yet.</p>}

      {decisions.map((decision,index) =>
        <label key={index}>
          Decision {index+1}
          <textarea
            rows={2}
            value={decision}
            onChange={e=>{
              setDecisions(previous =>
                previous.map((d,i) => i===index ? e.target.value : d)
              );
              setCopied(false);
            }}
          />
        </label>
      )}
    </Card>

    <Card title="Suggested action items" subtitle="Review all assigned tasks and deadlines before using them">
      {actionItems.length === 0 && <p className="muted">No action items generated yet.</p>}

      {actionItems.length > 0 &&
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Task</th>
                <th>Assigned to</th>
                <th>Due date</th><th>Review</th>
              </tr>
            </thead>
            <tbody>
              {actionItems.map((item,index) =>
                <tr key={index}>
                  <td>
                    <input
                      aria-label={`Task ${index+1}`}
                      value={item.description}
                      onChange={e=>updateItem(index,{description:e.target.value})}
                    />
                  </td>
                  <td>
                    <input
                      aria-label={`Assignee ${index+1}`}
                      placeholder="Unassigned"
                      value={item.assigneeName || ''}
                      onChange={e=>updateItem(index,{assigneeName:e.target.value})}
                    />
                  </td>
                  <td>
                    <input
                      aria-label={`Due date ${index+1}`}
                      type="date"
                      value={item.dueDate || ''}
                      onChange={e=>updateItem(index,{dueDate:e.target.value || null})}
                    />
                  </td>
                  <td>
                    <Button className="subtle" onClick={()=>{
                      setActionItems(items=>items.filter((_,i)=>i!==index));
                      setCopied(false);
                    }}>
                      Remove
                    </Button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      }
    </Card>

    <Card title="Review and copy">
      <p>Check the AI-generated details before sharing them.</p>
      <div className="toolbar">
        <Button
          disabled={!summary.trim()}
          onClick={copyAll}
        >
          Copy reviewed results
        </Button>
        <Button className="subtle" onClick={discard}>
          Discard
        </Button>
        {copied && <span className="muted">Copied successfully</span>}
      </div>
    </Card>

    <Card title="Evaluation evidence">
      <p>
        Test the five synthetic meeting-note cases.
        Record missing action items, incorrect dates,
        unsupported claims and human corrections.
      </p>
    </Card>
  </>;
}

const tabs:{id:Tab;label:string;icon:string;officerOnly?:boolean}[]=[
 {id:'overview',label:'Overview',icon:'⌂'},{id:'members',label:'Membership',icon:'♙'},
 {id:'events',label:'Events & RSVP',icon:'◷'},{id:'tasks',label:'Committees & tasks',icon:'☷'},
 {id:'content',label:'Announcements & links',icon:'▤'},{id:'notifications',label:'Notifications',icon:'♧'},
 {id:'reports',label:'Reports',icon:'▥',officerOnly:true},{id:'ai',label:'AI meeting notes',icon:'✦',officerOnly:true},
];
export default function App(){
 const [user,setUser]=useState<User|null>(null);const [tab,setTab]=useState<Tab>('overview');
 // Keep tokens only in memory for this course demo. Refreshing requires another login.
 function login(result:LoginResponse){setToken(result.accessToken);setUser(result.user);setTab('overview');}
 function logout(){setToken(null);setUser(null);setTab('overview');}
 if(!user)return <Auth onLogin={login}/>;
 const available=tabs.filter(t=>!t.officerOnly||isOfficer(user));
 return <div className="app-shell"><aside className="sidebar"><div className="brand"><div className="logo">SO</div><div><strong>Student Org</strong><span>Operations Portal</span></div></div><nav aria-label="Main navigation">{available.map(t=><button key={t.id} className={`nav-item ${tab===t.id?'selected':''}`} onClick={()=>setTab(t.id)}><span className="nav-icon">{t.icon}</span>{t.label}</button>)}</nav><div className="profile"><div className="avatar">{user.displayName.slice(0,1).toUpperCase()}</div><div><strong>{user.displayName}</strong><span>{user.role.toLowerCase()}</span></div><button className="logout" onClick={logout} title="Log out">↪</button></div></aside>
 <div className="main-area"><header className="topbar"><span>Team 7 · Student Organization Portal</span><span className="muted">Connected API: {import.meta.env.VITE_API_BASE_URL||'/api'}</span></header><main className="page-content">{tab==='overview'?<Overview user={user}/>:tab==='members'?<Members user={user}/>:tab==='events'?<Events user={user}/>:tab==='tasks'?<Tasks user={user}/>:tab==='content'?<Content user={user}/>:tab==='notifications'?<Notifications/>:tab==='reports'?<Reports/>:<AI/>}</main></div></div>;
}
