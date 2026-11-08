import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Layout, Row, Select, Space, Statistic, Table, Tabs, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { usePreferencesStore } from '@/app/preferencesStore';
import { formatJalaliDate } from '@/i18n/formatters';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { ErrorState } from '@/shared/components/ErrorState';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';
import { StatusTag } from '@/shared/components/StatusTag';

const { Header, Content } = Layout;

type CustomerOrder = {
  id: string;
  orderNumber: string;
  customerId: string;
  status: string;
  fulfillmentType: string;
  currency: string;
  createdAt: string;
};

type OrderRequest = {
  customerId?: string;
  fulfillmentType?: string;
  shipTo: Record<string, unknown>;
  currency?: string;
  lines: Array<{ productId: string; description: string; quantity: number; unit?: string; unitPrice?: number }>;
};

const unwrap = <T,>(response: { data: { data: T } }) => response.data.data;
const orderRequestSchema = z.object({
  customerId: z.string().uuid().optional(),
  fulfillmentType: z.string().optional(),
  shipTo: z.record(z.string(), z.unknown()).refine((value) => Object.keys(value).length > 0),
  currency: z.string().length(3).optional(),
  lines: z.array(z.object({
    productId: z.string().uuid(),
    description: z.string().min(1).max(255),
    quantity: z.number().positive(),
    unit: z.string().optional(),
    unitPrice: z.number().optional(),
  })).min(1),
});

export function CustomerPortalLayout() {
  const { t } = useTranslation();
  const location = useLocation();
  const locale = usePreferencesStore((state) => state.locale);
  const setLocale = usePreferencesStore((state) => state.setLocale);

  return <Layout className="portal-layout">
    <Header className="portal-header">
      <Link to="/portal" className="portal-brand">{t('customerPortal')}</Link>
      <nav className="portal-navigation" aria-label={t('customerPortal')}>
        <Link aria-current={location.pathname === '/portal' ? 'page' : undefined} to="/portal">{t('orderHistory')}</Link>
        <Link aria-current={location.pathname.startsWith('/portal/shipments') ? 'page' : undefined} to="/portal/shipments">{t('shipments')}</Link>
        <Link aria-current={location.pathname.startsWith('/portal/documents') ? 'page' : undefined} to="/portal/documents">{t('documents')}</Link>
        <Link aria-current={location.pathname.startsWith('/portal/addresses') ? 'page' : undefined} to="/portal/addresses">{t('addresses')}</Link>
        <Link aria-current={location.pathname.startsWith('/portal/notifications') ? 'page' : undefined} to="/portal/notifications">{t('notifications')}</Link>
        <Link aria-current={location.pathname.startsWith('/portal/profile') ? 'page' : undefined} to="/portal/profile">{t('profile')}</Link>
      </nav>
      <Select aria-label={t('language')} value={locale} onChange={setLocale} options={[
        { value: 'en', label: 'English' }, { value: 'fa', label: 'فارسی' },
      ]} />
    </Header>
    <Content className="portal-content"><Outlet /></Content>
  </Layout>;
}

