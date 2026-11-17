// API client (commit 23). All calls go through /api, proxied to the backend
// by Vite in dev and by nginx in the container.
const BASE = '/api';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json();
}

export const api = {
  listAppointments: (status) => request(`/appointments${status ? `?status=${status}` : ''}`),
  patchProviderStatus: (id, status) =>
    request(`/providers/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  patchSlotStatus: (id, status) =>
    request(`/slots/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  suggest: (appointmentId) => request(`/appointments/${appointmentId}/suggest`, { method: 'POST' }),
  decideSuggestion: (id, status) =>
    request(`/suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),
};
