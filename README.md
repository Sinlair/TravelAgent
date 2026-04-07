# Travel Agent

<p align="center">
  <a href="./README.md">English</a> |
  <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Sinlair/TravelAgent/ci.yml?branch=main&label=ci&style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/license-MIT-F7C948?style=flat-square" />
  <img alt="Java 21" src="https://img.shields.io/badge/java-21-ED8B00?logo=openjdk&logoColor=white&style=flat-square" />
  <img alt="Spring Boot 4" src="https://img.shields.io/badge/spring%20boot-4-6DB33F?logo=springboot&logoColor=white&style=flat-square" />
  <img alt="Spring AI" src="https://img.shields.io/badge/spring%20ai-2.0-0F766E?style=flat-square" />
  <img alt="WebFlux" src="https://img.shields.io/badge/webflux-reactive-0EA5E9?style=flat-square" />
  <img alt="Vue 3" src="https://img.shields.io/badge/vue-3-4FC08D?logo=vuedotjs&logoColor=white&style=flat-square" />
  <img alt="TypeScript" src="https://img.shields.io/badge/typescript-5-3178C6?logo=typescript&logoColor=white&style=flat-square" />
  <img alt="Vite" src="https://img.shields.io/badge/vite-6-646CFF?logo=vite&logoColor=white&style=flat-square" />
  <img alt="Vitest" src="https://img.shields.io/badge/vitest-3-6E9F18?logo=vitest&logoColor=white&style=flat-square" />
  <img alt="SQLite" src="https://img.shields.io/badge/sqlite-3-003B57?logo=sqlite&logoColor=white&style=flat-square" />
  <img alt="Docker" src="https://img.shields.io/badge/docker-ready-2496ED?logo=docker&logoColor=white&style=flat-square" />
</p>

<p align="center">
  <img alt="Multi-Agent Routing" src="https://img.shields.io/badge/multi--agent-routing-111827?style=flat-square" />
  <img alt="Structured Planning" src="https://img.shields.io/badge/structured-planning-0F766E?style=flat-square" />
  <img alt="Amap Gaode" src="https://img.shields.io/badge/amap-gaode-1677FF?style=flat-square" />
  <img alt="Travel Knowledge RAG" src="https://img.shields.io/badge/travel%20knowledge-rag-7C3AED?style=flat-square" />
  <img alt="Image Attachments" src="https://img.shields.io/badge/image-input-enabled-C2410C?style=flat-square" />
  <img alt="Feedback Loop" src="https://img.shields.io/badge/feedback-loop-9333EA?style=flat-square" />
  <img alt="MCP Tools" src="https://img.shields.io/badge/mcp-tools-4F46E5?style=flat-square" />
  <img alt="OpenAI Compatible" src="https://img.shields.io/badge/openai-compatible-10A37F?logo=openai&logoColor=white&style=flat-square" />
  <img alt="Milvus Optional" src="https://img.shields.io/badge/milvus-optional-00A1EA?style=flat-square" />
</p>

<p align="center">
  Travel Agent is a full-stack multi-agent travel planning workspace that turns free-form chat and travel screenshots into grounded itineraries with map enrichment, validation, retrieval, timeline visibility, and explicit recommendation feedback.
</p>

<p align="center">
  <a href="#why-travel-agent">Why Travel Agent</a> •
  <a href="#feature-overview">Feature Overview</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#development">Development</a> •
  <a href="#docs">Docs</a>
</p>

<p align="center">
  <img src="./docs/assets/homepage.png" alt="Travel Agent homepage" />
</p>

## Why Travel Agent

Most travel assistants stop at chat. This repository is built around a more product-like workflow:

- It routes requests to specialist agents instead of treating everything as one generic completion.
- It produces structured plans with budget ranges, daily stops, hotel suggestions, and visible checks.
- It grounds plans with Amap / Gaode weather, POI, geocoding, and transit context.
- It exposes the execution timeline in the UI instead of hiding everything behind a single answer.
- It already has the first practical feedback loop for recommendation quality analysis.

