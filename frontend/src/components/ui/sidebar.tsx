import { createContext, useContext, useState, type ReactNode } from 'react';
import { PanelLeft } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Modal } from './dialog';
const Context=createContext({open:false,setOpen:(_v:boolean)=>{},collapsed:false,setCollapsed:(_v:boolean)=>{}});
export function SidebarProvider({children}:{children:ReactNode}) {const[open,setOpen]=useState(false),[collapsed,setCollapsed]=useState(false);return <Context.Provider value={{open,setOpen,collapsed,setCollapsed}}><div className="flex min-h-dvh">{children}</div></Context.Provider>;}
export function useSidebar(){return useContext(Context);}
export function SidebarTrigger(){const {open,setOpen,collapsed,setCollapsed}=useSidebar();const{t}=useTranslation();return <button className="rounded-md p-2 hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring" aria-label={t('redesign.toggleNavigation')} onClick={()=>{if(window.matchMedia('(min-width: 1024px)').matches)setCollapsed(!collapsed);else {setCollapsed(false);setOpen(!open);}}}><PanelLeft size={18}/></button>;}
export function Sidebar({children}:{children:ReactNode}){const{open,setOpen,collapsed}=useSidebar();const{t}=useTranslation();return <><aside className={`sticky top-0 hidden h-dvh shrink-0 border-r bg-sidebar lg:flex lg:flex-col ${collapsed?'w-20':'w-64'}`} data-collapsed={collapsed}>{children}</aside><div className="lg:hidden"><Modal open={open} onOpenChange={setOpen} title="KELIOS" description={t('redesign.navigation')} sheet><aside className="flex min-h-[80dvh] flex-col">{children}</aside></Modal></div></>;}
export function SidebarInset({children}:{children:ReactNode}){return <div className="min-w-0 flex-1">{children}</div>;}
