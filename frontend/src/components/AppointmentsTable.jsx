import Badge from './Badge.jsx';
import { fmtTime, STATUS_LABEL } from '../format.js';

const CAN_SUGGEST = ['CONFIRMED', 'RESCHEDULED'];

/** A table on wide screens; on phones each row becomes a small card (see .appointments in CSS). */
export default function AppointmentsTable({ appointments, onSuggest }) {
  if (appointments.length === 0) return <p className="muted">No appointments.</p>;
  return (
    <div className="table-wrap">
      <table className="appointments">
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
              <td className="c-patient">{a.patientName}</td>
              <td className="c-type">
                <Badge value={a.type} />
              </td>
              <td className="c-provider">{a.provider.name}</td>
              <td className="c-time">{fmtTime(a.slot.startTime)}</td>
              <td className="c-status">
                <Badge value={a.status} label={STATUS_LABEL[a.status]} />
              </td>
              <td className="c-action">
                {CAN_SUGGEST.includes(a.status) && (
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
