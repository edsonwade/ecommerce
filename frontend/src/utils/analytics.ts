/**
 * Pure aggregation helpers for the Seller/Admin dashboards.
 * Built only from data the API already returns (order/payment headers) — no
 * extra backend. Real e-commerce dashboards (Amazon Seller Central, Shopify
 * admin) center on: revenue/GMV over time, order/payment volume, AOV, and
 * categorical breakdowns (status, payment method). These helpers feed exactly
 * those charts.
 */

export interface DayPoint {
  /** ISO day, YYYY-MM-DD — sortable + used as the x-axis key. */
  date: string;
  revenue: number;
  count: number;
}

export interface Slice {
  name: string;
  value: number;
}

/** Group items with an `amount` + optional `createdDate` into per-day revenue & count. */
export function revenueByDay<T extends { amount: number; createdDate?: string }>(
  items: T[] | undefined
): DayPoint[] {
  const map = new Map<string, { revenue: number; count: number }>();
  for (const it of items ?? []) {
    if (!it.createdDate) continue;
    const day = it.createdDate.slice(0, 10);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) continue;
    const cur = map.get(day) ?? { revenue: 0, count: 0 };
    cur.revenue += it.amount ?? 0;
    cur.count += 1;
    map.set(day, cur);
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, v]) => ({ date, revenue: Math.round(v.revenue * 100) / 100, count: v.count }));
}

/** Count items grouped by a categorical key (status, payment method, …), desc. */
export function countByKey<T>(
  items: T[] | undefined,
  key: (t: T) => string | undefined | null,
  fallback = 'Unknown'
): Slice[] {
  const map = new Map<string, number>();
  for (const it of items ?? []) {
    const k = (key(it) ?? '').toString().trim() || fallback;
    map.set(k, (map.get(k) ?? 0) + 1);
  }
  return [...map.entries()]
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value);
}

/** Average order value = total revenue / order count (0 when no orders). */
export function averageOrderValue<T extends { amount: number }>(items: T[] | undefined): number {
  const list = items ?? [];
  if (list.length === 0) return 0;
  return list.reduce((s, i) => s + (i.amount ?? 0), 0) / list.length;
}

/** Short axis label from an ISO day: "06-24". */
export function shortDay(isoDay: string): string {
  return isoDay.length >= 10 ? isoDay.slice(5) : isoDay;
}
