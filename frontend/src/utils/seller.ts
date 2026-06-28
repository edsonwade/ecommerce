/**
 * Seller-ownership helpers.
 *
 * A product's `createdBy` is only a numeric user id for real sellers. Seed/catalog products
 * are owned by the `"system"` sentinel (and other non-numeric values are possible for legacy
 * rows). Only numeric owners have a seller business profile, so the order-detail "sold by"
 * block must skip everything else — otherwise GET /auth/sellers/{owner} hits a `Long` path
 * variable and 500s (e.g. /auth/sellers/system).
 */

/** True only when `createdBy` is a non-empty, purely numeric user id. */
export function isNumericSellerId(createdBy: string | null | undefined): createdBy is string {
  return !!createdBy && /^\d+$/.test(createdBy);
}

/** Distinct numeric owner ids from a set of products (drops "system" and other sentinels). */
export function distinctSellerIds(
  createdByValues: (string | null | undefined)[],
): string[] {
  return [...new Set(createdByValues.filter(isNumericSellerId))];
}
