import { useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Breadcrumb, Button, Dropdown, Input, Layout, Menu, Select, Space, Switch, Typography, Grid } from 'antd';
import {
  AppstoreOutlined, BellOutlined, BoxPlotOutlined, CarOutlined, DashboardOutlined, FileSearchOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, MoonOutlined, SearchOutlined, SettingOutlined, SunOutlined,
  TeamOutlined, UserOutlined, LogoutOutlined, SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/app/authStore';
import { usePreferencesStore } from '@/app/preferencesStore';
import { authService } from '@/features/auth/authService';
import type { Locale } from '@/i18n';

const { Sider, Header, Content } = Layout;

export function AppShell() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const [mobileOpen, setMobileOpen] = useState(false);
  const profile = useAuthStore((state) => state.profile);
  const clearAuth = useAuthStore((state) => state.clear);
  const hasPermission = useAuthStore((state) => state.hasPermission);
  const hasRole = useAuthStore((state) => state.hasRole);
  const { colorMode, locale, sidebarCollapsed, setColorMode, setLocale, toggleSidebar } = usePreferencesStore();
  const [search, setSearch] = useState('');
  const isMobile = !screens.lg;
  const collapsed = isMobile ? !mobileOpen : sidebarCollapsed;
  const toggleNavigation = () => isMobile ? setMobileOpen((open) => !open) : toggleSidebar();
  const labels: Record<string, string> = {
    app: t('appName'), staff: t('staff'), customer: t('customer'), driver: t('driver'), dashboard: t('dashboard'),
    orders: t('orders'), shipments: t('shipments'), dispatch: t('dispatch'), 'control-tower': 'Control tower', warehouse: t('warehouse'),
    users: t('users'), roles: t('roles'), permissions: t('permissionMatrix', 'Permission matrix'), settings: t('settings'),
    profile: t('profile'), sessions: t('sessions', 'Sessions'),
    'master-data': 'Master data', customers: 'Customers', suppliers: 'Suppliers', carriers: 'Carriers', products: 'Products', vehicles: 'Vehicles', drivers: 'Drivers', warehouses: 'Warehouses', regions: 'Regions',
  };
  const breadcrumbItems = location.pathname.split('/').filter(Boolean).map((segment, index, all) => ({
    title: index === all.length - 1 ? labels[segment] ?? segment : <Link to={`/${all.slice(0, index + 1).join('/')}`}>{labels[segment] ?? segment}</Link>,
  }));

  const menuItems = useMemo(() => [
    { key: '/app/staff', icon: <DashboardOutlined />, label: t('dashboard') },
    { key: '/app/staff/orders', icon: <AppstoreOutlined />, label: t('orders') },
    { key: '/app/staff/shipments', icon: <BoxPlotOutlined />, label: t('shipments') },
    { key: '/app/staff/dispatch', icon: <CarOutlined />, label: t('dispatch') },
    { key: '/app/staff/control-tower', icon: <DashboardOutlined />, label: 'Control tower' },
    { key: '/app/staff/warehouse', icon: <BoxPlotOutlined />, label: t('warehouse') },
    ...[['customers','Customers'],['suppliers','Suppliers'],['carriers','Carriers'],['products','Products'],['vehicles','Vehicles'],['drivers','Drivers'],['warehouses','Warehouse locations'],['regions','Regions'],['delivery-zones','Delivery zones'],['tariff-zones','Tariff zones'],['business-calendars','Business calendars'],['holidays','Holidays']].map(([type,label]) => ({ key: `/app/staff/master-data/${type}`, icon: <BoxPlotOutlined />, label })),
    { key: '/app/staff/users', icon: <TeamOutlined />, label: t('users'), permission: 'identity.users.read' },
    { key: '/app/staff/roles', icon: <SafetyCertificateOutlined />, label: t('roles'), permission: 'identity.roles.read' },
    { key: '/app/staff/permissions', icon: <SafetyCertificateOutlined />, label: t('permissionMatrix', 'Permission matrix'), permission: 'identity.permissions.read' },
    { key: '/app/customer', icon: <UserOutlined />, label: t('customer') },
    { key: '/app/driver', icon: <CarOutlined />, label: t('driver') },
    { key: '/tracking', icon: <FileSearchOutlined />, label: t('publicTracking') },
  ].filter((item) => {
    const isStaff = item.key.startsWith('/app/staff');
    if (isStaff && !['PLATFORM_ADMIN', 'ORGANIZATION_ADMIN', 'OPERATIONS_MANAGER', 'ORDER_CLERK', 'TRANSPORT_DISPATCHER', 'WAREHOUSE_OPERATOR'].some(hasRole)) return false;
    if (item.key === '/app/customer' && !hasRole('CUSTOMER_USER')) return false;
    if (item.key === '/app/driver' && !hasRole('DRIVER')) return false;
    return !('permission' in item) || hasPermission(item.permission);
  }), [hasPermission, hasRole, t]);

  async function logout() {
    await authService.logout().catch(() => undefined);
    clearAuth();
    navigate('/login', { replace: true });
  }

  return (
    <Layout className="app-layout">
      <Sider className="app-sider" collapsible collapsed={collapsed} collapsedWidth={isMobile ? 0 : 80} trigger={null} width={252}>
        <Link to="/app/staff" className="sider-brand">
          <span className="brand-mark">LC</span>{!collapsed && <span className="brand-name">{t('appName')}</span>}
        </Link>
        <Menu mode="inline" selectedKeys={[menuItems.find((item) => location.pathname === item.key || location.pathname.startsWith(`${item.key}/`))?.key ?? '/app/staff']}
          items={menuItems} onClick={({ key }) => { navigate(key); setMobileOpen(false); }} className="app-menu" />
        <Button type="text" className="sider-collapse" aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={toggleNavigation} />
      </Sider>
      <Layout className="main-layout">
        <Header className="app-header">
          <Button type="text" aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'} icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={toggleNavigation} />
          <Input className="global-search" allowClear prefix={<SearchOutlined />} placeholder={t('globalSearch')}
            value={search} onChange={(event) => setSearch(event.target.value)} onPressEnter={() => navigate(`/app/staff?search=${encodeURIComponent(search)}`)} />
          <Space size="middle" className="header-actions">
            <Button type="text" aria-label={t('notifications')} icon={<Badge dot><BellOutlined /></Badge>} onClick={() => navigate('/app/notifications')} />
            <Select aria-label={t('language')} value={locale} onChange={(value: Locale) => setLocale(value)} options={[
              { value: 'en', label: 'English' }, { value: 'fa', label: 'فارسی' },
            ]} />
            <Switch aria-label="Toggle dark theme" checked={colorMode === 'dark'} checkedChildren={<MoonOutlined />} unCheckedChildren={<SunOutlined />}
              onChange={(checked) => setColorMode(checked ? 'dark' : 'light')} />
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'profile', label: t('profile'), icon: <UserOutlined /> },
              { key: 'sessions', label: t('sessions', 'Sessions'), icon: <SafetyCertificateOutlined /> },
              { key: 'settings', label: t('settings'), icon: <SettingOutlined /> },
              { type: 'divider' }, { key: 'logout', label: t('signOut'), icon: <LogoutOutlined />, danger: true },
            ], onClick: ({ key }) => key === 'logout' ? logout() : navigate(['profile', 'sessions', 'settings'].includes(key) ? `/app/${key}` : `/app/staff/${key}`) }}>
              <Button type="text" className="profile-button"><Avatar size="small" icon={<UserOutlined />} />{!isMobile && <Typography.Text>{profile?.email ?? profile?.phone ?? t('profile')}</Typography.Text>}</Button>
            </Dropdown>
          </Space>
        </Header>
        <Content className="page-content">
          <Breadcrumb items={breadcrumbItems} className="page-breadcrumb" />
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
