import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { lazy, Suspense, useMemo } from 'react';
import type { Branding } from './branding';
const Authentication=lazy(()=>import('./Authentication').then(module=>({default:module.Authentication})));
import { ConfirmationProvider } from './components/ui/confirmation';
import { ToastProvider } from './components/ui/toast';
export function App({branding}:{branding:Branding}){const router=useMemo(()=>createBrowserRouter([{path:'*',element:<ToastProvider><ConfirmationProvider><Suspense fallback={<div className="min-h-dvh bg-background"/>}><Authentication branding={branding}/></Suspense></ConfirmationProvider></ToastProvider>}]),[branding]);return <RouterProvider router={router}/>;}
