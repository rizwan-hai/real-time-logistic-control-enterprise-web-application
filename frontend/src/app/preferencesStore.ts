import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { Locale } from '@/i18n';

export type ColorMode = 'light' | 'dark';
interface PreferencesState {
  colorMode: ColorMode;
  locale: Locale;
  sidebarCollapsed: boolean;
  setColorMode: (mode: ColorMode) => void;
  setLocale: (locale: Locale) => void;
  toggleSidebar: () => void;
}

export const usePreferencesStore = create<PreferencesState>()(persist((set) => ({
  colorMode: 'light',
  locale: 'en',
  sidebarCollapsed: false,
  setColorMode: (colorMode) => set({ colorMode }),
  setLocale: (locale) => set({ locale }),
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
}), { name: 'logistics-preferences', storage: createJSONStorage(() => localStorage) }));
