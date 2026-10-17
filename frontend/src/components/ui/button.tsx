import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '../../lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary data-[pointer-restored=true]:outline-none disabled:pointer-events-none disabled:opacity-50',
  { variants: { variant: { default: 'bg-primary text-primary-foreground hover:opacity-90', outline: 'border bg-card hover:bg-muted', ghost: 'hover:bg-muted text-foreground', destructive: 'bg-destructive text-white hover:opacity-90' }, size: { default: 'h-9 px-4 py-2', sm: 'h-8 px-3', icon: 'h-9 w-9', control: 'h-10 px-4', controlIcon: 'h-10 w-10' } }, defaultVariants: { variant: 'default', size: 'default' } },
);

export type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants> & { asChild?: boolean };
export function Button({ className, variant, size, asChild = false, onKeyDown, onBlur, ...props }: ButtonProps) {
  const Component = asChild ? Slot : 'button';
  return <Component onKeyDown={event=>{delete event.currentTarget.dataset.pointerRestored;onKeyDown?.(event);}} onBlur={event=>{delete event.currentTarget.dataset.pointerRestored;onBlur?.(event);}} className={cn(buttonVariants({ variant, size, className }))} {...props} />;
}
