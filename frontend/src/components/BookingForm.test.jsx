import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import BookingForm from './BookingForm.jsx';
import { doctorKim, provider, slot } from '../test/fixtures.js';

const doctorChen = provider({ id: 'PRV-001', name: 'Dr. Sarah Chen' });
const offline = provider({ id: 'PRV-003', name: 'Dr. Priya Sharma', specialty: 'PEDIATRIC', status: 'OFFLINE' });
const kimEarly = slot({ id: 'SLT-005', providerId: 'PRV-004', startTime: '2030-01-01T09:00:00', durationMinutes: 30 });
const kimLate = slot({ id: 'SLT-007', providerId: 'PRV-004', startTime: '2030-01-01T15:00:00', durationMinutes: 45 });
const kimBooked = slot({ id: 'SLT-008', providerId: 'PRV-004', status: 'BOOKED' });
const chenOpen = slot({ id: 'SLT-004', providerId: 'PRV-001', durationMinutes: 60 });

function renderForm(overrides = {}) {
  const props = {
    providers: [doctorChen, doctorKim, offline],
    slots: [kimEarly, kimLate, kimBooked, chenOpen],
    onBook: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  render(<BookingForm {...props} />);
  return props;
}

async function fillIn(user, { name = 'Ana Lopez', provider = 'Dr. David Kim', slotText = /30 min/ } = {}) {
  await user.type(screen.getByLabelText('Patient name'), name);
  await user.selectOptions(screen.getByLabelText('Provider'), screen.getByRole('option', { name: new RegExp(provider) }));
  await user.selectOptions(screen.getByLabelText('Open slot'), screen.getByRole('option', { name: slotText }));
}

describe('BookingForm', () => {
  it('only offers providers that are not offline', () => {
    renderForm();

    expect(screen.getByRole('option', { name: /Dr\. Sarah Chen/ })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Dr\. David Kim/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Dr\. Priya Sharma/ })).not.toBeInTheDocument();
  });

  it('offers the three appointment types', () => {
    renderForm();

    const types = within(screen.getByLabelText('Type'));
    expect(types.getByRole('option', { name: 'routine' })).toBeInTheDocument();
    expect(types.getByRole('option', { name: 'urgent' })).toBeInTheDocument();
    expect(types.getByRole('option', { name: 'follow up' })).toBeInTheDocument();
  });

  it('lists only the chosen providers open slots, earliest first', async () => {
    const user = userEvent.setup();
    renderForm();

    expect(screen.getByLabelText('Open slot')).toBeDisabled();
    await user.selectOptions(screen.getByLabelText('Provider'), screen.getByRole('option', { name: /Dr\. David Kim/ }));

    const slotSelect = within(screen.getByLabelText('Open slot'));
    const options = slotSelect.getAllByRole('option').slice(1); // skip the placeholder
    expect(options).toHaveLength(2); // the booked slot and Dr. Chen's slot are not offered
    expect(options[0]).toHaveTextContent('30 min');
    expect(options[1]).toHaveTextContent('45 min');
  });

  it('says when the provider has no open slots', async () => {
    const user = userEvent.setup();
    renderForm({ slots: [kimBooked] });

    await user.selectOptions(screen.getByLabelText('Provider'), screen.getByRole('option', { name: /Dr\. David Kim/ }));

    expect(screen.getByRole('option', { name: 'No open slots' })).toBeInTheDocument();
  });

  it('keeps Book disabled until name, provider and slot are all chosen', async () => {
    const user = userEvent.setup();
    renderForm();
    const book = screen.getByRole('button', { name: 'Book appointment' });
    expect(book).toBeDisabled();

    await user.type(screen.getByLabelText('Patient name'), 'Ana Lopez');
    expect(book).toBeDisabled();
    await user.selectOptions(screen.getByLabelText('Provider'), screen.getByRole('option', { name: /Dr\. David Kim/ }));
    expect(book).toBeDisabled();
    await user.selectOptions(screen.getByLabelText('Open slot'), screen.getByRole('option', { name: /30 min/ }));

    expect(book).toBeEnabled();
  });

  it('does not accept a blank patient name', async () => {
    const user = userEvent.setup();
    renderForm();

    await fillIn(user, { name: '   ' });

    expect(screen.getByRole('button', { name: 'Book appointment' })).toBeDisabled();
  });

  it('clears the slot when the provider changes', async () => {
    const user = userEvent.setup();
    renderForm();
    await fillIn(user);

    await user.selectOptions(screen.getByLabelText('Provider'), screen.getByRole('option', { name: /Dr\. Sarah Chen/ }));

    expect(screen.getByLabelText('Open slot')).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Book appointment' })).toBeDisabled();
  });

  it('submits the booking with a trimmed name and the chosen type', async () => {
    const user = userEvent.setup();
    const { onBook } = renderForm();
    await user.selectOptions(screen.getByLabelText('Type'), 'URGENT');
    await fillIn(user, { name: '  Ana Lopez  ' });

    await user.click(screen.getByRole('button', { name: 'Book appointment' }));

    expect(onBook).toHaveBeenCalledWith({
      patientName: 'Ana Lopez',
      type: 'URGENT',
      providerId: 'PRV-004',
      slotId: 'SLT-005',
    });
  });

  it('confirms success and resets the patient and slot but keeps the provider', async () => {
    const user = userEvent.setup();
    renderForm();
    await fillIn(user);

    await user.click(screen.getByRole('button', { name: 'Book appointment' }));

    expect(await screen.findByText('Appointment booked.')).toBeInTheDocument();
    expect(screen.getByLabelText('Patient name')).toHaveValue('');
    expect(screen.getByLabelText('Open slot')).toHaveValue('');
    expect(screen.getByLabelText('Provider')).toHaveValue('PRV-004');
  });

  it('shows the server reason and keeps the form filled when booking fails', async () => {
    const user = userEvent.setup();
    renderForm({ onBook: vi.fn().mockRejectedValue(new Error('Slot is not open')) });
    await fillIn(user);

    await user.click(screen.getByRole('button', { name: 'Book appointment' }));

    expect(await screen.findByText('Slot is not open')).toBeInTheDocument();
    expect(screen.getByLabelText('Patient name')).toHaveValue('Ana Lopez');
    expect(screen.queryByText('Appointment booked.')).not.toBeInTheDocument();
  });

  it('disables the button while the booking is in flight', async () => {
    const user = userEvent.setup();
    let finish;
    renderForm({ onBook: vi.fn(() => new Promise((resolve) => (finish = resolve))) });
    await fillIn(user);

    await user.click(screen.getByRole('button', { name: 'Book appointment' }));

    expect(screen.getByRole('button', { name: 'Booking…' })).toBeDisabled();
    finish();
  });
});
