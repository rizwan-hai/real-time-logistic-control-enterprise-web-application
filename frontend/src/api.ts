const base = import.meta.env.VITE_API_BASE ?? '';
let csrf = '';

async function csrfToken(): Promise<string> {
  if (csrf) return csrf;
  const response = await fetch(`${base}/api/v1/auth/csrf`, { credentials: 'include' });
  const json = await response.json();
  csrf = json.data?.token ?? '';
  return csrf;
}

async function send(path: string, method: string, body: unknown, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers['X-XSRF-TOKEN'] = await csrfToken();
  return fetch(`${base}${path}`, {
    method, headers, credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  let access = localStorage.getItem('accessToken');
  let response = await send(path, method, body, access);
  if (response.status === 401 && access && path !== '/api/v1/auth/refresh' && path !== '/api/v1/auth/login') {
    const refresh = await send('/api/v1/auth/refresh', 'POST', undefined, null);
    if (refresh.ok) {
      const refreshed = await refresh.json();
      access = refreshed.data?.accessToken ?? null;
      setAccess(access);
      if (access) response = await send(path, method, body, access);
    } else {
      setAccess(null);
    }
  }
  const json = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(json.detail ?? json.message ?? `Request failed (${response.status})`);
  return json.data as T;
}

export const setAccess = (token: string | null) => token
  ? localStorage.setItem('accessToken', token)
  : localStorage.removeItem('accessToken');
