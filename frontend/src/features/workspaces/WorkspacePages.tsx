import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Checkbox, Col, Form, Input, Row, Select, Space, Statistic, Typography } from 'antd';
import { zodResolver } from '@hookform/resolvers/zod';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { authService } from '@/features/auth/authService';
import { useAuthStore } from '@/app/authStore';
import { DataTable } from '@/shared/components/DataTable';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';
import { StatusTag } from '@/shared/components/StatusTag';
import { AnalyticsDashboard } from '@/features/analytics/AnalyticsDashboard';
import { FormField } from '@/shared/components/FormField';
import { useConfirmDialog } from '@/shared/components/ConfirmDialog';
import type {
  CreateRoleRequest, CreateUserRequest, ManagedUserView, PermissionView, PermissionsData, PermissionsResponse,
  ProfileData, RoleView, RolesData, RolesResponse, SessionView,
  TotpEnableData, TotpSetupData, TotpSetupResponse, UpdateRolePermissionsRequest, UsersData, UsersResponse,
} from '@/services/apiTypes';

function dataOf<T>(response: unknown): T { return (response as { data: T }).data; }

function PageTitle({ title, description }: { title: string; description?: string }) {
  return <div className="page-title"><Typography.Title level={2}>{title}</Typography.Title>{description && <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>}</div>;
}

export function StaffOverviewPage() {
  return <AnalyticsDashboard />;
}

export function WorkspaceModulePage({ title, description }: { title: string; description?: string }) {
  return <><PageTitle title={title} description={description} /><Card><EmptyState /></Card></>;
}

export function CustomerPortalPage() {
  return <Navigate to="/portal" replace />;
}

export function DriverWorkspacePage() {
  const { t } = useTranslation();
  const [trip, setTrip] = useState<any>(null);
  const [pending, setPending] = useState(false);
  const [otp, setOtp] = useState('');
  const [gps, setGps] = useState<{ lat: number; lng: number } | null>(null);
  const [offlineQueue, setOfflineQueue] = useState<number>(0);

  useEffect(() => {
    const saved = localStorage.getItem('driver-trip');
    if (saved) setTrip(JSON.parse(saved));
    setOfflineQueue(Number(localStorage.getItem('driver-offline-queue-count') ?? '0'));
    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition((position) => setGps({ lat: position.coords.latitude, lng: position.coords.longitude }), () => undefined, { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 });
    }
  }, []);

  async function markState(status: string) {
    const payload = { status, timestamp: new Date().toISOString(), gps, source: 'driver-pwa' };
    setPending(true);
    try {
      const queue = Number(localStorage.getItem('driver-offline-queue-count') ?? '0');
      if (navigator.onLine) {
        setOfflineQueue(0);
        localStorage.setItem('driver-offline-queue-count', '0');
      } else {
        localStorage.setItem('driver-offline-queue-count', String(queue + 1));
        setOfflineQueue(queue + 1);
      }
      localStorage.setItem('driver-trip', JSON.stringify({ ...trip, status, lastUpdate: payload.timestamp }));
      setTrip((current: any) => ({ ...current, status, lastUpdate: payload.timestamp }));
    } finally { setPending(false); }
  }

  return <>
    <PageTitle title={t('driver')} description={t('driverDescription', 'Assigned trips, deliveries, and proof of delivery.')} />
    <Row gutter={[16, 16]}>
      <Col xs={24} md={8}><Card title="Trip status"><Typography.Text>Assigned trip: {trip?.tripNumber ?? 'TRP-1001'}</Typography.Text><br /><Typography.Text>Status: {trip?.status ?? 'ASSIGNED'}</Typography.Text><br /><Typography.Text>GPS: {gps ? `${gps.lat.toFixed(5)}, ${gps.lng.toFixed(5)}` : 'Waiting for location'}</Typography.Text></Card></Col>
      <Col xs={24} md={8}><Card title="Delivery actions"><Space direction="vertical"><Button onClick={() => markState('ARRIVED')} loading={pending}>Start / arrive</Button><Button onClick={() => markState('DELIVERED')} loading={pending}>Complete delivery</Button><Button onClick={() => setOtp(String(Math.floor(100000 + Math.random() * 900000)))}>Generate OTP</Button></Space></Card></Col>
      <Col xs={24} md={8}><Card title="Offline queue"><Statistic title="Sync queue" value={offlineQueue} /><Typography.Text>OTP: {otp || 'Not generated'}</Typography.Text></Card></Col>
    </Row>
    <Card className="section-card" title="Proof of delivery">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Input placeholder="Recipient name" />
        <Input.TextArea placeholder="Delivery notes" rows={3} />
        <Input placeholder="OTP confirmation" value={otp} onChange={(e) => setOtp(e.target.value)} />
        <Button type="primary">Capture proof</Button>
      </Space>
    </Card>
  </>;
}

