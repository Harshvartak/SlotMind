# Sprint 3: Waitlist & Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tick a box when its step is done and commit the plan file with the task, so the next agent sees the true state.

**Prerequisite:** Sprint 2 is merged (`docs/superpowers/plans/2026-11-17-sprint-2-constraints-preferences.md`). This plan uses its `TypeRequirements`, `LoadService`, `Slot#room()`, `RoomConstraintStrategy` and the 5-component `SchedulingContext` (with a 4-argument constructor).

**Goal:** Fill freed slots from a waitlist, notify patients by SMS/email when a reschedule is accepted, route patients across clinics, make the AI aware of provider queue pressure, and let patients reschedule themselves through a link.

**Architecture:** Everything reuses Sprint 1's event-driven shape: a new `SlotOpened` event drives waitlist auto-fill and a new `AppointmentRescheduled` event drives notifications, both through `@Async @TransactionalEventListener(AFTER_COMMIT)` listeners so the HTTP request never waits and a failure never rolls back a booking. Humans stay in the loop: the waitlist produces an *offer* that the front desk books after calling the patient; the patient portal is the one path where the patient is the confirming party. Clinic routing is a decorator strategy over `room`.

**Tech Stack:** Java 17, Spring Boot 3.3.5, JPA/H2, Lombok, JUnit 5 + MockMvc + Awaitility, React 18 + Vitest. No new dependencies; no real SMS/email provider (see Task 3).

**Spec:** `slotmind-brief.html` section "Sprint 3 - Waitlist & Notifications" (5 bullets), plus "waitlist panel" and "provider load view" from the T-5 ceiling. Existing design: `ADR.md`.

## Global Constraints

- Same rules as Sprint 2's Global Constraints (`.claude/CLAUDE.md`): Spotless via `mvn spotless:apply`, no wildcard imports, no bare `null` (Optional accessors for nullable columns), no magic numbers/strings, class <= 300 lines, method <= 30 lines, <= 4 parameters (use a parameter object), records for DTOs, no `catch (Exception)`, no Boot/Java/dependency upgrade.
- Ask before adding infrastructure: **no** SMTP/Twilio/broker/cache in this sprint. Channels are interfaces with logging implementations; a real one is a separate decision.
- The brief seed must keep loading unmodified (`BriefSeedCompatibilityTest`): new columns nullable or defaulted.
- Listeners that touch the database after commit must run in their own transaction (`@Transactional(propagation = REQUIRES_NEW)` on a method of a **different** bean than the listener; `@Transactional` and `@Async` do nothing on self-invocation).
- Do not put PII (email, phone, patient token) in logs or in `Views` returned to the front-desk list endpoints.
- Verify with `cd backend && mvn spotless:apply && mvn verify` and `cd frontend && npm test`. Async tests follow `AgentLoopTest` (not transactional, `@DirtiesContext`, Awaitility with a deadline and a quiet period).

## Review Focus

1. Two slots opening at once, or the same slot opening twice, must produce **one** outstanding offer per slot and never offer one entry two slots at once (Task 5).
2. A waitlist offer whose slot is taken by a displaced patient before the front desk books it must fail with 409 and leave the entry `OFFERED`, not double-book (Task 6).
3. A notification channel that throws, a patient with no email/phone, and a rolled-back accept must not break or produce a message for an accept that did not happen (Task 3).
4. Portal tokens: unknown, malformed, and reused after the appointment is `COMPLETED` or already `RESCHEDULE_PENDING` all give a non-leaking 404/409; a slot id the engine did not offer cannot be booked (Task 7).
5. Cross-clinic: same-clinic options are used first, other clinics only when none fit, and the patient is told the clinic name; with `cross-clinic-enabled=false` there is no cross-clinic fallback (Task 2).
6. A waitlist match must respect Sprint 2's hard rules (equipment, duration, provider daily cap, provider not OFFLINE) (Task 5).

## File Structure

