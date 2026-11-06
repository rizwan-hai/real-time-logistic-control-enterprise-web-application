import { Table, type TableProps } from 'antd';

export function DataTable<Row extends object>(props: TableProps<Row>) {
  return <Table<Row> size="middle" rowKey={props.rowKey ?? 'id'} scroll={{ x: 'max-content' }} {...props} />;
}
