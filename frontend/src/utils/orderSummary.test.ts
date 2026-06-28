import { describe, expect, it } from 'vitest';
import { deriveSellerSummary } from './orderSummary';
import type { ProductResponse } from '@api/types';

function product(id: number, price: number): ProductResponse {
  return {
    id,
    name: `P${id}`,
    description: '',
    availableQuantity: 10,
    price,
    categoryId: 1,
    categoryName: 'cat',
    categoryDescription: '',
  };
}

describe('deriveSellerSummary', () => {
  it('sums only the visible (seller-scoped) lines — never the whole basket', () => {
    const products = new Map([
      [10, product(10, 299.99)],
    ]);
    const { subtotal, tax, total } = deriveSellerSummary(
      [{ productId: 10, quantity: 1 }],
      products,
      0.23,
    );
    expect(subtotal).toBeCloseTo(299.99, 2);
    expect(tax).toBeCloseTo(68.9977, 2);
    expect(total).toBeCloseTo(368.9877, 2);
  });

  it('multiplies by quantity', () => {
    const products = new Map([[10, product(10, 50)]]);
    const { subtotal, total } = deriveSellerSummary(
      [{ productId: 10, quantity: 3 }],
      products,
      0,
    );
    expect(subtotal).toBe(150);
    expect(total).toBe(150);
  });

  it('skips lines whose product has not resolved yet', () => {
    const products = new Map([[10, product(10, 100)]]);
    const { subtotal } = deriveSellerSummary(
      [
        { productId: 10, quantity: 1 },
        { productId: 99, quantity: 5 }, // not in map → skipped
      ],
      products,
      0,
    );
    expect(subtotal).toBe(100);
  });

  it('treats a missing/zero tax rate as no tax', () => {
    const products = new Map([[10, product(10, 100)]]);
    expect(deriveSellerSummary([{ productId: 10, quantity: 1 }], products, 0).tax).toBe(0);
  });

  it('returns zeros for an empty line set', () => {
    expect(deriveSellerSummary([], new Map(), 0.23)).toEqual({ subtotal: 0, tax: 0, total: 0 });
  });
});
