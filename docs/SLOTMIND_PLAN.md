# SlotMind: 3-Hour Execution Plan

> Hand this file to Claude when you have your machine ready. It contains the full context (problem summary, decisions, commit-by-commit plan, time budget). Say: "Execute SLOTMIND_PLAN.md starting from commit N."

## 0. Context (source: SlotMind hackathon brief)

**Problem:** CareBridge clinics lose time when a provider goes offline or a slot is cancelled, because front desk staff re-book patients by hand. Build a **reactive AI re-scheduling engine**: when a provider goes `OFFLINE` or a slot is `CANCELLED`, the system finds the affected appointments, recommends re-book options (rule-based or AI), and queues them for front-desk approval.

**Format:** solo, originally 5 hours, **now compressed to 3 hours**. 115 pts total (+8 UI ceiling, +5 SSE bonus).
**Stack:** Java 17+, Spring Boot 3.x, JPA, H2 (or Postgres), React 18 (or Angular 17).
**Submit:** public GitHub repo, 5-minute demo, `ADR.md`.
**Demo must show:** PATCH provider `PRV-001` OFFLINE → suggestions appear automatically for 2 appointments → accept → appointment and slots update atomically.

### Scoring map
| Task | Pts | Area |
|---|---|---|
| T-1 Domain model and API | 20 | Backend |
| T-2 Pluggable scheduling engine | 25 | Design pattern |
| T-3 AI scheduling strategy | 25 | AI integration |
| T-4 Agentic re-schedule loop | 15 | Agentic |
| T-5 Front-desk UI | 12 (+8) | Frontend |
| T-6 ADR and walkthrough | 20 | Architecture |
| SSE stream (bonus) | +5 | Optional |

### Domain model
- **Appointment:** id, patientName, type (`ROUTINE | URGENT | FOLLOW_UP`), provider, slot, status `CONFIRMED → RESCHEDULE_PENDING → RESCHEDULED → COMPLETED`
- **Provider:** id, name, specialty (`GENERAL | SPECIALIST | PEDIATRIC`), status (`AVAILABLE | BUSY | OFFLINE`)
- **Slot:** id, provider, startTime, durationMinutes, status (`OPEN | BOOKED | CANCELLED`), `@Version`
- **RescheduleSuggestion:** id, appointment, recommendedSlotId, alternateProviderId (nullable), confidence, reasoning (patient-friendly), status (`PENDING → ACCEPTED | REJECTED`), triggerReason (`INITIAL | PROVIDER_OFFLINE | SLOT_CANCELLED`)

### Endpoints
- `POST /appointments`: book provider and slot
- `GET /appointments?status=`: filterable list
- `PATCH /providers/{id}/status`: `OFFLINE` fires the agentic loop
- `PATCH /slots/{id}/status`: `CANCELLED` fires the agentic loop
- `POST /appointments/{id}/suggest`: on-demand reschedule
- `PATCH /suggestions/{id}`: accept or reject
- *(bonus)* `POST /appointments/{id}/suggest/stream`: SSE reasoning

### Hard requirements from the brief
- Accept updates appointment, old slot and new slot **atomically**, with no double-booking.
- Strategy is switchable at runtime via config, and HTTP and async paths use the same contract.
- AI output must be validated: slot ID and provider ID exist, and the slot is `OPEN`. On LLM failure, fall back to rule-based.
- **Two different prompts:** provider offline (may need an alternate provider) and slot cancelled (the same provider with a new slot may be enough).
- AI reasoning is readable aloud to patients, with no jargon.
- Async loop: endpoints return immediately, and PENDING suggestions are deduplicated per appointment and triggerReason.
- Human checkpoint: front desk confirms with the patient before accept finalizes.
- Sprint 2 extensibility: `RoomConstraintStrategy` should need only a new class implementing the contract and registering.
- LLM output JSON: `{"slotId":"...","alternateProviderId":"...","confidence":0.88,"reasoning":"..."}`

---

## 1. Key design decisions

