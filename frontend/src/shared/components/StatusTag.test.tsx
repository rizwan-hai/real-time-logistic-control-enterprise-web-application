import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusTag } from './StatusTag';

describe('StatusTag', () => {
  it('renders the status label', () => {
    render(<StatusTag status="active" />);
    expect(screen.getByText(/active/i)).toBeInTheDocument();
  });
});
