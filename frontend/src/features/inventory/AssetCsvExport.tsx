import {useEffect,useRef,useState} from 'react';
import {useTranslation} from 'react-i18next';
import {authSession as session} from '../../auth';
import {errorMessage} from '../../i18n';
import {Button} from '../../components/ui/button';
import {Modal} from '../../components/ui/dialog';

export function AssetCsvExport({tenant,query}:{tenant:string;query:string}){
 const {t}=useTranslation(),[open,setOpen]=useState(false),[delimiter,setDelimiter]=useState('SEMICOLON'),[custom,setCustom]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const request=useRef(0);useEffect(()=>()=>{request.current++;},[]);
 const change=(next:boolean)=>{if(!next){request.current++;setBusy(false);}setOpen(next);setError('');if(next){setDelimiter('SEMICOLON');setCustom('');}};
 const exportCsv=async()=>{
  const separator=delimiter==='CUSTOM'?custom:delimiter==='COMMA'?',':delimiter==='TAB'?'\t':';';
  if(separator.length!==1||separator==='"'||separator==='\r'||separator==='\n'){setError(t('csv.customDelimiterRequired'));return;}
  const params=new URLSearchParams(query);params.delete('page');params.delete('size');if(tenant)params.set('tenantId',tenant);params.set('delimiter',separator);
  const seq=++request.current;setBusy(true);setError('');
  try{
   const csv=await session.request<string>(`/api/v1/asset-csv/export?${params}`,'GET',undefined,'text');
   if(seq!==request.current)return;
   // Response.text() strips the server's UTF-8 BOM; restore it for spreadsheet downloads.
   const url=URL.createObjectURL(new Blob([csv.startsWith('\ufeff')?csv:'\ufeff'+csv],{type:'text/csv;charset=utf-8'}));const link=document.createElement('a');link.href=url;link.download='assets.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);change(false);
  }catch(cause){if(seq===request.current)setError(errorMessage(cause));}
  finally{if(seq===request.current)setBusy(false);}
 };
 return <><Button type="button" variant="outline" className="h-10 shrink-0" onClick={()=>change(true)}>{t('csv.exportTitle')}</Button><Modal open={open} onOpenChange={change} title={t('csv.exportTitle')} description={t('csv.exportScope')} compact>
  <div className="space-y-4"><label className="field">{t('csv.delimiter')}<select className="input" value={delimiter} onChange={event=>setDelimiter(event.target.value)}>{['SEMICOLON','COMMA','TAB','CUSTOM'].map(value=><option key={value} value={value}>{t(`csv.delimiter${value}`)}</option>)}</select></label>
   {delimiter==='CUSTOM'&&<label className="field">{t('csv.customDelimiter')}<input className="input" maxLength={1} value={custom} onChange={event=>setCustom(event.target.value)}/></label>}
   {error&&<p role="alert" className="error-box">{error}</p>}
   <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={()=>change(false)}>{t('redesign.cancel')}</Button><Button type="button" disabled={busy||delimiter==='CUSTOM'&&custom.length!==1} onClick={()=>void exportCsv()}>{busy?t('csv.preparingExport'):t('csv.downloadExport')}</Button></div>
  </div>
 </Modal></>;
}
