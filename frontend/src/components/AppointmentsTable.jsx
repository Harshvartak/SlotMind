import Badge from './Badge.jsx';
import { fmtTime } from '../format.js';

export default function AppointmentsTable({ appointments, onSuggest }) {
  if (appointments.length === 0) return <p className="muted">No appointments.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Patient</th>
            <th>Type</th>
            <th>Provider</th>
            <th>Time</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {appointments.map((a) => (
            <tr key={a.id}>
              <td>{a.patientName}</td>
              <td>
                <Badge value={a.type} />
              </td>
              <td>{a.provider.name}</td>
              <td>{fmtTime(a.slot.startTime)}</td>
              <td>
                <Badge value={a.status} />
              </td>
              <td>
                {(a.status === 'CONFIRMED' || a.status === 'RESCHEDULED') && (
                  <button className="link" onClick={() => onSuggest(a.id)}>
                    Suggest new time
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
