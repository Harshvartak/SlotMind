# Sprint 2: Constraints & Preferences Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tick a box when its step is done and commit the plan file with the task, so the next agent sees the true state.

**Goal:** Make rescheduling respect rooms, equipment, appointment duration, provider daily caps and patient time-of-day habits, delivered as a third scheduling option `RoomConstraintStrategy`.

**Architecture:** Sprint 1's `SchedulingStrategy` contract stays unchanged. Hard rules (room features, duration, daily cap) are enforced in three places that cannot be bypassed: the context factory (candidate slots), `AppointmentService.book` and `SuggestionService.accept`. Soft rules (morning/afternoon preference) only re-order candidates inside the new `room` strategy, which wraps `RuleBasedStrategy`. The `ai` strategy gets the same facts in its prompt and has its answer re-validated against the hard rules.

**Tech Stack:** Java 17, Spring Boot 3.3.5, JPA/H2, Lombok, JUnit 5 + MockMvc, React 18 + Vitest.

**Spec:** `slotmind-brief.html` section "Sprint 2 - Constraints & Preferences" (5 bullets), plus the "Sprint 2 heads up" in T-2 ("RoomConstraintStrategy should mean implementing the contract and registering"). Sprint 1 design: `ADR.md`, `docs/SLOTMIND_PLAN.md`.

## Global Constraints

- Project rules are in `.claude/CLAUDE.md`. Key ones: google-java-format enforced by Spotless (run `mvn spotless:apply` in `backend/` after editing any `.java`, never skip the check); no wildcard imports; no bare `null` (nullable JPA columns are read through an `Optional` accessor, as `RescheduleSuggestion.alternateProvider()` does); no magic numbers or strings (constants); class <= 300 lines, method <= 30 lines, <= 4 parameters; records for DTOs; constructor injection; no `catch (Exception)`.
- Do not upgrade Spring Boot (3.3.5), Java (17) or add dependencies.
- The brief's Addendum A seed must keep loading **unmodified** (`BriefSeedCompatibilityTest`, `backend/src/test/resources/brief-seed.sql`). So every new column is nullable or carries a SQL default, as `Slot.version` does with `columnDefinition`.
- A new strategy is one `@Component("name")` implementing `SchedulingStrategy`; the engine, controllers and agent must not change to accommodate it.
- Strategies stay pure functions of `SchedulingContext`; only `SchedulingContextFactory` reads data for them.
- Controllers return `Views` records, never entities. Errors go through `service/Errors` (409 for rule violations).
- Verify with `cd backend && mvn spotless:apply && mvn verify` and `cd frontend && npm test`. Baseline before starting: 107 backend tests and 57 frontend tests pass.
- Test style: backend tests extend `ApiTestSupport` (transactional, rolls back, brief seed). Async-loop tests do not (see `AgentLoopTest`).

## Review Focus

Failure modes the brief implies but no single task's happy path covers. Each has its test in the named task.

1. An appointment moving to another slot of the **same** provider on a day where that provider is at the cap must not be blocked by its own current booking (Task 3).
2. A slot with **no room** offered to an appointment type that requires equipment must be rejected, not treated as "unconstrained" (Task 2).
3. `room` strategy with candidates that all violate a hard rule returns **empty** (HTTP 422 on `/suggest`); it must never fall back to unconstrained slots (Task 5).
4. A patient with no history, one past appointment, or an even morning/afternoon split has **no** preference, and nothing breaks (Task 4).
5. The AI returns a slot that exists and is OPEN but violates the type's equipment/duration: it must fall back to rules, not be saved (Task 6).
6. The brief's seed script, loaded without rooms, still works and `SCHEDULING_STRATEGY=rule` behaves exactly as in Sprint 1 (Tasks 1, 8).

## File Structure

