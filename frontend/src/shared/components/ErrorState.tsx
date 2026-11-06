import { Alert, Button, Result } from 'antd';
import { useTranslation } from 'react-i18next';

export function ErrorState({ message, onRetry, compact = false }: { message?: string; onRetry?: () => void; compact?: boolean }) {
  const { t } = useTranslation();
  if (compact) return <Alert type="error" showIcon message={message ?? t('errorTitle')} action={onRetry && <Button size="small" onClick={onRetry}>{t('retry')}</Button>} />;
  return <Result status="error" title={t('errorTitle')} subTitle={message} extra={onRetry && <Button type="primary" onClick={onRetry}>{t('retry')}</Button>} />;
}
