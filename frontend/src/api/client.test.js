import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './client.js';

const ok = (body) => ({ ok: true, status: 200, json: async () => body });
const fail = (status, text) => ({ ok: false, status, text: async () => text });

function stubFetch(response) {
  const fn = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe('api client', () => {
  it('lists appointments through the /api proxy', async () => {
    const fetchMock = stubFetch(ok([{ id: 'APT-001' }]));

    expect(await api.listAppointments()).toEqual([{ id: 'APT-001' }]);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/appointments');
  });

  it('filters suggestions by status', async () => {
    const fetchMock = stubFetch(ok([]));

    await api.listSuggestions('PENDING');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/suggestions?status=PENDING');
  });

  it('accepts a suggestion with a PATCH and a JSON body', async () => {
    const fetchMock = stubFetch(ok({}));

    await api.decideSuggestion('SUG-1', 'ACCEPTED');

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/suggestions/SUG-1');
    expect(options.method).toBe('PATCH');
    expect(options.body).toBe('{"status":"ACCEPTED"}');
    expect(options.headers['Content-Type']).toBe('application/json');
  });

  it('books an appointment with a POST of exactly the booking fields', async () => {
    const fetchMock = stubFetch(ok({ id: 'APT-X' }));

    const result = await api.bookAppointment({
      patientName: 'Ana Lopez',
      type: 'URGENT',
      providerId: 'PRV-002',
      slotId: 'SLT-004',
      somethingElse: 'ignored',
    });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/appointments');
    expect(options.method).toBe('POST');
    expect(JSON.parse(options.body)).toEqual({
      patientName: 'Ana Lopez',
      type: 'URGENT',
      providerId: 'PRV-002',
      slotId: 'SLT-004',
    });
    expect(result).toEqual({ id: 'APT-X' });
  });

  it('posts a manual suggestion request', async () => {
    const fetchMock = stubFetch(ok({}));

    await api.suggest('APT-003');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/appointments/APT-003/suggest');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
  });

  it('changes provider and slot status', async () => {
    const fetchMock = stubFetch(ok({}));

    await api.setProviderStatus('PRV-001', 'OFFLINE');
    await api.setSlotStatus('SLT-003', 'CANCELLED');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/providers/PRV-001/status');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/slots/SLT-003/status');
    expect(fetchMock.mock.calls[1][1].body).toBe('{"status":"CANCELLED"}');
  });

  it('turns a Spring error body into its message', async () => {
    stubFetch(fail(409, '{"status":409,"error":"Conflict","message":"Slot SLT-005 is no longer open"}'));

    await expect(api.decideSuggestion('SUG-1', 'ACCEPTED')).rejects.toThrow(
      'Slot SLT-005 is no longer open',
    );
  });

  it('falls back to a problem-detail field, then the raw text, then the status', async () => {
    stubFetch(fail(400, '{"detail":"status must be ACCEPTED or REJECTED"}'));
    await expect(api.suggest('A')).rejects.toThrow('status must be ACCEPTED or REJECTED');

    stubFetch(fail(502, 'Bad gateway'));
    await expect(api.suggest('A')).rejects.toThrow('Bad gateway');

    stubFetch(fail(500, ''));
    await expect(api.suggest('A')).rejects.toThrow('HTTP 500');
  });

  it('returns null for an empty 204 response', async () => {
    stubFetch({ ok: true, status: 204 });

    expect(await api.suggest('A')).toBeNull();
  });
});
