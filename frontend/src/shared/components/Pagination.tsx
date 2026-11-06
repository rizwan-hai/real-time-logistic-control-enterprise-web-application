import { Pagination as AntPagination, type PaginationProps } from 'antd';

export function Pagination(props: PaginationProps) {
  return <AntPagination showSizeChanger showTotal={(total, range) => `${range[0]} to ${range[1]} of ${total}`} {...props} />;
}
