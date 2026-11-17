import Badge from './Badge.jsx';
import { fmtTime } from '../format.js';

export default function SlotList({ slots, providers, onCancel }) {
  const providerName = (id) => providers.find((p) => p.id === id)?.name ?? id;
  const sorted = [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime));
  return (
    <ul className="list">
      {sorted.map((s) => (
        <li key={s.id}>
          <div>
            <strong>{fmtTime(s.startTime)}</strong>
            <span className="muted">
              {' '}
              {providerName(s.providerId)} · {s.durationMinutes} min
            </span>
          </div>
          <div className="row">
            <Badge value={s.status} />
            {s.status !== 'CANCELLED' && (
              <button className="link danger" onClick={() => onCancel(s.id)}>
                Cancel
              </button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