| File | Responsibility |
|---|---|
| `domain/Clinic.java`, `repository/ClinicRepository.java` | Clinics; `Provider.clinic()` |
| `engine/ClinicRoutingStrategy.java` | `@Component("clinic")` decorator over `room` |
| `domain/NotificationLog.java`, `notification/*` | Channels, composing and recording messages |
| `events/AppointmentRescheduled.java`, `events/SlotOpened.java` | The two new events |
| `domain/WaitlistEntry.java`, `domain/WaitlistStatus.java`, `service/WaitlistService.java`, `agent/WaitlistAgent.java`, `engine/WaitlistMatcher.java`, `api/WaitlistController.java` | Waitlist |
| `service/PortalService.java`, `api/PortalController.java` | Patient self-service |
| `frontend/src/components/WaitlistPanel.jsx`, `NotificationLog.jsx`, `frontend/src/PortalApp.jsx` | UI |

---

### Task 1: Queue pressure in the context, the AI prompt and the provider load view

**Files:**
- Modify: `service/LoadService.java`, `engine/SchedulingContext.java`, `engine/SchedulingContextFactory.java`, `ai/PromptBuilder.java`, `api/Views.java` (`ProviderView`), `api/ProviderController.java`
- Test: extend `LoadServiceTest.java`, `PromptBuilderTest.java`; new `SchedulingContextFactoryQueueTest.java`

**Interfaces:**
- Produces: `LoadService#queueLength(String providerId): int` = active appointments (`AppointmentStatus.ACTIVE`) whose slot starts within `QUEUE_HORIZON_HOURS = 24` from now; `SchedulingContext` gains `Map<String, Integer> queueByProvider` (provider id to queue length, every provider in the roster present) as a 6th component, keeping the 4-argument constructor (empty map) and adding `SchedulingContext#withSlots(List<Slot> slots): SchedulingContext` (used by Task 2); `ProviderView.queueLength` and `ProviderView.maxDailyAppointments` already exists from Sprint 2.

- [ ] **Step 1: Write failing tests.** `LoadServiceTest.queueLengthCountsOnlyNextTwentyFourHours` (seed: PRV-001 has 2 within the next 24h -> 2; add a PRV-001 appointment on a slot 48h out -> still 2); `SchedulingContextFactoryQueueTest.contextCarriesQueueForEveryProvider` (map has all five seed provider ids, PRV-001 -> 2, PRV-002 -> 0); `PromptBuilderTest.rosterLinesShowQueueAndPromptAsksToAvoidBusyProviders` (roster line contains `upcomingAppointments=2`; prompt contains the sentence constant `QUEUE_ADVICE` telling the model to prefer providers with fewer upcoming appointments unless the appointment is URGENT and the busier provider is clearly sooner).
- [ ] **Step 2: Run** `cd backend && mvn -q test -Dtest=LoadServiceTest,SchedulingContextFactoryQueueTest,PromptBuilderTest`. Expected: FAIL.
- [ ] **Step 3: Implement** per Interfaces. The factory builds the map with one `queueLength` call per provider (5 providers; note in a `shortcut:` comment that this is N queries, switch to one grouped query when providers exceed ~50). `ProviderController` response uses `queueLength` from `LoadService`.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(ai): provider queue pressure in the scheduling context and prompt`

### Task 2: Multi-clinic routing

**Files:**
- Create: `domain/Clinic.java`, `repository/ClinicRepository.java`, `engine/ClinicRoutingStrategy.java`
- Modify: `domain/Provider.java`, `api/Views.java` (`ProviderView`, new `ClinicView`), `engine/SchedulingProperties.java`, `application.yml`, `data.sql`, `ai/PromptBuilder.java`
- Test: `ClinicRoutingStrategyTest.java`, extend `ProviderSlotApiTest.java`, `PromptBuilderTest.java`

**Interfaces:**
- Produces: `@Entity Clinic(String id, String name)`; `Provider#clinic(): Optional<Clinic>` (nullable `@ManyToOne`, getter suppressed, same pattern as `Slot#room()`); `ProviderView.clinic: Optional<ClinicView>` with `record ClinicView(String id, String name)`; `@Component("clinic") final class ClinicRoutingStrategy implements SchedulingStrategy`, `NAME = "clinic"`, constructor `(RoomConstraintStrategy room, SchedulingProperties props)`; `SchedulingProperties` gains `boolean crossClinicEnabled` (`slotmind.scheduling.cross-clinic-enabled`, default `true`).
- Behaviour of `recommend(ctx)`: `sameClinic` = available slots whose provider's clinic equals the appointment provider's clinic (two empty clinics count as the same); `room.recommend(ctx.withSlots(sameClinic))`; if that is empty **and** cross-clinic is enabled, `room.recommend(ctx.withSlots(otherClinics))`, with each reasoning gaining the sentence constant `CLINIC_NOTE` (`" This is at our %s clinic."`). Never both lists merged.
- Seed (appended to `data.sql`, brief seed untouched): clinics `CLN-001 'CareBridge Central'`, `CLN-002 'CareBridge North'`; all providers to `CLN-001` except `PRV-004` to `CLN-002`.