export function PublicTrackingPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [trackingCode, setTrackingCode] = useState(searchParams.get('code') ?? '');
  const [submitted, setSubmitted] = useState(false);
  return <main className="auth-page public-track-page"><Card className="auth-card" bordered={false}>
    <Typography.Title level={2}>{t('publicTrackingTitle')}</Typography.Title>
    <Form layout="vertical" onFinish={() => setSubmitted(true)}>
      <Form.Item label={t('trackingCode')} required><Input value={trackingCode} onChange={(event) => setTrackingCode(event.target.value)} /></Form.Item>
      <Button type="primary" htmlType="submit" disabled={!trackingCode.trim()}>{t('track', 'Track')}</Button>
    </Form>
    {submitted && trackingCode.trim() && <PublicTrackingDetails token={trackingCode.trim()} />}
  </Card></main>;
}

export function ProfilePage() {
  const { t } = useTranslation();
  const profileQuery = useQuery({ queryKey: ['identity', 'profile'], queryFn: async () => dataOf<ProfileData>((await authService.profile())) });
  const [setup, setSetup] = useState<TotpSetupData | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const setupMutation = useMutation({ mutationFn: async () => dataOf<TotpSetupData>((await apiClient.post<TotpSetupResponse>(apiPath('auth/totp/setup'), {})).data), onSuccess: setSetup });
  const enableMutation = useMutation({ mutationFn: async (code: string) => dataOf<TotpEnableData>((await authService.enableTotp(code)).data), onSuccess: (result) => {
    setRecoveryCodes(result.codes ?? []); setSetup(null); void profileQuery.refetch();
  } });

  if (profileQuery.isLoading) return <LoadingSkeleton />;
  if (profileQuery.isError || !profileQuery.data) return <ErrorState message={profileQuery.error instanceof Error ? profileQuery.error.message : undefined} onRetry={() => void profileQuery.refetch()} />;
  const profile = profileQuery.data as NonNullable<ProfileData>;
  const setupData = setup;
  return <>
    <PageTitle title={t('profile')} />
    <Row gutter={[16, 16]}><Col xs={24} lg={12}><Card title={t('accountDetails', 'Account details')}>
      <p><Typography.Text type="secondary">{t('emailOrPhone')}</Typography.Text><br /><Typography.Text strong>{profile.email ?? profile.phone ?? '—'}</Typography.Text></p>
      <p><Typography.Text type="secondary">{t('roles')}</Typography.Text><br /><Typography.Text>{profile.roles?.join(', ') || '—'}</Typography.Text></p>
      <p><Typography.Text type="secondary">{t('twoFactor', 'Two-factor authentication')}</Typography.Text><br /><StatusTag status={profile.totpEnabled ? 'active' : 'inactive'} /></p>
      {!profile.totpEnabled && !setupData && <Button type="primary" onClick={() => setupMutation.mutate()} loading={setupMutation.isPending}>{t('enableTwoFactor', 'Enable two-factor authentication')}</Button>}
      {setupData && <Form layout="vertical" onFinish={() => enableMutation.mutate(totpCode)}>
        <Alert type="info" message={`${t('authenticatorSecret', 'Add this secret to your authenticator')}: ${setupData.secret ?? ''}`} />
        <Form.Item label={t('verificationCode', 'Verification code')} required><Input value={totpCode} onChange={(event) => setTotpCode(event.target.value)} /></Form.Item>
        <Button type="primary" htmlType="submit" loading={enableMutation.isPending}>{t('verifyEnable', 'Verify and enable')}</Button>
      </Form>}
      {recoveryCodes.length > 0 && <Alert className="section-card" type="warning" showIcon message={t('saveRecoveryCodes', 'Save these recovery codes securely. They are shown once.')} description={recoveryCodes.join(' · ')} />}
    </Card></Col><Col xs={24} lg={12}><Card title={t('accountSecurity', 'Account security')}><Typography.Paragraph>{t('securityDescription', 'Use a strong password and review your active sessions regularly.')}</Typography.Paragraph><Button href="/app/sessions">{t('manageSessions', 'Manage sessions')}</Button></Card></Col></Row>
  </>;
}

