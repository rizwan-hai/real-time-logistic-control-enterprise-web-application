import type { paths } from '@/types/api.generated';

type JsonBody<Operation> = Operation extends { responses: infer Responses }
  ? 200 extends keyof Responses
    ? Responses[200] extends { content: infer Content }
      ? Content extends { 'application/json': infer Json } ? Json : unknown
      : unknown
    : unknown
  : unknown;

type JsonRequest<Operation> = Operation extends { requestBody: infer RequestBody }
  ? RequestBody extends { content: infer Content }
    ? Content extends { 'application/json': infer Json } ? Json : never
    : never
  : never;

export type ApiResponseFor<Path extends keyof paths, Method extends keyof paths[Path]> = JsonBody<paths[Path][Method]>;
export type ApiRequestFor<Path extends keyof paths, Method extends keyof paths[Path]> = JsonRequest<paths[Path][Method]>;

export type AuthLoginRequest = ApiRequestFor<'/api/v1/auth/login', 'post'>;
export type AuthLoginResponse = ApiResponseFor<'/api/v1/auth/login', 'post'>;
export type ForgotPasswordRequest = ApiRequestFor<'/api/v1/auth/forgot-password', 'post'>;
export type ResetPasswordRequest = ApiRequestFor<'/api/v1/auth/reset-password', 'post'>;
export type ProfileResponse = ApiResponseFor<'/api/v1/auth/profile', 'get'>;
export type SessionsResponse = ApiResponseFor<'/api/v1/auth/sessions', 'get'>;
export type UsersResponse = ApiResponseFor<'/api/v1/admin/identity/users', 'get'>;
export type RolesResponse = ApiResponseFor<'/api/v1/admin/identity/roles', 'get'>;
export type PermissionsResponse = ApiResponseFor<'/api/v1/admin/identity/permissions', 'get'>;

export type EnvelopeData<Response> = Response extends { data: infer Data } ? Data : never;
export type LoginData = EnvelopeData<AuthLoginResponse>;
export type ProfileData = EnvelopeData<ProfileResponse>;
export type SessionData = EnvelopeData<SessionsResponse>;
export type UsersData = EnvelopeData<UsersResponse>;
export type RolesData = EnvelopeData<RolesResponse>;
export type PermissionsData = EnvelopeData<PermissionsResponse>;
export type CreateUserRequest = ApiRequestFor<'/api/v1/admin/identity/users', 'post'>;
export type UpdateUserRequest = ApiRequestFor<'/api/v1/admin/identity/users/{id}', 'patch'>;
export type CreateRoleRequest = ApiRequestFor<'/api/v1/admin/identity/roles', 'post'>;
export type UpdateRolePermissionsRequest = ApiRequestFor<'/api/v1/admin/identity/roles/{id}/permissions', 'put'>;
export type TotpSetupResponse = ApiResponseFor<'/api/v1/auth/totp/setup', 'post'>;
export type TotpEnableResponse = ApiResponseFor<'/api/v1/auth/totp/enable', 'post'>;
export type TotpEnableRequest = ApiRequestFor<'/api/v1/auth/totp/enable', 'post'>;
export type TotpSetupData = EnvelopeData<TotpSetupResponse>;
export type TotpEnableData = EnvelopeData<TotpEnableResponse>;
export type SessionView = SessionData extends readonly (infer Item)[] ? Item : never;
export type ManagedUserView = UsersData extends readonly (infer Item)[] ? Item : never;
export type RoleView = RolesData extends readonly (infer Item)[] ? Item : never;
export type PermissionView = PermissionsData extends readonly (infer Item)[] ? Item : never;