- [ ] **Step 1: Write failing tests.** `ClinicRoutingStrategyTest`: `usesSameClinicWhenOneFits` (APT-001 on PRV-001/CLN-001; add an open PRV-001 slot -> recommended, reasoning has no `CLINIC_NOTE`); `crossesClinicsOnlyWhenNoneFitsLocally` (no same-clinic slot; SLT-005 is PRV-004/CLN-002 -> recommended, reasoning ends with `This is at our CareBridge North clinic.`); `noCrossClinicWhenDisabled` (props `false` -> empty); `clinicLessProvidersAreTreatedAsOneClinic` (providers with no clinic route normally). `ProviderSlotApiTest.providersExposeClinic`. `PromptBuilderTest.slotLinesCarryClinicName`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=ClinicRoutingStrategyTest,ProviderSlotApiTest,PromptBuilderTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add `clinic=<name|none>` to the AI slot lines and one prompt sentence preferring the patient's own clinic.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected fallout: Sprint 1/2 tests that expect `PRV-004`'s reasoning text without the clinic note under strategy `rule`/`room` are unaffected (they do not use `clinic`); tests asserting the provider JSON shape may need the new `clinic` field. Fix fixtures only.
- [ ] **Step 5: Commit** `feat(engine): multi-clinic routing as a decorator strategy`

### Task 3: SMS/email on accept

**Files:**
- Create: `events/AppointmentRescheduled.java`, `domain/NotificationLog.java`, `domain/ChannelType.java`, `domain/NotificationStatus.java`, `repository/NotificationLogRepository.java`, `notification/NotificationChannel.java`, `notification/NotificationException.java`, `notification/LoggingEmailChannel.java`, `notification/LoggingSmsChannel.java`, `notification/NotificationListener.java`, `notification/NotificationService.java`, `service/BookingCommand.java`, `api/NotificationController.java`
- Modify: `domain/Appointment.java`, `service/AppointmentService.java`, `service/SuggestionService.java`, `api/AppointmentController.java` (`BookRequest`), all callers of `AppointmentService.book`
- Test: `NotificationFlowTest.java` (async style), `NotificationServiceTest.java`

**Interfaces:**
- Produces: `Appointment#email(): Optional<String>` and `#phone(): Optional<String>` (nullable columns `contact_email`, `contact_phone`, getters suppressed); `record BookingCommand(String patientName, AppointmentType type, String providerId, String slotId, Optional<String> contactEmail, Optional<String> contactPhone)` and `AppointmentService#book(BookingCommand)` **replacing** the 4-parameter `book` (update every caller and test); `BookRequest` gains optional `@Email String contactEmail` and `String contactPhone`; `record AppointmentRescheduled(String appointmentId, String oldSlotId, String newSlotId)` published inside `SuggestionService.accept`'s transaction; `enum ChannelType { EMAIL, SMS }`, `enum NotificationStatus { SENT, FAILED }`; `interface NotificationChannel { ChannelType type(); void send(String recipient, String body) throws NotificationException; }`; `NotificationListener#onRescheduled(AppointmentRescheduled)` (`@Async @TransactionalEventListener(AFTER_COMMIT)`) calling `NotificationService#notifyRescheduled(String appointmentId)` (`@Transactional(REQUIRES_NEW)`); `GET /notifications?appointmentId=` returning `NotificationView(id, appointmentId, channel, status, createdAt, body)` (never the recipient).
- Message body (constant template): `"Hello %s, your appointment has moved to %s with %s on %s."` using first name, clinic-or-nothing, provider name, formatted time. Task 7 appends the portal link.

