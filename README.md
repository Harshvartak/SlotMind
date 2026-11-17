# SlotMind

When a provider goes offline or a slot is cancelled, SlotMind finds the affected appointments,
proposes new slots (rule-based or AI) and queues each proposal for front-desk approval. Nothing
changes until a person accepts.

Design decisions: [ADR.md](ADR.md). Original plan: [docs/SLOTMIND_PLAN.md](docs/SLOTMIND_PLAN.md).

## Layout

```
backend/    Spring Boot 3.3, Java 17, JPA, H2 (in memory)
frontend/   Vite + React 18, served by nginx in the container
docs/       Execution plan, exception-handling plan
ADR.md      Architecture decisions
docker-compose.yml
```

Backend packages (`com.slotmind`): `domain`, `repository`, `api`, `service`, `engine`, `ai`,
`events`, `agent`, `config`.

## Run it

### With Docker (recommended)

```bash
cp .env.example .env        # optional; the defaults need no key
docker compose up --build   # UI http://localhost:3000, API http://localhost:8080
```

The first build takes a few minutes. The backend is ready when `docker compose ps` shows it as
`healthy`. Data is in memory and **resets on every restart**, which gives you a clean demo each
time.

### Without Docker

You need **JDK 17 to 21** and Node 20+. JDK 25 does not work with this Spring Boot version.

```bash
cd backend  && mvn spring-boot:run              # API on :8080
cd frontend && npm install && npm run dev       # UI on :5173, proxies /api to :8080
```

`mvn spring-boot:run` does not read `.env`. Set the variables below in the same terminal first
(PowerShell: `$env:LLM_PROVIDER = "..."`).

## Configuration

Everything is in `backend/src/main/resources/application.yml`, overridable with environment
variables or `.env`. No code change is needed to switch strategy or model.

| Setting | Env var | Values | Default |
|---|---|---|---|
| `slotmind.scheduling.strategy` | `SCHEDULING_STRATEGY` | `rule`, `ai` | `rule` |
| `slotmind.llm.provider` | `LLM_PROVIDER` | `mock`, `gemini`, `openai_compatible` | `mock` |
| `slotmind.llm.model` | `LLM_MODEL` | the provider's model id | empty |
| `slotmind.llm.base-url` | `LLM_BASE_URL` | API root, or the full `.../chat/completions` URL | empty |
| `slotmind.llm.api-key` | `LLM_API_KEY` | secret; never commit it | empty |
| `slotmind.llm.headers.product` | `LLM_PRODUCT` | extra header some gateways require; blank is not sent | empty |
| `slotmind.llm.timeout-ms` | `LLM_TIMEOUT_MS` | per-call timeout | `8000` |

- `rule` needs no model at all. `ai` with `mock` works offline (the mock picks the nearest open
  slot). If the real model fails or answers badly, the suggestion falls back to the rule logic.
- **OpenAI-compatible** (LiteLLM, Azure/OpenAI gateways, Groq, Ollama):
  `LLM_PROVIDER=openai_compatible`, plus `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` and, if your
  gateway needs it, `LLM_PRODUCT`.
- **Gemini:** `LLM_PROVIDER=gemini`, `LLM_MODEL`, `LLM_API_KEY`.
- Keep `.env` to one `KEY=value` per line. It is git-ignored.

## Seed data

This is the brief's Addendum A, unchanged (apart from the minute-based times, see the ADR).

**Providers:** PRV-001 Dr. Sarah Chen (general, busy), PRV-002 Dr. James Okonkwo (specialist),
PRV-003 Dr. Priya Sharma (pediatric, busy), PRV-004 Dr. Rahul Verma (general), PRV-005 Dr. Ananya
Iyer (specialist, busy).

**Slots:** SLT-001 and SLT-002 (PRV-001, booked), SLT-003 (PRV-003, booked), SLT-004 (PRV-002,
**open**), SLT-005 (PRV-004, **open**), SLT-006 (PRV-005, booked, 60 min).

**Appointments:** APT-001 John Martinez (routine, PRV-001), APT-002 Emily Watson (follow-up,
PRV-001), APT-003 Aisha Patel (urgent, PRV-003), APT-004 Robert Kim (routine, PRV-005).

Slot times are relative to startup (60 to 240 minutes ahead), so the demo always has future slots.

## Behaviour worth knowing

- **Lifecycle.** Appointment, slot and suggestion statuses are explicit state machines; an illegal
  move (for example reopening a completed appointment, or booking a cancelled slot) is a 409.
- **What the loop reschedules.** On provider `OFFLINE`: that provider's `CONFIRMED` appointments, and
  also `RESCHEDULED` ones (already moved once and confirmed with the patient). On slot `CANCELLED`:
  the appointment on that slot. Appointments already awaiting a decision are left alone.
