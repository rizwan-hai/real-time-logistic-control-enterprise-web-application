import { describe, expect, it } from 'vitest';
import { ApiClientError, mapApiError } from './apiErrors';

describe('API error mapping', () => {
  it('maps RFC 7807 fields into a user-facing API error', () => {
    const error = mapApiError({ response: { status: 409, data: { title: 'Conflict', detail: 'Version changed', code: 'OPTIMISTIC_LOCK' } } });
    expect(error).toBeInstanceOf(ApiClientError);
    expect(error.message).toBe('Version changed');
    expect(error.code).toBe('OPTIMISTIC_LOCK');
  });
});