- [ ] **Step 1: Write failing tests.**
  - `NotificationServiceTest.sendsOneMessagePerAvailableContact`: appointment with email only -> one `NotificationLog` (EMAIL, SENT); both -> two; neither -> none and no error.
  - `NotificationServiceTest.channelFailureIsRecordedNotThrown`: a stub `NotificationChannel` throwing `NotificationException` -> one log with `FAILED`, no exception escapes.
  - `NotificationFlowTest.acceptingASuggestionNotifiesThePatient` (async style): book/seed an appointment with an email, set its provider OFFLINE, accept the resulting suggestion; await one `NotificationLog` with `SENT` whose body contains the new provider's name; `GET /notifications?appointmentId=` returns it.
  - `NotificationFlowTest.noNotificationWhenAcceptFails`: accepting a suggestion whose slot was taken (409) leaves zero logs.
- [ ] **Step 2: Run** `mvn -q test -Dtest=NotificationServiceTest,NotificationFlowTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Channels log `"{channel} to {masked}"` only (mask all but the last 2 chars of the recipient). `NotificationService` injects `List<NotificationChannel>`. Publish the event in `accept` after the appointment is saved.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green after updating the `book` callers.
- [ ] **Step 5: Commit** `feat(notify): SMS/email on accepted reschedules behind channel interfaces`

### Task 4: Waitlist domain and API

**Files:**
- Create: `domain/WaitlistEntry.java`, `domain/WaitlistStatus.java`, `repository/WaitlistRepository.java`, `service/WaitlistService.java`, `api/WaitlistController.java`
- Modify: `api/Views.java` (`WaitlistView`). `InvalidTransitionException` is already mapped to 409 by `ApiExceptionHandler`.
- Test: `WaitlistStatusTest.java`, `WaitlistApiTest.java`

**Interfaces:**
- Produces: `enum WaitlistStatus { WAITING, OFFERED, BOOKED, CANCELLED }` with `canTransitionTo`: `WAITING -> OFFERED | CANCELLED`; `OFFERED -> BOOKED | WAITING | CANCELLED`; `BOOKED`, `CANCELLED` final; repeating the status is allowed (same style as `SlotStatus`). `@Entity WaitlistEntry(String id /* "WLT-" via Ids */, String patientName, AppointmentType type, Specialty specialty, preferredProviderId, contactEmail, contactPhone, WaitlistStatus status, offeredSlotId, LocalDateTime createdAt)` with `Optional` accessors `preferredProvider()`, `email()`, `phone()`, `offeredSlot()`, and `moveTo(WaitlistStatus)` (no status setter). `WaitlistService`: `WaitlistEntry add(WaitlistCommand c)`, `List<WaitlistEntry> list(WaitlistStatus status)`, `WaitlistEntry decide(String id, WaitlistStatus status)` (Task 6 implements the `BOOKED` branch; until then `BOOKED` throws `Errors.badRequest("booking an offer is not supported yet")`). Endpoints: `POST /waitlist` -> 201; `GET /waitlist?status=`; `PATCH /waitlist/{id}` body `{"status": "BOOKED"|"WAITING"|"CANCELLED"}`. `record WaitlistCommand(String patientName, AppointmentType type, Specialty specialty, Optional<String> preferredProviderId, Optional<String> contactEmail, Optional<String> contactPhone)`. Request validation: `@NotBlank patientName`, `@NotNull type`, `@NotNull specialty`; an unknown `preferredProviderId` is 404 (`Errors.notFound("Provider", id)`).

- [ ] **Step 1: Write failing tests.** `WaitlistStatusTest` (table of all allowed and forbidden moves, including `BOOKED -> WAITING` forbidden); `WaitlistApiTest`: `addsAndListsEntries` (201, then `GET /waitlist?status=WAITING` contains it), `rejectsBlankPatient` (400), `unknownPreferredProviderIs404`, `cancelMovesToCancelled`, `cancellingTwiceIs200AndCancelledToWaitingIs409`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=WaitlistStatusTest,WaitlistApiTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Follow `AppointmentController`/`AppointmentService` conventions exactly (view mapping in `Views`, errors via `Errors`).
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(waitlist): waitlist entries, state machine and API`

