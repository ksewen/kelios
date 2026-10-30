import {useEffect,useRef,useState} from 'react';
import {useTranslation} from 'react-i18next';
import {authSession as session} from '../../auth';
import {errorMessage} from '../../i18n';
import {Button} from '../../components/ui/button';
import {Modal} from '../../components/ui/dialog';
import {ArrowDown,ArrowUp} from 'lucide-react';

const columns=['id','assetTag','localIdentifier','tenantId','tenant','productId','product','manufacturerId','manufacturer','categoryId','category','serialNumber','externalInventoryNumber','locationId','location','ownerId','owner','lifecycle','condition','loanable','loanedTo','loanedAt','expectedReturnAt','createdAt','createdBy','createdByDisplayName','note','loanNote'];
const defaults=['assetTag','localIdentifier','tenant','product','location','lifecycle','condition','loanedTo','createdAt'];
const nameKey=(column:string)=>({tenant:'tenantName',product:'productName',manufacturer:'manufacturerName',category:'categoryName',location:'locationName',owner:'ownerName'} as Record<string,string>)[column]??column;

export function AssetCsvExport({tenant,query}:{tenant:string;query:string}){
 const {t}=useTranslation(),[open,setOpen]=useState(false),[delimiter,setDelimiter]=useState('SEMICOLON'),[custom,setCustom]=useState(''),[selected,setSelected]=useState(defaults),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const request=useRef(0);useEffect(()=>()=>{request.current++;},[]);
 const change=(next:boolean)=>{if(!next){request.current++;setBusy(false);}setOpen(next);setError('');if(next){setDelimiter('SEMICOLON');setCustom('');setSelected(defaults);}};
 const move=(index:number,step:number)=>setSelected(current=>{const next=[...current];[next[index],next[index+step]]=[next[index+step],next[index]];return next;});
 const exportCsv=async()=>{
  const separator=delimiter==='CUSTOM'?custom:delimiter==='COMMA'?',':delimiter==='TAB'?'\t':';';
  if(separator.length!==1||separator==='"'||separator==='\r'||separator==='\n'){setError(t('csv.customDelimiterRequired'));return;}
  if(!selected.length){setError(t('csv.selectExportColumn'));return;}
  const params=new URLSearchParams(query);params.delete('page');params.delete('size');if(tenant)params.set('tenantId',tenant);params.set('delimiter',separator);params.set('columns',selected.join(','));
  const seq=++request.current;setBusy(true);setError('');
  try{
   const csv=await session.request<string>(`/api/v1/asset-csv/export?${params}`,'GET',undefined,'text');
   if(seq!==request.current)return;
   // Response.text() strips the server's UTF-8 BOM; restore it for spreadsheet downloads.
   const url=URL.createObjectURL(new Blob([csv.startsWith('\ufeff')?csv:'\ufeff'+csv],{type:'text/csv;charset=utf-8'}));const link=document.createElement('a');link.href=url;link.download='assets.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);change(false);
  }catch(cause){if(seq===request.current)setError(errorMessage(cause));}
  finally{if(seq===request.current)setBusy(false);}
 };
 return <><Button type="button" variant="outline" className="h-10 shrink-0" onClick={()=>change(true)}>{t('csv.exportTitle')}</Button><Modal open={open} onOpenChange={change} title={t('csv.exportTitle')} description={t('csv.exportScope')}>
  <div className="space-y-4"><label className="field">{t('csv.delimiter')}<select className="input" value={delimiter} onChange={event=>setDelimiter(event.target.value)}>{['SEMICOLON','COMMA','TAB','CUSTOM'].map(value=><option key={value} value={value}>{t(`csv.delimiter${value}`)}</option>)}</select></label>
   {delimiter==='CUSTOM'&&<label className="field">{t('csv.customDelimiter')}<input className="input" maxLength={1} value={custom} onChange={event=>setCustom(event.target.value)}/></label>}
   <fieldset><legend className="mb-2 font-medium">{t('csv.exportColumns')}</legend><p className="mb-2 text-sm text-muted-foreground">{t('csv.exportColumnHelp')}</p><div className="max-h-[48dvh] space-y-1 overflow-y-auto rounded-md border p-2">
    {[...selected,...columns.filter(column=>!selected.includes(column))].map(column=>{const index=selected.indexOf(column),name=t(['id','createdBy','createdByDisplayName'].includes(column)?`csv.exportField.${column}`:`fields.${nameKey(column)}`);return <div key={column} className="flex min-w-0 items-center gap-2 rounded px-1 py-1 hover:bg-muted/50"><label className="flex min-w-0 flex-1 items-center gap-2 text-sm"><input type="checkbox" checked={index>=0} onChange={event=>setSelected(current=>event.target.checked?[...current,column]:current.filter(value=>value!==column))}/><span className="min-w-0 break-words">{name}</span></label>{index>=0&&<><Button type="button" size="icon" variant="ghost" aria-label={t('csv.moveColumnUp',{column:name})} disabled={index===0} onClick={()=>move(index,-1)}><ArrowUp size={16}/></Button><Button type="button" size="icon" variant="ghost" aria-label={t('csv.moveColumnDown',{column:name})} disabled={index===selected.length-1} onClick={()=>move(index,1)}><ArrowDown size={16}/></Button></>}</div>;})}
   </div></fieldset>
   {error&&<p role="alert" className="error-box">{error}</p>}
   <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={()=>change(false)}>{t('redesign.cancel')}</Button><Button type="button" disabled={busy||!selected.length||delimiter==='CUSTOM'&&custom.length!==1} onClick={()=>void exportCsv()}>{busy?t('csv.preparingExport'):t('csv.downloadExport')}</Button></div>
  </div>
 </Modal></>;
}
