import {useEffect,useId,useRef,useState,type KeyboardEvent} from 'react';
import {useTranslation} from 'react-i18next';
import {Button} from './button';

export type Candidate = {id:string;label:string;detail:string};
/** Inline, non-modal listbox: the dialog keeps ownership of focus. */
export function SearchableCombobox({name,text,selected,options,pending,initialLoading,error,page,total,onQuery,onSelect,onPage,excluded,reset=0,clear,required=false}: {
 name:string;text:string;selected:string;options:Candidate[];pending:boolean;initialLoading:boolean;error:string;page:number;total:number;
 onQuery:(text:string)=>void;onSelect:(candidate:Candidate)=>void;onPage:(page:number)=>void;excluded:string;reset?:number;clear?:()=>void;required?:boolean;
}) {
 const {t}=useTranslation(),id=useId(),[open,setOpen]=useState(false),[active,setActive]=useState(''),root=useRef<HTMLDivElement>(null);
 useEffect(()=>{setOpen(false);setActive('');},[reset]);
 useEffect(()=>{root.current?.querySelector('input')?.setCustomValidity(required&&!selected?t('ui.select'):'');},[required,selected,t]);
 // Radix Dialog listens for Escape in document capture. Handle the open
 // inline listbox first, without creating another modal or focus trap.
 useEffect(()=>{if(!open)return;const escape=(event:globalThis.KeyboardEvent)=>{if(event.key==='Escape'&&root.current?.contains(event.target as Node)){event.preventDefault();event.stopPropagation();root.current?.querySelector('input')?.focus();setOpen(false);setActive('');}};window.addEventListener('keydown',escape,true);return()=>window.removeEventListener('keydown',escape,true);},[open,id]);
 const available=pending||error?[]:options.filter(option=>option.id!==excluded);
 const activeOption=available.find(option=>option.id===active);
 useEffect(()=>{if(open&&activeOption)document.getElementById(`${id}-${activeOption.id}`)?.scrollIntoView({block:'nearest'});},[open,id,activeOption?.id]);
 function choose(option:Candidate){if(!pending&&!error&&option.id!==excluded){onSelect(option);setOpen(false);setActive('');}}
 function key(event:KeyboardEvent<HTMLInputElement>){
  if(event.key==='ArrowDown'||event.key==='ArrowUp'){
   event.preventDefault();setOpen(true);const index=available.findIndex(option=>option.id===active);
   setActive(available.length?available[(index+(event.key==='ArrowDown'?1:index<0?0:-1)+available.length)%available.length].id:'');
  }else if(event.key==='Enter'&&open){event.preventDefault();if(activeOption)choose(activeOption);}
  else if(event.key==='Escape'&&open){event.preventDefault();event.stopPropagation();setOpen(false);setActive('');}
  else if(event.key==='Tab'){setOpen(false);setActive('');}
 }
 return <div ref={root} className="relative space-y-1" onBlur={event=>{if(!event.currentTarget.contains(event.relatedTarget)){setOpen(false);setActive('');}}}>
  <label className="text-sm font-medium" htmlFor={id}>{name}</label>
  <input id={id} className="input" role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={`${id}-list`} aria-activedescendant={open&&activeOption?`${id}-${activeOption.id}`:undefined} autoComplete="off" value={text}
   onFocus={event=>{setOpen(true);if(selected)event.currentTarget.select();}} onClick={()=>setOpen(true)} onKeyDown={key}
   onChange={event=>{onQuery(event.target.value);setActive('');setOpen(true);}}/>
  {clear&&(text||selected)&&<Button data-clear-selection type="button" variant="ghost" size="sm" onClick={clear}>{t('mergeCombobox.clearSelection')}</Button>}
  {open&&<div className="relative z-10 mt-1 w-full rounded-md border bg-popover p-1 text-popover-foreground shadow-md">
   {initialLoading&&<p role="status" className="px-2 py-1 text-xs text-muted-foreground">{t('mergeCombobox.searching')}</p>}
   {error?<p role="alert" className="px-2 text-sm text-destructive">{error}</p>:null}
   <ul id={`${id}-list`} role="listbox" aria-label={name} aria-busy={pending} className="max-h-48 overflow-y-auto">
    {options.map(option=><li id={`${id}-${option.id}`} key={option.id} role="option" aria-selected={selected===option.id} aria-disabled={pending||!!error||excluded===option.id}
     className={`rounded px-2 py-2 text-sm ${activeOption?.id===option.id?'bg-accent':''} ${pending||error||excluded===option.id?'opacity-50':'cursor-pointer hover:bg-accent'}`}
     onMouseDown={event=>event.preventDefault()} onMouseMove={()=>{if(!pending&&!error&&excluded!==option.id)setActive(option.id);}} onClick={()=>choose(option)}>
     <span className="block font-medium">{option.label}</span><span className="block text-xs text-muted-foreground">{option.detail}</span>
    </li>)}
   </ul>
   {!initialLoading&&!error&&!options.length&&<p className="px-2 py-2 text-sm">{t('ui.noRecords')}</p>}
   {(total>25||page>0)&&<div className="flex justify-between border-t pt-1">
    <Button type="button" size="sm" variant="ghost" disabled={pending||!!error||page===0} onClick={()=>{root.current?.querySelector('input')?.focus();setActive('');onPage(page-1);}}>{t('ui.previousPage')}</Button>
    <Button type="button" size="sm" variant="ghost" disabled={pending||!!error||(page+1)*25>=total} onClick={()=>{root.current?.querySelector('input')?.focus();setActive('');onPage(page+1);}}>{t('ui.nextPage')}</Button>
   </div>}
  </div>}
 </div>;
}
