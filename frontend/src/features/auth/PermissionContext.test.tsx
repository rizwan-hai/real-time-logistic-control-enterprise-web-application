import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Can } from './PermissionContext';
import { PermissionRoute } from './ProtectedRoute';
import { useAuthStore } from '@/app/authStore';
import type { ProfileData } from '@/services/apiTypes';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

describe('Can', () => {
  it('hides content without the permission and renders allowed content', () => {
    useAuthStore.setState({ profile: { id: 'user-1', organizationId: null, customerId: null, driverId: null, email: null, phone: null, active: true, totpEnabled: false, roles: [], permissions: ['orders.read'] } as ProfileData });
    const { rerender } = render(<Can permission="users.read">Secret</Can>);
    expect(screen.queryByText('Secret')).not.toBeInTheDocument();
    rerender(<Can permission="orders.read">Orders</Can>);
    expect(screen.getByText('Orders')).toBeInTheDocument();
  });

  it('blocks a route when its permission is missing', () => {
    useAuthStore.setState({ profile: { id: 'user-1', organizationId: null, customerId: null, driverId: null, email: null, phone: null, active: true, totpEnabled: false, roles: [], permissions: [] } as ProfileData });
    render(<MemoryRouter initialEntries={['/restricted']}><Routes>
      <Route path="/403" element={<h1>Denied</h1>} />
      <Route element={<PermissionRoute permission="users.read" />}><Route path="/restricted" element={<h1>Users</h1>} /></Route>
    </Routes></MemoryRouter>);
    expect(screen.getByRole('heading', { name: 'Denied' })).toBeInTheDocument();
  });
});
