// All calls go through /api: proxied to the backend by Vite in dev and by nginx in the container.
const BASE = '/api';

async function errorText(res) {
  const raw = await res.text();
  try {
    const body = JSON.parse(raw);
    return body.message || body.detail || body.error || raw;
  } catch {
    return raw || `HTTP ${res.status}`;
  }
}

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) throw new Error(await errorText(res));
  return res.status === 204 ? null : res.json();
}

const patch = (path, status) =>
  request(path, { method: 'PATCH', body: JSON.stringify({ status }) });

export const api = {
  listAppointments: () => request('/appointments'),
  listSuggestions: (status) => request(`/suggestions${status ? `?status=${status}` : ''}`),
  listProviders: () => request('/providers'),
  listSlots: () => request('/slots'),
  suggest: (appointmentId) => request(`/appointments/${appointmentId}/suggest`, { method: 'POST' }),
  decideSuggestion: (id, status) => patch(`/suggestions/${id}`, status),
  setProviderStatus: (id, status) => patch(`/providers/${id}/status`, status),
  setSlotStatus: (id, status) => patch(`/slots/${id}/status`, status),
};
