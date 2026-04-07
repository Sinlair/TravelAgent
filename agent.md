# Repository Guidelines

## Project Overview

TravelAgent is a multi-module travel planning workspace built with Spring Boot 4, Spring AI 2.0, Vue 3, and Vite.
It turns free-form travel requests into structured plans with specialist-agent routing, Amap enrichment, validation and repair, retrieval, timeline streaming, and recommendation feedback.

Keep changes grounded in the current architecture. Prefer repo-specific fixes over generic AI-generated patterns.

## Core Principles

- Understand the module boundary before editing code.
- Fix root causes instead of stacking narrow patches.
- Keep scripts, docs, config samples, and runtime behavior aligned.
- Do not commit secrets, local env files, logs, exported data, or build artifacts.
- Keep changes reviewable. Small, coherent patches are preferred.

## Repository Layout

### Backend modules

- `travel-agent-domain`
  - Domain contracts: entities, value objects, repository interfaces, gateways, and service contracts.
  - Put domain rules here instead of leaking them into controllers or infrastructure code.

- `travel-agent-app`
  - Application and orchestration layer.
  - Owns API controllers, DTOs, bootstrapping, health checks, and workflow coordination such as `ConversationWorkflow`.

- `travel-agent-infrastructure`
  - Concrete implementations for LLM agents, retrieval, persistence, validators, repairers, vector integrations, and adapters.

- `travel-agent-amap`
  - Amap integration module used behind domain-facing gateway interfaces.

- `travel-agent-amap-mcp-server`
  - Standalone MCP server for exposing Amap-backed tools.

- `travel-agent-types`
  - Shared response, error, and common transport types.

### Frontend and support directories

- `web`
  - Vue 3 + TypeScript frontend using Vite, Pinia, and Vitest.

- `scripts`
  - PowerShell operational scripts and knowledge-processing helpers.

- `docs`
  - Operations, release, and project documentation.

- `data`
  - Local SQLite data, exports, and optional vector-store state.

- `logs`
  - Runtime and smoke-test logs.

## Multi-Agent Workflow

This project uses orchestrated multi-agent execution, not free-form agent-to-agent chat.

Key components:

- `ConversationWorkflow`
  - Main orchestrator for intake, context assembly, routing, specialist execution, persistence, and timeline publication.

- `AgentRouter`
  - Chooses the specialist for the current turn and may request clarification when required planning facts are missing.

- `SpecialistAgent`
  - Unified contract implemented by specialist agents.

- `AgentExecutionResult`
  - Normalized result object returned by specialist agents.

- `TimelinePublisher` and `ConversationStreamHub`
  - Publish backend execution events to the frontend SSE timeline.

Current specialist set:

- `WEATHER`
- `GEO`
- `TRAVEL_PLANNER`
- `GENERAL`

When adding a new specialist:

1. Add or extend a `SpecialistAgent` implementation.
2. Update router selection rules.
3. Preserve the shared execution contract.
4. Update config samples and docs when new configuration is introduced.

## Editing Boundaries

Choose the correct layer before making changes:

- API or workflow orchestration issues: start in `travel-agent-app`
- Domain rules, interfaces, or value objects: start in `travel-agent-domain`
- LLM, retrieval, persistence, Amap integration, validation, or repair logic: start in `travel-agent-infrastructure`
- External map provider integration: start in `travel-agent-amap`
- MCP exposure or tool-serving behavior: start in `travel-agent-amap-mcp-server`
- UI, client state, or interaction issues: start in `web`

Avoid editing generated or local-runtime outputs unless the task explicitly requires it:

- `**/target/`
- `web/dist/`
- `web/node_modules/`
- `logs/`
- `data/exports/`
- `data/milvus/`
- `data/travel-agent.db`
- `.env.travel-agent`
- `web/.env.local`

If a script writes local output, prefer `logs/` or `data/exports/`. Do not create new root-level dump files.

## Build, Run, and Test Commands

Initialize local env:

```powershell
Copy-Item .env.travel-agent.example .env.travel-agent
```

Run preflight:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

Start the full local stack:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -RunPreflight -ToolProvider LOCAL
```

Stop locally started processes:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

Run backend tests:

```powershell
.\mvnw.cmd -B test
```

Run frontend install, tests, and build:

```powershell
cd web
npm.cmd ci
npm.cmd run test
npm.cmd run build
```

Run release-style smoke checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1
```

## Change Expectations

- Keep behavior consistent with `README.md`, `README.zh-CN.md`, and `docs/operations.md`.
- If configuration changes, update `.env.travel-agent.example`.
- If scripts, startup flow, or release flow change, update the relevant docs.
- If UI or API-visible behavior changes, include the smallest useful verification evidence.
- Preserve existing module boundaries. Do not move domain decisions into transport or infrastructure layers without a clear reason.
- Prefer incremental changes that match existing naming and structure.

## Testing Expectations

Run the narrowest relevant validation first, then broaden when the change affects shared flows.

Typical expectations:

- Backend logic changes: relevant Maven tests, or full `.\mvnw.cmd -B test` when the change is cross-cutting.
- Frontend changes: `npm.cmd run test` and `npm.cmd run build` in `web`.
- Operational or release-path changes: `release-smoke-travel-agent.ps1`.
- Startup/configuration changes: `preflight-travel-agent.ps1` and, when relevant, the local start script.

Do not claim tests passed unless they were actually run.

## Configuration and Security

- Never commit `.env.travel-agent`, `web/.env.local`, logs, SQLite files, or export data.
- Keep secrets out of source, docs, examples, and test fixtures.
- Use `.env.travel-agent.example` and `web/.env.example` for documented configuration.
- Treat unexpected root-level runtime files as repository hygiene issues and move them under the approved output directories.

## Commit and Review Hygiene

Recent history uses short, descriptive messages such as `docs: expand architecture overview in README`.

Prefer commit messages that are:

- scoped
- descriptive
- written in imperative style

Before finalizing a change, verify:

- the relevant tests or scripts were run
- docs and config samples match the implementation
- no generated or local-runtime artifacts were added to version control
- the multi-agent routing and shared execution contract still make sense

## Reference Docs

- `README.md`
- `README.zh-CN.md`
- `CONTRIBUTING.md`
- `docs/operations.md`
- `docs/release-checklist.md`
