import { errorMessage } from './i18n';
import { useTranslation } from 'react-i18next';
import { lazy, Suspense, useEffect, useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { authSession as session, type Identity } from './auth';
import { Button } from './components/ui/button';
import { Card, Skeleton } from './components/ui/surfaces';
import { LanguageMenu } from './app/LanguageMenu';
import { Logo } from './app/Logo';
import { OrganizationPicker } from './app/OrganizationPicker';
const Shell=lazy(()=>import('./app/Shell').then(module=>({default:module.Shell})));
import type { Branding } from './branding';
import { ShieldCheck, ArrowRight, LoaderCircle } from 'lucide-react';
export function Authentication({branding}:{branding:Branding}) {
 const {t}=useTranslation();const location=useLocation();const[user,setUser]=useState<Identity|null>(null),[busy,setBusy]=useState(true),[loading,setLoading]=useState(true),[message,setMessage]=useState(''),[login,setLogin]=useState(''),[password,setPassword]=useState(''),[organizations,setOrganizations]=useState(false);
 const tenant=/^\/login\/tenant\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i.exec(location.pathname)?.[1];
 useEffect(()=>session.subscribe(setUser),[]);
 useEffect(()=>{let alive=true;if(location.pathname==='/auth/failed'){setMessage('ui.tenantFailure');setBusy(false);setLoading(false);return;}void session.refresh().then(identity=>{if(alive){setUser(identity);setBusy(false);setLoading(false);}});return()=>{alive=false;};},[]);
 async function local(event:FormEvent){event.preventDefault();setBusy(true);setMessage('');try{setUser(await session.login(login,password));setPassword('');}catch(error){setMessage(error&&typeof error==='object'&&'code'in error?String(error.code):'REQUEST_FAILED');setPassword('');}finally{setBusy(false);}}
 async function oidc(selected: string){setBusy(true);setMessage('');try{window.location.assign(await session.startTenant(selected));}catch(error){setMessage(error&&typeof error==='object'&&'code'in error?String(error.code):'REQUEST_FAILED');setBusy(false);}}
 async function logout(){setBusy(true);setUser(null);setMessage('');try{await session.logout();}catch(error){setMessage(error&&typeof error==='object'&&'code'in error?String(error.code):'REQUEST_FAILED');}finally{setBusy(false);}}
 async function refresh(){setBusy(true);setUser(await session.refresh());setBusy(false);}
 if(loading)return <div className="flex min-h-dvh items-center justify-center bg-background"><Skeleton className="h-48 w-80" aria-label={t('redesign.loading')}/></div>;
 if(user?.status==='ACTIVE')return <Suspense fallback={<div className="min-h-dvh bg-background p-6"><Skeleton className="h-72"/></div>}><Shell user={user} branding={branding} logout={()=>void logout()}/></Suspense>;
 return <div className="relative flex min-h-dvh items-center justify-center bg-background p-6"><div className="absolute right-5 top-5"><LanguageMenu/></div><div className="w-full max-w-sm"><div className="mb-8 flex justify-center"><Logo branding={branding}/></div>{user?<Card className="p-6"><ShieldCheck className="mb-4 text-primary" size={28}/><h1 className="text-xl font-semibold">{t('redesign.pendingTitle')}</h1><p className="mt-3 text-sm leading-relaxed text-muted-foreground">{t('ui.pendingHelp')}</p><div className="mt-6 flex flex-wrap gap-2"><Button disabled={busy} onClick={refresh}>{t('ui.refreshAccountStatus')}</Button><Button variant="outline" disabled={busy} onClick={()=>void logout()}>{t('ui.signOut')}</Button></div></Card>:<><form onSubmit={local} className="space-y-5" aria-label={t('ui.globalLogin')}><label className="field">{t('redesign.username')}<input className="input" value={login} onChange={e=>setLogin(e.target.value)} autoComplete="username" required maxLength={64}/></label><label className="field">{t('ui.password')}<input type="password" className="input" value={password} onChange={e=>setPassword(e.target.value)} autoComplete="current-password" required/></label><Button className="w-full" disabled={busy} type="submit">{busy?<LoaderCircle size={16} className="animate-spin"/>:null}{t('ui.signIn')}<ArrowRight size={15}/></Button></form><div className="my-5 h-px bg-border"/><Button className="w-full" variant="outline" disabled={busy} type="button" onClick={()=>{setMessage('');if(tenant)void oidc(tenant);else setOrganizations(true);}}>{t('ui.signInWithIServ')}</Button></>}<OrganizationPicker open={organizations} onOpenChange={setOrganizations} busy={busy} failure={message} select={id=>void oidc(id)}/>{message&&<p role="alert" className="error-box mt-5">{message.startsWith('ui.')?t(message):errorMessage(message)}</p>}</div></div>;
}
