import { describe, expect, it } from 'vitest';
import { confidenceTone, fmtTime, pct, PROVIDER_STATUSES, TRIGGER_LABEL } from './format.js';

const NOW = new Date(2030, 0, 1, 9, 0);
const at = (y, m, d, h = 14, min = 11) => new Date(y, m, d, h, min).toISOString();

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

  it('says Today and Tomorrow instead of a full date', () => {
    expect(fmtTime(at(2030, 0, 1), NOW)).toMatch(/^Today, /);
    expect(fmtTime(at(2030, 0, 2), NOW)).toMatch(/^Tomorrow, /);
  });

  it('uses a short date from the day after tomorrow, and for the past', () => {
    expect(fmtTime(at(2030, 0, 3), NOW)).not.toMatch(/Today|Tomorrow/);
    expect(fmtTime(at(2029, 11, 31), NOW)).not.toMatch(/Today|Tomorrow/);
  });

  it('treats late evening as today, not as the next day', () => {
    expect(fmtTime(at(2030, 0, 1, 23, 59), NOW)).toMatch(/^Today, /);
  });

  it('marks a strong suggestion high and a weaker one mid', () => {
    expect(confidenceTone(0.99)).toBe('high');
    expect(confidenceTone(0.8)).toBe('high');
    expect(confidenceTone(0.75)).toBe('mid');
  });

  it('formats a timestamp into readable text', () => {
    expect(fmtTime('2030-01-01T10:00:00')).toMatch(/2030|Jan|10/);
  });
});
