import { Modal } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';

export function useConfirmDialog() {
  return (title: string, content: string, onConfirm: () => void | Promise<void>) => {
    Modal.confirm({ title, content, icon: <ExclamationCircleOutlined />, okButtonProps: { danger: true }, onOk: onConfirm });
  };
}
