import {useEffect,useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Building2,ArrowRight,Search} from 'lucide-react';
import {errorMessage} from '../i18n';
import {authSession} from '../auth';
import {Modal} from '../components/ui/dialog';
import {Button} from '../components/ui/button';
import {Skeleton} from '../components/ui/surfaces';
function routingContext(): string {
 const user=authSession.current();
 return JSON.stringify([authSession.contextVersion(),user?.id,user?.scope,user?.tenantId,user?.role,user?.status]);
}
export function OrganizationPicker({open,onOpenChange,busy,select,failure}:{failure?:string;open:boolean;onOpenChange:(open:boolean)=>void;busy:boolean;select:(id:string)=>void}){
 const {t}=useTranslation();const [q,setQ]=useState(''),[page,setPage]=useState(0);
 const [,sessionChanged]=useState(0);
 useEffect(()=>authSession.subscribe(()=>sessionChanged(v=>v+1)),[]);
 const context=routingContext();
 const [result,setResult]=useState<{context:string;q:string;page:number;data:Awaited<ReturnType<typeof authSession.organizations>>|null;failed:boolean;pending:boolean}|null>(null);
 const sameContext=open&&result?.context===context;
 const currentRequest=sameContext&&result?.q===q&&result?.page===page;
 const data=sameContext?result!.data:null;
 const failed=currentRequest&&result!.failed;
 const pending=open&&(!currentRequest||result!.pending);
 useEffect(()=>{
  if(!open){setResult(null);return;}
  let alive=true;
  setResult(old=>({context,q,page,data:old?.context===context?old.data:null,failed:false,pending:true}));
  const timer=setTimeout(()=>void authSession.organizations(page,q).then(data=>{
   if(alive&&routingContext()===context)setResult({context,q,page,data,failed:false,pending:false});
  }).catch(()=>{if(alive&&routingContext()===context)setResult({context,q,page,data:null,failed:true,pending:false});}),150);
  return()=>{alive=false;clearTimeout(timer);};
 },[open,q,page,context]);
 return <Modal open={open} onOpenChange={onOpenChange} title={t('redesign.selectOrganization')} description={t('redesign.organizationHelp')}><div className="relative mb-4"><Search size={16} className="absolute left-3 top-3 text-muted-foreground"/><input className="input pl-9" aria-label={t('redesign.searchOrganizations')} placeholder={t('redesign.searchOrganizations')} value={q} onChange={e=>{setQ(e.target.value);setPage(0);}} maxLength={128}/></div>{failed?<p role="alert" className="error-box">{t('redesign.organizationsUnavailable')}</p>:data?<div className="space-y-2">{data.items.length===0?<p className="empty-state">{t('redesign.noOrganizations')}</p>:data.items.map(org=><Button key={org.routingId} variant="outline" className="h-auto w-full justify-between py-3 text-left" disabled={busy||pending} onClick={()=>{if(!pending)select(org.routingId);}}><span className="flex items-center gap-3"><Building2 size={18}/>{org.name}</span><ArrowRight size={16}/></Button>)}{data.totalElements>25&&<div className="flex justify-between pt-3"><Button variant="outline" disabled={page===0} onClick={()=>setPage(page-1)}>{t('ui.previousPage')}</Button><Button variant="outline" disabled={(page+1)*25>=data.totalElements} onClick={()=>setPage(page+1)}>{t('ui.nextPage')}</Button></div>}</div>:<div className="space-y-2"><Skeleton className="h-12"/><Skeleton className="h-12"/></div>}{failure&&<p role="alert" className="error-box mt-4">{errorMessage(failure)}</p>}</Modal>;
}