export function CustomerPortalPage() {
  const { t } = useTranslation();
  const locale = usePreferencesStore((state) => state.locale);
  const queryClient = useQueryClient();
  const [form] = Form.useForm<Omit<OrderRequest, 'shipTo'> & { shipToJson: string }>();
  const [bulkText, setBulkText] = useState('');
  const [showBulk, setShowBulk] = useState(false);
  const [toast, contextHolder] = message.useMessage();
  const orders = useQuery({
    queryKey: ['portal', 'orders'],
    queryFn: async () => unwrap<CustomerOrder[]>(await apiClient.get(apiPath('portal/orders'))),
  });
  const createOrder = useMutation({
    mutationFn: async (request: OrderRequest) => unwrap<CustomerOrder>(
      await apiClient.post(apiPath('portal/orders'), request),
    ),
    onSuccess: () => {
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['portal', 'orders'] });
      toast.success(t('orderCreated'));
    },
    onError: () => toast.error(t('portalActionFailed')),
  });
  const bulkCreate = useMutation({
    mutationFn: async (request: OrderRequest[]) => unwrap<CustomerOrder[]>(
      await apiClient.post(apiPath('portal/orders/bulk'), { orders: request }),
    ),
    onSuccess: () => {
      setBulkText('');
      setShowBulk(false);
      void queryClient.invalidateQueries({ queryKey: ['portal', 'orders'] });
      toast.success(t('ordersImported'));
    },
    onError: () => toast.error(t('portalActionFailed')),
  });
  const columns = useMemo<ColumnsType<CustomerOrder>>(() => [
    { title: t('orderNumber'), dataIndex: 'orderNumber', key: 'orderNumber' },
    { title: t('status'), dataIndex: 'status', key: 'status', render: (status: string) => <StatusTag status={status} /> },
    { title: t('fulfillment'), dataIndex: 'fulfillmentType', key: 'fulfillmentType' },
    { title: t('created'), dataIndex: 'createdAt', key: 'createdAt', render: (value: string) => locale === 'fa' ? formatJalaliDate(value) : new Date(value).toLocaleDateString('en-US') },
  ], [t, locale]);

  if (orders.isLoading) return <LoadingSkeleton rows={5} />;
  if (orders.isError || !orders.data) return <ErrorState onRetry={() => void orders.refetch()} />;

  function submitBulk() {
    try {
      const parsed: unknown = JSON.parse(bulkText);
      if (!Array.isArray(parsed) || parsed.length === 0 || parsed.length > 100) {
        throw new Error(t('bulkImportLimit'));
      }
      const validated = z.array(orderRequestSchema).max(100).safeParse(parsed);
      if (!validated.success) throw new Error(t('bulkImportInvalid'));
      bulkCreate.mutate(validated.data);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('bulkImportInvalid'));
    }
  }

  return <main className="portal-page">
    {contextHolder}
    <div className="portal-title">
      <div>
        <Typography.Title level={2}>{t('customerPortal')}</Typography.Title>
        <Typography.Paragraph type="secondary">{t('portalDescription')}</Typography.Paragraph>
      </div>
      <Row gutter={[16, 16]} className="portal-stats">
        <Col xs={12} md={6}><Card><Statistic title={t('totalOrders')} value={orders.data.length} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title={t('openOrders')} value={orders.data.filter((order) => !['DELIVERED', 'CANCELLED', 'RETURNED'].includes(order.status)).length} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title={t('completedOrders')} value={orders.data.filter((order) => order.status === 'DELIVERED').length} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title={t('activeShipments')} value="—" /></Card></Col>
      </Row>
      <Space wrap>
        <Button onClick={() => setShowBulk((shown) => !shown)}>{showBulk ? t('singleOrder') : t('bulkImport')}</Button>
      </Space>
    </div>
    <Card title={t('orderHistory')} className="portal-card">
      <Table<CustomerOrder> rowKey="id" columns={columns} dataSource={orders.data}
        pagination={{ pageSize: 10, responsive: true }} locale={{ emptyText: t('noOrders') }} />
    </Card>
    <Card title={showBulk ? t('bulkImport') : t('createOrder')} className="portal-card">
      {showBulk ? <>
        <Typography.Paragraph type="secondary">{t('bulkImportHelp')}</Typography.Paragraph>
        <Input.TextArea aria-label={t('bulkImport')} rows={8} value={bulkText} onChange={(event) => setBulkText(event.target.value)} />
        <Button className="portal-submit" type="primary" loading={bulkCreate.isPending} onClick={submitBulk}>{t('importOrders')}</Button>
        {bulkCreate.isError && <Alert className="portal-submit" type="error" showIcon message={t('portalActionFailed')} />}
      </> : <Form form={form} layout="vertical" onFinish={(value: Omit<OrderRequest, 'shipTo'> & { shipToJson: string }) => {
        try {
          const { shipToJson, ...request } = value;
          createOrder.mutate({ ...request, shipTo: JSON.parse(shipToJson) as Record<string, unknown> });
        } catch { toast.error(t('deliveryAddressRequired')); }
      }}
        initialValues={{ currency: 'USD', fulfillmentType: 'WAREHOUSE', lines: [{ quantity: 1, unit: 'EA' }] }}>
        <div className="portal-form-grid">
          <Form.Item label={t('productId')} name={['lines', 0, 'productId']} rules={[{ required: true }, { pattern: /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i, message: t('productIdInvalid') }]}>
            <Input placeholder={t('productId')} />
          </Form.Item>
          <Form.Item label={t('description')} name={['lines', 0, 'description']} rules={[{ required: true }, { max: 255 }]}>
            <Input />
          </Form.Item>
          <Form.Item label={t('quantity')} name={['lines', 0, 'quantity']} rules={[{ required: true, type: 'number', min: 0.0001 }]}>
            <InputNumber min={0.0001} step={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={t('currency')} name="currency" rules={[{ required: true }, { len: 3 }]}>
            <Input maxLength={3} />
          </Form.Item>
          <Form.Item label={t('fulfillment')} name="fulfillmentType">
            <Select options={[{ value: 'WAREHOUSE', label: t('warehouse') }, { value: 'TRANSPORT_ONLY', label: t('transportOnly') }]} />
          </Form.Item>
        </div>
        <Form.Item label={t('deliveryAddressJson')} name="shipToJson" rules={[{ required: true, validator: async (_, value) => {
          try {
            const parsed: unknown = JSON.parse(value);
            if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && Object.keys(parsed).length > 0) return;
          } catch {
            throw new Error(t('deliveryAddressRequired'));
          }
          throw new Error(t('deliveryAddressRequired'));
        } }]}>
          <Input.TextArea rows={3} placeholder='{"street":"…","city":"…","postalCode":"…"}' />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={createOrder.isPending}>{t('createOrder')}</Button>
        {createOrder.isError && <Alert className="portal-submit" type="error" showIcon message={t('portalActionFailed')} />}
      </Form>}
    </Card>
  </main>;
}

