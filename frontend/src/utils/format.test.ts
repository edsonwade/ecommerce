import { describe, it, expect } from 'vitest';
import { formatCurrency, formatDate, formatDateTime, truncate } from './format';

describe('formatCurrency', () => {
  it('formats a dollar amount with USD symbol', () => {
    expect(formatCurrency(29.99)).toBe('$29.99');
  });

  it('formats zero correctly', () => {
    expect(formatCurrency(0)).toBe('$0.00');
  });

  it('formats large amounts with commas', () => {
    expect(formatCurrency(1234567.89)).toBe('$1,234,567.89');
  });
});

describe('formatDate / formatDateTime', () => {
  it('formats a valid ISO-8601 string', () => {
    expect(formatDate('2026-06-21T21:23:19')).toBe('Jun 21, 2026');
  });

  it('parses Jackson numeric-array timestamps without crashing', () => {
    // [year, month, day, hour, minute, second] — what LocalDateTime emits when
    // WRITE_DATES_AS_TIMESTAMPS is enabled. Must not throw "date value is not finite".
    expect(formatDate([2026, 6, 21, 21, 23, 19] as unknown as string)).toBe('Jun 21, 2026');
  });

  it('returns the fallback for null / empty / invalid input instead of throwing', () => {
    expect(formatDate(null as unknown as string)).toBe('—');
    expect(formatDate('')).toBe('—');
    expect(formatDate('not-a-date')).toBe('—');
    expect(formatDateTime(undefined as unknown as string)).toBe('—');
  });
});

describe('truncate', () => {
  it('returns the string unchanged when shorter than maxLength', () => {
    expect(truncate('hello', 10)).toBe('hello');
  });

  it('returns the string unchanged when equal to maxLength', () => {
    expect(truncate('hello', 5)).toBe('hello');
  });

  it('truncates and appends ellipsis when string is too long', () => {
    expect(truncate('hello world', 8)).toBe('hello...');
  });
});
