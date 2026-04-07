# Travel Agent

[![CI](https://github.com/Sinlair/TravelAgent/actions/workflows/ci.yml/badge.svg)](https://github.com/Sinlair/TravelAgent/actions/workflows/ci.yml)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-3-003B57?logo=sqlite&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

An open-source multi-agent travel planning application built with Spring Boot, Spring AI, Vue 3, SQLite, optional Amap MCP integration, and a city-level travel knowledge RAG pipeline.

The project is designed to do more than return free-form chat. It routes requests to specialized agents, builds structured itineraries, validates them against practical constraints, enriches them with weather and map context, and keeps a UI-friendly execution timeline.

## Overview

- `WEATHER`, `GEO`, `TRAVEL_PLANNER`, and `GENERAL` specialist routing
- structured itinerary generation with validation and repair
- Amap / Gaode enrichment for weather, geocoding, POI matching, and transit
- destination knowledge retrieval from local data or Milvus
- full-stack delivery with Vue UI, health checks, scripts, Docker, and CI

## Contents

- [What It Does](#what-it-does)
- [Why This Repo Exists](#why-this-repo-exists)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Manual Run](#manual-run)
- [Docker Deployment](#docker-deployment)
- [Testing and CI](#testing-and-ci)
- [Travel Knowledge RAG](#travel-knowledge-rag)
- [Security Notes](#security-notes)
- [Contributing](#contributing)
- [License](#license)

## What It Does

- Routes user requests across `WEATHER`, `GEO`, `TRAVEL_PLANNER`, and `GENERAL`
- Generates structured travel plans with budget, pace, opening-hours, and transit-load checks
- Enriches itinerary stops with Amap / Gaode map data
- Retrieves destination-specific travel knowledge from a local dataset or Milvus
- Stores conversations, task memory, timelines, and travel plans in SQLite
- Exposes a Vue 3 frontend for chat, plan visualization, timeline inspection, and retrieval provenance

## Why This Repo Exists

Most travel assistants stop at chat completion. This project focuses on a more product-like workflow:

- deterministic plan structure instead of only prose
- validation and repair loops instead of single-shot generation
- visible planner context instead of hidden internal reasoning
- operational scripts, health checks, smoke tests, and CI instead of demo-only code

## Highlights

- Agent-based request routing instead of a single general-purpose reply path
- Constraint-aware itinerary generation with budget, pace, opening-hours, and transit checks
- Real map enrichment through Amap / Gaode plus optional MCP-based tool execution
- Travel knowledge RAG with topic inference, trip-style hints, and provenance
- Full-stack developer experience with frontend, scripts, Docker, smoke tests, and CI

## Technology Stack

- Backend
  - Java 21
  - Spring Boot 4
  - Spring WebFlux
  - Spring Validation
  - Spring Boot Actuator
  - JDBC + SQLite
  - Jackson
- AI and orchestration
  - Spring AI
  - OpenAI chat and embedding integration
  - OpenAI-compatible gateway support via `SPRING_AI_OPENAI_BASE_URL`
  - Model Context Protocol (MCP)
  - standalone Amap MCP server built with Spring AI MCP Server WebMVC
- Retrieval and memory
  - local curated + cleaned travel knowledge dataset
  - optional Milvus vector store
  - long-term memory + destination knowledge retrieval
- Frontend
  - Vue 3
  - TypeScript
  - Vite
  - Pinia
  - Vitest
  - Vue Test Utils
  - jsdom
- Delivery and operations
  - Maven Wrapper
  - npm
  - Docker and Docker Compose
  - Nginx
  - PowerShell automation scripts
  - GitHub Actions CI
  - Micrometer + OpenTelemetry tracing
  - NSSM-based Windows service deployment

## Architecture

High-level request flow:

1. A user message enters `ConversationWorkflow`.
2. Short-term history, task memory, summary, and long-term memory are assembled.
3. `AgentRouter` selects the most suitable specialist.
4. The specialist executes with shared context.
5. Planner requests go through generate, enrich, validate, repair, and revalidate stages.
6. The final answer, task memory, timeline, and structured plan are persisted.

Core modules:

- `travel-agent-types`: shared response and exception types
- `travel-agent-domain`: entities, value objects, repository interfaces, domain services
- `travel-agent-amap`: HTTP Amap gateway and configuration
- `travel-agent-infrastructure`: Spring AI agents, retrieval, vector stores, persistence adapters
- `travel-agent-app`: WebFlux API, health endpoints, timeline streaming, bootstrap runners
- `travel-agent-amap-mcp-server`: standalone MCP server for Amap-backed tools
- `web`: Vue 3 frontend

## Repository Layout

- [`travel-agent-app`](travel-agent-app)
- [`travel-agent-domain`](travel-agent-domain)
- [`travel-agent-infrastructure`](travel-agent-infrastructure)
- [`travel-agent-amap`](travel-agent-amap)
- [`travel-agent-amap-mcp-server`](travel-agent-amap-mcp-server)
- [`travel-agent-types`](travel-agent-types)
- [`web`](web)
- [`scripts`](scripts)
- [`docs`](docs)

## Current Status

This repository is already suitable for:

- local development
- demos and experiments
- self-hosted MVP deployment
- release-style smoke validation

It already includes:

- health indicators
- preflight checks
- release smoke scripts
- start / stop scripts
- Docker deployment assets
- GitHub Actions CI

## Prerequisites

- Java 21
- Node.js / npm for frontend development
- Docker Desktop if you want Milvus or containerized deployment
- Python 3 only if you want to rerun the knowledge collection / cleaning pipeline

Current automation is Windows-first because the operational scripts are PowerShell-based, but the application itself is not conceptually tied to Windows.

## Configuration

Copy [`.env.travel-agent.example`](.env.travel-agent.example) to `.env.travel-agent` and fill the values you need.

The example file defaults to `LOCAL` mode so the fastest local path works without MCP.

Important environment variables:

- OpenAI / compatible provider
  - `SPRING_AI_OPENAI_API_KEY`
  - `SPRING_AI_OPENAI_BASE_URL`
  - `SPRING_AI_OPENAI_CHAT_MODEL`
  - `SPRING_AI_OPENAI_EMBEDDING_MODEL`
  - `SPRING_PROFILES_ACTIVE=prod` for deployed environments
- Amap / tools
  - `TRAVEL_AGENT_TOOL_PROVIDER=LOCAL|MCP`
  - `TRAVEL_AGENT_AMAP_MCP_ENABLED=true|false`
  - `TRAVEL_AGENT_AMAP_API_KEY`
  - `TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND`
- Retrieval
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED`
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI`
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_COLLECTION_NAME`
  - `TRAVEL_AGENT_MILVUS_ENABLED`
  - `TRAVEL_AGENT_MILVUS_URI`
- Frontend
  - `VITE_AMAP_WEB_KEY`
  - `VITE_AMAP_SECURITY_JS_CODE`

Notes:

- `SPRING_AI_OPENAI_BASE_URL` allows the backend to work with OpenAI-compatible gateways.
- `TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND` defaults to `3.0`, which matches a conservative Amap HTTP quota.
- `.env.travel-agent` is ignored by Git and must not be committed.

## Quick Start

### 1. Preflight

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

What it checks:

- Java availability
- OpenAI key presence / placeholder shape
- MCP reachability when MCP mode is enabled
- cleaned knowledge dataset availability
- backend health when requested

The preflight script loads `.env.travel-agent` automatically when the file exists.

To validate a different env file explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1 -EnvFile .env.travel-agent.example -SkipHealthCheck
```

### 2. Fast Local Startup

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -RunPreflight -ToolProvider LOCAL
```

Stop it with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

Default local endpoints:

- backend: `http://localhost:18080`
- frontend: `http://localhost:4173`

### 3. Release Smoke

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1
```

The smoke path verifies:

- backend startup
- actuator health response
- planner API wrapper success
- structured `travelPlan`
- weather snapshot presence
- knowledge retrieval presence
- frontend build artifact presence

## Manual Run

### Backend

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "<your-openai-key>"
.\mvnw.cmd -pl travel-agent-app -am spring-boot:run
```

For deployed environments:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
```

For an OpenAI-compatible provider:

```powershell
$env:SPRING_AI_OPENAI_BASE_URL = "https://api.example.com"
$env:SPRING_AI_OPENAI_CHAT_MODEL = "gpt-5.4"
```

### Standalone Amap MCP Server

```powershell
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-amap-mcp-server -am spring-boot:run
```

### Frontend

```powershell
cd web
npm.cmd ci
npm.cmd run dev
```

## Docker Deployment

Included assets:

- [`Dockerfile.app`](Dockerfile.app)
- [`Dockerfile.mcp`](Dockerfile.mcp)
- [`docker-compose.app.yml`](docker-compose.app.yml)
- [`docker-compose.milvus.yml`](docker-compose.milvus.yml)
- [`web/Dockerfile`](web/Dockerfile)
- [`web/nginx.conf`](web/nginx.conf)

Run the app stack:

```powershell
docker compose -f docker-compose.app.yml up --build -d
```

Default containerized ports:

- backend: `8080`
- MCP server: `8090` when the `mcp` profile is enabled
- frontend: `8088`

Run the app stack with MCP:

```powershell
docker compose -f docker-compose.app.yml --profile mcp up --build -d
```

Start Milvus separately when vector retrieval is needed:

```powershell
docker compose -f docker-compose.milvus.yml up -d
```

The Docker app stack defaults the backend container to `SPRING_PROFILES_ACTIVE=prod`, so `/actuator/health` remains available while detailed component output is reduced for unauthenticated callers.

## Windows Service Deployment

This repository includes Windows service helpers built around NSSM:

- [`scripts/install-windows-services.ps1`](scripts/install-windows-services.ps1)
- [`scripts/uninstall-windows-services.ps1`](scripts/uninstall-windows-services.ps1)
- [`web/server.mjs`](web/server.mjs)

Install example:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-windows-services.ps1 -NssmPath C:\tools\nssm\nssm.exe -Build
```

Remove example:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-windows-services.ps1 -NssmPath C:\tools\nssm\nssm.exe
```

## Testing and CI

GitHub Actions workflow:

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- runs on pushes to `main`, pull requests, and manual dispatch
- executes backend Maven tests and frontend test/build validation

Backend:

```powershell
.\mvnw.cmd -B test
```

Planner-focused backend regression set:

```powershell
.\mvnw.cmd -pl travel-agent-infrastructure,travel-agent-app -am "-Dtest=TravelKnowledgeRetrievalSupportTest,TravelKnowledgeVectorStoreRepositoryTest,LocalTravelKnowledgeRepositoryTest,RoutingTravelKnowledgeRepositoryTest,TravelKnowledgeSeedServiceTest,TravelPlannerAgentTest,ConversationWorkflowPlannerDemoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Frontend:

```powershell
cd web
npm.cmd ci
npm.cmd run test
npm.cmd run build
```

## Health and Observability

Available actuator endpoint:

- `GET /actuator/health`

Current health contributors include:

- `openAi`
- `toolProvider`
- `knowledgeDataset`
- `knowledgeVector`

Tracing configuration:

- `OTEL_EXPORTER_OTLP_ENDPOINT`
- `TRAVEL_AGENT_TRACING_SAMPLING_PROBABILITY`

## Travel Knowledge RAG

Knowledge files:

- [`travel-agent-infrastructure/src/main/resources/travel-knowledge.json`](travel-agent-infrastructure/src/main/resources/travel-knowledge.json)
- [`travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json`](travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json)
- [`travel-agent-infrastructure/src/main/resources/travel-knowledge.cleaned.json`](travel-agent-infrastructure/src/main/resources/travel-knowledge.cleaned.json)

Current retrieval behavior:

- Milvus-first, local fallback second
- city alias resolution
- topic inference
- trip-style inference
- topic-balanced selection
- planner-oriented subtype ranking
- retrieval observability exposed to planner and frontend

More detail:

- [`docs/knowledge-rag.md`](docs/knowledge-rag.md)

Collector / cleaner scripts:

- [`scripts/collect_travel_attractions.py`](scripts/collect_travel_attractions.py)
- [`scripts/clean_travel_knowledge.py`](scripts/clean_travel_knowledge.py)
- [`scripts/seed-travel-knowledge.ps1`](scripts/seed-travel-knowledge.ps1)

## Security Notes

Before publishing or pushing:

- never commit `.env.travel-agent`
- never commit real OpenAI, Amap, or other provider secrets
- never commit local runtime logs under `data/runtime`
- remove generated smoke logs before publishing
- rotate any credential that has already been exposed in chat, screenshots, or terminal history

The current Git ignore rules already cover local env files, runtime output, and common key / certificate file types.

## Known Limitations

- Some transit knowledge chunks are still more listing-like than ideal planner guidance.
- Release smoke is easiest to run in `LOCAL` tool mode.
- The planner currently handles some requests more robustly than others when map enrichment produces heavy cross-district transit.
- Production packaging still lacks polished templates for TLS / domain reverse proxy and external secret managers.

## Contributing

Issues and pull requests are welcome.

If you want to contribute, the most useful areas right now are:

- planner robustness under real map data
- retrieval quality and ranking
- deployment templates and production hardening
- frontend UX polish
- dataset cleaning and travel knowledge coverage

## Docs

- [`LICENSE`](LICENSE)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)
- [`docs/knowledge-rag.md`](docs/knowledge-rag.md)
- [`docs/release-checklist.md`](docs/release-checklist.md)

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE).

## Suggested Release Flow

1. Fill `.env.travel-agent`
2. Run preflight
3. Run backend and frontend tests
4. Confirm GitHub Actions CI is green
5. Run release smoke
6. Start the target deployment mode
7. Verify `/actuator/health`
