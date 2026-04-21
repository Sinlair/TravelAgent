# Operations Guide

This file is written for day-to-day local work on macOS.
Assume:

- macOS Terminal
- `zsh` or `bash`
- `./mvnw`
- `python3`
- `docker compose`

If an older document still shows Windows-specific commands such as `mvnw.cmd` or `npm.cmd`, prefer the commands in this guide.

## Runtime Layout

- `logs/runtime/`
  local stdout and stderr logs when you redirect process output
- `logs/archive/`
  historical logs moved out of the repository root or legacy locations
- `data/exports/`
  saved feedback datasets, quality replay reports, and offline analysis output
- `data/travel-agent.db`
  local SQLite database used by the app
- `data/milvus/`
  local Milvus state when vector retrieval is enabled

## Local Conventions

- Put runtime logs under `logs/`, not the repository root.
- Put generated reports under `data/exports/`.
- Treat `data/exports/quality-reports/` as the default location for replay output.
- If you add a helper that writes local files, keep it inside `logs/` or `data/exports/`.

## Common Commands

Backend:

```bash
./mvnw -pl travel-agent-app -am spring-boot:run
```

Frontend:

```bash
cd web
npm ci
npm run dev
```

Optional MCP server:

```bash
./mvnw -pl travel-agent-amap-mcp-server -am spring-boot:run
```

Backend tests:

```bash
./mvnw -B test
```

Frontend tests and build:

```bash
cd web
npm run test
npm run build
```

## Mac-First Local Demo

Use `local-demo` when you need a stable local path without OpenAI, MCP, or Milvus:

```bash
./mvnw -pl travel-agent-app -am spring-boot:run -Dspring-boot.run.profiles=local-demo
```

This is the default profile for smoke checks and quality replay.

## Evaluation Commands

Offline feedback evaluation:

```bash
python3 scripts/analyze_feedback_loop.py
```

The script reads `data/travel-agent.db` by default and writes JSON plus Markdown reports under `data/exports/`.

Quality scenario replay:

```bash
python3 scripts/run_quality_scenarios.py --base-url http://localhost:8080
```

The replay runner writes JSON plus Markdown reports under `data/exports/quality-reports/`.
It exits non-zero when a scenario drifts from its expected outcome or expected agent route.

## Docker Notes

If you need local Milvus or containerized services on macOS, use Docker Desktop and the modern Compose CLI:

```bash
docker compose -f docker-compose.milvus.yml up -d
docker compose -f docker-compose.app.yml up -d
```

Logs:

```bash
docker compose -f docker-compose.app.yml logs -f
```

Shutdown:

```bash
docker compose -f docker-compose.milvus.yml down
docker compose -f docker-compose.app.yml down
```

## Shutdown

- Stop backend and frontend Terminal tabs with `Control-C`.
- If you started containers, stop them with `docker compose ... down`.

## Cleanup Notes

- Before publishing, make sure runtime logs in `logs/` are not mixed into a commit.
- If you need older logs after cleanup, check `logs/archive/`.
- If a local workflow writes to an unexpected root-level path, move it under `logs/` or `data/exports/`.

## Related Docs

- [`release-checklist.md`](./release-checklist.md)
- [`development-guide.md`](./development-guide.md)
