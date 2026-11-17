import Badge from './Badge.jsx';
import { fmtTime } from '../format.js';

export default function SlotList({ slots, providers, onCancel }) {
  const providerName = (id) => providers.find((p) => p.id === id)?.name ?? id;
  const isOffline = (id) => providers.find((p) => p.id === id)?.status === 'OFFLINE';
  const sorted = [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime));
  return (
    <ul className="list">
      {sorted.map((s) => (
        <li key={s.id} className={s.status === 'CANCELLED' ? 'dim' : ''}>
          <div className="slot-info">
            <strong>{fmtTime(s.startTime)}</strong>
            <span className="muted">
              {providerName(s.providerId)} · {s.durationMinutes} min
              {isOffline(s.providerId) && s.status === 'OPEN' && ' · offline'}
            </span>
          </div>
          <div className="row">
            <Badge value={s.status} />
            {s.status !== 'CANCELLED' && (
              <button className="link danger" onClick={() => onCancel(s.id)}>
                Cancel slot
              </button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
