import { Empty } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ReactNode } from 'react';

export function EmptyState({ title, description, action }: { title?: string; description?: string; action?: ReactNode }) {
  const { t } = useTranslation();
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><strong>{title ?? t('emptyTitle')}</strong><div>{description ?? t('emptyDescription')}</div></>}>
    {action}
  </Empty>;
}
