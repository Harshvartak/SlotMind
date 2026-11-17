import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import SuggestionCard from './SuggestionCard.jsx';
import { appointment, doctorKim, provider, slot, suggestion } from '../test/fixtures.js';

function renderCard(overrides = {}) {
  const props = {
    appointment: appointment(),
    suggestion: suggestion(),
    slots: [slot()],
    providers: [provider(), doctorKim],
    onDecide: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  render(<SuggestionCard {...props} />);
  return props;
}

describe('SuggestionCard', () => {
  it('shows who is affected and what is proposed', () => {
    renderCard();

    expect(screen.getByText('Maria Garcia')).toBeInTheDocument();
    expect(screen.getByText('Provider offline')).toBeInTheDocument();
    expect(screen.getByText(/Dr\. David Kim/)).toBeInTheDocument();
    expect(screen.getByText('91% confidence')).toBeInTheDocument();
    expect(screen.getByText(/earlier opening with another general provider/)).toBeInTheDocument();
  });

  it('flags a different provider only when the provider changes', () => {
    const { rerender } = render(
      <SuggestionCard
        appointment={appointment()}
        suggestion={suggestion()}
        slots={[slot()]}
        providers={[provider(), doctorKim]}
        onDecide={vi.fn()}
      />,
    );
    expect(screen.getByText('Different provider')).toBeInTheDocument();

    rerender(
      <SuggestionCard
        appointment={appointment()}
        suggestion={suggestion({ alternateProviderId: null })}
        slots={[slot()]}
        providers={[provider(), doctorKim]}
        onDecide={vi.fn()}
      />,
    );
    expect(screen.queryByText('Different provider')).not.toBeInTheDocument();
  });

  it('labels each trigger', () => {
    renderCard({ suggestion: suggestion({ triggerReason: 'SLOT_CANCELLED' }) });
    expect(screen.getByText('Slot cancelled')).toBeInTheDocument();
  });

  it('labels a manual request as Manual', () => {
    renderCard({ suggestion: suggestion({ triggerReason: 'INITIAL' }) });
    expect(screen.getByText('Manual')).toBeInTheDocument();
  });

  it('keeps Accept disabled until the patient confirmation is ticked', async () => {
    const user = userEvent.setup();
    const { onDecide } = renderCard();
    const accept = screen.getByRole('button', { name: 'Accept' });

    expect(accept).toBeDisabled();
    await user.click(accept);
    expect(onDecide).not.toHaveBeenCalled();

    await user.click(screen.getByRole('checkbox'));
    expect(accept).toBeEnabled();
  });

  it('tells the user why Accept is disabled, until they tick the box', async () => {
    const user = userEvent.setup();
    renderCard();

    expect(screen.getByText('Tick the box to enable Accept')).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox'));

    expect(screen.queryByText('Tick the box to enable Accept')).not.toBeInTheDocument();
  });

  it('colours the confidence bar by strength', () => {
    const { container } = render(
      <SuggestionCard
        appointment={appointment()}
        suggestion={suggestion({ confidence: 0.75 })}
        slots={[slot()]}
        providers={[provider(), doctorKim]}
        onDecide={vi.fn()}
      />,
    );

    expect(container.querySelector('.fill')).toHaveClass('tone-mid');
  });

  it('accepts the suggestion after the confirmation', async () => {
    const user = userEvent.setup();
    const { onDecide } = renderCard();

    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Accept' }));

    expect(onDecide).toHaveBeenCalledWith('SUG-1', 'ACCEPTED');
  });

  it('lets the desk reject without confirming with the patient', async () => {
    const user = userEvent.setup();
    const { onDecide } = renderCard();

    await user.click(screen.getByRole('button', { name: 'Reject' }));

    expect(onDecide).toHaveBeenCalledWith('SUG-1', 'REJECTED');
  });

  it('shows the server reason when accepting fails', async () => {
    const user = userEvent.setup();
    renderCard({ onDecide: vi.fn().mockRejectedValue(new Error('Slot SLT-005 is no longer open')) });

    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Accept' }));

    expect(await screen.findByText('Slot SLT-005 is no longer open')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Accept' })).toBeEnabled();
  });

  it('disables both buttons while a decision is in flight', async () => {
    const user = userEvent.setup();
    let finish;
    renderCard({ onDecide: vi.fn(() => new Promise((resolve) => (finish = resolve))) });

    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Accept' }));

    expect(screen.getByRole('button', { name: 'Working…' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeDisabled();
    finish();
  });

  it('explains when no suggestion exists yet and offers no actions', () => {
    renderCard({ suggestion: undefined });

    expect(screen.getByText('No suggestion yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
