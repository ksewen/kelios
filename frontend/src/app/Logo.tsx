import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Branding } from '../branding';
import { Boxes } from 'lucide-react';
export function Logo({branding,compact=false}:{branding:Branding;compact?:boolean}){const[failed,setFailed]=useState(false);const{t}=useTranslation();return <div className="flex h-10 min-w-0 items-center gap-2.5">{branding.logoUrl&&!failed?<img src={branding.logoUrl} alt={t('ui.keliosLogo')} className={compact?'h-8 w-10 object-contain':'h-9 max-w-40 object-contain'} onError={()=>setFailed(true)}/>:<><span className="rounded-lg bg-primary p-2 text-primary-foreground"><Boxes size={19}/></span>{!compact&&<span className="font-semibold tracking-tight">KELIOS</span>}</>}</div>;}
