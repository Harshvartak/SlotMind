import { useState } from 'react';
import { api } from './api/client.js';
import { usePolling } from './hooks/usePolling.js';
import AppointmentsTable from './components/AppointmentsTable.jsx';
import ProviderList from './components/ProviderList.jsx';
import SlotList from './components/SlotList.jsx';
import SuggestionCard from './components/SuggestionCard.jsx';

export default function App() {
  const appointments = usePolling(api.listAppointments);
  const suggestions = usePolling(() => api.listSuggestions('PENDING'));
  const providers = usePolling(api.listProviders);
  const slots = usePolling(api.listSlots);
  const [notice, setNotice] = useState(null);

  const all = [appointments, suggestions, providers, slots];
  const refreshAll = () => all.forEach((q) => q.refresh());
  const loadError = all.map((q) => q.error).find(Boolean);

  // Runs an action, reloads everything, and surfaces failures in the banner.
  async function run(action) {
    try {
      setNotice(null);
      await action();
    } catch (e) {
      setNotice(e.message);
    } finally {
      refreshAll();
    }
  }

  const waiting = appointments.data.filter((a) => a.status === 'RESCHEDULE_PENDING');
  // An appointment can have a manual and an automatic suggestion; show the newest.
  const suggestionFor = (a) =>
    suggestions.data
      .filter((s) => s.appointment.id === a.id)
      .sort((x, y) => y.createdAt.localeCompare(x.createdAt))[0];

  return (
    <div className="app">
      <header className="top">
        <h1>SlotMind</h1>
        <span className="live">Front desk · refreshes every 5s</span>
      </header>

      {(notice || loadError) && (
        <div className="banner" role="alert">
          <span>{notice || `Cannot reach the server: ${loadError}`}</span>
          {notice && <button className="link" onClick={() => setNotice(null)}>Dismiss</button>}
        </div>
      )}

      <main className="grid">
        <section>
          <h2>
            Needs rescheduling <span className="count">{waiting.length}</span>
          </h2>
          {appointments.loading && <p className="muted">Loading…</p>}
          {!appointments.loading && waiting.length === 0 && (
            <p className="muted">Nothing waiting. Take a provider offline or cancel a slot to try it.</p>
          )}
          {waiting.map((a) => (
            <SuggestionCard
              key={a.id}
              appointment={a}
              suggestion={suggestionFor(a)}
              slots={slots.data}
              providers={providers.data}
              onDecide={async (id, status) => {
                await api.decideSuggestion(id, status);
                refreshAll();
              }}
            />
          ))}

          <h2>Appointments</h2>
          <AppointmentsTable
            appointments={appointments.data}
            onSuggest={(id) => run(() => api.suggest(id))}
          />
        </section>

        <aside>
          <h2>Providers</h2>
          <ProviderList
            providers={providers.data}
            onStatus={(id, status) => run(() => api.setProviderStatus(id, status))}
          />
          <h2>Slots</h2>
          <SlotList
            slots={slots.data}
            providers={providers.data}
            onCancel={(id) => run(() => api.setSlotStatus(id, 'CANCELLED'))}
          />
        </aside>
      </main>
    </div>
  );
}