### Task 5: Waitlist auto-fill when a slot opens

**Files:**
- Create: `events/SlotOpened.java`, `engine/WaitlistMatcher.java`, `agent/WaitlistAgent.java`
- Modify: `service/WaitlistService.java`, `service/SuggestionService.java` (accept), `service/CatalogService.java` (`setSlotStatus`), `repository/WaitlistRepository.java`
- Test: `WaitlistMatcherTest.java`, `WaitlistAutoFillTest.java` (async style)

**Interfaces:**
- Consumes: `TypeRequirements#violation`, `LoadService#hasCapacity`, `Slot#getProvider`.
- Produces: `record SlotOpened(String slotId)` published (a) in `SuggestionService.accept` when the old slot is released `BOOKED -> OPEN`, and (b) in `CatalogService.setSlotStatus` when the new status is `OPEN`; `final class WaitlistMatcher` (pure) with `Optional<WaitlistEntry> pick(Slot slot, List<WaitlistEntry> waiting, TypeRequirements requirements)`: keeps entries whose `preferredProvider` equals the slot's provider id, or, when absent, whose `specialty` equals the provider's specialty, and for which `requirements.violation(type, slot)` is empty; orders `URGENT` first, then `createdAt` ascending; `WaitlistService#offerSlot(String slotId): Optional<WaitlistEntry>` (`@Transactional`): does nothing unless the slot is `OPEN`, in the future, its provider is not `OFFLINE`, `LoadService.hasCapacity` is true and no entry is already `OFFERED` for that slot; otherwise moves the picked entry `WAITING -> OFFERED` with `offeredSlotId`; `WaitlistAgent#onSlotOpened(SlotOpened)` (`@Async @TransactionalEventListener(AFTER_COMMIT)`) calls it and logs, catching `ResponseStatusException | DataAccessException` as `RescheduleAgent` does. An entry already `OFFERED` is never picked again (only `WAITING` entries are passed to the matcher). The slot is **not** reserved: it stays `OPEN` and the first booking wins (documented tradeoff).

- [ ] **Step 1: Write failing tests.**
  - `WaitlistMatcherTest` (no Spring): `urgentBeatsEarlierRoutine`; `olderEntryWinsAmongEqualUrgency`; `preferredProviderOverridesSpecialty` (entry wants PRV-002, slot is PRV-004 same specialty -> not picked); `equipmentRuleExcludesEntry` (URGENT entry, slot without ECG -> empty); `emptyWhenNobodyWaits`.
  - `WaitlistAutoFillTest` (async style): `cancelledThenReopenedSlotIsOfferedToWaitingPatient` (waiting GENERAL ROUTINE entry; cancel SLT-005 then reopen it via `PATCH /slots/SLT-005/status` `OPEN`; await entry `OFFERED` with `offeredSlotId = SLT-005`); `acceptedRescheduleFreesOldSlotAndOffersIt`; `twoEntriesOneSlotOnlyOneIsOffered` (Review Focus 1); `slotAtProviderDailyCapIsNotOffered` and `offlineProviderSlotIsNotOffered` (Review Focus 6); `reopeningTwiceDoesNotOfferTwice`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=WaitlistMatcherTest,WaitlistAutoFillTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add `existsBySlotOffered`-style repository query as needed (`existsByOfferedSlotIdAndStatus(String, WaitlistStatus)`).
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green. Existing `AgentLoopTest`/`DemoFlowTest` must stay green (they publish `SlotOpened` but nobody waits).
- [ ] **Step 5: Commit** `feat(agent): waitlist auto-fill when a slot opens`

