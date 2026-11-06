import { render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { AppProviders } from './AppProviders';
import { usePreferencesStore } from './preferencesStore';

afterEach(() => {
  usePreferencesStore.setState({ locale: 'en', colorMode: 'light' });
  document.documentElement.dir = 'ltr';
  document.documentElement.lang = 'en';
  delete document.documentElement.dataset.theme;
});

describe('application preferences', () => {
  it('applies Persian RTL direction to the document', async () => {
    usePreferencesStore.setState({ locale: 'fa' });
    render(<AppProviders><div>content</div></AppProviders>);
    await waitFor(() => expect(document.documentElement.dir).toBe('rtl'));
  });

  it('stores theme changes for the application provider', () => {
    usePreferencesStore.getState().setColorMode('dark');
    expect(usePreferencesStore.getState().colorMode).toBe('dark');
  });

  it('applies dark mode to the document theme', async () => {
    usePreferencesStore.setState({ colorMode: 'dark' });
    render(<AppProviders><div>content</div></AppProviders>);
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'));
  });
});
