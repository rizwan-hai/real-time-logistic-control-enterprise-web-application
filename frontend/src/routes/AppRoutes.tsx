import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';
import { ErrorBoundary } from '@/shared/components/ErrorBoundary';
import { ProtectedRoute, PermissionRoute, RoleRoute } from '@/features/auth/ProtectedRoute';
import { AccessDeniedPage, ForgotPasswordPage, LoginPage, ResetPasswordPage } from '@/features/auth/AuthPages';
import { AppShell } from '@/layouts/AppShell';
import { useAuthStore } from '@/app/authStore';
import {
  CustomerPortalPage, DriverWorkspacePage, PermissionMatrixPage, ProfilePage, PublicTrackingPage,
  RoleManagementPage, SessionsPage, ShipmentTrackingPlaceholder, StaffOverviewPage,
  UserManagementPage, WorkspaceModulePage,
} from '@/features/workspaces/WorkspacePages';

const staffRoles = ['PLATFORM_ADMIN', 'ORGANIZATION_ADMIN', 'OPERATIONS_MANAGER', 'ORDER_CLERK', 'TRANSPORT_DISPATCHER', 'WAREHOUSE_OPERATOR'];

function WorkspaceHomeRedirect() {
  const hasRole = useAuthStore((state) => state.hasRole);
  return <Navigate to={hasRole('CUSTOMER_USER') ? '/app/customer' : hasRole('DRIVER') ? '/app/driver' : '/app/staff'} replace />;
}

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/403', element: <AccessDeniedPage /> },
  { path: '/tracking', element: <PublicTrackingPage /> },
  { path: '/tracking/:trackingCode', element: <ShipmentTrackingPlaceholder /> },
  {
    element: <ProtectedRoute />,
    children: [{
      path: '/app',
      element: <AppShell />,
      children: [
        { index: true, element: <WorkspaceHomeRedirect /> },
        { path: 'profile', element: <ProfilePage /> },
        { path: 'sessions', element: <SessionsPage /> },
        { path: 'settings', element: <WorkspaceModulePage title="Settings" /> },
        { path: 'notifications', element: <WorkspaceModulePage title="Notifications" /> },
        {
          element: <RoleRoute roles={staffRoles} />,
          children: [
            { path: 'staff', element: <StaffOverviewPage /> },
            { path: 'staff/orders', element: <WorkspaceModulePage title="Orders" /> },
            { path: 'staff/shipments', element: <WorkspaceModulePage title="Shipments" /> },
            { path: 'staff/dispatch', element: <WorkspaceModulePage title="Dispatch" /> },
            { path: 'staff/warehouse', element: <WorkspaceModulePage title="Warehouse" /> },
            { element: <PermissionRoute permission="identity.users.read" />, children: [{ path: 'staff/users', element: <UserManagementPage /> }] },
            { element: <PermissionRoute permission="identity.roles.read" />, children: [{ path: 'staff/roles', element: <RoleManagementPage /> }] },
            { element: <PermissionRoute permission="identity.permissions.read" />, children: [{ path: 'staff/permissions', element: <PermissionMatrixPage /> }] },
          ],
        },
        { element: <RoleRoute roles={['CUSTOMER_USER']} />, children: [{ path: 'customer', element: <CustomerPortalPage /> }] },
        { element: <RoleRoute roles={['DRIVER']} />, children: [{ path: 'driver', element: <DriverWorkspacePage /> }] },
        { path: '*', element: <Navigate to="/403" replace /> },
      ],
    }],
  },
  { path: '*', element: <Navigate to="/app" replace /> },
]);

export function AppRoutes() {
  return <ErrorBoundary><RouterProvider router={router} /></ErrorBoundary>;
}
