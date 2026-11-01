import {useEffect,useRef,useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {TenantItem} from '../../api-contracts';
import {authSession as session,ApiFailure} from '../../auth';
import {errorMessage} from '../../i18n';
import {Button} from '../../components/ui/button';
import {Modal} from '../../components/ui/dialog';
import {confirmAction} from '../../components/ui/confirmation';
import {Badge,Card} from '../../components/ui/surfaces';
import {parsePlaceCsvTable,PlaceCsvError,type PlaceCsvRow,type PlaceCsvSettings,type PlaceCsvTable} from './place-csv';

type Outcome = PlaceCsvRow & {status:'SUCCESS'|'FAILED'|'UNKNOWN';id?:string;error?:string};
type Parsed = {file:File;encoding:PlaceCsvSettings['encoding'];delimiter:string;custom:string;header:boolean;table:PlaceCsvTable};
type Mapping = {file:File;columns:string[];index:number|null};
type Selection = {table:PlaceCsvTable;column:number;records:number[]};
type Review = {rows:PlaceCsvRow[];total:number;eligible:number;tenant:string;tenantLabel:string;columnLabel:string;fileName:string;generation:number};
type ParseProblem = PlaceCsvError | {message:string} | {custom:true};
const delimiterValue:Record<string,string>={SEMICOLON:';',COMMA:',',TAB:'\t'};
const sameColumns=(a:string[],b:string[])=>a.length===b.length&&a.every((value,index)=>value===b[index]);
// Match Java 25 Character.isWhitespace used by PlaceService.required, without changing the submitted name.
const blankName=(name:string)=>/^[\u0009-\u000D\u001C-\u0020\u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]*$/u.test(name);
const initialColumn=(columns:string[],header:boolean):number|null=>{
  if(columns.length===1)return 0;
  const matches=header?columns.flatMap((value,index)=>value.trim().toLowerCase()==='name'?[index]:[]):[];
  return matches.length===1?matches[0]:null;
};

export function PlaceCsvImport({kind,globalAdmin,tenantId,tenantName,tenants,onSuccess,onReturnToCollection}:{kind:'locations'|'owners';globalAdmin:boolean;tenantId:string;tenantName?:string;tenants?:TenantItem[]|null;onSuccess:(tenant:string)=>void;onReturnToCollection:()=>void}) {
  const {t}=useTranslation();
  const [open,setOpen]=useState(false),[step,setStep]=useState<0|1|2>(0),[file,setFile]=useState<File|null>(null);
  const [encoding,setEncoding]=useState<PlaceCsvSettings['encoding']>('AUTO'),[delimiter,setDelimiter]=useState('AUTO'),[custom,setCustom]=useState(''),[header,setHeader]=useState(true);
  const [target,setTarget]=useState(''),[parsed,setParsed]=useState<Parsed|null>(null),[mapping,setMapping]=useState<Mapping|null>(null),[selection,setSelection]=useState<Selection|null>(null),[parseError,setParseError]=useState<ParseProblem|null>(null),[parsing,setParsing]=useState(false);
  const [review,setReview]=useState<Review|null>(null),[outcomes,setOutcomes]=useState<Outcome[]|null>(null),[locked,setLocked]=useState(false),[visible,setVisible]=useState(50),[runError,setRunError]=useState('');
  const running=useRef(false),generation=useRef(0),stepHeading=useRef<HTMLHeadingElement>(null);
  const selectedTenant=globalAdmin?target:tenantId;
  const targetLabel=globalAdmin?tenants?.find(item=>item.id===target)?.name:tenantName;
  const table=parsed?.file===file&&parsed.encoding===encoding&&parsed.delimiter===delimiter&&parsed.custom===custom&&parsed.header===header?parsed.table:null;
  const mappedColumn=table&&mapping?.file===file&&sameColumns(mapping.columns,table.columns)?mapping.index:null;
  const rows=table&&mappedColumn!==null?table.rows.map(row=>({record:row.record,line:row.line,name:row.cells[mappedColumn]})):null;
  const eligibleRows=rows?.filter(row=>!blankName(row.name))??[];
  const selectedRecords=table&&mappedColumn!==null&&selection?.table===table&&selection.column===mappedColumn?selection.records:eligibleRows.map(row=>row.record);
  const selectedSet=new Set(selectedRecords),selectedRows=eligibleRows.filter(row=>selectedSet.has(row.record));
  const columnLabel=(value:string,index:number)=>`${t('csv.positionalColumn',{number:index+1})}${header&&value.trim()?` · ${value}`:''}`;
  const delimiterLabel=(value:string|null)=>value===null?t('placeCsv.singleColumn'):value===';'?t('csv.delimiterSEMICOLON'):value===','?t('csv.delimiterCOMMA'):value==='\t'?t('csv.delimiterTAB'):value;
  const invalidate=()=>{generation.current++;setParsed(null);setSelection(null);setParseError(null);setReview(null);setOutcomes(null);setVisible(50);};
  const reset=()=>{invalidate();setFile(null);setMapping(null);setEncoding('AUTO');setDelimiter('AUTO');setCustom('');setHeader(true);setTarget('');setStep(0);setRunError('');};

  useEffect(()=>{if(open)stepHeading.current?.focus();},[open,step]);
  useEffect(()=>{
    const token=++generation.current;
    if(!file){setParsing(false);return;}
    if(delimiter==='CUSTOM'&&Array.from(custom).length!==1){setParsing(false);setParseError({custom:true});return;}
    setParsing(true);
    const selected=delimiter==='AUTO'?'AUTO':delimiter==='CUSTOM'?custom:delimiterValue[delimiter];
    void file.arrayBuffer().then(bytes=>{
      const next=parsePlaceCsvTable(bytes,{encoding,delimiter:selected,header});
      if(token===generation.current){
        setParsed({file,encoding,delimiter,custom,header,table:next});
        setMapping(current=>current?.file===file&&sameColumns(current.columns,next.columns)?current:{file,columns:next.columns,index:initialColumn(next.columns,header)});
        setParseError(null);
      }
    }).catch(cause=>{
      if(token===generation.current){setParsed(null);setParseError(cause instanceof PlaceCsvError?cause:{message:errorMessage(cause)});}
    }).finally(()=>{if(token===generation.current)setParsing(false);});
    return ()=>{generation.current++;};
  },[file,encoding,delimiter,custom,header]);

  const execute=async()=>{
    if(step!==2||!review||!review.rows.length||outcomes||running.current||review.generation!==generation.current||review.tenant!==selectedTenant)return;
    running.current=true;setLocked(true);setRunError('');
    const chosen=review,version=session.contextVersion();
    const result:Outcome[]=[];let successes=0;
    try{
      if(!await confirmAction(t('placeCsv.confirm',{count:chosen.rows.length,tenant:chosen.tenantLabel})))return;
      if(chosen.generation!==generation.current||version!==session.contextVersion()){setRunError(t('placeCsv.contextChanged'));return;}
      setOutcomes([]);
      for(const row of chosen.rows){
        if(version!==session.contextVersion()){setRunError(t('placeCsv.contextChanged'));break;}
        try{
          const created=await session.request<{id:string}>(`/api/v1/tenants/${chosen.tenant}/${kind}`,'POST',{name:row.name,note:null});
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
      if(successes)onSuccess(chosen.tenant);
      if(result.length)setOutcomes([...result]);
      setLocked(false);running.current=false;
    }
  };

  const rawPreview=(limit:number)=>table&&<div className="table-frame"><table className="data-table" aria-label={t('placeCsv.sourcePreview')}><thead><tr><th scope="col">{t('csv.recordNumber')}</th><th scope="col">{t('csv.lineNumber')}</th>{table.columns.map((column,index)=><th scope="col" key={index}>{columnLabel(column,index)}</th>)}</tr></thead><tbody>{table.rows.slice(0,limit).map(row=><tr key={row.record}><th scope="row">{row.record}</th><td>{row.line}</td>{row.cells.map((cell,index)=><td key={index}>{cell||'—'}</td>)}</tr>)}</tbody></table></div>;
  const namePreview=(shownRows:PlaceCsvRow[],limit:number)=><div className="table-frame"><table className="data-table" aria-label={t('placeCsv.previewTitle')}><thead><tr><th scope="col">{t('csv.selectRow')}</th><th scope="col">{t('csv.recordNumber')}</th><th scope="col">{t('csv.lineNumber')}</th><th scope="col">{t('ui.name')}</th><th scope="col">{t('csv.previewStatus')}</th><th scope="col">{t('placeCsv.otherColumns')}</th></tr></thead><tbody>{shownRows.slice(0,limit).map(row=><tr key={row.record}><td><input type="checkbox" aria-label={t('csv.selectNumber',{number:row.record})} disabled={blankName(row.name)||locked} checked={selectedSet.has(row.record)&&!blankName(row.name)} onChange={event=>{if(table&&mappedColumn!==null)setSelection({table,column:mappedColumn,records:event.target.checked?[...selectedRecords,row.record]:selectedRecords.filter(record=>record!==row.record)});}}/></td><th scope="row">{row.record}</th><td>{row.line}</td><td>{row.name||'—'}</td><td>{blankName(row.name)?t('placeCsv.emptyName'):t('csv.statusREADY')}</td><td>{table?.rows[row.record-(header?2:1)]?.cells.flatMap((value,index)=>index===mappedColumn?[]:[`${columnLabel(table.columns[index],index)}: ${value||'—'}`]).slice(0,3).join(' · ')||'—'}</td></tr>)}</tbody></table></div>;
  const steps=['csv.fileSettings','placeCsv.mappingTitle','placeCsv.resultTitle'] as const;
  return <><Button type="button" variant="outline" size="control" className="shrink-0" onClick={()=>{reset();setOpen(true);}}>{t('csv.importTitle')}</Button>
    <Modal open={open} wide canClose={!locked} onOpenChange={value=>{if(!value)reset();setOpen(value);}} title={t('placeCsv.title',{resource:t(kind==='locations'?'ui.location':'ui.owner')})}>
      <div className="space-y-4" data-unsaved={!!file&&(!outcomes||locked)} data-import-executing={locked||undefined}>
        <nav aria-label={t('csv.steps')}><ol className="flex flex-wrap gap-2 text-sm">{steps.map((name,index)=><li key={name} aria-current={step===index?'step':undefined} className={`rounded-md border px-3 py-1.5 ${step===index?'border-primary bg-accent font-semibold text-accent-foreground':'text-muted-foreground'}`}>{index+1}. {t(name)}</li>)}</ol></nav>
        {step===0&&<Card className="space-y-4 p-4"><h2 ref={stepHeading} tabIndex={-1} className="text-lg font-semibold">{t('csv.fileSettings')}</h2>
          <label className="field">{t('fields.tenantName')}{globalAdmin?<select className="input" required value={target} disabled={locked} onChange={event=>{setTarget(event.target.value);setReview(null);setRunError('');}}><option value="">{t('ui.select')}</option>{tenants?.map(item=><option key={item.id} value={item.id}>{item.name} · {item.id}</option>)}</select>:<input className="input" readOnly value={targetLabel??tenantId}/>}</label>
          <label className="field">{t('csv.file')}<input className="input" type="file" accept=".csv,text/csv" disabled={locked} onChange={event=>{invalidate();setFile(event.target.files?.[0]??null);}}/></label>
          {file&&<p className="text-sm text-muted-foreground">{file.name}</p>}
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="field">{t('csv.encoding')}<select className="input" value={encoding} disabled={locked} onChange={event=>{invalidate();setEncoding(event.target.value as PlaceCsvSettings['encoding']);}}>{(['AUTO','UTF-8','WINDOWS-1252','ISO-8859-1'] as const).map(value=><option key={value} value={value}>{value==='AUTO'?t('csv.auto'):value}</option>)}</select></label>
            <label className="field">{t('csv.delimiter')}<select className="input" value={delimiter} disabled={locked} onChange={event=>{invalidate();setDelimiter(event.target.value);}}>{['AUTO','SEMICOLON','COMMA','TAB','CUSTOM'].map(value=><option key={value} value={value}>{t(`csv.delimiter${value}`)}</option>)}</select></label>
            {delimiter==='CUSTOM'&&<label className="field">{t('csv.customDelimiter')}<input className="input" maxLength={1} value={custom} disabled={locked} onChange={event=>{invalidate();setCustom(event.target.value);}}/></label>}
          </div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={header} disabled={locked} onChange={event=>{invalidate();setHeader(event.target.checked);}}/>{t('csv.headerPresent')}</label>
          {parsing&&<p role="status">{t('ui.checking')}</p>}
          {parseError&&<p role="alert" className="error-box">{parseError instanceof PlaceCsvError?t(`placeCsv.${parseError.reason}`,{line:parseError.line??1}):'custom' in parseError?t('csv.customDelimiterRequired'):parseError.message}</p>}
          {table&&<><p role="status" className="text-sm">{t('placeCsv.formatSummary',{count:table.rows.length,columns:table.columns.length,delimiter:delimiterLabel(table.delimiter)})}</p>{rawPreview(10)}{table.rows.length>10&&<p className="text-sm text-muted-foreground">{t('placeCsv.previewSample',{count:10})}</p>}</>}
          <div className="flex justify-end border-t pt-4"><Button type="button" disabled={!table||!selectedTenant||parsing||locked} onClick={()=>{setVisible(50);setStep(1);}}>{t('csv.next')}</Button></div>
        </Card>}
        {step===1&&table&&<Card className="space-y-4 p-4"><h2 ref={stepHeading} tabIndex={-1} className="text-lg font-semibold">{t('placeCsv.mappingTitle')}</h2>
          <label className="field">{t('ui.name')}<select className="input" value={mappedColumn??''} onChange={event=>{setMapping({file:file!,columns:table.columns,index:event.target.value===''?null:Number(event.target.value)});setSelection(null);setReview(null);}}><option value="">{t('ui.select')}</option>{table.columns.map((column,index)=><option key={index} value={index}>{columnLabel(column,index)}</option>)}</select></label>
          <p className="text-sm">{t('fields.tenantName')}: {targetLabel??selectedTenant} · {file?.name}</p>
          {rows?<><div className="flex flex-wrap gap-2"><Badge>{t('placeCsv.totalRows')}: {rows.length}</Badge><Badge>{t('placeCsv.eligibleRows')}: {eligibleRows.length}</Badge><Badge>{t('placeCsv.selectedRows')}: {selectedRows.length}</Badge></div><div className="flex flex-wrap gap-2"><Button type="button" variant="outline" disabled={!eligibleRows.length||selectedRows.length===eligibleRows.length} onClick={()=>setSelection({table,column:mappedColumn!,records:eligibleRows.map(row=>row.record)})}>{t('placeCsv.selectAll')}</Button><Button type="button" variant="outline" disabled={!selectedRows.length} onClick={()=>setSelection({table,column:mappedColumn!,records:[]})}>{t('placeCsv.deselectAll')}</Button></div>{namePreview(rows,visible)}{rows.length>visible&&<Button type="button" variant="outline" onClick={()=>setVisible(value=>value+50)}>{t('csv.showMoreRows',{count:rows.length-visible})}</Button>}</>:<p role="alert" className="error-box">{t('placeCsv.selectNameColumn')}</p>}
          <div className="flex flex-wrap justify-end gap-2 border-t pt-4"><Button type="button" variant="outline" onClick={()=>setStep(0)}>{t('csv.back')}</Button><Button type="button" disabled={!selectedRows.length||!selectedTenant} onClick={()=>{if(!rows||mappedColumn===null||!selectedRows.length)return;setReview({rows:selectedRows,total:rows.length,eligible:eligibleRows.length,tenant:selectedTenant,tenantLabel:targetLabel??selectedTenant,columnLabel:columnLabel(table.columns[mappedColumn],mappedColumn),fileName:file?.name??'',generation:generation.current});setStep(2);}}>{t('csv.next')}</Button></div>
        </Card>}
        {step===2&&review&&<Card className="space-y-4 p-4" role={outcomes?'status':undefined}><h2 ref={stepHeading} tabIndex={-1} className="text-lg font-semibold">{t('placeCsv.resultTitle')}</h2>
          <p>{t('fields.tenantName')}: {review.tenantLabel} · {t('ui.name')}: {review.columnLabel} · {review.fileName}</p>
          {!outcomes&&<><p>{t('placeCsv.reviewCount',{count:review.rows.length})}</p><div className="flex flex-wrap gap-2"><Badge>{t('placeCsv.totalRows')}: {review.total}</Badge><Badge>{t('placeCsv.eligibleRows')}: {review.eligible}</Badge><Badge>{t('placeCsv.selectedRows')}: {review.rows.length}</Badge></div><div className="flex flex-wrap justify-end gap-2 border-t pt-4"><Button type="button" variant="outline" disabled={locked} onClick={()=>setStep(1)}>{t('csv.back')}</Button><Button type="button" aria-busy={locked} disabled={locked||!review.rows.length} onClick={()=>void execute()}>{locked?t('csv.executing'):t('csv.importSelected',{count:review.rows.length})}</Button></div></>}
          {outcomes&&<><p>{t('placeCsv.progress',{done:outcomes.length,total:review.rows.length})}</p>{locked&&<p className="text-sm text-muted-foreground">{t('placeCsv.finishBeforeLeaving')}</p>}<p>{t('placeCsv.result',{successful:outcomes.filter(row=>row.status==='SUCCESS').length,failed:outcomes.filter(row=>row.status==='FAILED').length,unknown:outcomes.filter(row=>row.status==='UNKNOWN').length,remaining:review.rows.length-outcomes.length})}</p><div className="table-frame"><table className="data-table"><thead><tr><th scope="col">{t('csv.recordNumber')}</th><th scope="col">{t('csv.lineNumber')}</th><th scope="col">{t('ui.name')}</th><th scope="col">{t('csv.previewStatus')}</th><th scope="col">{t('csv.issues')}</th></tr></thead><tbody>{outcomes.slice(0,visible).map(row=><tr key={row.record}><th scope="row">{row.record}</th><td>{row.line}</td><td>{row.name||'—'}</td><td>{row.status==='UNKNOWN'?t('placeCsv.statusUnknown'):t(`csv.status${row.status}`)}</td><td>{row.id??row.error??'—'}</td></tr>)}</tbody></table></div>{outcomes.length>visible&&<Button type="button" variant="outline" onClick={()=>setVisible(value=>value+50)}>{t('csv.showMoreRows',{count:outcomes.length-visible})}</Button>}{outcomes.some(row=>row.status==='UNKNOWN')&&<p role="alert" className="error-box">{t('placeCsv.unknown')}</p>}</>}
          {runError&&<p role="alert" className="error-box">{runError}</p>}
          {!locked&&outcomes&&<div className="flex flex-wrap justify-end gap-2 border-t pt-4"><Button type="button" variant="outline" onClick={()=>{onReturnToCollection();reset();setOpen(false);}}>{t(kind==='locations'?'placeCsv.viewLocations':'placeCsv.viewOwners')}</Button><Button type="button" variant="outline" onClick={reset}>{t('csv.newImport')}</Button></div>}
        </Card>}
      </div>
    </Modal></>;
}
