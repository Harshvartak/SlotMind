import { describe, expect, it } from 'vitest';
import { fmtTime, pct, PROVIDER_STATUSES, TRIGGER_LABEL } from './format.js';

describe('format', () => {
  it('shows confidence as a whole percentage', () => {
    expect(pct(0.9)).toBe('90%');
    expect(pct(0.756)).toBe('76%');
    expect(pct(1)).toBe('100%');
  });

  it('labels every trigger the backend can send', () => {
    expect(TRIGGER_LABEL.PROVIDER_OFFLINE).toBe('Provider offline');
    expect(TRIGGER_LABEL.SLOT_CANCELLED).toBe('Slot cancelled');
    expect(TRIGGER_LABEL.INITIAL).toBe('Manual');
  });

  it('offers the three provider statuses', () => {
    expect(PROVIDER_STATUSES).toEqual(['AVAILABLE', 'BUSY', 'OFFLINE']);
  });

  it('formats a timestamp into readable text', () => {
    expect(fmtTime('2030-01-01T10:00:00')).toMatch(/2030|Jan|10/);
  });
});
