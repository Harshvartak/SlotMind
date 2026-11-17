-- Seed data. NOTE: H2 "NOW() + n" adds DAYS, so minutes use DATEADD.
INSERT INTO providers (id, name, specialty, status) VALUES
  ('PRV-001', 'Dr. Alice Chen',  'GENERAL',    'AVAILABLE'),
  ('PRV-002', 'Dr. Ben Okafor',  'SPECIALIST', 'AVAILABLE'),
  ('PRV-003', 'Dr. Carla Mendes','PEDIATRIC',  'AVAILABLE'),
  ('PRV-004', 'Dr. David Kim',   'GENERAL',    'AVAILABLE'),
  ('PRV-005', 'Dr. Eva Rossi',   'SPECIALIST', 'BUSY');

INSERT INTO slots (id, provider_id, start_time, duration_minutes, status, version) VALUES
  ('SLT-001', 'PRV-001', DATEADD('MINUTE',  60, CURRENT_TIMESTAMP), 30, 'BOOKED', 0),
  ('SLT-002', 'PRV-001', DATEADD('MINUTE', 120, CURRENT_TIMESTAMP), 30, 'BOOKED', 0),
  ('SLT-003', 'PRV-002', DATEADD('MINUTE',  90, CURRENT_TIMESTAMP), 45, 'BOOKED', 0),
  ('SLT-004', 'PRV-002', DATEADD('MINUTE', 150, CURRENT_TIMESTAMP), 45, 'OPEN',   0),
  ('SLT-005', 'PRV-004', DATEADD('MINUTE',  75, CURRENT_TIMESTAMP), 30, 'OPEN',   0),
  ('SLT-006', 'PRV-003', DATEADD('MINUTE', 180, CURRENT_TIMESTAMP), 30, 'BOOKED', 0);

INSERT INTO appointments (id, patient_name, type, provider_id, slot_id, status) VALUES
  ('APT-001', 'Maria Garcia', 'URGENT',    'PRV-001', 'SLT-001', 'CONFIRMED'),
  ('APT-002', 'John Smith',   'ROUTINE',   'PRV-001', 'SLT-002', 'CONFIRMED'),
  ('APT-003', 'Priya Patel',  'FOLLOW_UP', 'PRV-002', 'SLT-003', 'CONFIRMED'),
  ('APT-004', 'Liam Nguyen',  'ROUTINE',   'PRV-003', 'SLT-006', 'CONFIRMED');
