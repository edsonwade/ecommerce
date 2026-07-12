import { describe, it, expect } from 'vitest';
import { reconcileSellerStatus } from './sellerStatusSync';

describe('reconcileSellerStatus', () => {
  it('does nothing when there is no live value yet', () => {
    expect(reconcileSellerStatus('PENDING_APPROVAL', null)).toBe('none');
    expect(reconcileSellerStatus('PENDING_APPROVAL', undefined)).toBe('none');
  });

  it('does nothing when live matches the stored status', () => {
    expect(reconcileSellerStatus('PENDING_APPROVAL', 'PENDING_APPROVAL')).toBe('none');
    expect(reconcileSellerStatus('APPROVED', 'APPROVED')).toBe('none');
    expect(reconcileSellerStatus('SUSPENDED', 'SUSPENDED')).toBe('none');
  });

  it('unlocks when a pending seller becomes approved', () => {
    expect(reconcileSellerStatus('PENDING_APPROVAL', 'APPROVED')).toBe('unlock');
  });

  it('suspends when an approved seller becomes suspended', () => {
    expect(reconcileSellerStatus('APPROVED', 'SUSPENDED')).toBe('suspend');
  });

  it('suspends when a pending seller is suspended outright', () => {
    expect(reconcileSellerStatus('PENDING_APPROVAL', 'SUSPENDED')).toBe('suspend');
  });

  it('re-syncs when a seller is re-queued to pending (e.g. from approved)', () => {
    expect(reconcileSellerStatus('APPROVED', 'PENDING_APPROVAL')).toBe('sync');
  });

  it('unlocks a suspended seller who is reactivated to approved', () => {
    expect(reconcileSellerStatus('SUSPENDED', 'APPROVED')).toBe('unlock');
  });

  it('treats a null stored status (fresh session) becoming approved as an unlock', () => {
    expect(reconcileSellerStatus(null, 'APPROVED')).toBe('unlock');
  });
});
