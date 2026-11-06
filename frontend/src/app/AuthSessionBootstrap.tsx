import { useEffect, type PropsWithChildren } from 'react';
import { restoreSession } from '@/services/apiClient';
import { useAuthStore } from './authStore';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';

export function AuthSessionBootstrap({ children }: PropsWithChildren) {
  const sessionReady = useAuthStore((state) => state.sessionReady);
  useEffect(() => { void restoreSession(); }, []);
  return sessionReady ? children : <LoadingSkeleton rows={2} />;
}