### Task 6: Waitlist offer decision (book or decline)

**Files:**
- Modify: `service/WaitlistService.java`, `api/WaitlistController.java`
- Test: extend `WaitlistApiTest.java`; new `WaitlistRaceTest.java`

**Interfaces:**
- Consumes: `AppointmentService#book(BookingCommand)` (Task 3).
- Behaviour of `WaitlistService#decide(id, status)`: `BOOKED` requires the entry to be `OFFERED`; in one `@Transactional` method it calls `appointments.book(...)` for the offered slot and that slot's provider (copying name, type and contacts), then moves the entry to `BOOKED`; any 409 from `book` (slot taken, capacity, requirements) propagates and the entry stays `OFFERED`. `WAITING` (decline) clears `offeredSlotId`. `BOOKED` from any other status is 409. Returns the entry; the controller also returns the new appointment id in `WaitlistView.appointmentId: Optional<String>`.

- [ ] **Step 1: Write failing tests.** `bookingAnOfferCreatesTheAppointmentAndBooksTheSlot` (entry OFFERED for SLT-005 -> `PATCH BOOKED` -> 200, slot `BOOKED`, an appointment exists for the patient with status `CONFIRMED`, entry `BOOKED`); `decliningReturnsToWaitingAndClearsTheOffer`; `bookingWhenSlotWasTakenIs409AndEntryStaysOffered` (book SLT-005 for someone else first; Review Focus 2); `bookingAWaitingEntryIs409`; `WaitlistRaceTest.reschedulerAndWaitlistRaceForOneSlotOnlyOneWins` (concurrent threads like `SchedulingFlowTest`: accept a suggestion for SLT-005 and book the waitlist offer for SLT-005; exactly one succeeds, the other gets 409, slot has exactly one appointment).
- [ ] **Step 2: Run** `mvn -q test -Dtest=WaitlistApiTest,WaitlistRaceTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Remove the "not supported yet" stub from Task 4.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(waitlist): book or decline an offered slot`

### Task 7: Patient self-service portal (backend)

**Files:**
- Create: `service/PortalService.java`, `api/PortalController.java`
- Modify: `domain/Appointment.java`, `domain/TriggerReason.java`, `repository/AppointmentRepository.java`, `service/AppointmentService.java`, `service/SuggestionService.java`, `notification/NotificationService.java`, `application.yml`, `agent/RescheduleAgent.java` (the `affected` switch)
- Test: `PortalApiTest.java`

