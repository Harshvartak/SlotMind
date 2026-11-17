# Architecture Decision Record: SlotMind

SlotMind re-books patients automatically when a provider goes offline or a slot is cancelled, and
queues each proposal for front-desk approval. This record answers the five design questions. Each
section runs Context, Options, Decision, Tradeoffs.

## 1. Where does scheduling logic live?

**Context.** Booking, re-booking and conflict prevention touch the same rows (slots,
appointments). If that logic is spread across controllers, the HTTP path and the async path will
drift apart.

**Options.**
1. Logic in controllers.
2. Logic in the entities (rich domain model).
3. A service layer, with a dedicated engine that only *recommends*.

**Decision.** Option 3, split in two:
- `SchedulingEngine` picks a strategy and returns ordered `Recommendation`s. It never writes.
- `SuggestionService` and `AppointmentService` own every state change and every transaction.
  Controllers only validate input, call a service and map the result to a view record.

Conflicts are prevented at one boundary: a slot can only move `OPEN` to `BOOKED` inside a
transaction that re-reads its status, and `Slot` carries `@Version` (see section 3).

**Explicit state machines.** Appointment, slot and suggestion status each define their allowed
moves (`canTransitionTo`) and change only through `moveTo`, which throws `InvalidTransitionException`
(HTTP 409) otherwise; the status fields have no setter. Appointments follow the brief's
`CONFIRMED`, `RESCHEDULE_PENDING`, `RESCHEDULED`, `COMPLETED`, plus two documented ways back: a
rejected suggestion returns an appointment to `CONFIRMED`, and a rescheduled appointment that loses
its slot again goes back to `RESCHEDULE_PENDING`. `COMPLETED` is final; nothing sets it yet because
the brief has no completion endpoint. Provider status is unconstrained (any change is valid), so it
has no state machine.

**Tradeoffs.** More classes than controller-only code, but the HTTP path (`POST /suggest`) and the
async path (the agent) share one code path by construction. The rules that matter (status moves)
live on the entities, while the entities hold no scheduling logic.

## 2. How is the strategy switched at runtime?

**Context.** The brief needs a rule-based strategy and an AI one, switchable by configuration, with
HTTP and async paths using the same contract.

**Options.**
1. `if (ai) ... else ...` in the service.
2. A strategy interface with a registry, chosen by a config key.
3. Spring profiles.

**Decision.** Option 2. `SchedulingStrategy.recommend(SchedulingContext)` is the contract, and the
context is what the brief asks for: the appointment, the trigger, the available slots (open, in the
future, owned by a working provider, nearest first) and the provider roster. A strategy is
therefore a pure function and never reads data; one `SchedulingContextFactory` builds the context.
Spring
injects every implementation as `Map<String, SchedulingStrategy>` keyed by bean name (`"rule"`,
`"ai"`), and `slotmind.scheduling.strategy` selects the key. An unknown name logs a warning and
uses `rule`, so a typo never takes scheduling down. The LLM side has the same shape:
`LlmGateway` with implementations `mock`, `gemini` and `openai_compatible`, selected by
`slotmind.llm.provider`; an unknown provider fails at startup, because silently running a
different model would be worse.

**AI safety.** `AiSchedulingStrategy` never trusts the model. The reply must parse as JSON and then
pass checks: the slot must be one of the available slots in the context (which proves it exists, is
`OPEN`, is in the future and belongs to a working provider); any `alternateProviderId` must own
that slot; confidence is within 0 to 1; reasoning is not empty. Any failure falls back to `RuleBasedStrategy`, so a suggestion is still
created, never silently dropped. Two prompts exist (provider offline may need another provider;
slot cancelled usually needs only a new slot with the same provider) and both ask for one or two
plain sentences a receptionist can read aloud. The patient's name is deliberately not sent to the
model.

**Tradeoffs.** The strategy is read from configuration at startup, so changing it needs a restart,
not a code change. The fallback hides model outages from users, so they are visible only in logs.
The mock gateway picks the nearest listed slot and is a demo stand-in, not a model.

## 3. How is accept made transactional and race-free?

**Context.** Accepting must update the appointment, the old slot and the new slot atomically, and
two front-desk users must never double-book the same slot. The demo contains exactly this race:
two appointments are offered the one open slot.

**Options.**
1. Check-then-write with no protection.
2. Pessimistic locks (`SELECT ... FOR UPDATE`).
3. Optimistic locking with `@Version` on `Slot`, plus a status re-check.
4. A database unique constraint alone.

**Decision.** Option 3. `SuggestionService.decide` is one `@Transactional` method. It re-reads the
target slot, requires it to be `OPEN` and its provider not `OFFLINE`, flips it to `BOOKED` with
`saveAndFlush` (so a version conflict surfaces inside the transaction), releases the old slot only
if it was `BOOKED` (a `CANCELLED` slot stays cancelled), then updates the appointment. The loser
of a race gets HTTP 409 with a readable message, from either the status check or
`ObjectOptimisticLockingFailureException`. On accept, other pending suggestions for the same
appointment are superseded, so a manual and an automatic suggestion cannot both be applied.

