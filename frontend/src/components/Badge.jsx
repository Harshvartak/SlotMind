/** Small coloured pill; the colour comes from the value, e.g. status-OFFLINE. */
export default function Badge({ value, label }) {
  return <span className={`badge badge-${value}`}>{label ?? value.replaceAll('_', ' ')}</span>;
}
