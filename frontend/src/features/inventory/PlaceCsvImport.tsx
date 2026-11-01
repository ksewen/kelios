import {useEffect,useRef,useState} from 'react';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import type {TenantItem} from '../../api-contracts';
import {authSession as session,ApiFailure} from '../../auth';
import {errorMessage} from '../../i18n';
import {Button} from '../../components/ui/button';
import {Modal} from '../../components/ui/dialog';
import {confirmAction} from '../../components/ui/confirmation';
import {Badge,Card} from '../../components/ui/surfaces';
import {parsePlaceCsv,PlaceCsvError,type PlaceCsvRow,type PlaceCsvSettings} from './place-csv';

type Outcome = PlaceCsvRow & {status:'SUCCESS'|'FAILED'|'UNKNOWN';id?:string;error?:string};
type Parsed = {file:File;encoding:PlaceCsvSettings['encoding'];delimiter:string;custom:string;header:boolean;rows:PlaceCsvRow[]};
const delimiterValue:Record<string,string>={SEMICOLON:';',COMMA:',',TAB:'\t'};

export function PlaceCsvImport({kind,globalAdmin,tenantId,tenantName,tenants,onSuccess}:{kind:'locations'|'owners';globalAdmin:boolean;tenantId:string;tenantName?:string;tenants?:TenantItem[]|null;onSuccess:()=>void}) {
  const {t}=useTranslation();
  const [open,setOpen]=useState(false),[step,setStep]=useState<0|1|2>(0),[file,setFile]=useState<File|null>(null);
  const [encoding,setEncoding]=useState<PlaceCsvSettings['encoding']>('AUTO'),[delimiter,setDelimiter]=useState('AUTO'),[custom,setCustom]=useState(''),[header,setHeader]=useState(true);
  const [target,setTarget]=useState(''),[parsed,setParsed]=useState<Parsed|null>(null),[parseError,setParseError]=useState(''),[parsing,setParsing]=useState(false);
  const [outcomes,setOutcomes]=useState<Outcome[]|null>(null),[locked,setLocked]=useState(false),[visible,setVisible]=useState(100),[runError,setRunError]=useState('');
  const running=useRef(false),generation=useRef(0),stepHeading=useRef<HTMLHeadingElement>(null);
  const selectedTenant=globalAdmin?target:tenantId;
  const targetLabel=globalAdmin?tenants?.find(item=>item.id===target)?.name:tenantName;
  const rows=parsed?.file===file&&parsed.encoding===encoding&&parsed.delimiter===delimiter&&parsed.custom===custom&&parsed.header===header?parsed.rows:null;
  const ready=rows?.filter(row=>row.name.trim()).length??0;
  const invalidate=()=>{generation.current++;setParsed(null);setParseError('');setVisible(100);};
  const reset=()=>{invalidate();setFile(null);setEncoding('AUTO');setDelimiter('AUTO');setCustom('');setHeader(true);setTarget('');setStep(0);setOutcomes(null);setRunError('');};

  useEffect(()=>{if(open)stepHeading.current?.focus();},[open,step]);
  useEffect(()=>{
    const token=++generation.current;
    if(!file){setParsing(false);return;}
    if(delimiter==='CUSTOM'&&Array.from(custom).length!==1){setParsing(false);setParseError(t('csv.customDelimiterRequired'));return;}
    setParsing(true);
    const selected=delimiter==='AUTO'?'AUTO':delimiter==='CUSTOM'?custom:delimiterValue[delimiter];
    void file.arrayBuffer().then(bytes=>{
      const next=parsePlaceCsv(bytes,{encoding,delimiter:selected,header});
      if(token===generation.current){setParsed({file,encoding,delimiter,custom,header,rows:next});setParseError('');}
    }).catch(cause=>{
      if(token===generation.current)setParseError(cause instanceof PlaceCsvError?t(`placeCsv.${cause.reason}`,{line:cause.line??1}):errorMessage(cause));
    }).finally(()=>{if(token===generation.current)setParsing(false);});
    return ()=>{generation.current++;};
  },[file,encoding,delimiter,custom,header,t]);

  const execute=async()=>{
    if(step!==1||!rows||!selectedTenant||running.current)return;
    running.current=true;setLocked(true);setRunError('');
    const chosenRows=rows,chosenTenant=selectedTenant,version=session.contextVersion(),token=generation.current;
    const result:Outcome[]=[];let successes=0;
    try{
      if(!await confirmAction(t('placeCsv.confirm',{count:chosenRows.length,tenant:targetLabel??chosenTenant})))return;
      if(token!==generation.current||version!==session.contextVersion()){setRunError(t('placeCsv.contextChanged'));return;}
      setOutcomes([]);setStep(2);
      for(const row of chosenRows){
        if(version!==session.contextVersion()){setRunError(t('placeCsv.contextChanged'));break;}
        try{
          const created=await session.request<{id:string}>(`/api/v1/tenants/${chosenTenant}/${kind}`,'POST',{name:row.name,note:null});
          result.push({...row,status:'SUCCESS',id:created.id});successes++;
        }catch(cause){
          const known=cause instanceof ApiFailure&&cause.status>0;
          const code=cause instanceof ApiFailure?cause.code:undefined;
          result.push({...row,status:known?'FAILED':'UNKNOWN',error:`${code??(known?`HTTP_${cause.status}`:'REQUEST_FAILED')}${known?` (HTTP ${cause.status})`:''}: ${errorMessage(cause)}`});
          if(!known){setOutcomes([...result]);break;}
        }
        setOutcomes([...result]);
      }
    }finally{
      if(successes)onSuccess();
      if(result.length)setOutcomes([...result]);
      setLocked(false);running.current=false;
    }
  };

  const steps=['csv.fileSettings','placeCsv.previewTitle','placeCsv.resultTitle'] as const;
  const sourcePreview=(limit:number)=>rows&&<div className="table-frame max-h-64 overflow-auto"><table className="data-table" aria-label={t('placeCsv.previewTitle')}><thead><tr><th scope="col">{t('csv.recordNumber')}</th><th scope="col">{t('csv.lineNumber')}</th><th scope="col">{t('ui.name')}</th><th scope="col">{t('csv.previewStatus')}</th></tr></thead><tbody>{rows.slice(0,limit).map(row=><tr key={row.record}><th scope="row">{row.record}</th><td>{row.line}</td><td className="break-words">{row.name||'—'}</td><td>{row.name.trim()?t('csv.statusREADY'):t('placeCsv.emptyName')}</td></tr>)}</tbody></table></div>;
  return <><Button type="button" variant="outline" size="control" className="shrink-0" onClick={()=>{reset();setOpen(true);}}>{t('csv.importTitle')}</Button>
    <Modal open={open} canClose={!locked} onOpenChange={value=>{if(!value)reset();setOpen(value);}} title={t('placeCsv.title',{resource:t(kind==='locations'?'ui.location':'ui.owner')})}>
      <div className="space-y-4" data-unsaved={!!file&&(step!==2||locked)} data-import-executing={locked||undefined}>
        <nav aria-label={t('csv.steps')}><ol className="flex flex-wrap gap-2 text-sm">{steps.map((name,index)=><li key={name} aria-current={step===index?'step':undefined} className={`rounded-md border px-3 py-1.5 ${step===index?'border-primary bg-accent font-semibold text-accent-foreground':'text-muted-foreground'}`}>{index+1}. {t(name)}</li>)}</ol></nav>
        {step===0&&<Card className="space-y-4 p-4"><h2 ref={stepHeading} tabIndex={-1} className="text-lg font-semibold">{t('csv.fileSettings')}</h2>
          <label className="field">{t('fields.tenantName')}{globalAdmin?<select className="input" required value={target} disabled={locked} onChange={event=>{setTarget(event.target.value);setRunError('');}}><option value="">{t('ui.select')}</option>{tenants?.map(item=><option key={item.id} value={item.id}>{item.name} · {item.id}</option>)}</select>:<input className="input" readOnly value={targetLabel??tenantId}/>}</label>
          <label className="field">{t('csv.file')}<input className="input" type="file" accept=".csv,text/csv" disabled={locked} onChange={event=>{invalidate();setFile(event.target.files?.[0]??null);}}/></label>
          {file&&<p className="text-sm text-muted-foreground">{file.name}</p>}
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="field">{t('csv.encoding')}<select className="input" value={encoding} disabled={locked} onChange={event=>{invalidate();setEncoding(event.target.value as PlaceCsvSettings['encoding']);}}>{(['AUTO','UTF-8','WINDOWS-1252','ISO-8859-1'] as const).map(value=><option key={value} value={value}>{value==='AUTO'?t('csv.auto'):value}</option>)}</select></label>
            <label className="field">{t('csv.delimiter')}<select className="input" value={delimiter} disabled={locked} onChange={event=>{invalidate();setDelimiter(event.target.value);}}>{['AUTO','SEMICOLON','COMMA','TAB','CUSTOM'].map(value=><option key={value} value={value}>{t(`csv.delimiter${value}`)}</option>)}</select></label>
            {delimiter==='CUSTOM'&&<label className="field">{t('csv.customDelimiter')}<input className="input" maxLength={1} value={custom} disabled={locked} onChange={event=>{invalidate();setCustom(event.target.value);}}/></label>}
          </div>
          {(encoding==='AUTO'||delimiter==='AUTO')&&<p className="text-sm text-muted-foreground">{encoding==='AUTO'&&t('placeCsv.autoEncodingHelp')}{encoding==='AUTO'&&delimiter==='AUTO'&&' '}{delimiter==='AUTO'&&t('placeCsv.autoDelimiterHelp')}</p>}
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={header} disabled={locked} onChange={event=>{invalidate();setHeader(event.target.checked);}}/>{t('csv.headerPresent')}</label>
          {parsing&&<p role="status">{t('ui.checking')}</p>}
          {parseError&&<p role="alert" className="error-box">{parseError}</p>}
          {rows&&<><p role="status" className="text-sm">{t('placeCsv.previewCount',{count:rows.length})}</p>{sourcePreview(10)}{rows.length>10&&<p className="text-sm text-muted-foreground">{t('placeCsv.previewSample',{count:10})}</p>}</>}
          <div className="flex justify-end border-t pt-4"><Button type="button" disabled={!rows||!selectedTenant||parsing||locked} onClick={()=>{setVisible(100);setStep(1);}}>{t('csv.next')}</Button></div>
        </Card>}
        {step===1&&rows&&<Card className="space-y-4 p-4"><h2 ref={stepHeading} tabIndex={-1} className="text-lg font-semibold">{t('placeCsv.previewTitle')}</h2>
          <p className="text-sm">{t('fields.tenantName')}: {targetLabel??selectedTenant} · {file?.name}</p>
          <div className="flex flex-wrap gap-2"><Badge>{t('csv.ready')}: {ready}</Badge><Badge>{t('placeCsv.invalid')}: {rows.length-ready}</Badge></div>
          {sourcePreview(visible)}
          {rows.length>visible&&<Button type="button" variant="outline" onClick={()=>setVisible(value=>value+100)}>{t('csv.showMoreRows',{count:rows.length-visible})}</Button>}
          <div className="flex flex-wrap justify-end gap-2 border-t pt-4"><Button type="button" variant="outline" disabled={locked} onClick={()=>setStep(0)}>{t('csv.back')}</Button><Button type="button" aria-busy={locked} disabled={locked||!rows||!selectedTenant} onClick={()=>void execute()}>{locked?t('csv.executing'):t('csv.importSelected',{count:rows.length})}</Button></div>
          {runError&&<p role="alert" className="error-box">{runError}</p>}
        </Card>}
        {step===2&&<Card className="space-y-4 p-4" role="status"><h2 ref={stepHeading} tabIndex={-1} className="text-lg font-semibold">{t('placeCsv.resultTitle')}</h2>
          <p>{t('placeCsv.progress',{done:outcomes?.length??0,total:rows?.length??0})}</p>
          {locked&&<p className="text-sm text-muted-foreground">{t('placeCsv.finishBeforeLeaving')}</p>}
          {outcomes&&<><p>{t('placeCsv.result',{successful:outcomes.filter(row=>row.status==='SUCCESS').length,failed:outcomes.filter(row=>row.status==='FAILED').length,unknown:outcomes.filter(row=>row.status==='UNKNOWN').length,remaining:(rows?.length??0)-outcomes.length})}</p><div className="table-frame max-h-64 overflow-auto"><table className="data-table"><thead><tr><th scope="col">{t('csv.recordNumber')}</th><th scope="col">{t('csv.lineNumber')}</th><th scope="col">{t('ui.name')}</th><th scope="col">{t('csv.previewStatus')}</th><th scope="col">{t('csv.issues')}</th></tr></thead><tbody>{outcomes.slice(0,visible).map(row=><tr key={row.record}><th scope="row">{row.record}</th><td>{row.line}</td><td className="break-words">{row.name||'—'}</td><td>{row.status==='UNKNOWN'?t('placeCsv.statusUnknown'):t(`csv.status${row.status}`)}</td><td className="break-words">{row.id??row.error??'—'}</td></tr>)}</tbody></table></div>{outcomes.length>visible&&<Button type="button" variant="outline" onClick={()=>setVisible(value=>value+100)}>{t('csv.showMoreRows',{count:outcomes.length-visible})}</Button>}{outcomes.some(row=>row.status==='UNKNOWN')&&<p role="alert" className="error-box">{t('placeCsv.unknown')}</p>}</>}
          {runError&&<p role="alert" className="error-box">{runError}</p>}
          {!locked&&<div className="flex flex-wrap justify-end gap-2 border-t pt-4"><Button asChild variant="outline"><Link to={`/tenants/${selectedTenant}/${kind}`}>{t('placeCsv.viewTarget')}</Link></Button><Button type="button" variant="outline" onClick={reset}>{t('csv.newImport')}</Button></div>}
        </Card>}
      </div>
    </Modal></>;
}
