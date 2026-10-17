import {createContext,useCallback,useContext,useEffect,useRef,useState,type ReactNode} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {X} from 'lucide-react';
import {Button} from './button';

export const TOAST_LIFETIME_MS = 4500;
const MAX_VISIBLE_TOASTS = 3;
type Toast = {id:number;message:string};
const ToastContext=createContext<((message:string)=>void)|null>(null);

export function useToast() {
 const notify=useContext(ToastContext);
 if(!notify)throw new Error('ToastProvider is required');
 return notify;
}

function ToastItem({toast,remove}:{toast:Toast;remove:(id:number)=>void}) {
 const {t}=useTranslation();
 useEffect(()=>{const timer=window.setTimeout(()=>remove(toast.id),TOAST_LIFETIME_MS);return()=>window.clearTimeout(timer);},[toast.id,remove]);
 return <li role="status" aria-live="polite" className="pointer-events-auto flex items-start gap-3 rounded-lg border bg-card px-4 py-3 text-sm text-foreground shadow-lg">
  <span className="min-w-0 flex-1 break-words">{toast.message}</span>
  <Button type="button" variant="ghost" size="icon" className="-mr-2 -mt-1 h-8 w-8 shrink-0" aria-label={t('redesign.close')} onClick={()=>remove(toast.id)}><X size={16} aria-hidden="true"/></Button>
 </li>;
}

export function ToastProvider({children}:{children:ReactNode}) {
 const [toasts,setToasts]=useState<Toast[]>([]);
 const nextId=useRef(0);
 const remove=useCallback((id:number)=>setToasts(current=>current.filter(toast=>toast.id!==id)),[]);
 const notify=useCallback((message:string)=>{
  const id=nextId.current++;
  setToasts(current=>[...current,{id,message}].slice(-MAX_VISIBLE_TOASTS));
 },[]);
 return <ToastContext.Provider value={notify}>{children}{createPortal(<ol className="pointer-events-none fixed inset-x-4 bottom-4 z-40 flex flex-col gap-2 sm:inset-x-auto sm:right-6 sm:w-[min(24rem,calc(100vw-3rem))]">{toasts.map(toast=><ToastItem key={toast.id} toast={toast} remove={remove}/>)}</ol>,document.body)}</ToastContext.Provider>;
}
