import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Form, Input, Select, Space, Switch, Table, Tabs, Typography, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/app/authStore';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { ErrorState } from '@/shared/components/ErrorState';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';

type KeyRecord = {
  id: string;
  name: string;
  keyPrefix: string;
  scopes: string[];
  expiresAt: string | null;
  revokedAt: string | null;
  lastUsedAt: string | null;
};

type WebhookRecord = { id: string; name: string; targetUrl: string; events: string[]; active: boolean };
type AuditRecord = { id: string; actorId: string; action: string; entity: string; entityId: string; occurredAt: string; securityEvent: boolean };
type Health = { status: string; checks: Record<string, { status: string; pendingOutboxEvents?: number; positionsLastHour?: number; error?: string }> };
type SettingRecord = { settingKey: string; valueType: string; isSecret: boolean; updatedAt: string };
type CalendarRecord = { calendarKey: string; name: string; locale: string; timezone: string; weekendDays: string };
type WebhookDelivery = { id: string; endpointId: string; eventType: string; status: string; attemptCount: number; lastStatusCode: number | null; lastError: string | null; createdAt: string };

const unwrap = <T,>(response: { data: { data: T } }) => response.data.data;

export function AdministrationPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const hasRole = useAuthStore((state) => state.hasRole);
  const isAdmin = hasRole('PLATFORM_ADMIN') || hasRole('ORGANIZATION_ADMIN');
  const [secret, setSecret] = useState('');
  const [auditAction, setAuditAction] = useState('');
  const [auditEntity, setAuditEntity] = useState('');
  const [toast, contextHolder] = message.useMessage();

  const health = useQuery({
    queryKey: ['administration', 'health'],
    queryFn: async () => unwrap<Health>(await apiClient.get(apiPath('admin/health'))),
    enabled: isAdmin,
    refetchInterval: 30_000,
  });

  const audit = useQuery({
    queryKey: ['administration', 'audit', auditAction, auditEntity],
    queryFn: async () => unwrap<AuditRecord[]>(await apiClient.get(apiPath('admin/audit'), {
      params: {
        limit: 100,
        ...(auditAction ? { action: auditAction } : {}),
        ...(auditEntity ? { entity: auditEntity } : {}),
      },
    })),
  });

  const keys = useQuery({
    queryKey: ['administration', 'api-keys'],
    queryFn: async () => unwrap<KeyRecord[]>(await apiClient.get(apiPath('admin/api-keys'))),
    enabled: isAdmin,
  });

  const webhooks = useQuery({
    queryKey: ['administration', 'webhooks'],
    queryFn: async () => unwrap<WebhookRecord[]>(await apiClient.get(apiPath('admin/webhooks'))),
    enabled: isAdmin,
  });

  const settings = useQuery({
    queryKey: ['administration', 'settings'],
    queryFn: async () => unwrap<SettingRecord[]>(await apiClient.get(apiPath('admin/settings'))),
    enabled: isAdmin,
  });

  const deliveries = useQuery({
    queryKey: ['administration', 'webhook-deliveries'],
    queryFn: async () => unwrap<WebhookDelivery[]>(await apiClient.get(apiPath('admin/webhooks/deliveries'), { params: { limit: 100 } })),
    enabled: isAdmin,
  });

  const calendars = useQuery({
    queryKey: ['administration', 'calendars'],
    queryFn: async () => unwrap<CalendarRecord[]>(await apiClient.get(apiPath('admin/calendars'))),
    enabled: isAdmin,
  });

  const saveCalendar = useMutation({
    mutationFn: async (value: { calendarKey: string; name: string; locale: string; timezone: string; weekendDays: string }) => {
      const { calendarKey, weekendDays, ...rest } = value;
      return apiClient.put(apiPath(`admin/calendars/${encodeURIComponent(calendarKey)}`), {
        ...rest,
        weekendDays: weekendDays.split(',').map((day) => Number(day.trim())),
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['administration', 'calendars'] });
      toast.success(t('calendarSaved'));
    },
    onError: () => toast.error(t('adminActionFailed')),
  });

  const saveHoliday = useMutation({
    mutationFn: async (value: { calendarKey: string; date: string; name: string }) => {
      const { calendarKey, date, name } = value;
      return apiClient.put(apiPath(`admin/holidays/${encodeURIComponent(calendarKey)}/${date}`), { name });
    },
    onSuccess: () => toast.success(t('holidaySaved')),
    onError: () => toast.error(t('adminActionFailed')),
  });

  const nextSequence = useMutation({
    mutationFn: async (value: { key: string; prefix: string }) => unwrap<{ number: string }>(
      await apiClient.post(apiPath(`admin/sequences/${encodeURIComponent(value.key)}/next`), null, { params: { prefix: value.prefix } }),
    ),
    onSuccess: (value) => toast.success(`${t('sequenceIssued')}: ${value.number}`),
    onError: () => toast.error(t('adminActionFailed')),
  });

  const createKey = useMutation({
    mutationFn: async (value: { name: string; scopes: string[]; expiresAt?: string }) =>
      unwrap<{ secret: string }>(await apiClient.post(apiPath('admin/api-keys'), value)),
    onSuccess: (value) => {
      setSecret(value.secret);
      void queryClient.invalidateQueries({ queryKey: ['administration', 'api-keys'] });
    },
    onError: () => toast.error(t('adminActionFailed')),
  });

  const revokeKey = useMutation({
    mutationFn: async (id: string) => apiClient.post(apiPath(`admin/api-keys/${id}/revoke`)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'api-keys'] }),
    onError: () => toast.error(t('adminActionFailed')),
  });

  const createWebhook = useMutation({
    mutationFn: async (value: { name: string; targetUrl: string; events: string[] }) =>
      unwrap<{ secret: string }>(await apiClient.post(apiPath('admin/webhooks'), value)),
    onSuccess: (value) => {
      setSecret(value.secret);
      void queryClient.invalidateQueries({ queryKey: ['administration', 'webhooks'] });
    },
    onError: () => toast.error(t('adminActionFailed')),
  });

  const rotateWebhook = useMutation({
    mutationFn: async (id: string) =>
      unwrap<{ secret: string }>(await apiClient.post(apiPath(`admin/webhooks/${id}/rotate-secret`))),
    onSuccess: (value) => setSecret(value.secret),
    onError: () => toast.error(t('adminActionFailed')),
  });

  const revokeWebhook = useMutation({
    mutationFn: async (id: string) => apiClient.post(apiPath(`admin/webhooks/${id}/revoke`)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['administration', 'webhooks'] }),
    onError: () => toast.error(t('adminActionFailed')),
  });

  const saveSetting = useMutation({
    mutationFn: async (value: { settingKey: string; type: string; value: string; secret: boolean }) => {
      const { settingKey, ...body } = value;
      return apiClient.put(apiPath(`admin/settings/${encodeURIComponent(settingKey)}`), body);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['administration', 'settings'] });
      toast.success(t('settingSaved'));
    },
    onError: () => toast.error(t('adminActionFailed')),
  });

  if (audit.isLoading) return <LoadingSkeleton rows={4} />;
  if (audit.isError || !audit.data) return <ErrorState onRetry={() => void audit.refetch()} />;

  const auditColumns = [
    { title: t('action'), dataIndex: 'action', key: 'action' },
    { title: t('entity'), dataIndex: 'entity', key: 'entity' },
    { title: t('actor'), dataIndex: 'actorId', key: 'actorId' },
    { title: t('occurredAt'), dataIndex: 'occurredAt', key: 'occurredAt', render: (value: string) => new Date(value).toLocaleString() },
    { title: t('securityEvent'), dataIndex: 'securityEvent', key: 'securityEvent', render: (value: boolean) => (value ? t('yes') : t('no')) },
  ];

  const keyColumns = [
    { title: t('name'), dataIndex: 'name', key: 'name' },
    { title: t('keyPrefix'), dataIndex: 'keyPrefix', key: 'keyPrefix' },
    { title: t('scopes'), dataIndex: 'scopes', key: 'scopes', render: (value: string[]) => value.join(', ') },
    { title: t('lastUsed'), dataIndex: 'lastUsedAt', key: 'lastUsedAt', render: (value: string | null) => (value ? new Date(value).toLocaleString() : '—') },
    {
      title: '',
      key: 'actions',
      render: (_: unknown, row: KeyRecord) => (row.revokedAt ? t('revoked') : <Button danger onClick={() => revokeKey.mutate(row.id)}>{t('revoke')}</Button>),
    },
  ];

  const webhookColumns = [
    { title: t('name'), dataIndex: 'name', key: 'name' },
    { title: t('targetUrl'), dataIndex: 'targetUrl', key: 'targetUrl' },
    { title: t('events'), dataIndex: 'events', key: 'events', render: (value: string[]) => value.join(', ') },
    {
      title: '',
      key: 'actions',
      render: (_: unknown, row: WebhookRecord) => (
        <Space>
          {row.active ? (
            <>
              <Button onClick={() => rotateWebhook.mutate(row.id)}>{t('rotateSecret')}</Button>
              <Button danger onClick={() => revokeWebhook.mutate(row.id)}>{t('revoke')}</Button>
            </>
          ) : (
            t('revoked')
          )}
        </Space>
      ),
    },
  ];

  const settingColumns = [
    { title: t('settingKey'), dataIndex: 'settingKey', key: 'settingKey' },
    { title: t('type'), dataIndex: 'valueType', key: 'valueType' },
    { title: t('secret'), dataIndex: 'isSecret', key: 'isSecret', render: (value: boolean) => (value ? t('yes') : t('no')) },
    { title: t('updated'), dataIndex: 'updatedAt', key: 'updatedAt', render: (value: string) => new Date(value).toLocaleString() },
  ];

  const deliveryColumns = [
    { title: t('eventType'), dataIndex: 'eventType', key: 'eventType' },
    { title: t('status'), dataIndex: 'status', key: 'status' },
    { title: t('attempts'), dataIndex: 'attemptCount', key: 'attemptCount' },
    { title: t('httpStatus'), dataIndex: 'lastStatusCode', key: 'lastStatusCode' },
    { title: t('failure'), dataIndex: 'lastError', key: 'lastError' },
  ];

  const calendarColumns = [
    { title: t('calendarKey'), dataIndex: 'calendarKey', key: 'calendarKey' },
    { title: t('name'), dataIndex: 'name', key: 'name' },
    { title: t('locale'), dataIndex: 'locale', key: 'locale' },
    { title: t('timezone'), dataIndex: 'timezone', key: 'timezone' },
    { title: t('weekendDays'), dataIndex: 'weekendDays', key: 'weekendDays' },
  ];

  const exportParams = new URLSearchParams({
    limit: '500',
    ...(auditAction ? { action: auditAction } : {}),
    ...(auditEntity ? { entity: auditEntity } : {}),
  });
  const exportUrl = `${import.meta.env.VITE_API_BASE_URL ?? ''}${apiPath('admin/audit/export')}?${exportParams.toString()}`;

  const healthTab = health.isLoading ? <LoadingSkeleton rows={3} /> : health.isError || !health.data ? <ErrorState onRetry={() => void health.refetch()} /> : (
    <Card title={`${t('overallStatus')}: ${health.data.status}`} extra={<Button onClick={() => void health.refetch()}>{t('refresh')}</Button>}>
      <Table
        rowKey="key"
        pagination={false}
        dataSource={Object.entries(health.data.checks).map(([key, value]) => ({ key, ...value }))}
        columns={[
          { title: t('component'), dataIndex: 'key', key: 'key' },
          { title: t('status'), dataIndex: 'status', key: 'status' },
          { title: t('details'), key: 'details', render: (_: unknown, row: Health['checks'][string]) => row.pendingOutboxEvents ?? row.positionsLastHour ?? row.error ?? '—' },
        ]}
      />
    </Card>
  );

  const tabs = [
    ...(isAdmin ? [{ key: 'health', label: t('systemHealth'), children: healthTab }] : []),
    {
      key: 'audit',
      label: t('auditLog'),
      children: (
        <Card extra={<Button href={exportUrl} target="_blank">{t('exportCsv')}</Button>}>
          <Form layout="inline" onFinish={(value: { action?: string; entity?: string }) => {
            setAuditAction(value.action?.trim() ?? '');
            setAuditEntity(value.entity?.trim() ?? '');
          }}>
            <Form.Item name="action"><Input allowClear placeholder={t('action')} /></Form.Item>
            <Form.Item name="entity"><Input allowClear placeholder={t('entity')} /></Form.Item>
            <Button htmlType="submit">{t('search')}</Button>
          </Form>
          <Table<AuditRecord> rowKey="id" columns={auditColumns} dataSource={audit.data} pagination={{ pageSize: 10, responsive: true }} />
        </Card>
      ),
    },
    ...(isAdmin ? [
      {
        key: 'keys',
        label: t('apiKeys'),
        children: (
          <Card>
            <Form layout="inline" onFinish={(value: { name: string; scopes: string }) => createKey.mutate({
              name: value.name,
              scopes: value.scopes.split(',').map((scope) => scope.trim()).filter(Boolean),
            })}>
              <Form.Item name="name" rules={[{ required: true }]}><Input placeholder={t('name')} /></Form.Item>
              <Form.Item name="scopes" rules={[{ required: true }]}><Input placeholder="orders.read, shipments.read" /></Form.Item>
              <Button type="primary" htmlType="submit" loading={createKey.isPending}>{t('createApiKey')}</Button>
            </Form>
            {keys.isLoading ? <LoadingSkeleton rows={2} /> : keys.isError || !keys.data ? <ErrorState onRetry={() => void keys.refetch()} /> : <Table<KeyRecord> className="section-card" rowKey="id" columns={keyColumns} dataSource={keys.data} pagination={{ pageSize: 8 }} />}
          </Card>
        ),
      },
      {
        key: 'webhooks',
        label: t('webhooks'),
        children: (
          <Card>
            <Form layout="inline" onFinish={(value: { name: string; targetUrl: string; events: string }) => createWebhook.mutate({
              name: value.name,
              targetUrl: value.targetUrl,
              events: value.events.split(',').map((event) => event.trim()).filter(Boolean),
            })}>
              <Form.Item name="name" rules={[{ required: true }]}><Input placeholder={t('name')} /></Form.Item>
              <Form.Item name="targetUrl" rules={[{ required: true, type: 'url' }]}><Input placeholder="https://example.com/webhook" /></Form.Item>
              <Form.Item name="events" rules={[{ required: true }]}><Input placeholder="OrderCreated, ShipmentAssigned" /></Form.Item>
              <Button type="primary" htmlType="submit" loading={createWebhook.isPending}>{t('createWebhook')}</Button>
            </Form>
            {webhooks.isLoading ? <LoadingSkeleton rows={2} /> : webhooks.isError || !webhooks.data ? <ErrorState onRetry={() => void webhooks.refetch()} /> : <Table<WebhookRecord> className="section-card" rowKey="id" columns={webhookColumns} dataSource={webhooks.data} pagination={{ pageSize: 8 }} />}
            <Typography.Title level={4} className="section-card">{t('deliveryHistory')}</Typography.Title>
            {deliveries.isLoading ? <LoadingSkeleton rows={2} /> : deliveries.isError || !deliveries.data ? <ErrorState onRetry={() => void deliveries.refetch()} /> : <Table<WebhookDelivery> rowKey="id" columns={deliveryColumns} dataSource={deliveries.data} pagination={{ pageSize: 8 }} />}
          </Card>
        ),
      },
      {
        key: 'settings',
        label: t('settings'),
        children: (
          <Card>
            <Form layout="inline" onFinish={saveSetting.mutate}>
              <Form.Item name="settingKey" rules={[{ required: true }]}><Input placeholder={t('settingKey')} /></Form.Item>
              <Form.Item name="type" rules={[{ required: true }]}><Select placeholder={t('type')} options={['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'JSON'].map((value) => ({ value, label: value }))} /></Form.Item>
              <Form.Item name="value" rules={[{ required: true }]}><Input placeholder={t('value')} /></Form.Item>
              <Form.Item name="secret" valuePropName="checked"><Switch aria-label={t('secret')} /></Form.Item>
              <Button type="primary" htmlType="submit" loading={saveSetting.isPending}>{t('save')}</Button>
            </Form>
            {settings.isLoading ? <LoadingSkeleton rows={2} /> : settings.isError || !settings.data ? <ErrorState onRetry={() => void settings.refetch()} /> : <Table<SettingRecord> className="section-card" rowKey="settingKey" columns={settingColumns} dataSource={settings.data} pagination={{ pageSize: 8 }} />}
          </Card>
        ),
      },
      {
        key: 'calendars',
        label: t('businessCalendar'),
        children: (
          <Card>
            <Form layout="inline" onFinish={saveCalendar.mutate}>
              <Form.Item name="calendarKey" rules={[{ required: true }]}><Input placeholder={t('calendarKey')} /></Form.Item>
              <Form.Item name="name" rules={[{ required: true }]}><Input placeholder={t('name')} /></Form.Item>
              <Form.Item name="locale" rules={[{ required: true }]} initialValue="en"><Select options={[{ value: 'en', label: 'English' }, { value: 'fa', label: 'فارسی' }]} /></Form.Item>
              <Form.Item name="timezone" rules={[{ required: true }]} initialValue="UTC"><Input placeholder="Asia/Tehran" /></Form.Item>
              <Form.Item name="weekendDays" rules={[{ required: true }]} initialValue="5,6"><Input placeholder="0,6" /></Form.Item>
              <Button type="primary" htmlType="submit" loading={saveCalendar.isPending}>{t('saveCalendar')}</Button>
            </Form>
            {calendars.isLoading ? <LoadingSkeleton rows={2} /> : calendars.isError || !calendars.data ? <ErrorState onRetry={() => void calendars.refetch()} /> : <Table<CalendarRecord> className="section-card" rowKey="calendarKey" columns={calendarColumns} dataSource={calendars.data} pagination={{ pageSize: 8 }} />}
            <Form layout="inline" className="section-card" onFinish={saveHoliday.mutate}>
              <Form.Item name="calendarKey" rules={[{ required: true }]}><Input placeholder={t('calendarKey')} /></Form.Item>
              <Form.Item name="date" rules={[{ required: true }, { pattern: /^\d{4}-\d{2}-\d{2}$/ }]}><Input placeholder="YYYY-MM-DD" /></Form.Item>
              <Form.Item name="name" rules={[{ required: true }]}><Input placeholder={t('holidayName')} /></Form.Item>
              <Button htmlType="submit" loading={saveHoliday.isPending}>{t('saveHoliday')}</Button>
            </Form>
          </Card>
        ),
      },
      {
        key: 'sequences',
        label: t('numberSequences'),
        children: (
          <Card>
            <Form layout="inline" onFinish={nextSequence.mutate}>
              <Form.Item name="key" rules={[{ required: true }]}><Input placeholder={t('sequenceKey')} /></Form.Item>
              <Form.Item name="prefix"><Input placeholder={t('prefix')} /></Form.Item>
              <Button type="primary" htmlType="submit" loading={nextSequence.isPending}>{t('issueNextNumber')}</Button>
            </Form>
          </Card>
        ),
      },
    ] : []),
  ];

  return (
    <>
      {contextHolder}
      <div className="page-title"><Typography.Title level={2}>{t('administration')}</Typography.Title></div>
      {secret && (
        <Alert
          type="warning"
          showIcon
          closable
          message={t('copySecretNow')}
          description={<Input.Password readOnly value={secret} />}
          onClose={() => setSecret('')}
        />
      )}
      <Tabs items={tabs} />
    </>
  );
}