| File | Responsibility |
|---|---|
| `domain/Room.java`, `domain/RoomFeature.java` | A room and the equipment it has |
| `domain/DayPeriod.java` | `MORNING`/`AFTERNOON` and `of(LocalDateTime)` |
| `engine/TypeRule.java`, `engine/AppointmentTypeProperties.java` | Per-type duration and required features, from config |
| `engine/TypeRequirements.java` | `violation(type, slot)`: the one place that says whether a slot suits a type |
| `service/LoadService.java` | How many active appointments a provider has on a day; cap checks |
| `engine/PatientProfile.java`, `service/PreferenceService.java` | Preferred day period derived from booking history |
| `engine/RoomConstraintStrategy.java` | The third strategy (`@Component("room")`) |
| modified: `Slot`, `Provider`, `SchedulingContext`, `SchedulingContextFactory`, `AppointmentService`, `SuggestionService`, `PromptBuilder`, `AiSchedulingStrategy`, `Views`, `data.sql`, `application.yml` | see tasks |

---

### Task 1: Rooms and equipment on slots

**Files:**
- Create: `backend/src/main/java/com/slotmind/domain/Room.java`, `RoomFeature.java`, `backend/src/main/java/com/slotmind/repository/RoomRepository.java`
- Modify: `domain/Slot.java`, `api/Views.java` (`SlotView`), `backend/src/main/resources/data.sql`, `backend/src/test/java/com/slotmind/ApiTestSupport.java`
- Test: `backend/src/test/java/com/slotmind/RoomApiTest.java`

**Interfaces:**
- Produces: `enum RoomFeature { ECG, XRAY }`; `@Entity Room(String id, String name, Set<RoomFeature> features)` (`@ElementCollection(fetch = EAGER)`, `@CollectionTable(name = "room_features")`); `Slot#room(): Optional<Room>` (nullable `@ManyToOne`, Lombok getter suppressed like `RescheduleSuggestion.alternateProviderId`); `record RoomView(String id, String name, Set<RoomFeature> features)` and `SlotView.room: Optional<RoomView>`; test helper `addOpenSlot(String id, String providerId, long minutesFromNow, String roomId)` overload in `ApiTestSupport`.

- [ ] **Step 1: Write the failing test.** In `RoomApiTest`: `slotsExposeRoomAndFeatures` does `GET /slots`, asserts SLT-004 has `$[?(@.id=='SLT-004')].room.id` = `RM-2` and `.room.features` = `["ECG"]`; `slotWithoutRoomHasEmptyRoom` saves a slot with no room and asserts the JSON `room` is absent/null (match whichever way `Optional.empty` already serializes in `SuggestionView.alternateProviderId`).
- [ ] **Step 2: Run** `mvn -q test -Dtest=RoomApiTest` in `backend/`. Expected: FAIL (no `room` field).
- [ ] **Step 3: Implement** the entities, repository and view. Seed in `data.sql`, appended after the existing inserts, with a comment that the brief's seed itself is untouched: rooms `RM-1 'Room 1'` (no features), `RM-2 'Room 2'` (`ECG`), `RM-3 'Room 3'` (no features); `UPDATE slots SET room_id = ...`: SLT-001, SLT-002, SLT-005 -> RM-1; SLT-003 -> RM-3; SLT-004, SLT-006 -> RM-2. Check the generated join column name (`room_id`) and element-collection column name by running the app/test, and adjust the INSERT into `room_features` accordingly.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: all tests green, including `BriefSeedCompatibilityTest`.
- [ ] **Step 5: Commit** `feat(domain): rooms and equipment on slots`

### Task 2: Appointment-type rules and `TypeRequirements`

**Files:**
- Create: `engine/TypeRule.java`, `engine/AppointmentTypeProperties.java`, `engine/TypeRequirements.java`
- Modify: `application.yml`, `service/AppointmentService.java`, `service/SuggestionService.java`
- Test: `TypeRequirementsTest.java`, extend `AppointmentApiTest.java` and `SuggestionApiTest.java`

