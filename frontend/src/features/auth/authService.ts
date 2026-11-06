import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import type { AuthLoginRequest, AuthLoginResponse, ForgotPasswordRequest, ProfileResponse, ResetPasswordRequest } from '@/services/apiTypes';

export const authService = {
  login: async (body: AuthLoginRequest) => (await apiClient.post<AuthLoginResponse>(apiPath('auth/login'), body)).data,
  profile: async () => (await apiClient.get<ProfileResponse>(apiPath('auth/profile'))).data,
  logout: async () => apiClient.post(apiPath('auth/logout'), {}),
  forgotPassword: async (body: ForgotPasswordRequest) => apiClient.post(apiPath('auth/forgot-password'), body),
  resetPassword: async (body: ResetPasswordRequest) => apiClient.post(apiPath('auth/reset-password'), body),
  setupTotp: async () => apiClient.post(apiPath('auth/totp/setup'), {}),
  enableTotp: async (code: string) => apiClient.post(apiPath('auth/totp/enable'), { code }),
  sessions: async () => apiClient.get(apiPath('auth/sessions')),
  revokeSession: async (id: string) => apiClient.delete(apiPath(`auth/sessions/${id}`)),
};
