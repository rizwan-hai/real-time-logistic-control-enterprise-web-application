import { toJalaali } from 'jalaali-js';
import type { Locale } from './resources';

export function formatPersianDigits(value: string | number): string {
  return String(value).replace(/\d/g, (digit) => String.fromCharCode(digit.charCodeAt(0) + 1728));
}

export function formatNumber(value: number, locale: Locale, options?: Intl.NumberFormatOptions): string {
  const formatted = new Intl.NumberFormat(locale === 'fa' ? 'fa-IR-u-nu-arabext' : 'en-US', options).format(value);
  return locale === 'fa' ? formatPersianDigits(formatted) : formatted;
}

export function formatCurrency(value: number, currency: string, locale: Locale): string {
  return formatNumber(value, locale, { style: 'currency', currency, maximumFractionDigits: 2 });
}

export function formatJalaliDate(value: Date | string | number, locale: Locale = 'fa'): string {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number);
    if (year && month && day) {
      const { jy, jm, jd } = toJalaali(year, month, day);
      const result = `${jy}/${String(jm).padStart(2, '0')}/${String(jd).padStart(2, '0')}`;
      return locale === 'fa' ? formatPersianDigits(result) : result;
    }
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const { jy, jm, jd } = toJalaali(date.getFullYear(), date.getMonth() + 1, date.getDate());
  const result = `${jy}/${String(jm).padStart(2, '0')}/${String(jd).padStart(2, '0')}`;
  return locale === 'fa' ? formatPersianDigits(result) : result;
}