**Interfaces:**
- Produces: `record TypeRule(int durationMinutes, Set<RoomFeature> requiredFeatures)`; `@ConfigurationProperties("slotmind") record AppointmentTypeProperties(Map<AppointmentType, TypeRule> appointmentTypes)` (register the same way `SchedulingProperties` is); `@Component TypeRequirements` with `Optional<TypeRule> ruleFor(AppointmentType)` and `Optional<String> violation(AppointmentType type, Slot slot)` (empty = the slot suits the type; otherwise a readable reason such as `"needs 30 minutes, slot has 15"` or `"needs ECG, slot has none"`). A type with no configured rule has no requirements.
- `application.yml` defaults (add under `slotmind:`): `appointment-types: ROUTINE: {duration-minutes: 30}`, `FOLLOW_UP: {duration-minutes: 15}`, `URGENT: {duration-minutes: 30, required-features: [ECG]}`.

- [ ] **Step 1: Write failing tests.**
  - `TypeRequirementsTest` (build a `TypeRequirements` directly from an `AppointmentTypeProperties`, no Spring): `urgentNeedsEcgRoom` (slot with no room -> violation contains "ECG"; slot in a room with ECG -> empty); `routineNeedsThirtyMinutes` (15-minute slot -> violation contains "30"; 30-minute slot -> empty); `unconfiguredTypeHasNoRequirements`.
  - `AppointmentApiTest.bookingUrgentIntoRoomWithoutEcgIsConflict`: book URGENT into SLT-005 (RM-1, no ECG) -> 409 and message contains "ECG"; book URGENT into SLT-004 (RM-2, ECG) -> 201.
  - `SuggestionApiTest.acceptIntoUnsuitableSlotIsConflict`: set APT-001's type to URGENT in the test, create a suggestion for SLT-005 by saving a `RescheduleSuggestion` directly, accept -> 409 and appointment/slots unchanged.
- [ ] **Step 2: Run** `mvn -q test -Dtest=TypeRequirementsTest,AppointmentApiTest,SuggestionApiTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `TypeRequirements`. In `AppointmentService.requireBookable` (make it non-static) and in `SuggestionService.accept`, call `requirements.violation(...)` and throw `Errors.conflict("Slot does not suit this appointment: " + reason)`. Constructor-inject `TypeRequirements`.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected fallout: existing tests that book or accept `URGENT` appointments into slots without ECG now get 409; fix those fixtures (give the slot a room with ECG or use a ROUTINE type), do not loosen the rule.
- [ ] **Step 5: Commit** `feat(engine): per-type duration and equipment rules enforced at booking and accept`

### Task 3: Provider daily cap

**Files:**
- Create: `service/LoadService.java`
- Modify: `domain/Provider.java`, `repository/AppointmentRepository.java`, `engine/SchedulingContextFactory.java`, `service/AppointmentService.java`, `service/SuggestionService.java`, `api/Views.java` (`ProviderView`)
- Test: `LoadServiceTest.java`, extend `AppointmentApiTest.java`

**Interfaces:**
- Produces: `Provider.maxDailyAppointments` (`int`, `@Builder.Default` = constant `DEFAULT_MAX_DAILY = 20`, column `columnDefinition = "int default " + DEFAULT_MAX_DAILY + " not null"` so the brief seed still loads); `ProviderView.maxDailyAppointments`; `AppointmentRepository#countByProviderIdAndStatusInAndSlotStartTimeBetween(String providerId, Collection<AppointmentStatus> statuses, LocalDateTime from, LocalDateTime to): long`; `LoadService` with `int activeOn(String providerId, LocalDate day)`, `boolean hasCapacity(Provider provider, Slot slot)` and `boolean hasCapacityExcluding(Provider provider, Slot slot, Appointment moving)` (does not count `moving` when it is already one of that provider's bookings that day). "Active" = `AppointmentStatus.ACTIVE`.

