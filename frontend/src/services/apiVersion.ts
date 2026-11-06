const configuredVersion = import.meta.env.VITE_API_VERSION ?? 'v1';
export const API_VERSION = configuredVersion.replace(/^\/?api\/?/, '').replace(/^\/?/, '');
export const apiPath = (path: string): string => `/api/${API_VERSION}/${path.replace(/^\//, '')}`;