export function SessionsPage() {
  const { t } = useTranslation();
  const confirm = useConfirmDialog();
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['identity', 'sessions'], queryFn: async () => dataOf<SessionView[]>((await authService.sessions()).data) });
  const revoke = useMutation({ mutationFn: authService.revokeSession, onSuccess: () => client.invalidateQueries({ queryKey: ['identity', 'sessions'] }) });
  if (query.isLoading) return <LoadingSkeleton />;
  if (query.isError) return <ErrorState message={query.error.message} onRetry={() => void query.refetch()} />;
  const sessions = query.data ?? [];
  return <><PageTitle title={t('sessions', 'Sessions')} description={t('sessionDescription', 'Review devices that are signed in to your account.')} />
    {sessions.length === 0 ? <Card><EmptyState /></Card> : <DataTable<SessionView> dataSource={sessions} columns={[
      { title: t('device', 'Device'), dataIndex: 'deviceName' }, { title: t('ipAddress', 'IP address'), dataIndex: 'ipAddress' },
      { title: t('lastActive', 'Last active'), dataIndex: 'lastSeenAt', render: (value: string) => value ? new Date(value).toLocaleString() : '—' },
      { title: t('expires', 'Expires'), dataIndex: 'expiresAt', render: (value: string) => value ? new Date(value).toLocaleString() : '—' },
      { title: '', key: 'action', render: (_, session) => session.current ? <StatusTag status="active" /> : <Button danger onClick={() => confirm(t('revokeSession', 'Revoke session?'), session.deviceName ?? '', () => revoke.mutateAsync(session.id))}>{t('revoke', 'Revoke')}</Button> },
    ]} />}
  </>;
}

const createUserSchema = z.object({ email: z.email(), password: z.string().min(12) });
type CreateUserValues = z.infer<typeof createUserSchema>;