- [ ] **Step 1: Write failing tests.**
  - `LoadServiceTest` (extends `ApiTestSupport`): `countsActiveAppointmentsOnTheDay` (PRV-001 has 2 on the day of SLT-001/SLT-002; set the test provider's cap to 2 -> `hasCapacity` false, cap 3 -> true); `movingWithinSameProviderDoesNotCountItself` (cap 2, APT-001 moving to another PRV-001 slot that day -> `hasCapacityExcluding` true, `hasCapacity` false); `completedAppointmentsDoNotCount`.
  - `AppointmentApiTest.bookingBeyondDailyCapIsConflict`: cap PRV-004 at 1, book one appointment into SLT-005, add another PRV-004 open slot the same day, booking it -> 409 and message contains the provider name.
  - `SchedulingContextFactoryTest.excludesSlotsOfProvidersAtCap`: with PRV-004 at its cap, `contexts.create(APT-001, INITIAL).availableSlots()` does not contain SLT-005.
- [ ] **Step 2: Run** the three tests. Expected: FAIL.
- [ ] **Step 3: Implement.** Day bounds: `from = day.atStartOfDay()`, `to = day.plusDays(1).atStartOfDay().minusNanos(1)` (Spring Data `Between` is inclusive on both ends). `SchedulingContextFactory` filters with `hasCapacityExcluding(provider, slot, appointment)`. `AppointmentService.book` uses `hasCapacity`; `SuggestionService.accept` uses `hasCapacityExcluding(target.getProvider(), target, appt)`. Both throw `Errors.conflict(provider.getName() + " is fully booked on " + day)`.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(engine): provider max daily appointments`

### Task 4: Patient preference (morning vs afternoon)

**Files:**
- Create: `domain/DayPeriod.java`, `engine/PatientProfile.java`, `service/PreferenceService.java`
- Modify: `repository/AppointmentRepository.java`, `engine/SchedulingContext.java`, `engine/SchedulingContextFactory.java`
- Test: `PreferenceServiceTest.java`

**Interfaces:**
- Produces: `enum DayPeriod { MORNING, AFTERNOON; static DayPeriod of(LocalDateTime t) }` (before 12:00 is `MORNING`, constant `NOON_HOUR = 12`); `record PatientProfile(Optional<DayPeriod> preferredPeriod)` with `static final PatientProfile NONE`; `AppointmentRepository#findByPatientName(String): List<Appointment>`; `PreferenceService#profileOf(String patientName): PatientProfile`. History = the start time of the current slot of every appointment with that `patientName` (the brief has no patient entity; the name is the identity, a known limit). A period is preferred only when there are at least `MIN_HISTORY = 2` appointments and one period has strictly more than the other.
- `SchedulingContext` becomes `(Appointment appointment, TriggerReason trigger, List<Slot> availableSlots, List<Provider> providers, PatientProfile profile)` and keeps a **4-argument constructor** that uses `PatientProfile.NONE`, so `RuleBasedStrategyTest` and `SchedulingEngineTest` compile unchanged. `SchedulingContextFactory.create` fills the profile from `PreferenceService`.

- [ ] **Step 1: Write failing tests** in `PreferenceServiceTest` (builds appointments via repository for a fresh patient name): `noHistoryMeansNoPreference`, `singleAppointmentMeansNoPreference`, `twoMorningsAndOneAfternoonPrefersMorning`, `evenSplitMeansNoPreference`, and `dayPeriodBoundary` (11:59 MORNING, 12:00 AFTERNOON).
- [ ] **Step 2: Run** `mvn -q test -Dtest=PreferenceServiceTest`. Expected: FAIL.
- [ ] **Step 3: Implement** as specified. Keep the 4-arg constructor as an explicit secondary record constructor.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(engine): derive patient time-of-day preference into the scheduling context`

### Task 5: `RoomConstraintStrategy`

**Files:**
- Create: `engine/RoomConstraintStrategy.java`
- Modify: `README.md` only in Task 8 (not here)
- Test: `RoomConstraintStrategyTest.java`; extend `SchedulingEngineTest.java`

**Interfaces:**
- Consumes: `TypeRequirements#violation` (Task 2), `SchedulingContext#profile` (Task 4), `RuleBasedStrategy#recommend`.
- Produces: `@Component("room") final class RoomConstraintStrategy implements SchedulingStrategy`, `public static final String NAME = "room"`, constructor `(RuleBasedStrategy rules, TypeRequirements requirements)`.

- [ ] **Step 1: Write failing tests** (extend `ApiTestSupport`, build contexts with `SchedulingContextFactory`, `TriggerReason.INITIAL`):
  - `dropsSlotsThatViolateTheTypeRules`: set APT-001 to URGENT; candidates SLT-005 (no ECG) and a new open PRV-004 slot `SLT-T01` in RM-2 (ECG) -> only `SLT-T01` recommended.
  - `returnsEmptyWhenEveryCandidateViolates`: URGENT with only SLT-005 available -> empty list (Review Focus 3).
  - `prefersPatientsPeriodWithoutDroppingOthers`: two open PRV-001 slots tomorrow, 15:00 (listed first, because it is earlier in the context's nearest-first order) and 09:00 the day after; give the patient a morning history; assert the 09:00 slot is recommended first and the 15:00 slot is still present.
  - `reasoningMentionsMatchedPreference`: when the first recommendation is in the preferred period its reasoning ends with the sentence constant `PREFERENCE_NOTE` (`" That is in the %s, which you usually prefer."` formatted with `morning`/`afternoon`, lowercase); when not, reasoning equals the rule strategy's text.
  - `SchedulingEngineTest.engineSelectsRoomStrategyByName`: `slotmind.scheduling.strategy=room` selects it (follow the existing stand-in strategy test there).
- [ ] **Step 2: Run** `mvn -q test -Dtest=RoomConstraintStrategyTest,SchedulingEngineTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `recommend(ctx)`: filter `availableSlots` by `requirements.violation(appointment.getType(), slot).isEmpty()`; stable-sort preferred-period slots first (`List.sort` with a comparator on the boolean only, input already nearest-first); call `rules.recommend(new SchedulingContext(...refined...))`; append the preference note to a recommendation's reasoning when its slot's `DayPeriod` equals the profile's. No registry or engine change.
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(engine): RoomConstraintStrategy as the third scheduling option`

### Task 6: AI strategy knows the constraints

**Files:**
- Modify: `ai/PromptBuilder.java`, `ai/AiSchedulingStrategy.java`; tests `PromptBuilderTest.java`, `AiSchedulingStrategyTest.java`

**Interfaces:**
- Consumes: `TypeRequirements#ruleFor`, `#violation`, `SchedulingContext#profile`, `Slot#room`.
- Produces: `PromptBuilder.build(SchedulingContext ctx, Optional<TypeRule> rule): String` **replacing** the 4-argument `build` (update its callers and tests). The prompt adds: required duration and equipment for the type; the patient's preferred period when present (worded as a preference, not a rule); and `room=<id> features=<list|none>` on every slot line. `AiSchedulingStrategy` takes `TypeRequirements` in its constructor and, in `validate`, throws `AiException("slot does not suit the appointment type: " + reason)` when `violation` is present.

- [ ] **Step 1: Write failing tests.** `PromptBuilderTest.promptStatesDurationEquipmentAndPreference` (URGENT -> contains "ECG" and "30"; profile MORNING -> contains "morning"; no profile -> no "prefer" sentence); `promptSlotLinesCarryRoomFeatures`. `AiSchedulingStrategyTest.slotViolatingTypeRulesFallsBackToRules` (Mock gateway returning SLT-005 for an URGENT appointment -> result equals what `RuleBasedStrategy` would return for the same context, and no exception escapes). Reuse the test's existing stub-gateway pattern.
- [ ] **Step 2: Run** `mvn -q test -Dtest=PromptBuilderTest,AiSchedulingStrategyTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Keep both prompts (offline / cancelled); add the constraint lines once in the shared template. Keep "no ids, no jargon" wording intact (AGT-2).
- [ ] **Step 4: Run** `mvn -q spotless:apply && mvn -q verify`. Expected: green.
- [ ] **Step 5: Commit** `feat(ai): give the model room, duration and preference context and re-check its answer`

### Task 7: Front-desk UI shows the constraints

**Files:**
- Modify: `frontend/src/components/SlotList.jsx`, `ProviderList.jsx`, `frontend/src/test/fixtures.js`, `frontend/src/components/lists.test.jsx`

**Interfaces:**
- Consumes: `SlotView.room` (`{id, name, features}` or absent) and `ProviderView.maxDailyAppointments` from Tasks 1 and 3.
- Produces: slot rows show the room name and one chip per feature (`slot()` fixture gains `room: undefined` default, and a `room(o)` builder); provider rows show `max N/day`.

- [ ] **Step 1: Write failing tests** in `lists.test.jsx`: `slotRowShowsRoomAndFeatures` (room "Room 2" with `['ECG']` renders both texts); `slotWithoutRoomShowsNoRoomLabel`; `providerRowShowsDailyCap`.
- [ ] **Step 2: Run** `cd frontend && npx vitest run src/components/lists.test.jsx`. Expected: FAIL.
- [ ] **Step 3: Implement** in the two components, reusing the existing `Badge` for feature chips; add minimal CSS in `styles.css` only if the existing classes do not fit.
- [ ] **Step 4: Run** `npm test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(ui): show slot rooms, equipment and provider daily caps`

### Task 8: ADR, README and demo check

**Files:**
- Modify: `ADR.md`, `README.md`, `.env.example`

- [ ] **Step 1: ADR.** Extend section 5 (Extensibility): `RoomConstraintStrategy` now exists, was added without touching the engine, controllers or agent (state which files changed: only new class plus the context record). Add a short "Sprint 2 decisions" section in Context -> Options -> Decision -> Tradeoffs form: hard rules at three enforcement points vs soft ranking in the strategy; why the cap counts `ACTIVE` appointments and excludes the moving one; patient identity is the name (no patient entity); room double-booking and room-unavailable triggers are not modelled; `rule` and `ai` are not equipment-aware on their own beyond the shared checks at booking/accept (so `room` is the recommended strategy). Remove "RoomConstraintStrategy" from "Deliberately excluded" if listed there.
- [ ] **Step 2: README.** Config table: `SCHEDULING_STRATEGY` values `rule`, `ai`, `room`; new section documenting `slotmind.appointment-types` and `max-daily-appointments`. `.env.example`: update the `# rule | ai` comment to `# rule | ai | room`.
- [ ] **Step 3: Demo check.** Start with `SCHEDULING_STRATEGY=room`: PATCH `PRV-001` OFFLINE -> APT-001 and APT-002 get suggestions for SLT-005 (RM-1, no equipment needed for ROUTINE/FOLLOW_UP). Record the result in the PR description.
- [ ] **Step 4: Run** `cd backend && mvn verify` and `cd frontend && npm test`. Expected: all green.
- [ ] **Step 5: Commit** `docs: Sprint 2 ADR and README`

## Self-review

- **Spec coverage:** room and equipment per type (Tasks 1, 2); `RoomConstraintStrategy` third option (5); patient preference morning/afternoon (4, 5, 6); duration varies by type (2); provider max daily appointments (3). UI and docs (7, 8).
- **Consistency:** `TypeRequirements#violation`, `LoadService#hasCapacityExcluding`, `SchedulingContext#profile` and `PatientProfile` names are used identically across tasks. Sprint 3 builds on `TypeRequirements`, `LoadService`, `Slot#room()` and the 5-argument `SchedulingContext`.
- **Not in this sprint:** room-unavailable trigger, room double-booking checks, creating slots/rooms through the API, a patient entity.
