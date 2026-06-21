export function formatCurrency(amount: number, currency = 'USD'): string {
  const value = Number(amount);
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(
    Number.isFinite(value) ? value : 0,
  );
}

/**
 * Parses a backend date value into a valid Date, or null if it cannot be parsed.
 *
 * Tolerates the three shapes a JVM service can emit:
 *  - ISO-8601 string ("2026-06-21T21:23:19") — the normal case
 *  - epoch millis (number)
 *  - Jackson's numeric array [year, month, day, hour, minute, second, nanos], which is
 *    what `LocalDateTime` serialises to when WRITE_DATES_AS_TIMESTAMPS is left enabled.
 *
 * Returning null (instead of letting `Intl.*Format.format` throw "date value is not finite")
 * means a single bad timestamp can never white-screen a whole page.
 */
function toDate(value: unknown): Date | null {
  if (value == null || value === '') return null;

  if (Array.isArray(value)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0] = value as number[];
    if (y == null) return null;
    const dt = new Date(y, mo - 1, d, h, mi, s);
    return Number.isNaN(dt.getTime()) ? null : dt;
  }

  const dt = new Date(value as string | number);
  return Number.isNaN(dt.getTime()) ? null : dt;
}

export function formatDate(dateString: unknown, fallback = '—'): string {
  const dt = toDate(dateString);
  if (!dt) return fallback;
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(dt);
}

export function formatDateTime(dateString: unknown, fallback = '—'): string {
  const dt = toDate(dateString);
  if (!dt) return fallback;
  return new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(dt);
}

export function truncate(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength - 3) + '...';
}
