import {X} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import type {ReactNode} from 'react';
import {Button} from './button';

export function ClearableControl({name,value,onClear,children,select=false}:{name:string;value:string;onClear?:()=>void;children:ReactNode;select?:boolean}) {
 const {t}=useTranslation();
 return <div className={`relative min-w-0 ${select?'':'flex-1'}`}>{children}{onClear&&value&&<Button data-clear-selection type="button" variant="ghost" size="icon" className={`absolute top-1/2 -translate-y-1/2 ${select?'right-9':'right-1'} h-8 w-8 bg-card/90`} aria-label={t('actions.clearField',{field:name})} title={t('actions.clearField',{field:name})} onClick={event=>{const field=event.currentTarget.parentElement?.querySelector('input,select') as HTMLElement|null;onClear();field?.focus();}}><X size={16} aria-hidden="true"/></Button>}</div>;
}
