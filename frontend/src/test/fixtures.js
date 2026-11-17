// Builders shaped like the backend's view records, so tests override only what they care about.
export const provider = (o = {}) => ({
  id: 'PRV-001',
  name: 'Dr. Alice Chen',
  specialty: 'GENERAL',
  status: 'AVAILABLE',
  ...o,
});

export const slot = (o = {}) => ({
  id: 'SLT-005',
  providerId: 'PRV-004',
  startTime: '2030-01-01T10:00:00',
  durationMinutes: 30,
  status: 'OPEN',
  ...o,
});

export const appointment = (o = {}) => ({
  id: 'APT-001',
  patientName: 'Maria Garcia',
  type: 'URGENT',
  provider: provider(),
  slot: slot({ id: 'SLT-001', providerId: 'PRV-001', status: 'BOOKED' }),
  status: 'RESCHEDULE_PENDING',
  ...o,
});

export const suggestion = (o = {}) => ({
  id: 'SUG-1',
  appointment: appointment(),
  recommendedSlotId: 'SLT-005',
  alternateProviderId: 'PRV-004',
  confidence: 0.91,
  reasoning: 'We found an earlier opening with another general provider.',
  status: 'PENDING',
  triggerReason: 'PROVIDER_OFFLINE',
  createdAt: '2030-01-01T09:00:00',
  ...o,
});

export const doctorKim = provider({ id: 'PRV-004', name: 'Dr. David Kim' });
