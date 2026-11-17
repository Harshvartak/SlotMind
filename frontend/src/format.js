export const fmtTime = (iso) =>
  new Date(iso).toLocaleString([], {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });

export const pct = (confidence) => `${Math.round(confidence * 100)}%`;

// The loop's triggers get their own badge; a manual request has no trigger of its own.
export const TRIGGER_LABEL = {
  PROVIDER_OFFLINE: 'Provider offline',
  SLOT_CANCELLED: 'Slot cancelled',
  INITIAL: 'Manual',
};

export const PROVIDER_STATUSES = ['AVAILABLE', 'BUSY', 'OFFLINE'];

export const APPOINTMENT_TYPES = ['ROUTINE', 'URGENT', 'FOLLOW_UP'];