**Why not a database constraint alone.** A unique constraint on `appointments.slot_id` would stop a
double booking, but it cannot express the whole invariant: the old slot must be released, a
cancelled slot must stay cancelled, and an offline provider's slot must be refused. It would also
surface as a generic integrity error instead of a clear 409. A constraint is a sensible second line
of defence and is not in place yet.

**Tradeoffs.** Optimistic locking costs the loser a retry, which suits a front desk where
contention is rare. It relies on the database's isolation, and the double-accept race is covered by
a concurrent test (`SchedulingFlowTest`), which runs on H2 only.

## 4. How is the agentic loop decoupled from the API?

**Context.** `PATCH /providers/{id}/status` must return immediately even though finding and
ranking replacements (possibly through an LLM) takes seconds. Setting a provider offline affects
many appointments; cancelling a slot affects one.

**Options.**
1. Do the work inside the request.
2. A scheduler that polls for changes.
3. An application event with an `@Async` listener.
4. An external broker (Kafka, RabbitMQ).

**Decision.** Option 3, with one event type and one handler. `CatalogService` publishes
`RescheduleRequested(trigger, subjectId)` inside the status-change transaction.
`RescheduleAgent` listens with `@TransactionalEventListener(AFTER_COMMIT)` and `@Async`, so it only
runs once the new status is committed (otherwise it would read the stale provider status) and the
request never waits for it. The handler branches on the trigger: `PROVIDER_OFFLINE` selects the
provider's confirmed bookings, `SLOT_CANCELLED` selects the one on that slot. "Confirmed" is
`CONFIRMED` as the brief says, plus `RESCHEDULED`: that booking was confirmed with the patient at
accept time, so if its new slot is cancelled or its new provider goes offline it must be moved
again. This is the one deliberate widening of the brief's scope. Appointments already awaiting a
decision are left alone. Both triggers then call the same `SuggestionService.suggest`, hence the
same engine as the HTTP path.

**Behaviour that matters.** Pending suggestions are de-duplicated per appointment and trigger, so
a repeated event is a no-op. One appointment failing is logged and does not stop the others. The
human checkpoint is structural: the agent only creates `PENDING` suggestions; nothing changes
until a person accepts, and the UI requires ticking "I confirmed this with the patient" first.

**Tradeoffs.** In-process events mean a crash between commit and the listener loses that trigger;
there is no retry or outbox. Appointments are processed one after another, so many appointments
with a slow model take a while. A broker was not justified for a single-node, single-clinic system
and would have been unrequested infrastructure.

## 5. Extensibility and what was deliberately excluded

**Extensibility.** A new constraint, for example `RoomConstraintStrategy`, is one new class: a
`@Component("room")` implementing `SchedulingStrategy`. The engine registry picks it up and a
config value selects it; no other class changes (a test registers a stand-in strategy and shows the
engine selecting it by name and passing it the full context, but no real room strategy exists). A new LLM backend is one `LlmGateway` class plus one `case` in `LlmConfig`.
`openai_compatible` already covers any OpenAI-style server by configuration alone.

**Deliberately excluded.**
- Waitlist, notifications, patient portal and multi-clinic support: out of scope for the brief.
- Authentication and roles: the API is open, which is not acceptable outside a demo.
- Durable storage: H2 in memory is the default and resets on restart. Postgres is available in the
  build, but the seed script is not idempotent, so it has not been wired up.
- Event retry or an outbox, and a database unique constraint as a second defence against double
  booking.
- The SSE reasoning stream (`/suggest/stream`, bonus).
- Typed business exceptions with error codes: planned in `docs/EXCEPTION_PLAN.md`.

## Quality bar

- `mvn verify` runs Spotless (google-java-format) first, then 107 tests: unit tests, state-machine
  tests, MockMvc API tests, an end-to-end demo test over real HTTP, the concurrent double-accept
  race, and a test that loads the brief's Addendum A seed script unmodified. The UI has 65 Vitest
  tests (`npm test`).
- The seed data in `data.sql` is the brief's Addendum A. Its only changes are `DATEADD` for the
  times (in H2, `NOW() + 60` means 60 days) and no `version` column (the schema defaults it to 0).
- Patient-friendly reasoning (the brief's AGT-2) is enforced in the prompt and shown verbatim in the
  UI; the accept race (AGT-3) is covered by the concurrency test and by a 409 shown in the UI.
- The real LLM path has been run once end to end against an OpenAI-compatible gateway. Gemini is
  tested only against a local stub server, not against Google.
