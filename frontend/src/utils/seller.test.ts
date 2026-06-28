import { describe, it, expect } from 'vitest';
import { isNumericSellerId, distinctSellerIds } from './seller';

describe('isNumericSellerId', () => {
  it('accepts a purely numeric id', () => {
    expect(isNumericSellerId('7')).toBe(true);
    expect(isNumericSellerId('12345')).toBe(true);
  });

  it('rejects the "system" sentinel (the bug that 500d /auth/sellers/system)', () => {
    expect(isNumericSellerId('system')).toBe(false);
  });

  it('rejects null, undefined and empty', () => {
    expect(isNumericSellerId(null)).toBe(false);
    expect(isNumericSellerId(undefined)).toBe(false);
    expect(isNumericSellerId('')).toBe(false);
  });

  it('rejects non-numeric / mixed values', () => {
    expect(isNumericSellerId('admin@x.com')).toBe(false);
    expect(isNumericSellerId('7a')).toBe(false);
    expect(isNumericSellerId(' 7')).toBe(false);
  });
});

describe('distinctSellerIds', () => {
  it('keeps only distinct numeric owners and drops sentinels', () => {
    expect(distinctSellerIds(['7', 'system', '7', null, '9', undefined, 'system'])).toEqual([
      '7',
      '9',
    ]);
  });

  it('returns an empty array when every owner is a sentinel', () => {
    expect(distinctSellerIds(['system', 'system', null])).toEqual([]);
  });
});