type PortalShipment = { id: string; shipmentNumber: string; status: string; latitude: number | null; longitude: number | null; locationUpdatedAt: string | null; eta: string | null };
type PortalDocument = { id: string; documentNumber: string; documentType: string; shipmentId: string | null; createdAt: string; filename: string };
type PortalAddress = { id: string; label: string; address: Record<string, unknown>; isDefault: boolean };

export function CustomerShipmentsPage() {
  const { t } = useTranslation();
  const locale = usePreferencesStore((state) => state.locale);
  const shipments = useQuery({
    queryKey: ['portal', 'shipments'],
    queryFn: async () => unwrap<PortalShipment[]>(await apiClient.get(apiPath('portal/shipments'))),
    refetchInterval: 30_000,
  });
  if (shipments.isLoading) return <LoadingSkeleton rows={4} />;
  if (shipments.isError || !shipments.data) return <ErrorState onRetry={() => void shipments.refetch()} />;
  return <main className="portal-page">
    <Typography.Title level={2}>{t('shipments')}</Typography.Title>
    <Table<PortalShipment> rowKey="id" dataSource={shipments.data} pagination={{ pageSize: 10, responsive: true }}
      locale={{ emptyText: t('noShipments') }} columns={[
        { title: t('shipmentNumber'), dataIndex: 'shipmentNumber', key: 'shipmentNumber' },
        { title: t('status'), dataIndex: 'status', key: 'status', render: (status: string) => <StatusTag status={status} /> },
        { title: t('lastKnownLocation'), key: 'location', render: (_, row) => row.latitude === null || row.longitude === null
          ? t('locationUnavailable') : <a href={`https://www.openstreetmap.org/?mlat=${row.latitude}&mlon=${row.longitude}#map=14/${row.latitude}/${row.longitude}`}
            target="_blank" rel="noreferrer">{row.latitude.toFixed(3)}, {row.longitude.toFixed(3)}</a> },
        { title: t('eta'), dataIndex: 'eta', key: 'eta', render: (value: string | null) => value ? formatTimestamp(value, locale) : '—' },
        { title: t('updated'), dataIndex: 'locationUpdatedAt', key: 'locationUpdatedAt', render: (value: string | null) => value ? formatTimestamp(value, locale) : '—' },
        { title: t('deliveryTimeline'), key: 'timeline', render: (_, row) => <ShipmentTimeline shipmentId={row.id} /> },
      ]} />
  </main>;
}

