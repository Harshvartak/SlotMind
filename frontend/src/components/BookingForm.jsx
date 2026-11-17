import { useState } from 'react';
import { APPOINTMENT_TYPES, fmtTime } from '../format.js';

const EMPTY = { patientName: '', type: 'ROUTINE', providerId: '', slotId: '' };

/** Books an appointment: pick a working provider, then one of that provider's open slots. */
export default function BookingForm({ providers, slots, onBook }) {
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [booked, setBooked] = useState(false);

  const bookable = providers.filter((p) => p.status !== 'OFFLINE');
  const openSlots = slots
    .filter((s) => s.providerId === form.providerId && s.status === 'OPEN')
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  const complete = form.patientName.trim() && form.providerId && form.slotId;

  const set = (field) => (e) => {
    setBooked(false);
    // Changing the provider invalidates the slot chosen for the previous one.
    setForm({ ...form, [field]: e.target.value, ...(field === 'providerId' ? { slotId: '' } : {}) });
  };

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onBook({ ...form, patientName: form.patientName.trim() });
      setForm({ ...EMPTY, type: form.type, providerId: form.providerId });
      setBooked(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card booking" onSubmit={submit}>
      <label>
        Patient name
        <input value={form.patientName} onChange={set('patientName')} />
      </label>
      <label>
        Type
        <select value={form.type} onChange={set('type')}>
          {APPOINTMENT_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replaceAll('_', ' ').toLowerCase()}
            </option>
          ))}
        </select>
      </label>
      <label>
        Provider
        <select value={form.providerId} onChange={set('providerId')}>
          <option value="">Choose a provider</option>
          {bookable.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.specialty.toLowerCase()})
            </option>
          ))}
        </select>
      </label>
      <label>
        Open slot
        <select value={form.slotId} onChange={set('slotId')} disabled={!form.providerId}>
          <option value="">
            {form.providerId && openSlots.length === 0 ? 'No open slots' : 'Choose a slot'}
          </option>
          {openSlots.map((s) => (
            <option key={s.id} value={s.id}>
              {fmtTime(s.startTime)} · {s.durationMinutes} min
            </option>
          ))}
        </select>
      </label>
      {error && <p className="error">{error}</p>}
      {booked && <p className="success">Appointment booked.</p>}
      <button type="submit" disabled={!complete || busy}>
        {busy ? 'Booking…' : 'Book appointment'}
      </button>
    </form>
  );
}
