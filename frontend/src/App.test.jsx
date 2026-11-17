import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import { api } from './api/client.js';
import { appointment, doctorKim, provider, slot, suggestion } from './test/fixtures.js';

vi.mock('./api/client.js', () => ({
  api: {
    listAppointments: vi.fn(),
    listSuggestions: vi.fn(),
    listProviders: vi.fn(),
    listSlots: vi.fn(),
    bookAppointment: vi.fn(),
    suggest: vi.fn(),
    decideSuggestion: vi.fn(),
    setProviderStatus: vi.fn(),
    setSlotStatus: vi.fn(),
  },
}));

const waiting = appointment();
const confirmed = appointment({
  id: 'APT-003',
  patientName: 'Priya Patel',
  status: 'CONFIRMED',
});

beforeEach(() => {
  vi.resetAllMocks();
  api.listAppointments.mockResolvedValue([waiting, confirmed]);
  api.listSuggestions.mockResolvedValue([suggestion()]);
  api.listProviders.mockResolvedValue([provider(), doctorKim]);
  api.listSlots.mockResolvedValue([slot()]);
  api.bookAppointment.mockResolvedValue({});
  api.suggest.mockResolvedValue({});
  api.decideSuggestion.mockResolvedValue({});
  api.setProviderStatus.mockResolvedValue({});
  api.setSlotStatus.mockResolvedValue({});
});

describe('App', () => {
  it('queues appointments that are waiting for the front desk, with their suggestion', async () => {
    render(<App />);

    expect(await screen.findByText(/Needs rescheduling/)).toBeInTheDocument();
    expect(await screen.findByText('Provider offline')).toBeInTheDocument();
    expect(screen.getByText(/earlier opening with another general provider/)).toBeInTheDocument();
  });

  it('shows the newest suggestion when an appointment has more than one', async () => {
    api.listSuggestions.mockResolvedValue([
      suggestion({ id: 'SUG-OLD', reasoning: 'Older suggestion text', createdAt: '2030-01-01T09:00:00' }),
      suggestion({ id: 'SUG-NEW', reasoning: 'Newer suggestion text', createdAt: '2030-01-01T09:05:00' }),
    ]);

    render(<App />);

    expect(await screen.findByText('Newer suggestion text')).toBeInTheDocument();
    expect(screen.queryByText('Older suggestion text')).not.toBeInTheDocument();
  });

  it('explains how to trigger the flow when nothing is waiting', async () => {
    api.listAppointments.mockResolvedValue([confirmed]);
    api.listSuggestions.mockResolvedValue([]);

    render(<App />);

    expect(await screen.findByText(/Nothing waiting/)).toBeInTheDocument();
  });

  it('shows a banner when the server cannot be reached', async () => {
    api.listAppointments.mockRejectedValue(new Error('Failed to fetch'));

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server: Failed to fetch');
  });

  it('takes a provider offline and reloads the lists', async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText('Provider offline');
    const before = api.listAppointments.mock.calls.length;

    await user.selectOptions(screen.getByLabelText('Status of Dr. Alice Chen'), 'OFFLINE');

    expect(api.setProviderStatus).toHaveBeenCalledWith('PRV-001', 'OFFLINE');
    await waitFor(() => expect(api.listAppointments.mock.calls.length).toBeGreaterThan(before));
  });

  it('cancels a slot', async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText('Provider offline');

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(api.setSlotStatus).toHaveBeenCalledWith('SLT-005', 'CANCELLED');
  });

  it('requests a manual suggestion for a confirmed appointment', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Suggest new time' }));

    expect(api.suggest).toHaveBeenCalledWith('APT-003');
  });

  it('books an appointment from the form and reloads the lists', async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText('Provider offline');
    const before = api.listAppointments.mock.calls.length;

    await user.type(screen.getByLabelText('Patient name'), 'Ana Lopez');
    await user.selectOptions(screen.getByLabelText('Provider'), screen.getByRole('option', { name: /Dr\. David Kim/ }));
    await user.selectOptions(screen.getByLabelText('Open slot'), screen.getByRole('option', { name: /30 min/ }));
    await user.click(screen.getByRole('button', { name: 'Book appointment' }));

    expect(api.bookAppointment).toHaveBeenCalledWith({
      patientName: 'Ana Lopez',
      type: 'ROUTINE',
      providerId: 'PRV-004',
      slotId: 'SLT-005',
    });
    await waitFor(() => expect(api.listAppointments.mock.calls.length).toBeGreaterThan(before));
  });

  it('shows a dismissable banner when an action fails', async () => {
    const user = userEvent.setup();
    api.setProviderStatus.mockRejectedValue(new Error('Provider not found: PRV-001'));
    render(<App />);
    await screen.findByText('Provider offline');

    await user.selectOptions(screen.getByLabelText('Status of Dr. Alice Chen'), 'OFFLINE');

    expect(await screen.findByRole('alert')).toHaveTextContent('Provider not found: PRV-001');
    await user.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('accepts a suggestion after the patient is confirmed, then reloads', async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText('Provider offline');
    const before = api.listSuggestions.mock.calls.length;

    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Accept' }));

    expect(api.decideSuggestion).toHaveBeenCalledWith('SUG-1', 'ACCEPTED');
    await waitFor(() => expect(api.listSuggestions.mock.calls.length).toBeGreaterThan(before));
  });
});
