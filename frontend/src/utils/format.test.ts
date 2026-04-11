import { describe, it, expect } from 'vitest';
import { formatCurrency, truncate } from './format';

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