export function UserManagementPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const methods = useForm<CreateUserValues>({ resolver: zodResolver(createUserSchema) });
  const usersQuery = useQuery({ queryKey: ['identity', 'users'], queryFn: async () => dataOf<UsersData>((await apiClient.get<UsersResponse>(apiPath('admin/identity/users'))).data) });
  const createUser = useMutation({ mutationFn: (body: CreateUserRequest) => apiClient.post(apiPath('admin/identity/users'), body), onSuccess: () => { methods.reset(); void client.invalidateQueries({ queryKey: ['identity', 'users'] }); } });
  const updateUser = useMutation({ mutationFn: ({ id, body }: { id: string; body: NonNullable<import('@/services/apiTypes').UpdateUserRequest> }) => apiClient.patch(apiPath(`admin/identity/users/${id}`), body), onSuccess: () => void client.invalidateQueries({ queryKey: ['identity', 'users'] }) });

  if (usersQuery.isLoading) return <LoadingSkeleton />;
  if (usersQuery.isError) return <ErrorState message={usersQuery.error.message} onRetry={() => void usersQuery.refetch()} />;
  const users = (usersQuery.data ?? []) as ManagedUserView[];
  return <>
    <PageTitle title={t('users')} description={t('userAdminDescription', 'Manage accounts within your organization.')} />
    <Card title={t('createUser', 'Create user')} className="section-card">
      <FormProvider {...methods}><form className="form-grid" onSubmit={methods.handleSubmit((values) => createUser.mutate({ email: values.email, password: values.password, roleIds: [], warehouseIds: [] } as CreateUserRequest))}>
        <FormField<CreateUserValues> name="email" label={t('email', 'Email')} type="email" />
        <FormField<CreateUserValues> name="password" label={t('temporaryPassword', 'Temporary password')} type="password" />
        <Button type="primary" htmlType="submit" loading={createUser.isPending}>{t('createUser', 'Create user')}</Button>
      </form></FormProvider>
      {createUser.isError && <Alert className="section-card" type="error" showIcon message={createUser.error.message} />}
    </Card>
    <DataTable<ManagedUserView> dataSource={users} columns={[
      { title: t('emailOrPhone'), render: (_, user) => user.email ?? user.phone ?? '—' },
      { title: t('status', 'Status'), dataIndex: 'active', render: (active: boolean) => <StatusTag status={active ? 'active' : 'inactive'} /> },
      { title: t('roles'), render: (_, user) => user.roles?.join(', ') || '—' },
      { title: '', render: (_, user) => <Button onClick={() => updateUser.mutate({ id: user.id, body: { active: !user.active } })}>{user.active ? t('deactivate', 'Deactivate') : t('activate', 'Activate')}</Button> },
    ]} />
  </>;
}

const createRoleSchema = z.object({ roleKey: z.string().trim().min(2).max(80), displayName: z.string().trim().min(2).max(120) });
type CreateRoleValues = z.infer<typeof createRoleSchema>;

export function RoleManagementPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const methods = useForm<CreateRoleValues>({ resolver: zodResolver(createRoleSchema) });
  const query = useQuery({ queryKey: ['identity', 'roles'], queryFn: async () => dataOf<RolesData>((await apiClient.get<RolesResponse>(apiPath('admin/identity/roles'))).data) });
  const create = useMutation({ mutationFn: (body: CreateRoleRequest) => apiClient.post(apiPath('admin/identity/roles'), body), onSuccess: () => { methods.reset(); void client.invalidateQueries({ queryKey: ['identity', 'roles'] }); } });
  if (query.isLoading) return <LoadingSkeleton />;
  if (query.isError) return <ErrorState message={query.error.message} onRetry={() => void query.refetch()} />;
  const roles = (query.data ?? []) as RoleView[];
  return <><PageTitle title={t('roles')} description={t('roleAdminDescription', 'Create roles and manage their permission grants.')} />
    <Card title={t('createRole', 'Create role')} className="section-card"><FormProvider {...methods}><form className="form-grid" onSubmit={methods.handleSubmit((values) => create.mutate(values as CreateRoleRequest))}>
      <FormField<CreateRoleValues> name="roleKey" label={t('roleKey', 'Role key')} />
      <FormField<CreateRoleValues> name="displayName" label={t('displayName', 'Display name')} />
      <Button type="primary" htmlType="submit" loading={create.isPending}>{t('createRole', 'Create role')}</Button>
    </form></FormProvider></Card>
    {roles.map((role) => <Card key={role.id} className="section-card" title={role.displayName} extra={<StatusTag status={role.systemRole ? 'active' : 'pending'} />}>
      <Typography.Text code>{role.roleKey}</Typography.Text><Typography.Paragraph>{role.description}</Typography.Paragraph>
      <Typography.Text type="secondary">{t('permissionCount', { count: role.permissionIds.length, defaultValue: `${role.permissionIds.length} permissions` })}</Typography.Text>
    </Card>)}
  </>;
}

