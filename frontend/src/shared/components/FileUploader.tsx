import { InboxOutlined } from '@ant-design/icons';
import { Upload, message, type UploadProps } from 'antd';
import { useTranslation } from 'react-i18next';

const { Dragger } = Upload;

export function FileUploader({ onChange, accept, maxSizeMb = 20 }: {
  onChange: NonNullable<UploadProps['onChange']>; accept?: string; maxSizeMb?: number;
}) {
  const { t } = useTranslation();
  return <Dragger accept={accept} multiple beforeUpload={(file) => {
    if (file.size > maxSizeMb * 1024 * 1024) { void message.error(t('fileTooLarge', { size: maxSizeMb })); return Upload.LIST_IGNORE; }
    return false;
  }} onChange={onChange}>
    <p className="ant-upload-drag-icon"><InboxOutlined /></p>
    <p className="ant-upload-text">{t('dropFiles', 'Click or drag files into this area')}</p>
    <p className="ant-upload-hint">{t('uploadLimit', { size: maxSizeMb, defaultValue: `Maximum file size: ${maxSizeMb} MB` })}</p>
  </Dragger>;
}
