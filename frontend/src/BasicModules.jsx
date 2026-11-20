import {useEffect,useRef,useState} from 'react';
import {api} from './api';

const ROLES=['ADMIN','HR_ADMIN','MAIL_ADMIN','EMPLOYEE'];
const label=value=>value?.replaceAll('_',' ') || '—';
const definitions={
  employees:{title:'Employees',single:'employee',path:'/hr/employees',defaults:{employeeCode:'',firstName:'',lastName:'',email:'',phone:'',jobTitle:'',joiningDate:'',employmentType:'FULL_TIME',status:'ACTIVE',departmentId:''}},
  departments:{title:'Departments',single:'department',path:'/hr/departments',defaults:{name:'',description:'',active:true}},
  users:{title:'User accounts',single:'user',path:'/org/users',defaults:{name:'',email:'',password:'',role:'EMPLOYEE',active:true,employeeId:''}}
};

export function HrModule({user}) {
  const [tab,setTab]=useState('employees');
  if(user.role==='EMPLOYEE')return <EmployeeSelf/>;
  return <div className="page"><h1>HRMS</h1><p>Basic employee records and department management.</p><div className="tabs">
    <button className={'tab'+(tab==='employees'?' on':'')} onClick={()=>setTab('employees')}>Employees</button>
    <button className={'tab'+(tab==='departments'?' on':'')} onClick={()=>setTab('departments')}>Departments</button>
  </div><RecordManager key={tab} kind={tab} user={user}/></div>;
}
export function OrganizationModule({user}) {return <div className="page"><h1>Organization</h1><p>Manage accounts, employee links and module permissions.</p><div className="note">Admin: all modules · HR admin: employees/departments · Mail admin: local Mail Admin · Employee: own linked profile. No invitation email is sent.</div><RecordManager kind="users" user={user}/></div>;}
function EmployeeSelf() {
  const [profile,setProfile]=useState(null),[err,setErr]=useState('');
  useEffect(()=>{api('/hr/self').then(setProfile).catch(e=>setErr(e.message));},[]);
  return <div className="page"><h1>My employee profile</h1>{err?<p role="alert">{err}</p>:!profile?<p role="status">Loading profile…</p>:<div className="box" style={{padding:24}}><h2>{profile.firstName} {profile.lastName}</h2><p>{profile.employeeCode} · {profile.email}</p><p>{profile.jobTitle || 'Job title not set'}</p><p>Joining date: {profile.joiningDate} · {label(profile.employmentType)}</p><p>Status: {label(profile.status)}</p></div>}</div>;
}
function RecordManager({kind,user}) {
  const def=definitions[kind],[rows,setRows]=useState([]),[departments,setDepartments]=useState([]),[employees,setEmployees]=useState([]);
  const [loading,setLoading]=useState(true),[err,setErr]=useState(''),[modal,setModal]=useState(null),[search,setSearch]=useState('');
  const sequence=useRef(0);
  const load=async()=> {
    const id=++sequence.current;setLoading(true);setErr('');
    try {const [records,depts,staff]=await Promise.all([api(def.path),kind==='employees'?api('/hr/departments'):Promise.resolve([]),kind==='users'?api('/hr/employees'):Promise.resolve([])]);if(id===sequence.current){setRows(records);setDepartments(depts);setEmployees(staff);}}
    catch(e){if(id===sequence.current)setErr(e.message);}finally{if(id===sequence.current)setLoading(false);}
  };
  useEffect(()=>{load();return()=>{++sequence.current;};},[kind]);
  const display=rows.filter(r=>(kind==='employees'?`${r.employeeCode} ${r.firstName} ${r.lastName} ${r.email}`:kind==='users'?`${r.name} ${r.email} ${r.role}`:r.name).toLowerCase().includes(search.toLowerCase()));
  const saved=()=>{setModal(null);load();window.dispatchEvent(new Event('notifications-changed'));};
  return <>
    {err && <p className="err" role="alert">{err} <button className="btn" onClick={load}>Retry</button></p>}
    <div className="box"><div className="bh">{def.title}<button className="btn dark" disabled={loading||!!err} onClick={()=>setModal({type:'new'})}>+ New {def.single}</button></div>
      <div style={{padding:'0 24px 18px'}}><label htmlFor={'search-'+kind}>Search {def.title.toLowerCase()}</label><input id={'search-'+kind} value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search records"/></div>
      {loading?<p role="status" style={{padding:24}}>Loading…</p>:!display.length?<div className="empty"><h4>{search?'No matching records':'No '+def.title.toLowerCase()+' yet'}</h4><p>{search?'Try another search.':'Use New '+def.single+' to get started.'}</p></div>:<div className="tw"><table><thead><tr><th>{kind==='employees'?'EMPLOYEE':'NAME'}</th><th>{kind==='departments'?'DESCRIPTION':'EMAIL / DETAILS'}</th><th>STATUS / ROLE</th><th>ACTIONS</th></tr></thead><tbody>{display.map(r=><tr key={r.id}>
        <td>{kind==='employees'?`${r.firstName} ${r.lastName}`:r.name}{kind==='employees'&&<small>{r.employeeCode}</small>}</td>
        <td>{kind==='departments'?r.description||'—':r.email}{kind==='employees'&&<small>{departments.find(d=>d.id===r.departmentId)?.name||'No department'} · {r.jobTitle||'No job title'}</small>}</td>
        <td>{kind==='employees'?label(r.status):r.active?'ACTIVE':'INACTIVE'}{kind==='users'&&<small>{label(r.role)}</small>}</td>
        <td><div className="acts"><button className="btn" onClick={()=>setModal({type:'edit',record:r})}>Edit</button>{kind==='users'&&r.id!==user.id&&<button className="btn" onClick={()=>setModal({type:'reset',record:r})}>Reset password</button>}<button className="btn danger" disabled={kind==='users'&&r.id===user.id} onClick={()=>setModal({type:'delete',record:r})}>Delete</button></div></td>
      </tr>)}</tbody></table></div>}
    </div>
    {modal&&<RecordDialog kind={kind} modal={modal} departments={departments} employees={employees} user={user} close={()=>setModal(null)} saved={saved}/>}
  </>;
}
function RecordDialog({kind,modal,departments,employees,user,close,saved}) {
  const def=definitions[kind],record=modal.record,isNew=modal.type==='new',deleting=modal.type==='delete',reset=modal.type==='reset';
  const [form,setForm]=useState({...def.defaults,...record,password:'',confirm:'',departmentId:record?.departmentId??'',employeeId:record?.employeeId??''}),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const dialog=useRef(null),lock=useRef(false);
  const title=deleting?'Delete '+def.single:reset?'Reset user password':isNew?'New '+def.single:'Edit '+def.single;
  const confirm=kind==='employees'?record?.employeeCode:kind==='users'?record?.email:record?.name;
  useEffect(()=> {
    const previous=document.activeElement;dialog.current?.querySelector('input,select,button')?.focus();
    return()=>previous?.focus();
  },[]);
  const field=(key,name,options={})=><div key={key}><label htmlFor={'record-'+key}>{name}</label>{options.values?<select id={'record-'+key} value={form[key]} disabled={options.disabled} onChange={e=>setForm({...form,[key]:e.target.value})}>{options.values.map(v=><option key={String(v.value??v)} value={v.value??v}>{v.label??label(v)}</option>)}</select>:options.type==='checkbox'?<input id={'record-'+key} type="checkbox" style={{width:'auto'}} checked={!!form[key]} disabled={options.disabled} onChange={e=>setForm({...form,[key]:e.target.checked})}/>:<input id={'record-'+key} value={form[key]??''} type={options.type||'text'} required={options.required} disabled={options.disabled} maxLength={options.max||200} minLength={options.min} autoComplete={options.type==='password'?'new-password':undefined} onChange={e=>setForm({...form,[key]:e.target.value})}/>}</div>;
  const submit=async e=> {
    e.preventDefault();if(lock.current)return;lock.current=true;setBusy(true);setError('');
    try {
      if(deleting&&form.confirm.trim()!==confirm)throw new Error('Confirmation does not match.');
      let path=def.path+(isNew?'':'/'+record.id);if(reset)path+='/reset-password';
      await api(path,{method:deleting?'DELETE':isNew||reset?'POST':'PUT',body:deleting?undefined:form});saved();
    }catch(e){setError(e.message);}finally{lock.current=false;setBusy(false);}
  };
  const trap=e=> {
    if(e.key==='Escape'&&!busy){close();return;}
    if(e.key!=='Tab')return;
    const items=[...dialog.current.querySelectorAll('input,select,button')].filter(x=>!x.disabled),first=items[0],last=items.at(-1);
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
  };
  return <div className="ov" onKeyDown={trap} onClick={e=>{if(e.target===e.currentTarget&&!busy)close();}}><form className="md" ref={dialog} role="dialog" aria-modal="true" aria-labelledby="record-title" onSubmit={submit}><h3 id="record-title">{title}</h3><fieldset disabled={busy} style={{border:0,padding:0,margin:0,minWidth:0}}>
    {deleting?<><p>This permanently removes this local {def.single}. Linked records must be reassigned first. Type <b>{confirm}</b> to confirm.</p>{field('confirm','Confirmation',{required:true,max:254})}</>:reset?<>{field('password','New password',{type:'password',required:true,min:12,max:72})}<p>All sessions for this user will be revoked.</p></>:kind==='departments'?<>
      {field('name','Department name',{required:true,max:100})}{field('description','Description',{max:500})}{field('active','Active',{type:'checkbox'})}
    </>:kind==='employees'?<>
      {field('employeeCode','Employee code',{required:true,max:32})}{field('firstName','First name',{required:true,max:100})}{field('lastName','Last name',{max:100})}{field('email','Work email',{type:'email',required:true,max:254})}{field('phone','Phone',{max:32})}{field('jobTitle','Job title',{max:100})}{field('joiningDate','Joining date',{type:'date',required:true})}
      {field('departmentId','Department',{values:[{value:'',label:'No department'},...departments.filter(d=>d.active||d.id===record?.departmentId).map(d=>({value:d.id,label:d.name+(d.active?'':' (inactive)')}))]})}{field('employmentType','Employment type',{values:['FULL_TIME','PART_TIME','CONTRACT','INTERN']})}{field('status','Status',{values:['ACTIVE','INACTIVE']})}
    </>:<>
      {field('name','Name',{required:true})}{field('email','Login email',{type:'email',required:true,max:254,disabled:!isNew})}{isNew&&field('password','Initial password',{type:'password',required:true,min:12,max:72})}{field('role','Role',{values:ROLES,disabled:record?.id===user.id})}{field('active','Active',{type:'checkbox',disabled:record?.id===user.id})}
      {field('employeeId','Linked employee',{values:[{value:'',label:'No employee link'},...employees.map(e=>({value:e.id,label:e.employeeCode+' · '+e.firstName+' '+e.lastName}))]})}
      {isNew&&<p>Share the initial password securely. No invitation email is sent.</p>}
    </>}
    <p className="err" role="alert">{error}</p><div className="row"><button className="btn" type="button" onClick={close}>Cancel</button><button className={'btn dark'+(deleting?' danger':'')} type="submit" disabled={deleting&&form.confirm.trim()!==confirm}>{busy?'Saving…':deleting?'Delete':reset?'Reset password':'Save'}</button></div>
  </fieldset></form></div>;
}
