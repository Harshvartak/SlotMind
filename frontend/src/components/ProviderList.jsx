import { PROVIDER_STATUSES } from '../format.js';

export default function ProviderList({ providers, onStatus }) {
  return (
    <ul className="list">
      {providers.map((p) => (
        <li key={p.id} className={p.status === 'OFFLINE' ? 'offline' : ''}>
          <div className="who">
            <div className="who-name">
              <span className={`dot dot-${p.status}`} aria-hidden="true" />
              <strong>{p.name}</strong>
            </div>
            <span className="muted">{p.specialty.toLowerCase()}</span>
          </div>
          <select
            aria-label={`Status of ${p.name}`}
            value={p.status}
            onChange={(e) => onStatus(p.id, e.target.value)}
          >
            {PROVIDER_STATUSES.map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </li>
      ))}
    </ul>
  );
}
