// Contracts based on the team's six Oct 23 OpenAPI exports.
export const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
export type Role = 'MEMBER' | 'OFFICER_ADMIN';
export type User = {id:number; displayName:string; email:string; role:Role; status:'PENDING'|'ACTIVE'|'INACTIVE';createdAt?:string};
export type Application = {id:number;userId:number;applicantName:string;applicantEmail:string;submittedAt:string;decision:'PENDING'|'APPROVED'|'REJECTED'};
export type RosterEntry = {id:number;displayName:string;email?:string;role?:Role};
export type Page<T> = {content:T[];totalElements:number;totalPages:number;page:number};
export type LoginResponse = {accessToken:string;tokenType:string;expiresIn:number;user:User};
export class ApiError extends Error {constructor(public status:number,message:string){super(message);}}
let activeToken:string|null = null;
export function setToken(token:string|null){activeToken=token;}
export async function api<T>(path:string, options:RequestInit = {}):Promise<T>{
 const headers=new Headers(options.headers);
 if(activeToken)headers.set('Authorization',`Bearer ${activeToken}`);
 if(options.body&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');
 let response:Response;
 try{response=await fetch(`${API_BASE}${path}`,{...options,headers});}
 catch{throw new ApiError(0,'Cannot reach the backend. Make sure ./run-all.sh is running.');}
 if(!response.ok){
  const data:Record<string,unknown>|null=await response.json().catch(()=>null);
  const message=typeof data?.detail==='string'?data.detail:typeof data?.message==='string'?data.message:`Request failed (${response.status}).`;
  throw new ApiError(response.status,message);
 }
 if(response.status===204||response.headers.get('content-length')==='0')return undefined as T;
 const raw=await response.text();return(raw?JSON.parse(raw):undefined)as T;
}
export const post=<T,>(path:string,data?:unknown)=>api<T>(path,{method:'POST',...(data===undefined?{}:{body:JSON.stringify(data)})});
export const put=<T,>(path:string,data?:unknown)=>api<T>(path,{method:'PUT',...(data===undefined?{}:{body:JSON.stringify(data)})});
export const patch=<T,>(path:string,data?:unknown)=>api<T>(path,{method:'PATCH',...(data===undefined?{}:{body:JSON.stringify(data)})});
export const del=<T,>(path:string)=>api<T>(path,{method:'DELETE'});
export const isOfficer=(user:User|null)=>Boolean(user&&user.role==='OFFICER_ADMIN'&&user.status==='ACTIVE');
export const list=<T,>(data:Page<T>|T[]):T[]=>Array.isArray(data)?data:data.content||[];
