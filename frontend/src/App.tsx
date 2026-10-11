import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { lazy, Suspense, useMemo } from 'react';
import type { Branding } from './branding';
const Authentication=lazy(()=>import('./Authentication').then(module=>({default:module.Authentication})));
import { ConfirmationProvider } from './components/ui/confirmation';
export function App({branding}:{branding:Branding}){const router=useMemo(()=>createBrowserRouter([{path:'*',element:<ConfirmationProvider><Suspense fallback={<div className="min-h-dvh bg-background"/>}><Authentication branding={branding}/></Suspense></ConfirmationProvider>}]),[branding]);return <RouterProvider router={router}/>;}