| Concern | Decision |
|---|---|
| Where logic lives | `SchedulingEngine` service picks the strategy and returns ordered recommendations. Controllers stay thin. |
| Strategy switching | `SchedulingStrategy` interface, a `Map<String, SchedulingStrategy>` registry, and `slotmind.scheduling.strategy=rule\|ai` in config. Both HTTP and async paths call the engine. |
| **LLM provider (configurable)** | `LlmGateway.callLLM(String prompt)` interface. Implementations: `GeminiGateway`, `GroqGateway`, `OllamaGateway`, `MockGateway`. Selected by `slotmind.llm.provider=mock\|gemini\|groq\|ollama` via `@ConditionalOnProperty` or a factory. |
| LLM config keys | `slotmind.llm.provider`, `.model`, `.base-url`, `.api-key` (read from an env var such as `${LLM_API_KEY:}`), `.timeout-ms`. **Default is `mock`**, so the demo never depends on network or keys. |
| Transactional accept | One `@Transactional` service method. `@Version` on `Slot` gives optimistic locking, so a second accept on the same slot gets an `OptimisticLockException` and returns 409. It also re-checks that the slot is `OPEN`. |
| Agentic loop | The PATCH endpoints publish `RescheduleRequested(triggerReason, providerId or slotId)`. An `@Async @TransactionalEventListener(AFTER_COMMIT)` handler resolves the affected appointments, calls the engine and saves suggestions. One handler branches on the trigger, because both go through the same engine. |
| Failure handling | The AI strategy is wrapped in a try/catch that falls back to `RuleBasedStrategy`. The suggestion is still created and is never silently dropped. |
| Extensibility | A new strategy means one new `@Component` implementing `SchedulingStrategy`. Nothing else changes. |

---

## 2. Time budget (180 min)

| Phase | Time | Content |
|---|---|---|
| 1. Setup | 10 min | Scaffold and ADR skeleton |
| 2. Domain, API, rule engine | 45 min | Commits 3–11 |
| 3. AI strategy | 40 min | Commits 12–17 |
| 4. Agentic loop | 30 min | Commits 18–22 |
| 5. Front-desk UI | 30 min | Commits 23–26 |
| 6. ADR, README, demo | 20 min | Commits 27–29 |
| Buffer | 5 min | |

**Rule:** if a phase overruns by more than 5 minutes, cut from its "cut first" list and move on. Never skip the demo path or the ADR.

---

## 3. Commit plan

### Phase 1: Setup (10 min)
1. `chore: init Spring Boot 3 (web, jpa, h2, validation, lombok), Vite React app, folder layout`
2. `docs: ADR.md skeleton with the 5 questions (fill the content in Phase 6)`

### Phase 2: Domain, API, rule engine (45 min)
3. `feat(domain): Provider, Slot, Appointment, RescheduleSuggestion entities and enums`
4. `feat(db): schema and seed data` (see the seed warning in section 5)
5. `feat(api): POST /appointments (booking marks the slot BOOKED), GET /appointments?status=`
6. `feat(api): PATCH /providers/{id}/status and PATCH /slots/{id}/status (no loop yet)`
7. `feat(engine): SchedulingStrategy interface, registry, SchedulingEngine, config switch`
8. `feat(engine): RuleBasedStrategy (same provider's nearest OPEN slot, then nearest OPEN slot with matching specialty)`
9. `feat(api): POST /appointments/{id}/suggest`
10. `feat(api): PATCH /suggestions/{id}, transactional accept with @Version on Slot`
11. `test: rule strategy and accept (including the double-accept race)`

*Cut first:* test depth in commit 11. Keep one concurrency test.

### Phase 3: AI strategy (40 min)
12. `feat(llm): LlmGateway interface and LlmProperties`
13. `feat(llm): Mock (default), Gemini, Groq and Ollama gateways, selected by config`
14. `feat(ai): two prompt templates (PROVIDER_OFFLINE, SLOT_CANCELLED) with urgency, patient context, open slots, provider roster`
15. `feat(ai): AiSchedulingStrategy with JSON parsing`
16. `feat(ai): validate slotId and providerId, require the slot to be OPEN, fall back to rule-based on any failure`
17. `test: AI strategy with Mock, covering bad JSON, an invalid slot ID and a non-OPEN slot`