function ShipmentTimeline({ shipmentId }: { shipmentId: string }) {
  const { t } = useTranslation();
  const locale = usePreferencesStore((state) => state.locale);
  const timeline = useQuery({
    queryKey: ['portal', 'shipment-timeline', shipmentId],
    queryFn: async () => unwrap<Array<{ fromStatus: string | null; toStatus: string; occurredAt: string }>>(
      await apiClient.get(apiPath(`portal/shipments/${shipmentId}/timeline`))),
  });
  if (timeline.isLoading) return <Typography.Text type="secondary">{t('loading')}</Typography.Text>;
  if (timeline.isError) return <Typography.Text type="danger">{t('timelineUnavailable')}</Typography.Text>;
  return <Typography.Text>{timeline.data?.length
    ? timeline.data.map((event) => `${event.toStatus} · ${formatTimestamp(event.occurredAt, locale)}`).join(' / ')
    : t('noTimelineEvents')}</Typography.Text>;
}

export function CustomerDocumentsPage() {
  const { t } = useTranslation();
  const locale = usePreferencesStore((state) => state.locale);
  const [toast, contextHolder] = message.useMessage();
  const invoices = useQuery({
    queryKey: ['portal', 'invoices'],
    queryFn: async () => unwrap<PortalDocument[]>(await apiClient.get(apiPath('portal/invoices'))),
  });
  const proofs = useQuery({
    queryKey: ['portal', 'proofs'],
    queryFn: async () => unwrap<PortalDocument[]>(await apiClient.get(apiPath('portal/proofs'))),
  });
  const download = useMutation({
    mutationFn: async (document: PortalDocument) => {
      const response = await apiClient.get(apiPath(`portal/documents/${document.id}/download`), { responseType: 'blob' });
      const objectUrl = URL.createObjectURL(response.data);
      downloadBlob(objectUrl, document.filename || document.documentNumber);
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    },
    onError: () => toast.error(t('documentDownloadFailed')),
  });
  if (invoices.isLoading || proofs.isLoading) return <LoadingSkeleton rows={4} />;
  if (invoices.isError || proofs.isError || !invoices.data || !proofs.data) return <ErrorState onRetry={() => {
    void invoices.refetch(); void proofs.refetch();
  }} />;
  const columns = [
    { title: t('documentNumber'), dataIndex: 'documentNumber', key: 'documentNumber' },
    { title: t('created'), dataIndex: 'createdAt', key: 'createdAt', render: (value: string) => formatDate(value, locale) },
    { title: '', key: 'download', render: (_: unknown, row: PortalDocument) =>
      <Button loading={download.isPending} onClick={() => download.mutate(row)}>{t('download')}</Button> },
  ];
  return <main className="portal-page">{contextHolder}
    <Typography.Title level={2}>{t('documents')}</Typography.Title>
    <Tabs items={[
      { key: 'invoices', label: t('invoices'), children: <Table<PortalDocument> rowKey="id" columns={columns} dataSource={invoices.data} locale={{ emptyText: t('noInvoices') }} /> },
      { key: 'proofs', label: t('proofOfDelivery'), children: <Table<PortalDocument> rowKey="id" columns={columns} dataSource={proofs.data} locale={{ emptyText: t('noProofs') }} /> },
    ]} />
  </main>;
}