## Feature Overview

| Area | What it does |
| --- | --- |
| Multi-agent workflow | Routes requests across `WEATHER`, `GEO`, `TRAVEL_PLANNER`, and `GENERAL` specialists with shared memory and timeline events |
| Structured planning | Generates itinerary summaries, day-by-day routes, budget breakdowns, hotel suggestions, and constraint checks |
| Grounded enrichment | Uses Amap / Gaode for weather snapshots, POI matching, hotel area resolution, and transit enrichment |
| Travel RAG | Retrieves destination hints from local curated data or Milvus-backed vector search with provenance and trip-style hints |
| Image-assisted intake | Accepts image attachments, extracts travel facts, and merges confirmed facts back into the normal planning flow |
| Feedback loop | Stores `ACCEPTED`, `PARTIAL`, and `REJECTED` outcomes, exports datasets, and aggregates feedback signals on demand |
| Operations | Includes preflight checks, start/stop scripts, release smoke, Docker assets, health endpoints, and CI |

## What Makes This Repo Different

| Instead of | Travel Agent does |
| --- | --- |
| Chat-only itinerary text | Structured plans with UI-ready fields |
| Ungrounded travel suggestions | Map and weather enrichment where available |
| Hidden orchestration | Visible execution timeline and persisted state |
| One-shot generation | Validation and repair loop for planner outputs |
| No product feedback | Stored recommendation outcomes and analysis scripts |

## System Flow

1. `ConversationWorkflow` collects the current message, recent history, task memory, summary, and long-term memory.
2. `AgentRouter` selects the best specialist for the turn.
3. The selected specialist executes against the shared context.
4. Planner requests go through generation, enrichment, validation, repair, and persistence.
5. The frontend receives the answer together with task memory, timeline events, and the structured travel plan.

For image-assisted turns, uploaded images are first summarized into travel facts. Users can confirm or dismiss the extracted facts before they are fed back into the same planning path.

## Architecture

| Module | Responsibility |
| --- | --- |
| `travel-agent-types` | Shared API response and exception types |
| `travel-agent-domain` | Domain entities, value objects, repository interfaces, and service contracts |
| `travel-agent-amap` | Amap HTTP gateway and configuration |
| `travel-agent-infrastructure` | Spring AI agents, retrieval, persistence adapters, and vector integrations |
| `travel-agent-app` | WebFlux API, health endpoints, SSE timeline streaming, and app bootstrapping |
| `travel-agent-amap-mcp-server` | Standalone MCP server for Amap-backed tools |
| `web` | Vue 3 frontend workspace |

## Tech Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Spring WebFlux, Spring Validation, Spring Boot Actuator |
| AI | Spring AI, OpenAI-compatible chat integration, MCP |
| Storage | SQLite, optional Milvus |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vitest |
| Ops | PowerShell scripts, Docker, Docker Compose, Nginx, GitHub Actions |

## Quick Start

### Prerequisites

- Java 21
- Node.js with npm
- Docker Desktop if you want Milvus or containerized deployment
- Python 3 only if you want to rerun collection / cleaning scripts

### 1. Create your local env file

```powershell
Copy-Item .env.travel-agent.example .env.travel-agent
```

### 2. Run preflight

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

### 3. Start the stack

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -RunPreflight -ToolProvider LOCAL
```

Default local endpoints:

- Backend: `http://localhost:18080`
- Frontend: `http://localhost:4173`

### 4. Stop it

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

## Configuration

Important environment variables:

