import { Controller, useFormContext, type FieldPath, type FieldValues } from 'react-hook-form';
import { Form, Input, type InputProps } from 'antd';

export function FormField<Values extends FieldValues>({ name, label, ...inputProps }: {
  name: FieldPath<Values>; label: string;
} & InputProps) {
  const { control, formState: { errors } } = useFormContext<Values>();
  const error = name.split('.').reduce<unknown>((value, key) => (value as Record<string, unknown> | undefined)?.[key], errors);
  const message = (error as { message?: string } | undefined)?.message;
  return (
    <Controller control={control} name={name} render={({ field }) => (
      <Form.Item label={label} validateStatus={message ? 'error' : undefined} help={message}>
        <Input {...field} {...inputProps} value={field.value ?? ''} />
      </Form.Item>
    )} />
  );
}