*Cut first:* the Groq and Ollama implementations. Ship Mock plus one real provider, and keep the interface so the rest are trivial to add later.

### Phase 4: Agentic loop (30 min)
18. `feat(events): RescheduleRequested event, published from the PATCH endpoints`
19. `feat(agent): async listener, provider OFFLINE creates suggestions for all CONFIRMED appointments of that provider`
20. `feat(agent): slot CANCELLED creates a suggestion for the appointment on that slot`
21. `feat(agent): skip duplicate PENDING suggestions, set appointment to RESCHEDULE_PENDING`
22. `test: setting PRV-001 OFFLINE produces 2 suggestions`

### Phase 5: Front-desk UI (30 min)
23. `feat(ui): API client, polling hook (5s), layout`
24. `feat(ui): RESCHEDULE_PENDING appointments with inline suggestion (slot time, provider, confidence, reasoning) and trigger badge (PROVIDER_OFFLINE, SLOT_CANCELLED, manual)`
25. `feat(ui): accept/reject controls, loading and error states`
26. `feat(ui): provider list with status toggle, slot list`

*Cut first, only if time remains:* color coding, day view, SSE `/suggest/stream` (+5), waitlist panel.

### Phase 6: Wrap-up (20 min)
27. `docs: complete ADR.md (Context → Options → Decision → Tradeoffs for each of the 5 questions)`
28. `docs: README (run steps, LLM provider config table, demo script)`
29. `chore: final cleanup, push to public GitHub repo`

---

## 4. ADR checklist (fill in during Phase 6, 5 questions)
1. **Where scheduling logic lives:** engine service, with conflict prevention at the slot status and `@Version` boundary.
2. **Runtime strategy switching:** registry map and a config key, used by both the HTTP and async paths.
3. **Transactional accept:** a single transaction updates the appointment and both slots, with optimistic locking (and why not DB constraints alone).
4. **Agentic loop decoupling:** one event type and one handler with a trigger branch (provider offline affects many appointments, slot cancelled affects one).
5. **Extensibility and exclusions:** room constraints plug in as a new strategy. Deferred items: waitlist, notifications, patient portal, multi-clinic.

Also mention in the walkthrough: AGT-2 (patient-friendly reasoning) and AGT-3 (race on accept).

---

## 5. Watch-outs
- **Seed script bug:** the brief uses `NOW() + 60` for slot times, and in H2 that adds *days*, not minutes. Use `DATEADD('MINUTE', 60, CURRENT_TIMESTAMP)`.
- **Addendum B (LLM Gateway) is only a stub** (`package com.slotmind.ai; // LLMGateway.callLLM(prompt)`). We write the gateway ourselves.
- **Seed data:** providers PRV-001..005, slots SLT-001..006 (OPEN: SLT-004 on PRV-002, SLT-005 on PRV-004), appointments APT-001..004.
- **Demo path:** PATCH `PRV-001` to OFFLINE. APT-001 and APT-002 should get suggestions automatically (PRV-004 is also GENERAL, so the rule strategy has a fallback).
- Async listener must run **after commit**, or it will read stale provider status.
- Never put the API key in the repo. Use an env var, and document it in the README.

---

## 6. Definition of done (check before submitting)
- [ ] Book appointment → works, slot becomes BOOKED
- [ ] PATCH provider OFFLINE → returns immediately, suggestions appear within seconds
- [ ] Suggestions carry trigger badge, confidence and patient-friendly reasoning
- [ ] Accept → appointment, old slot and new slot update atomically; a second accept on the same slot is rejected
- [ ] Switch `slotmind.scheduling.strategy` and `slotmind.llm.provider` via config only, with no code change
- [ ] AI failure falls back to rule-based (test with an invalid key or Mock failure)
- [ ] `ADR.md`, README, public repo, 5-minute demo ready
