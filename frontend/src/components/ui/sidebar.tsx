import { createContext, useContext, useState, type ReactNode } from 'react';
import { PanelLeft, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Modal } from './dialog';
const Context=createContext({open:false,setOpen:(_v:boolean)=>{},collapsed:false,setCollapsed:(_v:boolean)=>{}});
export function SidebarProvider({children}:{children:ReactNode}) {const[open,setOpen]=useState(false),[collapsed,setCollapsed]=useState(false);return <Context.Provider value={{open,setOpen,collapsed,setCollapsed}}><div className="flex min-h-dvh flex-col">{children}</div></Context.Provider>;}
export function useSidebar(){return useContext(Context);}
export function SidebarTrigger(){
 const {open,setOpen, setCollapsed}=useSidebar();const{t}=useTranslation();
 return <button type="button" className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-md hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring lg:hidden" aria-label={t('redesign.toggleNavigation')} aria-expanded={open} aria-controls="mobile-navigation" onClick={()=>{setCollapsed(false);setOpen(!open);}}><PanelLeft size={18}/></button>;
}
export function SidebarCollapseTrigger(){
 const {collapsed,setCollapsed}=useSidebar();const{t}=useTranslation();
 const action=t(collapsed?'redesign.expandSidebar':'redesign.collapseSidebar');const Icon=collapsed?PanelLeftOpen:PanelLeftClose;
 return <button type="button" className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring" aria-label={action} aria-expanded={!collapsed} aria-controls="desktop-navigation" onClick={()=>setCollapsed(!collapsed)}><Icon size={18}/></button>;
}
export function Sidebar({children,mobileChildren=children}:{children:ReactNode;mobileChildren?:ReactNode}){const{open,setOpen,collapsed}=useSidebar();const{t}=useTranslation();return <><aside id="desktop-navigation" className={`sticky top-16 hidden h-[calc(100dvh-4rem)] shrink-0 border-r bg-sidebar lg:flex lg:flex-col ${collapsed?'w-16':'w-52 xl:w-56'}`} data-collapsed={collapsed}>{children}</aside><div className="lg:hidden"><Modal open={open} onOpenChange={setOpen} title="KELIOS" description={t('redesign.navigation')} sheet><aside id="mobile-navigation" className="flex min-h-[80dvh] flex-col">{mobileChildren}</aside></Modal></div></>;}
export function SidebarInset({children}:{children:ReactNode}){return <div className="min-w-0 flex-1">{children}</div>;}
