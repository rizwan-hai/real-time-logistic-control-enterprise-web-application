import { useEffect } from 'react';
import { Modal } from 'antd';
import { useBlocker } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export function UnsavedChangesGuard({ dirty }: { dirty: boolean }) {
  const blocker = useBlocker(dirty);
  const { t } = useTranslation();
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    Modal.confirm({
      title: t('unsavedTitle', 'Leave without saving?'),
      content: t('unsavedDescription', 'Your unsaved changes will be lost.'),
      okText: t('leave', 'Leave'), cancelText: t('cancel'), okButtonProps: { danger: true },
      onOk: () => blocker.proceed(), onCancel: () => blocker.reset(),
    });
  }, [blocker, t]);
  return null;
}
