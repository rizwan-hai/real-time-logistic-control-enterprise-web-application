import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Col, DatePicker, Input, Row, Select, Space, Statistic, Table, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import dayjs, { type Dayjs } from 'dayjs';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { useAuthStore } from '@/app/authStore';
import { ErrorState } from '@/shared/components/ErrorState';
import { LoadingSkeleton } from '@/shared/components/LoadingSkeleton';

type DeliveryMetrics = {
  deliveryStops?: number; completedDeliveries?: number; onTimeDeliveryPercent?: number | null;
  averageLeadTimeHours?: number | null; delayRatePercent?: number | null;
  firstAttemptSuccessRatePercent?: number | null; failedDeliveryRatePercent?: number | null;
};
type FleetMetrics = {
  completedTrips?: number; distanceKm?: number; driverPerformancePercent?: number | null;
  vehicleUtilizationPercent?: number | null; costPerKilometer?: Array<{ currency: string; amountPerKm: number }>;
  fuelEfficiency?: { unit: string; quantityPerKm: number } | null; fleetAvailabilityPercent?: number | null;
};
type WarehouseMetrics = {
  inventoryItems?: number; expiredStockItems?: number; expiringWithin30DaysItems?: number;
  stockAccuracyPercent?: number | null; pickingPerformancePercent?: number | null;
  inventoryTurnover?: number | null; lowStockCount?: number | null; receivingPerformancePercent?: number | null;
};
type DashboardData = {
  filters: { from: string; to: string };
  delivery?: DeliveryMetrics;
  fleet?: FleetMetrics;
  warehouse?: WarehouseMetrics;
  financial?: { operatingCostByCurrency?: Array<{ currency: string; operating_cost: number }> };
  unavailableMetrics: string[];
};
type TrendRow = { report_date: string; status: string; shipment_count: number };
type DeliveryRow = {
  shipmentNumber: string; shipmentStatus: string; orderNumber: string; customerId: string;
  stopSequence: number; stopStatus: string; scheduledAt: string | null; completedAt: string | null; failedAttempts: number;
};
type ExportJob = { id: string; status: string; exportFormat: string; rowCount: number; errorMessage?: string };
type ReportSchedule = { id: string; frequency: string; exportFormat: string; nextRunAt: string };

const currencyCosts = (value: DashboardData['financial']) => value?.operatingCostByCurrency ?? [];