| Category | Variables |
| --- | --- |
| OpenAI / compatible provider | `SPRING_AI_OPENAI_API_KEY`, `SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_CHAT_MODEL`, `SPRING_AI_OPENAI_EMBEDDING_MODEL` |
| Tool provider | `TRAVEL_AGENT_TOOL_PROVIDER`, `TRAVEL_AGENT_AMAP_MCP_ENABLED`, `TRAVEL_AGENT_AMAP_API_KEY`, `TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND` |
| Retrieval | `TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED`, `TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI`, `TRAVEL_AGENT_KNOWLEDGE_VECTOR_COLLECTION_NAME`, `TRAVEL_AGENT_MILVUS_ENABLED`, `TRAVEL_AGENT_MILVUS_URI` |
| Frontend map rendering | `VITE_AMAP_WEB_KEY`, `VITE_AMAP_SECURITY_JS_CODE` |
| Deployment profile | `SPRING_PROFILES_ACTIVE=prod` |

Notes:

- `SPRING_AI_OPENAI_BASE_URL` lets the backend work with OpenAI-compatible gateways.
- `TRAVEL_AGENT_TOOL_PROVIDER` supports `LOCAL` and `MCP`.
- `.env.travel-agent` is git-ignored and should never be committed.

## Development

### Backend

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "<your-openai-key>"
.\mvnw.cmd -pl travel-agent-app -am spring-boot:run
```

### Frontend

```powershell
Set-Location .\web
npm.cmd ci
npm.cmd run dev
```

### Standalone MCP server

```powershell
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-amap-mcp-server -am spring-boot:run
```

## Common Commands

| Task | Command |
| --- | --- |
| Backend tests | `.\mvnw.cmd test` |
| Frontend tests | `Set-Location .\web; npm.cmd run test` |
| Frontend build | `Set-Location .\web; npm.cmd run build` |
| Release smoke | `powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1` |
| Export feedback dataset | `powershell -ExecutionPolicy Bypass -File .\scripts\export-feedback-dataset.ps1` |
| Analyze feedback loop | `powershell -ExecutionPolicy Bypass -File .\scripts\analyze-feedback-loop.ps1` |

## Docker

Included assets:

- [`Dockerfile.app`](./Dockerfile.app)
- [`Dockerfile.mcp`](./Dockerfile.mcp)
- [`docker-compose.app.yml`](./docker-compose.app.yml)
- [`docker-compose.milvus.yml`](./docker-compose.milvus.yml)
- [`web/Dockerfile`](./web/Dockerfile)
- [`web/nginx.conf`](./web/nginx.conf)

Start the main stack:

```powershell
docker compose -f docker-compose.app.yml up --build -d
```

Start the stack with MCP enabled:

```powershell
docker compose -f docker-compose.app.yml --profile mcp up --build -d
```

Start Milvus separately when vector retrieval is enabled:

```powershell
docker compose -f docker-compose.milvus.yml up -d
```

## Project Structure

```text
.
|- travel-agent-app
|- travel-agent-domain
|- travel-agent-infrastructure
|- travel-agent-amap
|- travel-agent-amap-mcp-server
|- travel-agent-types
|- web
|- scripts
`- docs
```

## Docs

- [`docs/knowledge-rag.md`](./docs/knowledge-rag.md)
- [`docs/multimodal-roadmap.md`](./docs/multimodal-roadmap.md)
- [`docs/multimodal-roadmap.zh-CN.md`](./docs/multimodal-roadmap.zh-CN.md)
- [`docs/operations.md`](./docs/operations.md)
- [`docs/release-checklist.md`](./docs/release-checklist.md)
- [`CONTRIBUTING.md`](./CONTRIBUTING.md)
- [`SECURITY.md`](./SECURITY.md)

## Known Limits

- Amap-backed grounding is the strongest path today, so the planner currently fits China-focused travel scenarios best.
- Some retrieval chunks are still more listing-shaped than ideal planner guidance.
- Production packaging is usable, but secret management and reverse-proxy templates are still lightweight.
- Full value from the planner depends on valid model-provider and map-provider configuration.

## License

This project is licensed under the MIT License. See [`LICENSE`](./LICENSE).
