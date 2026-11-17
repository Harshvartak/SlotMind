const MS_PER_DAY = 24 * 60 * 60 * 1000;
const HIGH_CONFIDENCE = 0.8;

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
const clock = (d) => d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

/**
 * "Today, 02:11 PM", "Tomorrow, 09:00 AM", otherwise "Fri, 5 Dec, 02:11 PM". The common case at a
 * front desk is today, so it reads as a word instead of a full date. `now` is injectable for tests.
 */
export function fmtTime(iso, now = new Date()) {
  const d = new Date(iso);
  const days = Math.round((startOfDay(d) - startOfDay(now)) / MS_PER_DAY);
  if (days === 0) return `Today, ${clock(d)}`;
  if (days === 1) return `Tomorrow, ${clock(d)}`;
  const day = d.toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short' });
  return `${day}, ${clock(d)}`;
}

export const pct = (confidence) => `${Math.round(confidence * 100)}%`;

/** Colour band for the confidence bar: green when the suggestion is strong, amber otherwise. */
export const confidenceTone = (confidence) => (confidence >= HIGH_CONFIDENCE ? 'high' : 'mid');

// The loop's triggers get their own badge; a manual request has no trigger of its own.
export const TRIGGER_LABEL = {
  PROVIDER_OFFLINE: 'Provider offline',
  SLOT_CANCELLED: 'Slot cancelled',
  INITIAL: 'Manual',
};

// Appointment statuses whose raw name is too long for a table cell.
export const STATUS_LABEL = { RESCHEDULE_PENDING: 'Pending' };

export const PROVIDER_STATUSES = ['AVAILABLE', 'BUSY', 'OFFLINE'];

export const APPOINTMENT_TYPES = ['ROUTINE', 'URGENT', 'FOLLOW_UP'];
