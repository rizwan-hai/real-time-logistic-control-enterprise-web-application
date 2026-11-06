import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/app/authStore';
import { PermissionProvider } from './PermissionContext';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';

export function ProtectedRoute() {
  const token = useAuthStore((state) => state.accessToken);
  const sessionReady = useAuthStore((state) => state.sessionReady);
  const location = useLocation();
  if (!sessionReady) return <LoadingSkeleton rows={2} />;
  return token
    ? <PermissionProvider><Outlet /></PermissionProvider>
    : <Navigate to="/login" replace state={{ from: location.pathname }} />;
}

export function PermissionRoute({ permission }: { permission: string }) {
  const allowed = useAuthStore((state) => state.hasPermission(permission));
  return allowed ? <Outlet /> : <Navigate to="/403" replace />;
}

export function RoleRoute({ roles }: { roles: string[] }) {
  const allowed = useAuthStore((state) => roles.some((role) => state.hasRole(role)));
  return allowed ? <Outlet /> : <Navigate to="/403" replace />;
}