export function CustomerAddressesPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [toast, contextHolder] = message.useMessage();
  const [label, setLabel] = useState('');
  const [addressJson, setAddressJson] = useState('');
  const [isDefault, setDefault] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const addresses = useQuery({
    queryKey: ['portal', 'addresses'],
    queryFn: async () => unwrap<PortalAddress[]>(await apiClient.get(apiPath('portal/addresses'))),
  });
  const addAddress = useMutation({
    mutationFn: async () => {
      const address: unknown = JSON.parse(addressJson);
      if (!address || typeof address !== 'object' || Array.isArray(address)) throw new Error(t('addressInvalid'));
      return editingId
        ? apiClient.put(apiPath(`portal/addresses/${editingId}`), { label, address, isDefault })
        : apiClient.post(apiPath('portal/addresses'), { label, address, isDefault });
    },
    onSuccess: () => {
      setLabel(''); setAddressJson(''); setEditingId(null); setDefault(false);
      void queryClient.invalidateQueries({ queryKey: ['portal', 'addresses'] });
    },
    onError: () => toast.error(t('addressSaveFailed')),
  });
  const removeAddress = useMutation({
    mutationFn: async (id: string) => apiClient.delete(apiPath(`portal/addresses/${id}`)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['portal', 'addresses'] }),
    onError: () => toast.error(t('addressSaveFailed')),
  });
  if (addresses.isLoading) return <LoadingSkeleton rows={3} />;
  if (addresses.isError || !addresses.data) return <ErrorState onRetry={() => void addresses.refetch()} />;
  return <main className="portal-page">{contextHolder}
    <Typography.Title level={2}>{t('addresses')}</Typography.Title>
    <Table<PortalAddress> rowKey="id" dataSource={addresses.data} pagination={false} columns={[
      { title: t('name'), dataIndex: 'label', key: 'label' },
      { title: t('address'), dataIndex: 'address', key: 'address', render: (value: Record<string, unknown>) => Object.values(value).join(', ') },
      { title: t('defaultAddress'), dataIndex: 'isDefault', key: 'isDefault', render: (value: boolean) => value ? t('yes') : '—' },
      { title: '', key: 'actions', render: (_, row) => <Space>
        <Button onClick={() => {
          setEditingId(row.id); setLabel(row.label); setAddressJson(JSON.stringify(row.address, null, 2)); setDefault(row.isDefault);
        }}>{t('edit')}</Button>
        <Button danger onClick={() => removeAddress.mutate(row.id)}>{t('delete')}</Button>
      </Space> },
    ]} />
    <Card title={editingId ? t('editAddress') : t('addAddress')} className="portal-card">
      <Space direction="vertical" className="portal-address-form">
        <Input aria-label={t('name')} value={label} onChange={(event) => setLabel(event.target.value)} placeholder={t('name')} />
        <Input.TextArea aria-label={t('addressJson')} value={addressJson} onChange={(event) => setAddressJson(event.target.value)} rows={4} placeholder='{"street":"…","city":"…"}' />
        <label><input type="checkbox" checked={isDefault} onChange={(event) => setDefault(event.target.checked)} /> {t('defaultAddress')}</label>
        <Space>
          <Button type="primary" loading={addAddress.isPending} disabled={!label.trim() || !addressJson.trim()} onClick={() => addAddress.mutate()}>{t('save')}</Button>
          {editingId && <Button onClick={() => { setEditingId(null); setLabel(''); setAddressJson(''); setDefault(false); }}>{t('cancel')}</Button>}
        </Space>
      </Space>
    </Card>
  </main>;
}

export function CustomerProfilePage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const setLocale = usePreferencesStore((state) => state.setLocale);
  const [toast, contextHolder] = message.useMessage();
  const profile = useQuery({
    queryKey: ['portal', 'profile'],
    queryFn: async () => unwrap<{ customerId: string; email: string | null; phone: string | null; locale: 'en' | 'fa' }>(
      await apiClient.get(apiPath('portal/profile'))),
  });
  const update = useMutation({
    mutationFn: async (locale: 'en' | 'fa') => apiClient.put(apiPath('portal/profile'), { locale }),
    onSuccess: (_, locale) => {
      void queryClient.invalidateQueries({ queryKey: ['portal', 'profile'] });
      setLocale(locale);
      toast.success(t('profileSaved'));
    },
    onError: () => toast.error(t('portalActionFailed')),
  });
  if (profile.isLoading) return <LoadingSkeleton rows={2} />;
  if (profile.isError || !profile.data) return <ErrorState onRetry={() => void profile.refetch()} />;
  return <main className="portal-page">{contextHolder}
    <Typography.Title level={2}>{t('profile')}</Typography.Title>
    <Card title={t('accountDetails')}>
      <p>{t('emailOrPhone')}: {profile.data.email ?? profile.data.phone ?? '—'}</p>
      <p>{t('customerId')}: {profile.data.customerId}</p>
      <Space><Typography.Text>{t('language')}</Typography.Text>
        <Select value={profile.data.locale} loading={update.isPending} onChange={(value: 'en' | 'fa') => update.mutate(value)}
          options={[{ value: 'en', label: 'English' }, { value: 'fa', label: 'فارسی' }]} /></Space>
    </Card>
  </main>;
}

function downloadBlob(url: string, filename: string) {
  const anchor = window.document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  window.document.body.append(anchor);
  anchor.click();
  anchor.remove();
}

function formatDate(value: string, locale: 'en' | 'fa') {
  return locale === 'fa' ? formatJalaliDate(value) : new Date(value).toLocaleDateString('en-US');
}

function formatTimestamp(value: string, locale: 'en' | 'fa') {
  return `${formatDate(value, locale)} ${new Date(value).toLocaleTimeString(locale === 'fa' ? 'fa-IR' : 'en-US')}`;
}
