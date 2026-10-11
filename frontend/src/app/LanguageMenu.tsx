import { ChevronDown, Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { i18n, selectLanguage } from '../i18n';
import { Dropdown, DropdownItem } from '../components/ui/dropdown-menu';
export function LanguageMenu(){const{t}=useTranslation();return <Dropdown label={t('common.language')} trigger={<><span aria-hidden="true">{i18n.language==='de'?'🇩🇪':'🇬🇧'}</span><ChevronDown size={13}/></>}>{(['de','en'] as const).map(l=><DropdownItem key={l} onSelect={()=>void selectLanguage(l)}><span aria-hidden="true">{l==='de'?'🇩🇪':'🇬🇧'}</span>{l==='de'?'Deutsch':'English'}{i18n.language===l&&<Check size={14} className="ml-auto"/>}</DropdownItem>)}</Dropdown>;}
