import { useEffect } from 'react';
import { Alert, Badge, Button, Card, Empty, List, Select, Space, Spin, Switch, Tag, Typography, message } from 'antd';
import { BellOutlined, CheckOutlined } from '@ant-design/icons';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { useAuthStore } from '@/app/authStore';
import { usePreferencesStore } from '@/app/preferencesStore';

type NotificationItem = {
  id: string;
  eventType: string;
  title: string;
  body: string;
  locale: string;
  deepLink: string;
  createdAt: string;
  readAt: string | null;
};

type Preferences = { inApp: boolean; email: boolean; sms: boolean; webPush: boolean; locale: 'en' | 'fa' };
type NotificationPage = { items: NotificationItem[]; page: number; size: number; total: number };
const unwrap = <T,>(value: { data: { data: T } }) => value.data.data;

export function useNotificationRealtime() {
  const token = useAuthStore((state) => state.accessToken);
  const client = useQueryClient();
  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => unwrap<{ count: number }>(await apiClient.get(apiPath('notifications/unread-count'))),
    refetchInterval: 60_000,
  });

  useEffect(() => {
    if (!token) return;
    let subject: string;
    try {
      const segment = token.split('.')[1];
      if (!segment) return;
      const claims = JSON.parse(atob(segment.replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: string };
      if (!claims.sub) return;
      subject = claims.sub;
    } catch {
      return;
    }

    const base = import.meta.env.VITE_API_BASE_URL || window.location.origin;
    const url = new URL('/ws/tracking', base);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    let disposed = false;
    let socket: WebSocket | undefined;
    let retryTimer: number | undefined;
    const connect = () => {
      if (disposed) return;
      socket = new WebSocket(url);
      socket.onopen = () => {
        socket?.send(`CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ${token}\n\n\0`);
        socket?.send(`SUBSCRIBE\nid:notifications\ndestination:/topic/notifications/${subject}\nack:auto\n\n\0`);
      };
      socket.onmessage = (event) => {
        const frame = String(event.data);
        if (!frame.startsWith('MESSAGE')) return;
        void client.invalidateQueries({ queryKey: ['notifications'] });
      };
      socket.onclose = () => {
        if (!disposed) retryTimer = window.setTimeout(connect, 5000);
      };
      socket.onerror = () => socket?.close();
    };
    connect();
    return () => {
      disposed = true;
      if (retryTimer !== undefined) window.clearTimeout(retryTimer);
      socket?.close();
    };
  }, [client, token]);

  return unread.data?.count ?? 0;
}

export function NotificationBell() {
  const { t } = useTranslation();
  const unread = useNotificationRealtime();
  const navigate = useNavigate();
  return <Button type="text" aria-label={t('notifications')} icon={
    <Badge count={unread} size="small" overflowCount={99}><BellOutlined /></Badge>
  } onClick={() => navigate('/app/notifications')} />;
}

export function NotificationsPage() {
  const { t } = useTranslation();
  const client = useQueryClient();
  const navigate = useNavigate();
  const locale = usePreferencesStore((state) => state.locale);
  const history = useInfiniteQuery({
    queryKey: ['notifications', 'history'],
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => unwrap<NotificationPage>(
      await apiClient.get(apiPath('notifications'), { params: { page: pageParam, size: 20 } })),
    getNextPageParam: (lastPage) => lastPage.page + 1 < Math.ceil(lastPage.total / lastPage.size)
      ? lastPage.page + 1 : undefined,
  });
  const preferences = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: async () => unwrap<Preferences>(await apiClient.get(apiPath('notification-preferences'))),
  });
  const markRead = useMutation({
    mutationFn: (id: string) => apiClient.patch(apiPath(`notifications/${id}/read`)),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
  const markAllRead = useMutation({
    mutationFn: () => apiClient.post(apiPath('notifications/read-all')),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['notifications'] });
      message.success(t('allNotificationsRead'));
    },
  });
  const savePreferences = useMutation({
    mutationFn: (value: Preferences) => apiClient.put(apiPath('notification-preferences'), value),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['notifications', 'preferences'] });
      message.success(t('notificationPreferencesSaved'));
    },
  });

  if (history.isLoading || preferences.isLoading) return <div className="page-title"><Spin /></div>;
  if (history.isError || preferences.isError || !history.data || !preferences.data) {
    return <Alert type="error" showIcon message={t('notificationLoadError')} description={String(history.error ?? preferences.error)} />;
  }

  const updatePreference = (key: keyof Omit<Preferences, 'locale'>, checked: boolean) => {
    savePreferences.mutate({ ...preferences.data, [key]: checked });
  };
  const items = history.data.pages.flatMap((page) => page.items);
  const total = history.data.pages[0]?.total ?? 0;

  return <div className="page-title">
    <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
      <div>
        <Typography.Title level={2}>{t('notifications')}</Typography.Title>
        <Typography.Paragraph type="secondary">{t('notificationHistoryDescription')}</Typography.Paragraph>
      </div>
      <Button icon={<CheckOutlined />} disabled={items.every((item) => item.readAt)}
        loading={markAllRead.isPending} onClick={() => markAllRead.mutate()}>{t('markAllRead')}</Button>
    </Space>

    <Card title={t('notificationPreferences')} className="section-card">
      <Space wrap size="large">
        <label>{t('inAppNotifications')} <Switch checked={preferences.data.inApp} onChange={(checked) => updatePreference('inApp', checked)} /></label>
        <label>{t('emailNotifications')} <Switch checked={preferences.data.email} onChange={(checked) => updatePreference('email', checked)} /></label>
        <label>{t('smsNotifications')} <Switch checked={preferences.data.sms} onChange={(checked) => updatePreference('sms', checked)} /></label>
        <label>{t('webPushNotifications')} <Switch checked={preferences.data.webPush} onChange={(checked) => updatePreference('webPush', checked)} /></label>
        <label>{t('notificationLanguage')} <Select value={preferences.data.locale ?? locale} style={{ width: 130 }}
          options={[{ value: 'en', label: 'English' }, { value: 'fa', label: 'فارسی' }]}
          onChange={(value: Preferences['locale']) => savePreferences.mutate({ ...preferences.data, locale: value })} /></label>
      </Space>
      {savePreferences.isError && <Alert className="section-card" type="error" message={String(savePreferences.error)} />}
    </Card>

    <Card title={`${t('notificationHistory')} · ${total}`} className="section-card">
      {items.length === 0 ? <Empty description={t('emptyDescription')} /> :
        <List itemLayout="horizontal" dataSource={items} renderItem={(item) =>
          <List.Item actions={[
            !item.readAt && <Button key="read" type="link" loading={markRead.isPending}
              onClick={() => markRead.mutate(item.id)}>{t('markRead')}</Button>,
            <Button key="open" type="link" onClick={() => {
              if (!item.readAt) markRead.mutate(item.id);
              navigate(item.deepLink);
            }}>{t('openNotification')}</Button>,
          ]}>
            <List.Item.Meta
              title={<Space>{!item.readAt && <Badge status="processing" />}{item.title}<Tag>{new Date(item.createdAt).toLocaleString(item.locale === 'fa' ? 'fa-IR' : 'en-US')}</Tag></Space>}
              description={<><Typography.Paragraph>{item.body}</Typography.Paragraph><Link to={item.deepLink}>{t('viewRelatedRecord')}</Link></>}
            />
          </List.Item>
        } />}
      {history.hasNextPage && <Button block loading={history.isFetchingNextPage} onClick={() => void history.fetchNextPage()}>
        {t('loadMoreNotifications')}
      </Button>}
    </Card>
  </div>;
}
