import { describe, it, expect } from 'vitest';
import { render, screen } from '@test/test-utils';
import OrderStatusBadge from './OrderStatusBadge';

describe('OrderStatusBadge', () => {
  it('renders CONFIRMED with correct label', () => {
    render(<OrderStatusBadge status="CONFIRMED" />);
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
  });

  it('renders CANCELLED with correct label', () => {
    render(<OrderStatusBadge status="CANCELLED" />);
    expect(screen.getByText('Cancelled')).toBeInTheDocument();
  });

  it('renders REQUESTED with correct label', () => {
    render(<OrderStatusBadge status="REQUESTED" />);
    expect(screen.getByText('Requested')).toBeInTheDocument();
  });

  it('renders INVENTORY_RESERVED with correct label', () => {
    render(<OrderStatusBadge status="INVENTORY_RESERVED" />);
    expect(screen.getByText('Inventory Reserved')).toBeInTheDocument();
  });

  it('falls back to raw status for unknown values', () => {
    render(<OrderStatusBadge status="UNKNOWN_STATUS" />);
    expect(screen.getByText('UNKNOWN_STATUS')).toBeInTheDocument();
  });
});
