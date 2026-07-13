import { describe, it, expect } from 'vitest';
import {
  shouldRetryQuery,
  queryRetryDelay,
  isTransientStatus,
  MAX_TRANSIENT_RETRIES,
  MAX_OTHER_RETRIES,
} from './queryRetry';

describe('isTransientStatus', () => {
  it.each([0, 502, 503, 504])('treats %i as transient', (status) => {
    expect(isTransientStatus(status)).toBe(true);
  });

  it.each([400, 401, 403, 404, 409, 500])('treats %i as non-transient', (status) => {
    expect(isTransientStatus(status)).toBe(false);
  });

  it('treats a missing status as non-transient', () => {
    expect(isTransientStatus(undefined)).toBe(false);
  });
});

describe('shouldRetryQuery', () => {
  const err = (status?: number) => ({ status });

  it('retries a transient error up to MAX_TRANSIENT_RETRIES', () => {
    for (let n = 0; n < MAX_TRANSIENT_RETRIES; n++) {
      expect(shouldRetryQuery(n, err(503))).toBe(true);
    }
    expect(shouldRetryQuery(MAX_TRANSIENT_RETRIES, err(503))).toBe(false);
  });

  it('retries a dropped connection (status 0) but stops at the transient cap', () => {
    expect(shouldRetryQuery(0, err(0))).toBe(true);
    expect(shouldRetryQuery(MAX_TRANSIENT_RETRIES, err(0))).toBe(false);
  });

  it('never exceeds the old 4-retry behaviour (regression: retry storm)', () => {
    // The whole point of the fix: a flaky network hop must not stack 4 retries.
    expect(MAX_TRANSIENT_RETRIES).toBeLessThanOrEqual(2);
    expect(shouldRetryQuery(3, err(503))).toBe(false);
    expect(shouldRetryQuery(3, err(0))).toBe(false);
  });

  it('retries deterministic errors (404/403/401/400) at most once', () => {
    for (const status of [400, 401, 403, 404]) {
      expect(shouldRetryQuery(0, err(status))).toBe(true);
      expect(shouldRetryQuery(MAX_OTHER_RETRIES, err(status))).toBe(false);
    }
  });

  it('handles errors without a status field (treated as non-transient)', () => {
    expect(shouldRetryQuery(0, new Error('boom'))).toBe(true);
    expect(shouldRetryQuery(1, new Error('boom'))).toBe(false);
  });

  it('handles null error (treated as non-transient)', () => {
    expect(shouldRetryQuery(0, null)).toBe(true);
    expect(shouldRetryQuery(1, null)).toBe(false);
  });
});

describe('queryRetryDelay', () => {
  it('backs off exponentially from 250ms', () => {
    expect(queryRetryDelay(0)).toBe(250);
    expect(queryRetryDelay(1)).toBe(500);
  });

  it('caps every delay at 1s', () => {
    expect(queryRetryDelay(2)).toBe(1000);
    expect(queryRetryDelay(10)).toBe(1000);
  });

  it('bounds total worst-case backoff under 1s for the transient cap (regression: multi-second stall)', () => {
    let total = 0;
    for (let n = 0; n < MAX_TRANSIENT_RETRIES; n++) total += queryRetryDelay(n);
    expect(total).toBeLessThanOrEqual(1000);
  });
});
