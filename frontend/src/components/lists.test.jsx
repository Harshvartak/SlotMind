import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import AppointmentsTable from './AppointmentsTable.jsx';
import ProviderList from './ProviderList.jsx';
import SlotList from './SlotList.jsx';
import { appointment, doctorKim, provider, slot } from '../test/fixtures.js';

describe('AppointmentsTable', () => {
  it('offers a manual suggestion for confirmed and rescheduled appointments, not pending ones', async () => {
    const user = userEvent.setup();
    const onSuggest = vi.fn();
    render(
      <AppointmentsTable
        onSuggest={onSuggest}
        appointments={[
          appointment({ id: 'APT-001', status: 'CONFIRMED' }),
          appointment({ id: 'APT-002', patientName: 'John Smith', status: 'RESCHEDULE_PENDING' }),
          appointment({ id: 'APT-003', patientName: 'Priya Patel', status: 'RESCHEDULED' }),
        ]}
      />,
    );

    const buttons = screen.getAllByRole('button', { name: 'Suggest new time' });
    expect(buttons).toHaveLength(2);

    await user.click(buttons[1]);
    expect(onSuggest).toHaveBeenCalledWith('APT-003');
  });

  it('shows patient, provider and status for each row', () => {
    render(<AppointmentsTable onSuggest={vi.fn()} appointments={[appointment({ status: 'CONFIRMED' })]} />);

    const row = screen.getByText('Maria Garcia').closest('tr');
    expect(within(row).getByText('Dr. Alice Chen')).toBeInTheDocument();
    expect(within(row).getByText('CONFIRMED')).toBeInTheDocument();
  });

  it('uses a short label for appointments awaiting a decision', () => {
    render(
      <AppointmentsTable
        onSuggest={vi.fn()}
        appointments={[appointment({ status: 'RESCHEDULE_PENDING' })]}
      />,
    );

    const row = screen.getByText('Maria Garcia').closest('tr');
    expect(within(row).getByText('Pending')).toBeInTheDocument();
    expect(within(row).queryByText('RESCHEDULE PENDING')).not.toBeInTheDocument();
  });

  it('says so when there are no appointments', () => {
    render(<AppointmentsTable onSuggest={vi.fn()} appointments={[]} />);

    expect(screen.getByText('No appointments.')).toBeInTheDocument();
  });
});

describe('ProviderList', () => {
  it('reports a status change for the right provider', async () => {
    const user = userEvent.setup();
    const onStatus = vi.fn();
    render(<ProviderList providers={[provider(), doctorKim]} onStatus={onStatus} />);

    await user.selectOptions(screen.getByLabelText('Status of Dr. Alice Chen'), 'OFFLINE');

    expect(onStatus).toHaveBeenCalledWith('PRV-001', 'OFFLINE');
  });

  it('highlights offline providers and shows each current status', () => {
    render(
      <ProviderList
        onStatus={vi.fn()}
        providers={[provider({ status: 'OFFLINE' }), doctorKim]}
      />,
    );

    expect(screen.getByText('Dr. Alice Chen').closest('li')).toHaveClass('offline');
    expect(screen.getByText('Dr. David Kim').closest('li')).not.toHaveClass('offline');
    expect(screen.getByLabelText('Status of Dr. Alice Chen')).toHaveValue('OFFLINE');
  });
});

describe('SlotList', () => {
  const early = slot({ id: 'SLT-001', startTime: '2030-01-01T09:00:00', durationMinutes: 45, status: 'BOOKED', providerId: 'PRV-001' });
  const late = slot({ id: 'SLT-005', startTime: '2030-01-01T15:00:00', durationMinutes: 30, status: 'OPEN', providerId: 'PRV-004' });

  it('lists slots earliest first with the provider name', () => {
    render(<SlotList slots={[late, early]} providers={[provider(), doctorKim]} onCancel={vi.fn()} />);

    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Dr. Alice Chen');
    expect(items[0]).toHaveTextContent('45 min');
    expect(items[1]).toHaveTextContent('Dr. David Kim');
  });

  it('notes when an open slot belongs to a provider who is offline', () => {
    render(
      <SlotList
        slots={[slot({ id: 'SLT-9', providerId: 'PRV-003', status: 'OPEN' })]}
        providers={[provider({ id: 'PRV-003', name: 'Dr. Priya Sharma', status: 'OFFLINE' })]}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.getByRole('listitem')).toHaveTextContent('· offline');
  });

  it('cancels a slot by id', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(<SlotList slots={[early]} providers={[provider()]} onCancel={onCancel} />);

    await user.click(screen.getByRole('button', { name: 'Cancel slot' }));

    expect(onCancel).toHaveBeenCalledWith('SLT-001');
  });

  it('offers no cancel button for a slot that is already cancelled', () => {
    render(
      <SlotList
        slots={[slot({ status: 'CANCELLED' })]}
        providers={[doctorKim]}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: 'Cancel slot' })).not.toBeInTheDocument();
  });
});
