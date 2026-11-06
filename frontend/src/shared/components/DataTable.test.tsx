import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DataTable } from './DataTable';

describe('DataTable', () => {
  it('renders rows from typed table data', () => {
    render(<DataTable dataSource={[{ id: 'order-1', reference: 'ORD-1001' }]} columns={[{ title: 'Reference', dataIndex: 'reference' }]} />);
    expect(screen.getByText('ORD-1001')).toBeInTheDocument();
  });
});