export function AnalyticsDashboard({ customer = false }: { customer?: boolean }) {
  const profile = useAuthStore((state) => state.profile);
  const queryClient = useQueryClient();
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()]);
  const [customerId, setCustomerId] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [vehicleId, setVehicleId] = useState('');
  const [driverId, setDriverId] = useState('');
  const [drillDown, setDrillDown] = useState(false);
  const [frequency, setFrequency] = useState('WEEKLY');
  const [toast, contextHolder] = message.useMessage();
  const params = useMemo(() => ({
    from: range[0].format('YYYY-MM-DD'),
    to: range[1].format('YYYY-MM-DD'),
    ...(customerId.trim() && !customer ? { customerId: customerId.trim() } : {}),
    ...(warehouseId.trim() ? { warehouseId: warehouseId.trim() } : {}),
    ...(vehicleId.trim() ? { vehicleId: vehicleId.trim() } : {}),
    ...(driverId.trim() ? { driverId: driverId.trim() } : {}),
  }), [range, customerId, warehouseId, vehicleId, driverId, customer]);
  const queryKey = ['analytics', customer ? 'customer' : 'staff', params];
  const reportQuery = useQuery({
    queryKey,
    queryFn: async () => {
      const [dashboard, trends] = await Promise.all([
        apiClient.get<{ data: DashboardData }>(apiPath('reports/dashboard'), { params }),
        apiClient.get<{ data: TrendRow[] }>(apiPath('reports/trends'), { params }),
      ]);
      return { dashboard: dashboard.data.data, trends: trends.data.data };
    },
  });
  const drillQuery = useQuery({
    queryKey: ['analytics', 'deliveries', params],
    enabled: drillDown,
    queryFn: async () => (await apiClient.get<{ data: DeliveryRow[] }>(apiPath('reports/deliveries'), { params: { ...params, size: 100 } })).data.data,
  });
  const jobsQuery = useQuery({
    queryKey: ['analytics', 'exports'],
    enabled: reportQuery.data?.dashboard.delivery !== undefined,
    queryFn: async () => (await apiClient.get<{ data: ExportJob[] }>(apiPath('reports/exports'))).data.data,
    refetchInterval: (query) => query.state.data?.some((job) => job.status === 'QUEUED' || job.status === 'RUNNING') ? 3000 : false,
  });
  const schedulesQuery = useQuery({
    queryKey: ['analytics', 'schedules'],
    enabled: reportQuery.data?.dashboard.delivery !== undefined,
    queryFn: async () => (await apiClient.get<{ data: ReportSchedule[] }>(apiPath('reports/schedules'))).data.data,
  });
  const exportMutation = useMutation({
    mutationFn: async (format: string) => (await apiClient.post<{ data: { id: string } }>(
      apiPath('reports/exports'), null, { params: { ...params, format } },
    )).data.data,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['analytics', 'exports'] }); toast.success('Export queued'); },
    onError: () => toast.error('Unable to queue the export'),
  });
  const scheduleMutation = useMutation({
    mutationFn: async () => apiClient.post(apiPath('reports/schedules'), null, { params: { ...params, frequency } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['analytics', 'schedules'] });
      toast.success('Report schedule created');
    },
    onError: () => toast.error('Unable to create the report schedule'),
  });
  const deleteScheduleMutation = useMutation({
    mutationFn: async (id: string) => apiClient.delete(apiPath(`reports/schedules/${id}`)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['analytics', 'schedules'] }),
    onError: () => toast.error('Unable to delete the report schedule'),
  });
  const downloadMutation = useMutation({
    mutationFn: async (job: ExportJob) => {
      const response = await apiClient.get(apiPath(`reports/exports/${job.id}/download`), { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `delivery-report.${job.exportFormat.toLowerCase()}`;
      anchor.click();
      URL.revokeObjectURL(url);
    },
    onError: () => toast.error('Unable to download the report'),
  });

  if (reportQuery.isLoading) return <LoadingSkeleton rows={5} />;
  if (reportQuery.isError || !reportQuery.data) return <ErrorState onRetry={() => void reportQuery.refetch()} />;
  const dashboard = reportQuery.data.dashboard;
  const trends = reportQuery.data.trends;
  const statusTotals = trends.reduce<Record<string, number>>((totals, point) => {
    totals[point.status] = (totals[point.status] ?? 0) + point.shipment_count;
    return totals;
  }, {});
  const groupedTrends = new Map<string, Record<string, number>>();
  trends.forEach((point) => {
    const date = String(point.report_date).slice(0, 10);
    groupedTrends.set(date, { ...groupedTrends.get(date), [point.status]: point.shipment_count });
  });
  const dates = [...groupedTrends.keys()];
  const trendStatuses = [...new Set(trends.map((row) => row.status))];
  const trendOption: EChartsOption = {
    tooltip: { trigger: 'axis' }, legend: { data: trendStatuses }, grid: { left: 40, right: 20, bottom: 45, containLabel: true },
    xAxis: { type: 'category', data: dates }, yAxis: { type: 'value', minInterval: 1 },
    series: trendStatuses.map((status) => ({
      name: status, type: 'line', smooth: true, data: dates.map((date) => groupedTrends.get(date)?.[status] ?? 0),
    })),
  };
  const statusOption: EChartsOption = {
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['38%', '68%'], data: Object.entries(statusTotals).map(([name, value]) => ({ name, value })) }],
  };
  const performanceKeys: Array<[string, number]> = [
    ['On-time delivery', dashboard.delivery?.onTimeDeliveryPercent],
    ['Delay rate', dashboard.delivery?.delayRatePercent],
    ['First-attempt success', dashboard.delivery?.firstAttemptSuccessRatePercent],
    ['Failed delivery', dashboard.delivery?.failedDeliveryRatePercent],
    ['Driver performance', dashboard.fleet?.driverPerformancePercent],
    ['Vehicle utilization', dashboard.fleet?.vehicleUtilizationPercent],
    ['Stock accuracy', dashboard.warehouse?.stockAccuracyPercent],
    ['Picking performance', dashboard.warehouse?.pickingPerformancePercent],
  ].filter((entry): entry is [string, number] => typeof entry[1] === 'number');
  const performanceOption: EChartsOption = {
    tooltip: { trigger: 'axis' }, grid: { left: 40, right: 20, bottom: 35, containLabel: true },
    xAxis: { type: 'category', data: performanceKeys.map(([key]) => key.replace(/Percent$/, '').replace(/([A-Z])/g, ' $1')) },
    yAxis: { type: 'value', max: 100 }, series: [{ type: 'bar', data: performanceKeys.map(([, value]) => value), itemStyle: { color: '#3478f6' } }],
  };
  const columns: ColumnsType<DeliveryRow> = [
    { title: 'Shipment', dataIndex: 'shipmentNumber' }, { title: 'Order', dataIndex: 'orderNumber' },
    { title: 'Status', dataIndex: 'stopStatus' }, { title: 'Scheduled', dataIndex: 'scheduledAt' },
    { title: 'Completed', dataIndex: 'completedAt' }, { title: 'Failed attempts', dataIndex: 'failedAttempts' },
  ];
  const metrics: Array<[string, number | null | undefined, string?]> = [];
  if (dashboard.delivery) metrics.push(
    ['On-time delivery', dashboard.delivery.onTimeDeliveryPercent, '%'],
    ['Average lead time', dashboard.delivery.averageLeadTimeHours, 'h'],
    ['Delay rate', dashboard.delivery.delayRatePercent, '%'],
    ['First-attempt success', dashboard.delivery.firstAttemptSuccessRatePercent, '%'],
    ['Failed delivery', dashboard.delivery.failedDeliveryRatePercent, '%'],
  );
  if (dashboard.fleet) metrics.push(
    ['Completed trips', dashboard.fleet.completedTrips],
    ['Distance km', dashboard.fleet.distanceKm],
    ['Vehicle utilization', dashboard.fleet.vehicleUtilizationPercent, '%'],
    ['Driver performance', dashboard.fleet.driverPerformancePercent, '%'],
  );
  if (dashboard.warehouse) metrics.push(
    ['Inventory items', dashboard.warehouse.inventoryItems],
    ['Expired stock', dashboard.warehouse.expiredStockItems],
    ['Expiring within 30 days', dashboard.warehouse.expiringWithin30DaysItems],
    ['Stock accuracy', dashboard.warehouse.stockAccuracyPercent, '%'],
    ['Picking performance', dashboard.warehouse.pickingPerformancePercent, '%'],
  );
  const kpiItems = [
    ...metrics.filter(([, value]) => value !== undefined)
      .map(([title, value, suffix]) => ({ title, value: value ?? 'N/A', suffix })),
    ...(dashboard.fleet?.costPerKilometer ?? [])
      .map((cost) => ({ title: `Cost per km (${cost.currency})`, value: cost.amountPerKm })),
    ...(dashboard.fleet?.fuelEfficiency
      ? [{ title: `Fuel per km (${dashboard.fleet.fuelEfficiency.unit})`, value: dashboard.fleet.fuelEfficiency.quantityPerKm }]
      : []),
    ...currencyCosts(dashboard.financial).map((cost) => ({ title: `Operating cost (${cost.currency})`, value: cost.operating_cost })),
  ];
  const displayName = profile?.email ?? (customer ? 'Customer' : 'Operations');

  return <>
    {contextHolder}
    <div className="page-title"><Typography.Title level={2}>{customer ? 'Customer dashboard' : 'Operations & analytics'}</Typography.Title>
      <Typography.Paragraph type="secondary">Reporting for {displayName}, filtered by your access scope.</Typography.Paragraph></div>
    <Card className="section-card">
      <Space wrap>
        <DatePicker.RangePicker value={range} allowClear={false} onChange={(value) => {
          if (value?.[0] && value[1]) setRange([value[0], value[1]]);
        }} />
        {!customer && <Input aria-label="Customer ID filter" placeholder="Customer ID" value={customerId} onChange={(event) => setCustomerId(event.target.value)} />}
        <Input aria-label="Warehouse ID filter" placeholder="Warehouse ID" value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)} />
        <Input aria-label="Vehicle ID filter" placeholder="Vehicle ID" value={vehicleId} onChange={(event) => setVehicleId(event.target.value)} />
        <Input aria-label="Driver ID filter" placeholder="Driver ID" value={driverId} onChange={(event) => setDriverId(event.target.value)} />
      </Space>
    </Card>
    {dashboard.unavailableMetrics.length > 0 && <Alert className="section-card" type="info" showIcon
      message="Some KPIs are unavailable until their source data is recorded."
      description={dashboard.unavailableMetrics.map((metric) => metric.split(':')[0]).join(', ')} />}
    <Row gutter={[16, 16]} className="section-card">
      {kpiItems.map((item) => <Col key={item.title} xs={24} sm={12} xl={6}><Card><Statistic title={item.title} value={item.value} suffix={item.suffix} /></Card></Col>)}
    </Row>
    <Row gutter={[16, 16]} className="section-card">
      {dashboard.delivery && <>
        <Col xs={24} xl={16}><Card title="Shipment trends"><ReactECharts option={trendOption} style={{ height: 320 }} /></Card></Col>
        <Col xs={24} xl={8}><Card title="Shipment status"><ReactECharts option={statusOption} style={{ height: 320 }} /></Card></Col>
      </>}
      {performanceKeys.length > 0 && <Col xs={24}><Card title="Performance"><ReactECharts option={performanceOption} style={{ height: 280 }} /></Card></Col>}
      {dashboard.financial?.operatingCostByCurrency && dashboard.financial.operatingCostByCurrency.length > 0
        && <Col xs={24}><Card title="Operating cost by currency"><ReactECharts option={{
          tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: currencyCosts(dashboard.financial).map((cost) => cost.currency) },
          yAxis: { type: 'value' }, series: [{ type: 'bar', data: currencyCosts(dashboard.financial).map((cost) => cost.operating_cost) }],
        }} style={{ height: 260 }} /></Card></Col>}
      {dashboard.warehouse && <Col xs={24}><Card title="Inventory status"><ReactECharts option={{
        tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: ['Inventory items', 'Expiring in 30 days', 'Expired'] },
        yAxis: { type: 'value' }, series: [{ type: 'bar', data: [
          dashboard.warehouse.inventoryItems ?? 0, dashboard.warehouse.expiringWithin30DaysItems ?? 0,
          dashboard.warehouse.expiredStockItems ?? 0,
        ] }],
      }} style={{ height: 260 }} /></Card></Col>}
    </Row>
    {dashboard.delivery && <Card title="Delivery detail" className="section-card" extra={<Button onClick={() => setDrillDown((value) => !value)}>{drillDown ? 'Hide details' : 'Drill down'}</Button>}>
      {drillDown && <Table<DeliveryRow> rowKey={(row) => `${row.shipmentNumber}-${row.stopSequence}`} columns={columns}
        dataSource={drillQuery.data ?? []} loading={drillQuery.isLoading} pagination={{ pageSize: 10 }} />}
    </Card>}
    {dashboard.delivery && <Card title="Exports & schedules" className="section-card">
      <Space wrap>
        {['CSV', 'XLSX', 'PDF'].map((format) => <Button key={format} loading={exportMutation.isPending}
          onClick={() => exportMutation.mutate(format)}>Export {format}</Button>)}
        <Select aria-label="Schedule frequency" value={frequency} onChange={setFrequency}
          options={[{ value: 'DAILY', label: 'Daily' }, { value: 'WEEKLY', label: 'Weekly' }, { value: 'MONTHLY', label: 'Monthly' }]} />
        <Button loading={scheduleMutation.isPending} onClick={() => scheduleMutation.mutate()}>Schedule report</Button>
      </Space>
      {jobsQuery.isError && <ErrorState compact />}
      <Table<ExportJob> className="section-card" rowKey="id" pagination={false} dataSource={jobsQuery.data ?? []}
        columns={[
          { title: 'Format', dataIndex: 'exportFormat' }, { title: 'Status', dataIndex: 'status' },
          { title: 'Rows', dataIndex: 'rowCount' }, { title: 'Error', dataIndex: 'errorMessage' },
          { title: 'Action', render: (_, job) => job.status === 'COMPLETED'
            ? <Button size="small" onClick={() => downloadMutation.mutate(job)} loading={downloadMutation.isPending}>Download</Button> : null },
        ]} />
      {schedulesQuery.isError && <ErrorState compact />}
      <Table<ReportSchedule> className="section-card" rowKey="id" pagination={false} dataSource={schedulesQuery.data ?? []}
        columns={[
          { title: 'Frequency', dataIndex: 'frequency' }, { title: 'Format', dataIndex: 'exportFormat' },
          { title: 'Next run', dataIndex: 'nextRunAt' },
          { title: 'Action', render: (_, schedule) => <Button danger size="small" loading={deleteScheduleMutation.isPending}
            onClick={() => deleteScheduleMutation.mutate(schedule.id)}>Delete</Button> },
        ]} />
    </Card>}
  </>;
}
