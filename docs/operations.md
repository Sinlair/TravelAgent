# Operations Guide

This document centralizes runtime layout, local commands, and export conventions for the repository.

## Runtime Layout

- `logs/runtime/`
  - local stdout and stderr logs when you choose to redirect process output
- `logs/archive/`
  - historical logs moved out of the repository root or legacy locations
- `data/exports/`
  - manually saved feedback datasets or analysis reports
- `data/travel-agent.db`
  - local SQLite database used by the application
- `data/milvus/`
  - local Milvus state when vector retrieval is enabled

## Current Conventions

- Runtime logs should go under `logs/`, not the repository root.
- Feedback exports should go under `data/exports/`, not mixed into runtime logs.
- Generated files under `logs/` and `data/exports/` are git-ignored.
- If you introduce a new helper that writes files locally, prefer one of these directories instead of inventing a new root-level drop location.

## Common Local Commands

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

Offline feedback evaluation:

```bash
python scripts/analyze_feedback_loop.py
```

The script reads `data/travel-agent.db` by default and writes JSON plus Markdown reports under `data/exports/`.

Frontend tests and build:

```bash
cd web
npm run test
npm run build
```

Shutdown:

- Stop each running terminal with `Ctrl + C`, or stop the processes through your local process manager.

## Cleanup Notes

- Before publishing, make sure local runtime logs in `logs/` are not mixed into a commit.
- If you need to inspect old logs after cleanup, check `logs/archive/`.
- If a local workflow still writes to an unexpected root-level path, treat that as a repository hygiene issue and move it under `logs/` or `data/exports/`.
