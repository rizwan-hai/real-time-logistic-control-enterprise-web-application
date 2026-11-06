import { Tag } from 'antd';
import { useTranslation } from 'react-i18next';

const colors: Record<string, string> = {
  delivered: 'green', completed: 'green', active: 'green', in_transit: 'blue', assigned: 'cyan', pending: 'gold',
  delayed: 'orange', cancelled: 'default', failed: 'red', inactive: 'default', exception: 'volcano',
};

export function StatusTag({ status }: { status: string }) {
  const { t } = useTranslation();
  const key = status.toLowerCase().replaceAll(' ', '_');
  return <Tag color={colors[key] ?? 'default'}>{t(`status.${key}`, status.replaceAll('_', ' '))}</Tag>;
}
