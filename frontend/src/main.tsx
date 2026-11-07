import React from 'react';
import { createRoot } from 'react-dom/client';
import { AppProviders } from '@/app/AppProviders';
import { AuthSessionBootstrap } from '@/app/AuthSessionBootstrap';
import { AppRoutes } from '@/routes/AppRoutes';
import './style.css';

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => undefined);
  });
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProviders>
      <AuthSessionBootstrap><AppRoutes /></AuthSessionBootstrap>
    </AppProviders>
  </React.StrictMode>,
);
