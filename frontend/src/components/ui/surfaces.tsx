import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/utils';
export function Card({ className, ...p }: HTMLAttributes<HTMLDivElement>) { return <div className={cn('rounded-xl border bg-card text-card-foreground', className)} {...p} />; }
export function CardHeader({ className, ...p }: HTMLAttributes<HTMLDivElement>) { return <div className={cn('p-5 pb-3', className)} {...p} />; }
export function CardContent({ className, ...p }: HTMLAttributes<HTMLDivElement>) { return <div className={cn('p-5 pt-0', className)} {...p} />; }
export function Badge({ className, ...p }: HTMLAttributes<HTMLSpanElement>) { return <span className={cn('inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap',className)} {...p} />; }
export function Skeleton({ className, ...p }: HTMLAttributes<HTMLDivElement>) { return <div className={cn('animate-pulse rounded-md bg-muted',className)} {...p} />; }
