-- Seed data from the brief (Addendum A), unchanged except for two things:
--  * times use DATEADD: in H2 "NOW() + 60" adds 60 DAYS, not minutes
--  * the slots "version" column is omitted and defaults to 0 in the schema
INSERT INTO providers (id, name, specialty, status) VALUES
  ('PRV-001', 'Dr. Sarah Chen',    'GENERAL',    'BUSY'),
  ('PRV-002', 'Dr. James Okonkwo', 'SPECIALIST', 'AVAILABLE'),
  ('PRV-003', 'Dr. Priya Sharma',  'PEDIATRIC',  'BUSY'),
  ('PRV-004', 'Dr. Rahul Verma',   'GENERAL',    'AVAILABLE'),
  ('PRV-005', 'Dr. Ananya Iyer',   'SPECIALIST', 'BUSY');

INSERT INTO slots (id, provider_id, start_time, duration_minutes, status) VALUES
  ('SLT-001', 'PRV-001', DATEADD('MINUTE',  60, CURRENT_TIMESTAMP), 30, 'BOOKED'),
  ('SLT-002', 'PRV-001', DATEADD('MINUTE', 120, CURRENT_TIMESTAMP), 30, 'BOOKED'),
  ('SLT-003', 'PRV-003', DATEADD('MINUTE',  90, CURRENT_TIMESTAMP), 45, 'BOOKED'),
  ('SLT-004', 'PRV-002', DATEADD('MINUTE', 180, CURRENT_TIMESTAMP), 30, 'OPEN'),
  ('SLT-005', 'PRV-004', DATEADD('MINUTE', 150, CURRENT_TIMESTAMP), 30, 'OPEN'),
  ('SLT-006', 'PRV-005', DATEADD('MINUTE', 240, CURRENT_TIMESTAMP), 60, 'BOOKED');

INSERT INTO appointments (id, patient_name, appointment_type, provider_id, slot_id, status) VALUES
  ('APT-001', 'John Martinez', 'ROUTINE',   'PRV-001', 'SLT-001', 'CONFIRMED'),
  ('APT-002', 'Emily Watson',  'FOLLOW_UP', 'PRV-001', 'SLT-002', 'CONFIRMED'),
  ('APT-003', 'Aisha Patel',   'URGENT',    'PRV-003', 'SLT-003', 'CONFIRMED'),
  ('APT-004', 'Robert Kim',    'ROUTINE',   'PRV-005', 'SLT-006', 'CONFIRMED');
