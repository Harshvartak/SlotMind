import Badge from './Badge.jsx';
import { PROVIDER_STATUSES } from '../format.js';

export default function ProviderList({ providers, onStatus }) {
  return (
    <ul className="list">
      {providers.map((p) => (
        <li key={p.id} className={p.status === 'OFFLINE' ? 'offline' : ''}>
          <div>
            <strong>{p.name}</strong>
            <span className="muted"> {p.specialty.toLowerCase()}</span>
          </div>
          <div className="row">
            <Badge value={p.status} />
            <select
              aria-label={`Status of ${p.name}`}
              value={p.status}
              onChange={(e) => onStatus(p.id, e.target.value)}
            >
              {PROVIDER_STATUSES.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </div>
        </li>
      ))}
    </ul>
  );
}
