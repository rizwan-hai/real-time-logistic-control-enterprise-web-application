import { Timeline as AntTimeline } from 'antd';
import type { ReactNode } from 'react';

export interface ActivityItem { id: string; title: ReactNode; description?: ReactNode; timestamp: ReactNode; color?: string }

export function ActivityTimeline({ items }: { items: ActivityItem[] }) {
  return <AntTimeline items={items.map((item) => ({ color: item.color, children: <><strong>{item.title}</strong><div>{item.description}</div><small>{item.timestamp}</small></> }))} />;
}
