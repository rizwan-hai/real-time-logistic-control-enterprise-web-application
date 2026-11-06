import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';
import { useAuthStore } from '@/app/authStore';

describe('protected routing', () => {
  it('redirects guests to login', async () => {
    useAuthStore.setState({ accessToken: null, sessionReady: true });
    render(<MemoryRouter initialEntries={['/app']}><Routes><Route path="/login" element={<h1>Login</h1>} /><Route element={<ProtectedRoute />}><Route path="/app" element={<h1>Private</h1>} /></Route></Routes></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument();
  });
});
