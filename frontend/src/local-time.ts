/** Client-local input to absolute instants. Date-only upper bounds retain PostgreSQL microseconds. */
export function localInstant(value:string,edge:'from'|'until'='from'):string|null {
 if(!value)return null;
 const match=/^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?)?$/.exec(value);
 if(!match)throw new Error('INVALID_LOCAL_TIME');
 const [,y,m,d,h,min,sec,ms]=match,parts=[+y,+m-1,+d,+(h??0),+(min??0),+(sec??0),+(ms??'0').padEnd(3,'0')];
 const date=new Date(0);date.setFullYear(parts[0],parts[1],parts[2]);date.setHours(parts[3],parts[4],parts[5],parts[6]);
 if(date.getFullYear()!==parts[0]||date.getMonth()!==parts[1]||date.getDate()!==parts[2]||(h!==undefined&&(date.getHours()!==parts[3]||date.getMinutes()!==parts[4]||date.getSeconds()!==parts[5])))throw new Error('INVALID_LOCAL_TIME');
 if(h===undefined&&edge==='until'){const next=new Date(date);next.setDate(next.getDate()+1);next.setHours(0,0,0,0);return new Date(next.getTime()-1).toISOString().replace('.999Z','.999999Z');}
 return date.toISOString();
}
export function auditBounds(from:string,until:string){const lower=localInstant(from,'from'),upper=localInstant(until,'until');if(lower&&upper&&lower>upper)throw new Error('REVERSED_TIME');return{from:lower??undefined,until:upper??undefined};}
export function localTimestamp(value:string,language:string){const date=new Date(value);return Number.isNaN(date.getTime())?value:new Intl.DateTimeFormat(language,{year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',timeZoneName:'short'}).format(date);}

export function validLocalInput(value:string):boolean{try{localInstant(value);return true;}catch{return false;}}