- **Strategy contract.** A strategy receives the appointment, the trigger, the available slots and
  the provider roster, and returns ordered recommendations. It never reads data itself.

## Demo script (about 5 minutes)

Open http://localhost:3000. Every step can also be done with `curl`; see the API table below.

1. **The problem (30 s).** Show the appointments table: four confirmed appointments. John Martinez
   and Emily Watson are both with Dr. Sarah Chen, and only two slots are open anywhere (Dr. James
   Okonkwo and Dr. Rahul Verma).
2. **Trigger (30 s).** In *Providers*, set **Dr. Sarah Chen to OFFLINE**. The request returns
   immediately.
3. **Agentic loop (60 s).** Within a few seconds the *Needs rescheduling* panel fills in on its own
   for John and Emily, with no button pressed. Point out: the trigger badge (Provider offline), the
   confidence bar, the "Different provider" tag, and the reasoning written in plain language that
   the receptionist can read to the patient.
4. **Human checkpoint (45 s).** The Accept button is disabled until "I confirmed this new time with
   the patient" is ticked. Tick it and accept John's suggestion: his appointment moves to
   Dr. Verma, the new slot becomes booked and his old slot reopens.
5. **No double booking (45 s).** Try to accept Emily's suggestion for the same slot. It fails with
   "Slot SLT-005 is no longer open". Reject it, and Emily returns to confirmed.
6. **Slot cancelled (45 s).** In *Slots*, cancel Dr. Ananya Iyer's 60-minute booked slot (Robert
   Kim's). A suggestion with the *Slot cancelled* badge appears: with the rule strategy it offers
   the other specialist, Dr. James Okonkwo.
7. **Booking (30 s).** In *Book an appointment*, book a new patient with Dr. James Okonkwo in his
   open slot. The appointment appears at once, and Robert Kim's pending suggestion for that same
   slot will now fail with the 409 above. The brief's walkthrough starts with booking; doing it
   here keeps the open slots free for steps 2 to 6. To book first, use Dr. Okonkwo's slot and skip
   step 6, which needs it.
8. **Strategy switch (30 s).** Stop the app, set `SCHEDULING_STRATEGY=rule` (or break the API key)
   and repeat step 2. The suggestions still appear, now from the coded rules with fixed confidence
   (0.9 same provider, 0.75 other provider).
9. **Close (30 s).** `mvn verify` runs the formatter check and 107 tests, including the concurrent
   double-accept race, the brief's own seed script, and this whole demo over real HTTP.

## API

| Method and path | Purpose |
|---|---|
| `POST /appointments` | Book `{patientName, type, providerId, slotId}`; the slot becomes `BOOKED` |
| `GET /appointments?status=` | List, optionally filtered |
| `PATCH /providers/{id}/status` | `{status}`; `OFFLINE` fires the async loop |
| `PATCH /slots/{id}/status` | `{status}`; `CANCELLED` fires the async loop; 409 on an illegal move |
| `POST /appointments/{id}/suggest` | On-demand suggestion |
| `GET /suggestions?status=` | List, e.g. `PENDING` |
| `PATCH /suggestions/{id}` | `{status: ACCEPTED or REJECTED}`; 409 if the slot was taken |
| `GET /providers`, `GET /slots` | Reference data |

Quick try (Git Bash or macOS/Linux shell):

```bash
curl -X PATCH localhost:8080/providers/PRV-001/status -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}'
sleep 5
curl "localhost:8080/suggestions?status=PENDING"
```

## Develop and test

```bash
cd backend
mvn spotless:apply   # format; the build fails on unformatted Java
mvn verify           # formatter check, then 107 tests
```

```bash
cd frontend
npm test             # Vitest + Testing Library, 57 tests, no backend needed
```

Code rules for contributors are in [.claude/CLAUDE.md](.claude/CLAUDE.md).

## Known limits

- No authentication. In-memory data only. No retry if the server stops mid-loop.
- Postgres and Ollama profiles exist in `docker-compose.yml` but are untested; the seed script
  would need to be made idempotent for a persistent database.
- Gemini is tested against a local stub only. The OpenAI-compatible gateway has been run against a
  real endpoint once.
- The frontend tests mock the API, so they check the UI's behaviour, not its layout or a real
  browser. There is no end-to-end browser test.
- Not built: the optional SSE stream (`/suggest/stream`) and the UI "ceiling" extras (day view,
  waitlist panel, provider load view).
- Nothing sets an appointment to `COMPLETED`; the brief defines the status but no endpoint for it.
