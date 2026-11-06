import { describe, expect, it } from 'vitest';
import { formatJalaliDate, formatNumber, formatPersianDigits } from './formatters';

describe('locale formatting', () => {
  it('formats Persian digits', () => {
    expect(formatPersianDigits('2026-11')).toBe('۲۰۲۶-۱۱');
  });
  it('uses Persian digits for Persian number output', () => {
    expect(formatNumber(1234, 'fa')).toContain('۱');
  });
  it('formats a date-only value in the Jalali calendar without timezone shifting', () => {
    expect(formatJalaliDate('2023-03-21', 'fa')).toBe('۱۴۰۲/۰۱/۰۱');
  });
});
