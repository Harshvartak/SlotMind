# SlotMind

Reactive AI re-scheduling engine for CareBridge clinics. Full plan: [docs/SLOTMIND_PLAN.md](docs/SLOTMIND_PLAN.md).

## Layout

```
backend/    Spring Boot 3 (Java 17), JPA, H2 default / Postgres optional
frontend/   Vite + React 18, served by nginx in the container
docs/       Execution plan
ADR.md      Architecture decisions
docker-compose.yml
```

Backend packages (`com.slotmind`): `domain`, `repository`, `api`, `engine`, `ai`, `events`, `agent`.

## Run with containers

```bash
cp .env.example .env            # optional
docker compose up --build       # UI :3000, API :8080
docker compose --profile postgres up   # Postgres instead of H2
docker compose --profile ollama up     # local LLM
```

## Run locally

```bash
cd backend  && mvn spring-boot:run
cd frontend && npm install && npm run dev     # :5173, proxies /api -> :8080
```

## Configuration

| Key (env) | Values | Default |
|---|---|---|
| `slotmind.scheduling.strategy` (`SCHEDULING_STRATEGY`) | `rule`, `ai` | `rule` |
| `slotmind.llm.provider` (`LLM_PROVIDER`) | `mock`, `gemini` | `mock` |
| `LLM_MODEL`, `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_TIMEOUT_MS` | provider specific | empty |

Never commit API keys; use `.env`.
