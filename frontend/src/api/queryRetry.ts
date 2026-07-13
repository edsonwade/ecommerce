/**
 * Shared TanStack Query retry policy.
 *
 * Extracted from the QueryClient config so the logic is unit-testable. The goal is to
 * self-heal genuinely transient conditions (a cold-start 503 fallback, a 502/504, or a
 * dropped connection surfacing as status 0) WITHOUT letting a flaky hop stack into a
 * multi-second stall. The previous policy retried those up to 4 times with up to 2s of
 * backoff — worst case ~8s per query, and several such queries on one page produced the
 * "navigation takes a minute" reports. We keep the resilience but bound it hard.
 */

const TRANSIENT_STATUSES = new Set([0, 502, 503, 504]);

/** Max retries for a transient condition (in addition to the first attempt). */
export const MAX_TRANSIENT_RETRIES = 2;
/** Non-transient errors (404/403/401/400) are deterministic — one retry at most. */
export const MAX_OTHER_RETRIES = 1;

export function isTransientStatus(status: number | undefined): boolean {
  return status !== undefined && TRANSIENT_STATUSES.has(status);
}

/** TanStack `retry` predicate. */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  const status = (error as { status?: number } | null)?.status;
  return isTransientStatus(status)
    ? failureCount < MAX_TRANSIENT_RETRIES
    : failureCount < MAX_OTHER_RETRIES;
}

/**
 * TanStack `retryDelay`. Bounded exponential backoff: 250ms, 500ms, capped at 1s.
 * With MAX_TRANSIENT_RETRIES = 2 the total added backoff is at most ~0.75s (was ~4.1s).
 */
export function queryRetryDelay(attempt: number): number {
  return Math.min(250 * 2 ** attempt, 1000);
}
