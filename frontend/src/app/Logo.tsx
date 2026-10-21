import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Branding } from '../branding';
import { Boxes } from 'lucide-react';
export function Logo({branding}:{branding:Branding}){const[failed,setFailed]=useState(false);const{t}=useTranslation();const legacy=!!branding.logoUrl&&!branding.organizationLogoUrl;return <div className="flex h-10 min-w-0 items-center gap-2.5">{legacy&&!failed?<img src={branding.logoUrl} alt={t('ui.keliosLogo')} className="h-9 max-w-40 object-contain" onError={()=>setFailed(true)}/>:<>{branding.organizationLogoUrl&&!failed?<img src={branding.organizationLogoUrl} alt={t('ui.organizationLogo')} className="h-9 max-w-12 object-contain" onError={()=>setFailed(true)}/>:<span aria-hidden="true" className="rounded-lg border bg-muted p-2 text-foreground"><Boxes size={19}/></span>}<span className="font-semibold tracking-tight text-foreground">KELI<span className="text-primary">O</span>S</span></>}</div>;}