export function PermissionMatrixPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const [roleId, setRoleId] = useState<string>();
  const [selected, setSelected] = useState<string[]>([]);
  const rolesQuery = useQuery({ queryKey: ['identity', 'roles'], queryFn: async () => dataOf<RolesData>((await apiClient.get<RolesResponse>(apiPath('admin/identity/roles'))).data) });
  const permissionsQuery = useQuery({ queryKey: ['identity', 'permissions'], queryFn: async () => dataOf<PermissionsData>((await apiClient.get<PermissionsResponse>(apiPath('admin/identity/permissions'))).data) });
  const update = useMutation({ mutationFn: ({ id, body }: { id: string; body: UpdateRolePermissionsRequest }) => apiClient.put(apiPath(`admin/identity/roles/${id}/permissions`), body), onSuccess: () => void client.invalidateQueries({ queryKey: ['identity', 'roles'] }) });
  const roles = (rolesQuery.data ?? []) as RoleView[];
  const permissions = (permissionsQuery.data ?? []) as PermissionView[];
  const selectedRole = roles.find((role) => role.id === roleId);
  if (rolesQuery.isLoading || permissionsQuery.isLoading) return <LoadingSkeleton />;
  if (rolesQuery.isError || permissionsQuery.isError) return <ErrorState message={rolesQuery.error?.message ?? permissionsQuery.error?.message} onRetry={() => { void rolesQuery.refetch(); void permissionsQuery.refetch(); }} />;
  return <><PageTitle title={t('permissionMatrix', 'Permission matrix')} description={t('permissionMatrixDescription', 'Review and update permission grants for organization roles.')} />
    <Card>
      <Form.Item label={t('role', 'Role')}><Select value={roleId} placeholder={t('selectRole', 'Select a role')} options={roles.map((role) => ({ value: role.id, label: `${role.displayName}${role.systemRole ? ` (${t('system', 'system')})` : ''}` }))}
        onChange={(id: string) => { setRoleId(id); setSelected(roles.find((role) => role.id === id)?.permissionIds ?? []); }} /></Form.Item>
      {selectedRole && <>
        <Row gutter={[12, 12]}>{permissions.map((permission) => <Col xs={24} sm={12} xl={8} key={permission.id}>
          <Checkbox disabled={selectedRole.systemRole} checked={selected.includes(permission.id)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, permission.id] : current.filter((id) => id !== permission.id))}>
            <Space direction="vertical" size={0}><span>{permission.permissionKey}</span><Typography.Text type="secondary">{permission.description}</Typography.Text></Space>
          </Checkbox>
        </Col>)}</Row>
        {!selectedRole.systemRole && <Button className="section-card" type="primary" loading={update.isPending}
          onClick={() => update.mutate({ id: selectedRole.id, body: { permissionIds: selected } as UpdateRolePermissionsRequest })}>{t('save')}</Button>}
        {update.isError && <Alert className="section-card" type="error" message={update.error.message} />}
      </>}
    </Card>
  </>;
}

export function ShipmentTrackingPlaceholder() {
  const { trackingCode = '' } = useParams();
  return <><PublicTrackingDetails token={trackingCode} /></>;
}

function PublicTrackingDetails({ token }: { token: string }) {
  const { t } = useTranslation();
  const query = useQuery({ queryKey: ['public-tracking', token], queryFn: async () => (await apiClient.get(apiPath(`tracking/public/${encodeURIComponent(token)}`))).data.data as { shipmentNumber: string; status: string; eta: string | null; location: { latitude: number; longitude: number; recordedAt: string } | null }, enabled: token.length === 64, retry: false });
  return <><PageTitle title={t('publicTrackingTitle')} description={token.length === 64 ? undefined : t('trackingCode')} />
    {query.isLoading && <LoadingSkeleton rows={2} />}
    {query.isError && <Alert type="error" showIcon message="Tracking link is invalid, expired, or temporarily unavailable." />}
    {query.data && <Card title={`Shipment ${query.data.shipmentNumber}`}><Space direction="vertical"><Typography.Text>Status: <StatusTag status={query.data.status} /></Typography.Text><Typography.Text>ETA: {query.data.eta ? new Date(query.data.eta).toLocaleString() : 'Not available'}</Typography.Text><Typography.Text>Last known location: {query.data.location ? `${query.data.location.latitude}, ${query.data.location.longitude}` : 'Not available'}</Typography.Text></Space></Card>}
  </>;
}
