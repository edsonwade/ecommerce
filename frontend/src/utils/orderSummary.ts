import type { ProductResponse } from '@api/types';

export interface DerivedSummary {
  subtotal: number;
  tax: number;
  total: number;
}

/** A minimal line shape — only what the summary math needs. */
interface SummarisableLine {
  productId: number;
  quantity: number;
}

/**
 * Derives Subtotal / IVA / Total from a set of order lines and their resolved products,
 * using the platform's tax-exclusive model (tax added on top of the subtotal).
 *
 * Used by the SELLER order-detail view: the backend already returns only the seller's own
 * lines, and the order-level totals on the OrderResponse describe the WHOLE basket (every
 * seller's money). Showing those to a seller leaks other sellers' revenue, so the seller's
 * summary must be computed from their visible lines instead.
 *
 * Lines whose product has not resolved yet are skipped; the figure settles as the product
 * queries complete, mirroring the items table (which uses the same price × quantity).
 */
export function deriveSellerSummary(
  lines: SummarisableLine[],
  productsById: Map<number, ProductResponse>,
  taxRate: number,
): DerivedSummary {
  const subtotal = lines.reduce((sum, line) => {
    const price = productsById.get(line.productId)?.price;
    return price != null ? sum + price * line.quantity : sum;
  }, 0);
  const tax = subtotal * (taxRate ?? 0);
  return { subtotal, tax, total: subtotal + tax };
}
