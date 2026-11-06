import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';

type MasterRecord = { id: string; code: string; name: string; status: string; attributes: Record<string, unknown> };
const labels: Record<string, string> = { customers: 'Customers', suppliers: 'Suppliers', carriers: 'Carriers', products: 'Products', vehicles: 'Vehicles', drivers: 'Drivers', warehouses: 'Warehouses', regions: 'Regions', 'delivery-zones': 'Delivery zones', 'tariff-zones': 'Tariff zones', 'business-calendars': 'Business calendars', holidays: 'Holidays' };
const blank = { code: '', name: '', status: 'ACTIVE', attributes: '{}' };
function dataOf<T>(response: unknown): T { return (response as { data: T }).data; }

export function MasterDataPage({ resourceType }: { resourceType: string }) {
  const title = labels[resourceType] ?? 'Master data';
  const client = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<MasterRecord | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const query = useQuery({ queryKey: ['master-data', resourceType, search, page], queryFn: async () => dataOf<{ content: MasterRecord[]; totalElements: number }>((await apiClient.get(apiPath(`master-data/${resourceType}?search=${encodeURIComponent(search)}&page=${page}&size=20`))).data) });
  const refresh = () => client.invalidateQueries({ queryKey: ['master-data', resourceType] });
  const save = useMutation({ mutationFn: async (values: { code: string; name: string; status: string; attributes: string }) => {
    let attributes: Record<string, unknown>;
    try { attributes = JSON.parse(values.attributes || '{}'); } catch { throw new Error('Additional fields must contain valid JSON.'); }
    if (!attributes || Array.isArray(attributes) || typeof attributes !== 'object') throw new Error('Additional fields must be a JSON object.');
    const body = { ...values, attributes };
    return editing ? apiClient.put(apiPath(`master-data/${resourceType}/${editing.id}`), body) : apiClient.post(apiPath(`master-data/${resourceType}`), body);
  }, onSuccess: () => { setOpen(false); setEditing(null); form.resetFields(); void refresh(); } });
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(apiPath(`master-data/${resourceType}/${id}`)), onSuccess: () => void refresh() });
  function startEdit(record?: MasterRecord) { setEditing(record ?? null); form.setFieldsValue(record ? { code: record.code, name: record.name, status: record.status, attributes: JSON.stringify(record.attributes, null, 2) } : blank); setOpen(true); }
  if (query.isLoading) return <LoadingSkeleton />;
  if (query.isError) return <ErrorState message={query.error.message} onRetry={() => void query.refetch()} />;
  const records = query.data?.content ?? [];
  return <>
    <div className="page-title"><Typography.Title level={2}>{title}</Typography.Title><Typography.Paragraph type="secondary">Organization master data, saved through the API.</Typography.Paragraph></div>
    <Card>
      <Space className="section-card" wrap><Input.Search aria-label={`Search ${title}`} placeholder="Search by name or code" allowClear value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} onSearch={() => void query.refetch()} /><Button type="primary" onClick={() => startEdit()}>Add {title.toLowerCase().replace(/s$/, '')}</Button></Space>
      {records.length === 0 ? <EmptyState title={`No ${title.toLowerCase()} yet`} description="Create a record to start building this master data list." /> : <Table rowKey="id" dataSource={records} pagination={{ current: page + 1, pageSize: 20, total: query.data?.totalElements ?? 0, onChange: (next) => setPage(next - 1) }} columns={[
        { title: 'Code / SKU', dataIndex: 'code', width: 180 }, { title: 'Name', dataIndex: 'name' }, { title: 'Status', dataIndex: 'status', width: 130, render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{status}</Tag> },
        { title: 'Additional fields', dataIndex: 'attributes', render: (value: Record<string, unknown>) => Object.keys(value ?? {}).length ? Object.entries(value).slice(0, 3).map(([key, item]) => `${key}: ${String(item)}`).join(' · ') : '—' },
        { title: '', key: 'actions', width: 160, render: (_, record: MasterRecord) => <Space><Button type="link" onClick={() => startEdit(record)}>Edit</Button><Button type="link" danger loading={remove.isPending} onClick={() => Modal.confirm({ title: `Delete ${record.name}?`, content: 'This action removes the master data record.', okButtonProps: { danger: true }, onOk: () => remove.mutateAsync(record.id) })}>Delete</Button></Space> },
      ]} />}
      {save.isError && <Alert className="section-card" type="error" showIcon message={save.error.message} />}
      {remove.isError && <Alert className="section-card" type="error" showIcon message={remove.error.message} />}
    </Card>
    <Modal title={`${editing ? 'Edit' : 'Create'} ${title.toLowerCase().replace(/s$/, '')}`} open={open} onCancel={() => { setOpen(false); save.reset(); }} onOk={() => form.submit()} confirmLoading={save.isPending} destroyOnClose>
      <Form form={form} layout="vertical" initialValues={blank} onFinish={(values) => save.mutate(values)}>
        <Form.Item name="code" label="Code / SKU" rules={[{ required: true }, { max: 100 }]}><Input /></Form.Item>
        <Form.Item name="name" label="Name" rules={[{ required: true }, { max: 200 }]}><Input /></Form.Item>
        <Form.Item name="status" label="Status"><Select options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }, { value: 'PENDING', label: 'Pending' }, { value: 'SUSPENDED', label: 'Suspended' }]} /></Form.Item>
        <Form.Item name="attributes" label="Additional fields (JSON)" rules={[{ required: true }]} extra="Store fields such as contacts, addresses, capacities, expiry rules, or instructions as a JSON object."><Input.TextArea rows={8} spellCheck={false} /></Form.Item>
      </Form>
    </Modal>
  </>;
}
