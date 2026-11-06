import { Button, Flex, Form, type FormInstance, type FormProps } from 'antd';
import { useTranslation } from 'react-i18next';

export function SearchFilters<Values extends object>({ form, children, onFinish, ...formProps }: FormProps<Values> & { form?: FormInstance<Values> }) {
  const { t } = useTranslation();
  return (
    <Form form={form} layout="inline" onFinish={onFinish} {...formProps}>
      <Flex wrap gap="small" align="end">{children}
        <Button htmlType="submit" type="primary">{t('search', 'Search')}</Button>
        <Button onClick={() => form?.resetFields()}>{t('clear', 'Clear')}</Button>
      </Flex>
    </Form>
  );
}
