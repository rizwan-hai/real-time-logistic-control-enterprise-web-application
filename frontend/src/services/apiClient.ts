import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/app/authStore';
import { ApiClientError, mapApiError, type ProblemDetails } from './apiErrors';
import { apiPath } from './apiVersion';
import type { ProfileData, ProfileResponse } from './apiTypes';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 20_000,
  withCredentials: true,
  headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
});

let csrfToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;
let restoreInFlight: Promise<void> | null = null;
const safeMethods = new Set(['get', 'head', 'options']);

async function getCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  const { data } = await axios.get(`${apiBaseUrl}${apiPath('auth/csrf')}`, { withCredentials: true });
  csrfToken = (data?.data?.token as string | undefined) ?? '';
  return csrfToken;
}

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  if (!safeMethods.has((config.method ?? 'get').toLowerCase())) {
    const csrf = await getCsrfToken();
    if (csrf) config.headers['X-XSRF-TOKEN'] = csrf;
  }
  return config;
});

async function silentlyRefresh(sessionReady = true): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const csrf = await getCsrfToken();
      const response = await axios.post(`${apiBaseUrl}${apiPath('auth/refresh')}`, undefined, {
        withCredentials: true,
        headers: csrf ? { 'X-XSRF-TOKEN': csrf } : undefined,
      });
      const token = (response.data?.data?.accessToken as string | undefined) ?? null;
      useAuthStore.getState().setAccessToken(token, sessionReady);
      return token;
    })().catch(() => {
      useAuthStore.getState().clear();
      return null;
    }).finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

export function restoreSession(): Promise<void> {
  if (!restoreInFlight) {
    restoreInFlight = (async () => {
      try {
        const token = await silentlyRefresh(false);
        if (!token) return;
        const response = await apiClient.get<ProfileResponse>(apiPath('auth/profile'));
        useAuthStore.getState().setProfile((response.data as { data: ProfileData }).data);
      } catch {
        useAuthStore.getState().clear();
      } finally {
        useAuthStore.getState().markSessionReady();
      }
    })().finally(() => { restoreInFlight = null; });
  }
  return restoreInFlight;
}

apiClient.interceptors.response.use((response) => response, async (error: AxiosError<ProblemDetails>) => {
  const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  const url = config?.url ?? '';
  if (error.response?.status === 401 && config && !config._retried
    && !url.includes('/auth/login') && !url.includes('/auth/refresh')) {
    config._retried = true;
    const token = await silentlyRefresh();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
      return apiClient.request(config);
    }
  }
  if (error.response) {
    return Promise.reject(mapApiError(error));
  }
  return Promise.reject(new ApiClientError({ detail: 'The API is unavailable. Check your connection and try again.' }, 0));
});

