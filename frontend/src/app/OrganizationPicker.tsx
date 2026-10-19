import {useEffect,useRef,useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useLocation} from 'react-router-dom';
import {Building2,ArrowRight,Search} from 'lucide-react';
import {errorMessage} from '../i18n';
import {authSession} from '../auth';
import type {RoutingOrganizations} from '../api-contracts';
import {Modal} from '../components/ui/dialog';
import {Button} from '../components/ui/button';
import {ClearableControl} from '../components/ui/clearable-control';
import {Skeleton} from '../components/ui/surfaces';
function routingContext(): string {
 const user=authSession.current();
 return JSON.stringify([authSession.contextVersion(),user?.id,user?.scope,user?.tenantId,user?.role,user?.status]);
}
export function OrganizationPicker({open,onOpenChange,busy,select,failure}:{failure?:string;open:boolean;onOpenChange:(open:boolean)=>void;busy:boolean;select:(id:string)=>void}){
 const {t}=useTranslation(),location=useLocation();const [q,setQ]=useState('');
 const [,sessionChanged]=useState(0);
 useEffect(()=>authSession.subscribe(()=>sessionChanged(v=>v+1)),[]);
 const sessionContext=routingContext(),context=JSON.stringify([sessionContext,location.pathname]),query=q.trim(),key=JSON.stringify([context,query]);
 const [cursor,setCursor]=useState({key:'',page:0,attempt:0}),[selected,setSelected]=useState<{context:string;id:string}|null>(null);
 const page=cursor.key===key?cursor.page:0,attempt=cursor.key===key?cursor.attempt:0;
 const moreRequest=useRef(''),list=useRef<HTMLDivElement>(null);
 const [result,setResult]=useState<{context:string;q:string;page:number;data:RoutingOrganizations|null;failed:boolean;pending:boolean}|null>(null);
 const sameContext=open&&result?.context===context;
 const currentRequest=sameContext&&result?.q===query&&result?.page===page;
 const data=sameContext?result!.data:null;
 const failed=!!currentRequest&&result!.failed;
 const pending=open&&(!currentRequest||result!.pending);
 const continuation=!!data&&result?.q===query&&page>data.page;
 const more=!!data&&result?.q===query&&(data.page+1)*data.size<data.totalElements;
 const optionsDisabled=busy||(pending&&!continuation);
 useEffect(()=>{setCursor({key:'',page:0,attempt:0});setSelected(null);moreRequest.current='';},[context]);
 useEffect(()=>{if(list.current)list.current.scrollTop=0;},[open,key]);
 useEffect(()=>{
  if(!open){setResult(null);setQ('');setCursor({key:'',page:0,attempt:0});setSelected(null);moreRequest.current='';return;}
  let alive=true;
  setResult(old=>({context,q:query,page,data:old?.context===context?old.data:null,failed:false,pending:true}));
  const timer=setTimeout(()=>void authSession.organizations(page,query).then(next=>{
   if(alive&&routingContext()===sessionContext){setResult(old=>{
    const rows=page>0&&old?.context===context&&old.q===query&&old.data?[...old.data.items,...next.items]:next.items;
    const seen=new Set<string>();const items=rows.filter(org=>!seen.has(org.routingId)&&!!seen.add(org.routingId));
    return{context,q:query,page,data:{...next,items},failed:false,pending:false};
   });moreRequest.current='';}
  }).catch(()=>{if(alive&&routingContext()===sessionContext){setResult(old=>({context,q:query,page,data:page>0&&old?.context===context&&old.q===query?old.data:null,failed:true,pending:false}));moreRequest.current='';}}),page===0?150:0);
  return()=>{alive=false;clearTimeout(timer);};
 },[open,query,page,attempt,context,sessionContext]);
 function loadMore(){
  if(busy||pending||!more||!data)return;
  const token=JSON.stringify([key,data.page+1]);if(moreRequest.current===token)return;moreRequest.current=token;
  setCursor(old=>({key,page:data.page+1,attempt:old.attempt+1}));
 }
 return <Modal open={open} onOpenChange={onOpenChange} title={t('redesign.selectOrganization')} description={t('redesign.organizationHelp')}><div className="relative mb-4"><Search size={16} className="absolute left-3 top-3 text-muted-foreground"/><ClearableControl name={t('redesign.searchOrganizations')} value={q} onClear={()=>{setQ('');setCursor({key:'',page:0,attempt:0});moreRequest.current='';}}><input className="input pl-9 pr-11" aria-label={t('redesign.searchOrganizations')} placeholder={t('redesign.searchOrganizations')} value={q} onChange={e=>{const text=e.target.value;if(text.trim()!==query){setCursor({key:'',page:0,attempt:0});moreRequest.current='';}setQ(text);}} maxLength={128}/></ClearableControl></div>
 {failed&&<p role="alert" className="error-box">{t('redesign.organizationsUnavailable')}</p>}
 {data?<><div ref={list} role="list" aria-label={t('redesign.selectOrganization')} aria-busy={pending} className="max-h-64 space-y-2 overflow-y-auto" onScroll={event=>{const node=event.currentTarget;if(node.scrollTop+node.clientHeight>=node.scrollHeight-16)loadMore();}}>
 {data.items.length===0?<p className="empty-state">{t('redesign.noOrganizations')}</p>:data.items.map((org,index)=><div role="listitem" key={org.routingId}><Button data-routing-id={org.routingId} aria-pressed={selected?.context===context&&selected.id===org.routingId} variant="outline" className="h-auto w-full justify-between gap-3 py-3 text-left" disabled={optionsDisabled} onKeyDown={event=>{if(event.key==='ArrowDown'&&index===data.items.length-1&&more){event.preventDefault();loadMore();}}} onClick={()=>{if(!optionsDisabled){setSelected({context,id:org.routingId});select(org.routingId);}}}><span className="flex min-w-0 items-center gap-3"><Building2 size={18} className="shrink-0"/><span className="min-w-0 break-words">{org.name}{data.items.filter(candidate=>candidate.name===org.name).length>1&&<span className="block break-all text-xs text-muted-foreground">{org.routingId}</span>}</span></span><ArrowRight size={16} className="shrink-0"/></Button></div>)}
 </div>{(more||data.page>0)&&<Button className="mt-3" variant="outline" disabled={busy||pending||!more} onClick={loadMore}>{t('ui.loadMore')}</Button>}</>:!failed?<div className="space-y-2"><Skeleton className="h-12"/><Skeleton className="h-12"/></div>:null}
 {failure&&<p role="alert" className="error-box mt-4">{errorMessage(failure)}</p>}</Modal>;
}
