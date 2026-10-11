import * as Alert from '@radix-ui/react-alert-dialog';
import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './button';
let ask: ((message:string)=>Promise<boolean>) | null=null;
export function confirmAction(message:string):Promise<boolean> { return ask?ask(message):Promise.resolve(false); }
export function ConfirmationProvider({children}:{children:ReactNode}) { const {t}=useTranslation();const [request,setRequest]=useState<{message:string;resolve:(v:boolean)=>void}|null>(null);
 useEffect(()=>{ask=message=>new Promise(resolve=>setRequest({message,resolve}));return()=>{ask=null;};},[]);
 const answer=(v:boolean)=>{request?.resolve(v);setRequest(null);};
 return <>{children}<Alert.Root open={!!request} onOpenChange={v=>{if(!v)answer(false);}}><Alert.Portal><Alert.Overlay className="fixed inset-0 z-[70] bg-black/35"/><Alert.Content className="fixed left-1/2 top-1/2 z-[70] w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border bg-card p-6 shadow-lg"><Alert.Title className="text-lg font-semibold">{t('redesign.confirmTitle')}</Alert.Title><Alert.Description className="mt-2 text-sm text-muted-foreground">{request?.message}</Alert.Description><div className="mt-6 flex justify-end gap-2"><Alert.Cancel asChild><Button variant="outline" onClick={()=>answer(false)}>{t('redesign.cancel')}</Button></Alert.Cancel><Alert.Action asChild><Button onClick={()=>answer(true)}>{t('redesign.confirm')}</Button></Alert.Action></div></Alert.Content></Alert.Portal></Alert.Root></>;
}
