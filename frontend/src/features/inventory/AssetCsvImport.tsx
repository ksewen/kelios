import {useEffect,useRef,useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {authSession as session} from '../../auth';
import {errorMessage,label} from '../../i18n';
import {confirmAction} from '../../components/ui/confirmation';
import {Button} from '../../components/ui/button';
import {Card,Badge} from '../../components/ui/surfaces';
import type {TenantItem} from '../../api-contracts';
import {Pick} from './pages';

type RawRow={rowNumber:number;values:Record<string,string>};
type Parsed={sha256:string;encoding:string;delimiter:string;headers:string[];totalRows:number;rows:RawRow[]};
type Candidate={id:string;name:string;tenantId:string|null};
type Resolution={status:string;id:string|null;candidates:Candidate[];more:boolean};
type PreviewRow={rowNumber:number;status:'READY'|'WARNING'|'ERROR';source:Record<string,string>;normalized:Record<string,unknown>;references:Record<string,string>;issues:string[]};
type Preview={sha256:string;encoding:string;delimiter:string;headers:string[];totalRows:number;ready:number;warnings:number;errors:number;rows:PreviewRow[];referenceResolution:Record<string,Record<string,Resolution>>};
type Outcome={rowNumber:number;status:'SUCCESS'|'FAILED'|'SKIPPED';assetId:string|null;assetTag:string|null;reason:string|null};
type Result={successful:number;failed:number;skipped:number;rows:Outcome[];failureCsv:string};
const targets=['localIdentifier','serialNumber','externalInventoryNumber','note','productId','productName','categoryId','categoryName','manufacturerId','manufacturerName','locationId','locationName','ownerId','ownerName','lifecycle','condition','loanable','tagDate'] as const;
const enumValues:Record<string,string[]>={lifecycle:['REGISTERED','IN_STOCK','DEPLOYED','DEREGISTERED'],condition:['UNKNOWN','WORKING','DEFECTIVE','UNUSABLE'],loanable:['TRUE','FALSE']};
const collectionFor:Record<string,string>={product:'products',category:'categories',manufacturer:'manufacturers',location:'locations',owner:'owners'};
const targetLabel=(target:string,t:(key:string)=>string)=>target==='tagDate'?t('csv.tagDate'):target.endsWith('Id')||target.endsWith('Name')?
  `${label(target.replace(/(Id|Name)$/,''))} · ${t(target.endsWith('Id')?'csv.uuidMode':'csv.nameMode')}`:label(target);
function mappedColumns(sourceTargets:Record<string,string>){return Object.fromEntries(Object.entries(sourceTargets).filter(([,target])=>target).map(([source,target])=>[target,source]));}
function mappingAllowed(target:string,source:string,sourceTargets:Record<string,string>){
 const selected=Object.entries(sourceTargets).filter(([column])=>column!==source).map(([,value])=>value);
 if(selected.includes(target))return false;
 const kind=target.replace(/(Id|Name)$/,'');
 if(['product','category','manufacturer','location','owner'].includes(kind)&&selected.some(value=>value===`${kind}${target.endsWith('Id')?'Name':'Id'}`))return false;
 if(kind==='product'&&selected.some(value=>/^(category|manufacturer)(Id|Name)$/.test(value)))return false;
 if(['category','manufacturer'].includes(kind)&&selected.some(value=>/^product(Id|Name)$/.test(value)))return false;
 return true;
}
function multipart(file:File,config:object){const body=new FormData();body.append('file',file);body.append('config',new Blob([JSON.stringify(config)],{type:'application/json'}));return body;}
function downloadFailures(text:string){const url=URL.createObjectURL(new Blob([text],{type:'text/csv;charset=utf-8'}));const anchor=document.createElement('a');anchor.href=url;anchor.download='kelios-import-errors.csv';anchor.click();setTimeout(()=>URL.revokeObjectURL(url),0);}

export function AssetCsvImport({contextTenant,tenants}:{contextTenant:string;tenants:TenantItem[]|null}){
 const {t}=useTranslation();const [tenant,setTenant]=useState(contextTenant),[file,setFile]=useState<File|null>(null),[encoding,setEncoding]=useState('AUTO'),[delimiter,setDelimiter]=useState('AUTO'),[custom,setCustom]=useState('');
 const [parsed,setParsed]=useState<Parsed|null>(null),[sourceTargets,setSourceTargets]=useState<Record<string,string>>({});
 const [values,setValues]=useState<Record<string,Record<string,string>>>({}),[choices,setChoices]=useState<Record<string,Record<string,string>>>({});
 const [preview,setPreview]=useState<Preview|null>(null),[validated,setValidated]=useState(false),[selected,setSelected]=useState<number[]>([]),[visible,setVisible]=useState(100),[result,setResult]=useState<Result|null>(null);
 const [busy,setBusy]=useState(false),[error,setError]=useState('');const request=useRef(0);
 useEffect(()=>()=>{request.current++;},[]);
 const invalidate=()=>{request.current++;setValidated(false);setError('');};
 const resetParse=()=>{invalidate();setParsed(null);setPreview(null);setResult(null);setSelected([]);setVisible(100);setSourceTargets({});setValues({});setChoices({});};
 const config=()=>({tenantId:tenant,sha256:parsed!.sha256,encoding:parsed!.encoding,delimiter:parsed!.delimiter,
   columns:mappedColumns(sourceTargets),values,choices});
 const runParse=async()=>{
  if(!file)return;
  if(file.size>5*1024*1024){setError(t('csv.fileTooLarge'));return;}
  if(delimiter==='CUSTOM'&&custom.length!==1){setError(t('csv.customDelimiterRequired'));return;}
  const seq=++request.current,version=session.contextVersion();setBusy(true);setError('');
  try{
   const query=new URLSearchParams({encoding,delimiter:delimiter==='CUSTOM'?custom:delimiter});
   const body=new FormData();body.append('file',file);
   const data=await session.request<Parsed>(`/api/v1/asset-csv/parse-preview?${query}`,'POST',body);
   if(seq!==request.current||version!==session.contextVersion())return;
   setParsed(data);setSourceTargets({});setValues({});setChoices({});setPreview(null);setValidated(false);setResult(null);
  }catch(cause){if(seq===request.current)setError(errorMessage(cause));}
  finally{if(seq===request.current)setBusy(false);}
 };
 const runPreview=async()=>{
  if(!file||!parsed||!tenant||Object.keys(mappedColumns(sourceTargets)).length===0)return;
  const seq=++request.current,version=session.contextVersion();setBusy(true);setError('');
  try{
   const data=await session.request<Preview>('/api/v1/asset-csv/preview','POST',multipart(file,config()));
   if(seq!==request.current||version!==session.contextVersion())return;
   setPreview(data);setValidated(true);setVisible(100);setSelected(data.rows.filter(row=>row.status!=='ERROR').map(row=>row.rowNumber));
  }catch(cause){if(seq===request.current)setError(errorMessage(cause));}
  finally{if(seq===request.current)setBusy(false);}
 };
 const execute=async()=>{
  if(!file||!parsed||!preview||!validated||!selected.length||busy)return;
  if(!await confirmAction(t('csv.confirmImport',{count:selected.length})))return;
  const seq=++request.current,version=session.contextVersion();setBusy(true);setError('');
  try{
   const data=await session.request<Result>('/api/v1/asset-csv/execute','POST',multipart(file,{preview:config(),selectedRows:selected}));
   if(seq!==request.current||version!==session.contextVersion())return;
   setResult(data);setValidated(false);
  }catch(cause){if(seq===request.current)setError(errorMessage(cause));}
  finally{if(seq===request.current)setBusy(false);}
 };
 const columns=mappedColumns(sourceTargets),product=!!(columns.productId||columns.productName);
 const distinct=(target:string)=>preview&&columns[target]?[...new Set(preview.rows.map(row=>row.source[columns[target]]).filter(Boolean))]:[];
 const updateValue=(target:string,raw:string,value:string)=>{setValues(old=>({...old,[target]:{...old[target],[raw]:value}}));invalidate();};
 const updateChoice=(target:string,raw:string,id:string)=>{setChoices(old=>({...old,[target]:{...old[target],[raw]:id}}));invalidate();};
 const createLink=(target:string)=>{const kind=target.replace(/(Id|Name)$/,'');return `/tenants/${tenant}/${collectionFor[kind]}`;};
 const referencePath=(target:string)=>{const kind=target.replace(/(Id|Name)$/,'');return `/api/v1/tenants/${tenant}/${['location','owner'].includes(kind)?collectionFor[kind]:`catalog/${collectionFor[kind]}`}`;};
 return <section className="space-y-5" data-unsaved={!!file&&!result}>
  <div><h1 className="page-title">{t('csv.importTitle')}</h1><p className="text-sm text-muted-foreground">{t('csv.importScope')}</p></div>
  <Card className="space-y-4 p-4 sm:p-6"><h2 className="text-lg font-semibold">{t('csv.fileSettings')}</h2>
   <div className="grid gap-4 sm:grid-cols-2">
    <label className="field">{t('csv.tenant')}<span aria-hidden="true" className="text-destructive"> *</span>{contextTenant?<input className="input" readOnly value={tenants?.find(item=>item.id===contextTenant)?.name??contextTenant}/>:<select className="input" required value={tenant} disabled={busy} onChange={event=>{setTenant(event.target.value);setChoices({});setPreview(null);invalidate();}}><option value="">{t('ui.select')}</option>{tenants?.map(item=><option key={item.id} value={item.id}>{item.name}{tenants.filter(other=>other.name===item.name).length>1?` · ${item.id}`:''}</option>)}</select>}</label>
    <label className="field">{t('csv.file')}<span aria-hidden="true" className="text-destructive"> *</span><input type="file" accept=".csv,text/csv" className="input" disabled={busy} onChange={event=>{setFile(event.target.files?.[0]??null);resetParse();}}/></label>
    <label className="field">{t('csv.encoding')}<select className="input" value={encoding} disabled={busy} onChange={event=>{setEncoding(event.target.value);resetParse();}}>{['AUTO','UTF-8','WINDOWS-1252','ISO-8859-1'].map(value=><option key={value} value={value}>{value==='AUTO'?t('csv.auto'):value}</option>)}</select></label>
    <label className="field">{t('csv.delimiter')}<select className="input" value={delimiter} disabled={busy} onChange={event=>{setDelimiter(event.target.value);resetParse();}}>{['AUTO','SEMICOLON','COMMA','TAB','CUSTOM'].map(value=><option key={value} value={value}>{t(`csv.delimiter${value}`)}</option>)}</select></label>
    {delimiter==='CUSTOM'&&<label className="field">{t('csv.customDelimiter')}<input className="input" maxLength={1} value={custom} disabled={busy} onChange={event=>{setCustom(event.target.value);resetParse();}}/></label>}
   </div><Button type="button" disabled={!file||busy} onClick={()=>void runParse()}>{t('csv.parseFile')}</Button>
   {parsed&&<><p className="text-sm text-muted-foreground">{t('csv.parsedSummary',{rows:parsed.totalRows,encoding:parsed.encoding,delimiter:parsed.delimiter==='\t'?'TAB':parsed.delimiter})}</p><div className="table-frame max-h-64 overflow-auto"><table className="data-table"><thead><tr><th scope="col">#</th>{parsed.headers.map(header=><th key={header} scope="col">{header}</th>)}</tr></thead><tbody>{parsed.rows.map(row=><tr key={row.rowNumber}><th scope="row">{row.rowNumber}</th>{parsed.headers.map(header=><td key={header} className="max-w-64 break-words">{row.values[header]||'—'}</td>)}</tr>)}</tbody></table></div></>}
  </Card>
  {parsed&&<Card className="space-y-5 p-4 sm:p-6"><h2 className="text-lg font-semibold">{t('csv.columnMapping')}</h2><p className="text-sm text-muted-foreground">{t('csv.mappingHelp')}</p>
   <div className="grid gap-4 sm:grid-cols-2">{parsed.headers.map(source=><label className="field" key={source}>{source}<select className="input" value={sourceTargets[source]??''} disabled={busy} onChange={event=>{setSourceTargets(old=>({...old,[source]:event.target.value}));setChoices({});invalidate();}}><option value="">{t('csv.ignore')}</option>{targets.map(target=><option key={target} value={target} disabled={!mappingAllowed(target,source,sourceTargets)}>{targetLabel(target,t)}</option>)}</select></label>)}</div>
   <p className="text-xs text-muted-foreground">{product?t('csv.productDerivesCatalog'):t('csv.directCatalogRequired')}</p>
   <Button type="button" disabled={busy||!tenant||!Object.keys(columns).length} onClick={()=>void runPreview()}>{preview?t('csv.revalidate'):t('csv.showPreview')}</Button>
  </Card>}
  {preview&&<><Card className="space-y-5 p-4 sm:p-6"><h2 className="text-lg font-semibold">{t('csv.resolveValues')}</h2>
   {Object.entries(enumValues).filter(([target])=>columns[target]).map(([target,options])=><div key={target} className="space-y-2"><h3 className="font-medium">{targetLabel(target,t)}</h3><div className="grid gap-3 sm:grid-cols-2">{distinct(target)?.map(raw=><label className="field" key={raw}>{raw}<select className="input" disabled={busy} value={values[target]?.[raw]??(options.includes(raw.toUpperCase())?raw.toUpperCase():'')} onChange={event=>updateValue(target,raw,event.target.value)}><option value="">{t('ui.select')}</option>{options.map(value=><option key={value} value={value}>{target==='loanable'?t(value==='TRUE'?'fields.true':'fields.false'):t(`enums.${value}`)}</option>)}</select></label>)}</div></div>)}
   {Object.entries(preview.referenceResolution).flatMap(([target,byValue])=>Object.entries(byValue).filter(([,resolution])=>resolution.status!=='RESOLVED').map(([raw,resolution])=><div key={`${target}:${raw}`} className="rounded-lg border p-3"><p className="font-medium break-words">{targetLabel(target,t)}: {raw}</p><p className="text-sm text-muted-foreground">{t(`csv.resolution${resolution.status}`,{defaultValue:resolution.status})}</p>{resolution.more&&<p className="text-sm text-muted-foreground">{t('csv.moreCandidates')}</p>}{resolution.status==='AMBIGUOUS'||resolution.status==='CHOSEN'?<fieldset disabled={busy} className="mt-2"><Pick name={t('csv.chooseExisting')} path={referencePath(target)} value={choices[target]?.[raw]??''} set={id=>updateChoice(target,raw,id)} optional/></fieldset>:null}{resolution.status==='UNRESOLVED'&&target.endsWith('Name')&&<Link className="mt-2 inline-block text-sm text-primary underline" to={createLink(target)} target="_blank" rel="noopener noreferrer">{t('csv.openCreation',{resource:targetLabel(target,t)})}</Link>}</div>))}
  </Card><Card className="space-y-4 p-4 sm:p-6"><h2 className="text-lg font-semibold">{t('csv.previewTitle')}</h2><div className="flex flex-wrap gap-2"><Badge>{t('csv.ready')}: {preview.ready}</Badge><Badge>{t('csv.warning')}: {preview.warnings}</Badge><Badge>{t('csv.error')}: {preview.errors}</Badge></div>{!validated&&<p role="status" className="text-sm text-amber-800">{t('csv.revalidateNeeded')}</p>}
   <div className="table-frame max-h-[30rem] overflow-auto"><table className="data-table"><thead><tr><th scope="col">{t('csv.selectRow')}</th><th scope="col">#</th><th scope="col">{t('csv.previewStatus')}</th><th scope="col">{t('csv.sourceValue')}</th><th scope="col">{t('csv.issues')}</th></tr></thead><tbody>{preview.rows.slice(0,visible).map(row=><tr key={row.rowNumber}><td><input type="checkbox" aria-label={t('csv.selectNumber',{number:row.rowNumber})} disabled={busy||row.status==='ERROR'} checked={selected.includes(row.rowNumber)} onChange={event=>setSelected(old=>event.target.checked?[...old,row.rowNumber]:old.filter(value=>value!==row.rowNumber))}/></td><th scope="row">{row.rowNumber}</th><td>{t(`csv.status${row.status}`)}</td><td className="max-w-56 break-words">{String(row.normalized.localIdentifier??Object.values(row.source)[0]??'—')}</td><td className="max-w-96 break-words">{row.issues.length?row.issues.join('; '):'—'}</td></tr>)}</tbody></table></div>{preview.rows.length>visible&&<Button type="button" variant="outline" onClick={()=>setVisible(value=>value+100)}>{t('csv.showMoreRows',{count:preview.rows.length-visible})}</Button>}
   <Button type="button" disabled={busy||!validated||!selected.length} onClick={()=>void execute()}>{t('csv.importSelected',{count:selected.length})}</Button><p className="text-xs text-muted-foreground">{t('csv.retryWarning')}</p>
  </Card></>}
  {result&&<Card className="space-y-4 p-4 sm:p-6" role="status"><h2 className="text-lg font-semibold">{t('csv.resultTitle')}</h2><p>{t('csv.resultCounts',{successful:result.successful,failed:result.failed,skipped:result.skipped})}</p>{result.failureCsv&&<Button type="button" variant="outline" onClick={()=>downloadFailures(result.failureCsv)}>{t('csv.downloadFailures')}</Button>}<div className="table-frame max-h-72 overflow-auto"><table className="data-table"><thead><tr><th scope="col">#</th><th scope="col">{t('csv.previewStatus')}</th><th scope="col">{t('ui.tag')}</th><th scope="col">{t('csv.issues')}</th></tr></thead><tbody>{result.rows.map(row=><tr key={row.rowNumber}><th scope="row">{row.rowNumber}</th><td>{t(`csv.status${row.status}`)}</td><td>{row.assetTag??'—'}</td><td className="max-w-96 break-words">{row.reason??'—'}</td></tr>)}</tbody></table></div></Card>}
  {error&&<p role="alert" className="error-box">{error}</p>}
 </section>;
}
