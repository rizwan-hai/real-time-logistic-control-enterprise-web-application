import { create } from 'zustand';
import type { ProfileData } from '@/services/apiTypes';

interface AuthState {
  accessToken: string | null;
  profile: ProfileData | null;
  sessionReady: boolean;
  setAccessToken: (token: string | null, ready?: boolean) => void;
  setProfile: (profile: ProfileData | null) => void;
  markSessionReady: () => void;
  clear: () => void;
  hasPermission: (permission: string) => boolean;
  hasRole: (role: string) => boolean;
}

// Access tokens exist in memory only. The refresh token is an HttpOnly cookie.
export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  profile: null,
  sessionReady: false,
  setAccessToken: (accessToken, sessionReady = true) => set({ accessToken, sessionReady }),
  setProfile: (profile) => set({ profile }),
  markSessionReady: () => set({ sessionReady: true }),
  clear: () => set({ accessToken: null, profile: null, sessionReady: true }),
  hasPermission: (permission) => get().profile?.permissions.includes(permission) ?? false,
  hasRole: (role) => get().profile?.roles.includes(role) ?? false,
}));
