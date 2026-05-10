import { describe, it, expect } from 'vitest';
import { normalizeError } from './client';

describe('normalizeError — BUG-005 sanitization', () => {

  it('should strip Reference UUID from a server error message', () => {
    const error = {
      status: 500,
      message: 'Internal error processing order. Reference: [a6a30411-8ca2-4b73-9c04-013e46c4cb15]',
    };

    const result = normalizeError(error);
    expect(result.message).not.toContain('Reference:');
    expect(result.message).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}/);
    expect(result.message).toBe('Internal error processing order.');
  });

  it('should strip Reference UUID without brackets', () => {
    const error = {
      status: 500,
      message: 'Internal error in gateway. Reference: a6a30411-8ca2-4b73-9c04-013e46c4cb15',
    };

    const result = normalizeError(error);
    expect(result.message).not.toContain('Reference:');
    expect(result.message).toBe('Internal error in gateway.');
  });

  it('should leave clean messages untouched', () => {
    const error = {
      status: 500,
      message: 'An unexpected error occurred. Please try again later.',
    };

    const result = normalizeError(error);
    expect(result.message).toBe('An unexpected error occurred. Please try again later.');
  });

  it('should handle messages with no references gracefully', () => {
    const error = {
      status: 404,
      message: 'Order with ID [42] not found.',
    };

    const result = normalizeError(error);
    expect(result.message).toBe('Order with ID [42] not found.');
  });

  it('should return default message for network errors', () => {
    const result = normalizeError(new Error('Network error'));
    expect(result.message).toBe('Network error — check your connection');
    expect(result.status).toBe(0);
  });

  it('should preserve status code from the original error', () => {
    const error = {
      status: 500,
      message: 'Internal error. Reference: [abc-123]',
    };

    const result = normalizeError(error);
    expect(result.status).toBe(500);
  });
});
