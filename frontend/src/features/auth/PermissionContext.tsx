import { createContext, useContext, type PropsWithChildren, type ReactNode } from 'react';
import { useAuthStore } from '@/app/authStore';

const PermissionContext = createContext<((permission: string) => boolean) | null>(null);

export function PermissionProvider({ children }: PropsWithChildren) {
  const can = useAuthStore((state) => state.hasPermission);
  return <PermissionContext.Provider value={can}>{children}</PermissionContext.Provider>;
}

export function Can({ permission, children, fallback = null }: PropsWithChildren<{ permission: string; fallback?: ReactNode }>) {
  const contextualCheck = useContext(PermissionContext);
  const storeCheck = useAuthStore((state) => state.hasPermission);
  return (contextualCheck ?? storeCheck)(permission) ? <>{children}</> : <>{fallback}</>;
}
