import { Skeleton, Space } from 'antd';

export function LoadingSkeleton({ rows = 4 }: { rows?: number }) {
  return <Space direction="vertical" size="middle" className="full-width"><Skeleton active title paragraph={{ rows: 1 }} />{Array.from({ length: rows }, (_, index) => <Skeleton key={index} active title={false} paragraph={{ rows: 1, width: '100%' }} />)}</Space>;
}