**Interfaces:**
- Produces: `Appointment#portalToken(): Optional<String>` (nullable column, getter suppressed); `AppointmentRepository#findByPortalToken(String): Optional<Appointment>`; `AppointmentService#portalTokenFor(String appointmentId): String` (get-or-create `UUID.randomUUID().toString()`; lazily, so seed rows need no change); `TriggerReason.PATIENT_REQUEST` (the agent's `affected` switch returns `List.of()` for it, like `INITIAL`; the UI badge treats it as "patient"); `SuggestionService#rescheduleTo(String appointmentId, String slotId, TriggerReason trigger): RescheduleSuggestion` (`@Transactional`: the slot must be among `engine.recommend(appt, trigger)` or 409 `"That time is no longer offered"`; creates the suggestion exactly as `suggest` does, then accepts it through the existing `decide(...)` so atomicity and the `@Version` race protection are reused); `GET /portal/{token}` -> `PortalView(String patientFirstName, LocalDateTime currentStart, String providerName, Optional<String> clinicName, List<PortalOption> options)` with `PortalOption(String slotId, LocalDateTime startTime, String providerName, Optional<String> clinicName, String reasoning)` from `engine.recommend(appt, PATIENT_REQUEST)`; `POST /portal/{token}/reschedule` body `{"slotId": "..."}` -> `PortalView` of the updated appointment. `slotmind.portal.base-url` (env `PORTAL_BASE_URL`, default `http://localhost:5173`); the notification body from Task 3 gains `" Need another time? %s/portal/%s"` (base URL, token).
- Rules: only `CONFIRMED` or `RESCHEDULED` appointments can self-reschedule (`RESCHEDULE_PENDING` -> 409 "already being rescheduled by the clinic"; `COMPLETED` -> 409); an unknown or malformed token is always 404 `"Not found"` with no hint; the human checkpoint is the patient themself (document in ADR). The portal never returns other patients' data, appointment ids or provider ids beyond what `PortalView` lists.

- [ ] **Step 1: Write failing tests (`PortalApiTest`).** `unknownTokenIs404`; `malformedTokenIs404`; `getShowsOptionsFromTheEngine` (token for APT-001 via `AppointmentService.portalTokenFor`; options non-empty, first `slotId` = SLT-005 under strategy `rule`); `rescheduleBooksTheChosenSlotAtomically` (APT-001 -> `RESCHEDULED`, SLT-001 `OPEN`, SLT-005 `BOOKED`); `slotNotOfferedIs409` (e.g. `SLT-006`); `pendingAppointmentCannotSelfReschedule` (create a suggestion first); `completedAppointmentIs409`; `tokenIsStablePerAppointment` (`portalTokenFor` twice returns the same value; two appointments differ); `acceptNotificationContainsThePortalLink` (extends the async notification test from Task 3).
- [ ] **Step 2: Run** `mvn -q test -Dtest=PortalApiTest,NotificationFlowTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** `PortalController` stays thin (validate, call `PortalService`, map views).
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(portal): token-based patient self-service reschedule`

### Task 8: Front-desk UI: waitlist panel, notifications, provider load, patient badge

**Files:**
- Create: `frontend/src/components/WaitlistPanel.jsx`, `NotificationLog.jsx`, tests `WaitlistPanel.test.jsx`, `NotificationLog.test.jsx`
- Modify: `frontend/src/api/client.js`, `client.test.js`, `App.jsx`, `App.test.jsx`, `components/ProviderList.jsx`, `components/SuggestionCard.jsx` (trigger badge), `components/lists.test.jsx`, `src/test/fixtures.js`, `src/styles.css`

**Interfaces:**
- Produces (`api` in `client.js`): `listWaitlist(status?)`, `addToWaitlist({patientName, type, specialty, preferredProviderId?, contactEmail?, contactPhone?})`, `decideWaitlist(id, status)`, `listNotifications(appointmentId?)`. `WaitlistPanel({entries, onAdd, onDecide, busy})`: a small add form; rows with status; for `OFFERED` rows the offered slot and **Book** and **Decline** buttons (Book needs the same "I confirmed this with the patient" tick as `SuggestionCard`); errors (e.g. the 409 text) shown through `App`'s existing notice banner. `ProviderList` rows show `queueLength` against `maxDailyAppointments` (a simple "3 queued / max 20 per day" line plus the clinic name). The trigger badge maps `PATIENT_REQUEST` to "Patient".

- [ ] **Step 1: Write failing tests.** `WaitlistPanel.test.jsx`: `listsEntriesWithStatus`, `offeredEntryShowsBookAndDecline`, `bookIsDisabledUntilConfirmedWithPatient`, `submittingTheFormCallsOnAdd`; `NotificationLog.test.jsx`: `showsChannelAndStatusAndBody`, `showsEmptyState`; `client.test.js`: each new call hits the right path/method; `lists.test.jsx`: `providerRowShowsQueueAndClinic`; `App.test.jsx`: the waitlist panel renders from polled data.
- [ ] **Step 2: Run** `cd frontend && npm test`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement.** Poll the waitlist and notifications with the existing `usePolling`. Reuse existing CSS classes and `Badge`; add only the styles that do not exist.
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(ui): waitlist panel, notification log and provider load view`

### Task 9: Patient portal page

**Files:**
- Create: `frontend/src/PortalApp.jsx`, `frontend/src/PortalApp.test.jsx`
- Modify: `frontend/src/main.jsx`, `frontend/src/api/client.js`, `client.test.js`

**Interfaces:**
- Consumes: `GET /portal/{token}` and `POST /portal/{token}/reschedule` (Task 7).
- Produces: `api.getPortal(token)`, `api.portalReschedule(token, slotId)`; `main.jsx` renders `PortalApp` when `window.location.pathname` starts with `/portal/` (token is the next path segment), otherwise `App`. No router dependency. nginx already falls back to `index.html`, and `/api/` is proxied, so no server change.

- [ ] **Step 1: Write failing tests.** `PortalApp.test.jsx`: `showsCurrentAppointmentAndOptions`, `choosingAnOptionCallsRescheduleAndShowsTheNewTime`, `showsFriendlyMessageOn404` (no stack text, no ids), `showsServerMessageOnConflict`, `emptyOptionsShowsCallTheClinic`.
- [ ] **Step 2: Run** `npx vitest run src/PortalApp.test.jsx`. Expected: FAIL.
- [ ] **Step 3: Implement** a single-column mobile-friendly page using the existing styles; patient-friendly copy only (no ids, no jargon).
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(ui): patient self-service portal page`

### Task 10: ADR, README, demo script

**Files:**
- Modify: `ADR.md`, `README.md`, `.env.example`

- [ ] **Step 1: ADR.** Add "Sprint 3 decisions" (Context -> Options -> Decision -> Tradeoffs): (a) waitlist as offer + human booking, slot not reserved, first booking wins, 409 loser; (b) notifications as post-commit events behind channel interfaces, logging channels only, outbox/retry deferred (same limit as the agent); (c) clinic routing as a decorator, same clinic first; (d) portal: token auth, the patient is the human checkpoint, no rate limiting yet; (e) queue pressure only informs the AI, the rule strategy ignores it. Update "Deliberately excluded": remove waitlist, notifications, patient portal, multi-clinic; add real SMS/email providers, portal rate limiting and expiry, waitlist notifications, patient entity.
- [ ] **Step 2: README.** Config rows: `slotmind.scheduling.strategy` adds `clinic`; `SCHEDULING_CROSS_CLINIC_ENABLED`; `PORTAL_BASE_URL`; document the new endpoints and the demo: book an appointment with an email, PATCH `PRV-001` OFFLINE, accept, read `GET /notifications`, open the portal link; add a waitlist entry, cancel and reopen a slot, book the offer.
- [ ] **Step 3: `.env.example`** gets the two new variables with comments.
- [ ] **Step 4: Run** `cd backend && mvn verify` and `cd frontend && npm test`. Expected: all green.
- [ ] **Step 5: Commit** `docs: Sprint 3 ADR, README and demo script`

## Self-review

- **Spec coverage:** waitlist auto-fill (Tasks 4-6, 8); SMS/email on accept (3); multi-clinic routing (2); AI considers downstream queue pressure (1, shown in UI in 8); patient self-service portal (7, 9). The brief's T-5 ceiling items "waitlist panel" and "provider load view" are in Task 8.
- **Consistency:** `BookingCommand` (Task 3) is used by Task 6; `SchedulingContext#withSlots` and `queueByProvider` (Task 1) are used by Task 2; `SlotOpened` (Task 5) is the only new publisher edit in `SuggestionService`/`CatalogService` besides `AppointmentRescheduled` (Task 3). `TriggerReason.PATIENT_REQUEST` (Task 7) is handled in the agent switch.
- **Not in this sprint:** a real SMS/email provider, waitlist-booking notifications, portal rate limiting/expiry, patient entity, distance-based clinic ranking, slot or clinic management endpoints.
