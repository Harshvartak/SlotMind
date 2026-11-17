import { useState } from 'react';
import Badge from './Badge.jsx';
import { confidenceTone, fmtTime, pct, TRIGGER_LABEL } from '../format.js';

/** One appointment waiting for the front desk, with its suggested new slot. */
export default function SuggestionCard({ appointment, suggestion, slots, providers, onDecide }) {
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const slot = suggestion && slots.find((s) => s.id === suggestion.recommendedSlotId);
  const provider = slot && providers.find((p) => p.id === slot.providerId);

  async function decide(status) {
    setBusy(true);
    setError(null);
    try {
      await onDecide(suggestion.id, status);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="card">
      <header className="card-head">
        <div>
          <h3>{appointment.patientName}</h3>
          <p className="muted">
            Booked: {fmtTime(appointment.slot.startTime)} with {appointment.provider.name}
          </p>
        </div>
        <Badge value={appointment.type} />
      </header>

      {!suggestion && <p className="muted">No suggestion yet.</p>}

      {suggestion && (
        <>
          <div className="suggestion">
            <div className="row">
              <Badge
                value={suggestion.triggerReason}
                label={TRIGGER_LABEL[suggestion.triggerReason]}
              />
              {suggestion.alternateProviderId && <Badge value="ALTERNATE" label="Different provider" />}
            </div>
            <p className="when">
              {slot ? fmtTime(slot.startTime) : suggestion.recommendedSlotId}
              {provider && <span> with {provider.name}</span>}
            </p>
            <div className="confidence" title={`Confidence ${pct(suggestion.confidence)}`}>
              <div className="bar">
                <div
                  className={`fill tone-${confidenceTone(suggestion.confidence)}`}
                  style={{ width: pct(suggestion.confidence) }}
                />
              </div>
              <span>{pct(suggestion.confidence)} confidence</span>
            </div>
            <blockquote>{suggestion.reasoning}</blockquote>
          </div>

          <label className="check">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
            />
            I confirmed this new time with the patient
          </label>
          {error && <p className="error">{error}</p>}
          <div className="row actions">
            <button disabled={!confirmed || busy} onClick={() => decide('ACCEPTED')}>
              {busy ? 'Working…' : 'Accept'}
            </button>
            <button className="secondary" disabled={busy} onClick={() => decide('REJECTED')}>
              Reject
            </button>
            {!confirmed && <span className="hint">Tick the box to enable Accept</span>}
          </div>
        </>
      )}
    </article>
  );
}
