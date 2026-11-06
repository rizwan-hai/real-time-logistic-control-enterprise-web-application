import { useEffect, type PropsWithChildren } from 'react';
import { ConfigProvider, theme } from 'antd';
import enUS from 'antd/locale/en_US';
import faIR from 'antd/locale/fa_IR';
import { QueryClientProvider } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import '@/i18n/config';
import { queryClient } from './queryClient';
import { usePreferencesStore } from './preferencesStore';

export function AppProviders({ children }: PropsWithChildren) {
  const { i18n } = useTranslation();
  const { colorMode, locale } = usePreferencesStore();
  const direction = locale === 'fa' ? 'rtl' : 'ltr';

  useEffect(() => {
    void i18n.changeLanguage(locale);
    document.documentElement.lang = locale;
    document.documentElement.dir = direction;
    document.documentElement.dataset.theme = colorMode;
  }, [colorMode, direction, i18n, locale]);

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        direction={direction}
        locale={locale === 'fa' ? faIR : enUS}
        theme={{ algorithm: colorMode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm, token: { colorPrimary: '#147d78', borderRadius: 10 } }}
      >
        {children}
      </ConfigProvider>
    </QueryClientProvider>
  );
}
