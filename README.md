# Travel Agent

A multi-agent travel planning product built with Spring AI 2.0, Spring Boot 4, Vue 3, SQLite, an optional standalone Amap MCP server, and a travel knowledge RAG pipeline for major Chinese cities.

It can:

- route requests between `WEATHER`, `GEO`, `TRAVEL_PLANNER`, and `GENERAL`
- generate structured itineraries with budget and pace checks
- enrich planner output with weather, destination knowledge, and repair explanations
- persist conversations, task memory, timelines, and travel plans
- run with local fallbacks for development, or with MCP and Milvus for richer production behavior

## Product Status

Current repository status is suitable for:

- local development
- demo / portfolio delivery
- internal MVP deployment
- release-style smoke validation

The repository now includes:

- health indicators
- preflight checks
- release smoke script
- start / stop scripts
- Docker deployment assets

## Core Features

- Multi-agent orchestration:
  - `WEATHER`
  - `GEO`
  - `TRAVEL_PLANNER`
  - `GENERAL`
- Planner execution loop:
  - `generate -> enrich -> validate -> repair -> revalidate`
- Structured planner context:
  - weather snapshot
  - retrieved knowledge snippets
  - retrieval source
  - inferred topics
  - inferred trip styles
  - closest-feasible adjustment suggestions
- Knowledge RAG:
  - local fallback repository
  - optional Milvus vector retrieval
  - topic-balanced selection
  - city alias resolution
  - trip-style-aware ranking
- Frontend panels:
  - conversation
  - structured plan
  - timeline
  - retrieved knowledge provenance
- Release helpers:
  - preflight script
  - release smoke script
  - production-style start / stop scripts

## Architecture

High-level request flow:

1. User request enters `ConversationWorkflow`.
2. Short-term memory, task memory, and long-term memory are assembled.
3. `AgentRouter` selects the best specialist.
4. The selected specialist executes with shared context.
5. Planner requests can call tools, retrieve knowledge, validate, and repair.
6. Final answer, task memory, timeline, and structured plan are persisted.

Key modules:

- `travel-agent-types`: shared response and exception types
- `travel-agent-domain`: entities, value objects, repositories, domain services
- `travel-agent-amap`: shared Amap gateway and configuration
- `travel-agent-infrastructure`: Spring AI agents, retrieval, vector stores, persistence adapters
- `travel-agent-app`: WebFlux API, timeline streaming, health endpoints, bootstrap runners
- `travel-agent-amap-mcp-server`: standalone MCP server for Amap-backed tools
- `web`: Vue 3 frontend

## Repository Layout

- [travel-agent-app](/E:/Internship/program/TravelAgent/travel-agent-app)
- [travel-agent-domain](/E:/Internship/program/TravelAgent/travel-agent-domain)
- [travel-agent-infrastructure](/E:/Internship/program/TravelAgent/travel-agent-infrastructure)
- [travel-agent-amap](/E:/Internship/program/TravelAgent/travel-agent-amap)
- [travel-agent-amap-mcp-server](/E:/Internship/program/TravelAgent/travel-agent-amap-mcp-server)
- [travel-agent-types](/E:/Internship/program/TravelAgent/travel-agent-types)
- [web](/E:/Internship/program/TravelAgent/web)
- [scripts](/E:/Internship/program/TravelAgent/scripts)
- [docs](/E:/Internship/program/TravelAgent/docs)

## Requirements

- Windows PowerShell environment
- Docker Desktop if you want Milvus or containerized deployment
- Node.js / npm for frontend local development
- Java 21

The repository already includes a bundled JDK 21 under `.tooling`, and `.\mvnw.cmd` prefers it automatically.

## Environment

Copy [.env.travel-agent.example](/E:/Internship/program/TravelAgent/.env.travel-agent.example) to `.env.travel-agent` and fill the values that match your mode.

Most important variables:

- OpenAI:
  - `SPRING_AI_OPENAI_API_KEY`
  - `SPRING_AI_OPENAI_CHAT_MODEL`
  - `SPRING_AI_OPENAI_EMBEDDING_MODEL`
- Tool mode:
  - `TRAVEL_AGENT_TOOL_PROVIDER=LOCAL|MCP`
  - `TRAVEL_AGENT_AMAP_MCP_ENABLED=true|false`
  - `TRAVEL_AGENT_AMAP_API_KEY`
- Long-term memory vector:
  - `TRAVEL_AGENT_MILVUS_ENABLED`
  - `TRAVEL_AGENT_MILVUS_URI`
- Knowledge vector:
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED`
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI`
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_COLLECTION_NAME`
- Frontend Amap:
  - `VITE_AMAP_WEB_KEY`
  - `VITE_AMAP_SECURITY_JS_CODE`

## Quick Start

### 1. Preflight

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

This checks:

- Java availability
- OpenAI key shape
- MCP reachability when configured
- local knowledge dataset presence

### 2. Fast Local Startup

For a local product-style run in `LOCAL` mode:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -ToolProvider LOCAL
```

Stop it with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

Default runtime endpoints:

- backend: `http://localhost:18080`
- frontend: `http://localhost:4173`

### 3. Release Smoke

Run a release-style smoke path:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1
```

It verifies:

- backend startup
- actuator health endpoint response
- planner API returns structured `travelPlan`
- weather snapshot exists
- knowledge retrieval exists
- frontend build artifact exists

## Manual Run

### Standalone Amap MCP server

```powershell
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-amap-mcp-server -am spring-boot:run
```

### Main backend

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "<your-openai-key>"
.\mvnw.cmd -pl travel-agent-app -am spring-boot:run
```

### Backend in LOCAL tool mode

```powershell
$env:TRAVEL_AGENT_TOOL_PROVIDER = "LOCAL"
$env:TRAVEL_AGENT_AMAP_MCP_ENABLED = "false"
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-app -am spring-boot:run
```

### Frontend

```powershell
cd web
npm.cmd install
npm.cmd run dev
```

## Docker Deployment

The repository now includes:

- [Dockerfile.app](/E:/Internship/program/TravelAgent/Dockerfile.app)
- [Dockerfile.mcp](/E:/Internship/program/TravelAgent/Dockerfile.mcp)
- [docker-compose.app.yml](/E:/Internship/program/TravelAgent/docker-compose.app.yml)
- [web/Dockerfile](/E:/Internship/program/TravelAgent/web/Dockerfile)
- [web/nginx.conf](/E:/Internship/program/TravelAgent/web/nginx.conf)

### App stack only

```powershell
docker compose -f docker-compose.app.yml up --build -d
```

Default containerized ports:

- backend: `8080`
- MCP server: `8090` when the `mcp` profile is used
- frontend: `8088`

### App stack with MCP profile

```powershell
docker compose -f docker-compose.app.yml --profile mcp up --build -d
```

This is the recommended deployment target for:

- Linux server
- VPS
- self-hosted lab environment
- internal demo server

### Milvus

Knowledge vector retrieval and memory vector retrieval can use Milvus. Start it with:

```powershell
docker compose -f docker-compose.milvus.yml up -d
```

If you want Dockerized app services to use Milvus, enable the appropriate env vars in `.env.travel-agent`.

## Windows Service Deployment

For a Windows host that should keep the product running as services, the repository now supports:

- backend Windows service
- frontend Windows service
- optional MCP Windows service

Service installer scripts:

- [install-windows-services.ps1](/E:/Internship/program/TravelAgent/scripts/install-windows-services.ps1)
- [uninstall-windows-services.ps1](/E:/Internship/program/TravelAgent/scripts/uninstall-windows-services.ps1)
- [web/server.mjs](/E:/Internship/program/TravelAgent/web/server.mjs)

Current Windows service mode uses:

- `nssm.exe` as the Windows service wrapper
- backend jar as the backend service process
- `node web/server.mjs` as the frontend static/proxy service

Install example:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-windows-services.ps1 -NssmPath C:\tools\nssm\nssm.exe -Build
```

Remove example:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-windows-services.ps1 -NssmPath C:\tools\nssm\nssm.exe
```

## Health and Observability

Actuator endpoint:

- `GET /actuator/health`

Current health contributors:

- `openAi`
- `toolProvider`
- `knowledgeDataset`
- `knowledgeVector`

Tracing:

- `OTEL_EXPORTER_OTLP_ENDPOINT`
- `TRAVEL_AGENT_TRACING_SAMPLING_PROBABILITY`

## Knowledge Data Pipeline

Knowledge source files:

- [travel-knowledge.json](/E:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/resources/travel-knowledge.json)
- [travel-knowledge.collected.json](/E:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json)
- [travel-knowledge.cleaned.json](/E:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/resources/travel-knowledge.cleaned.json)

Current cleaned dataset:

- 75 cities
- 1219 cleaned records
- topics:
  - `scenic`
  - `activity`
  - `food`
  - `nightlife`
  - `transit`
  - `hotel`

Cleaner capabilities now include:

- parser artifact removal
- mojibake repair
- planner-oriented second-stage summarization
- `schemaSubtype`
- `qualityScore`
- `cityAliases`
- `tripStyleTags`

Collector and cleaner:

- [collect_travel_attractions.py](/E:/Internship/program/TravelAgent/scripts/collect_travel_attractions.py)
- [clean_travel_knowledge.py](/E:/Internship/program/TravelAgent/scripts/clean_travel_knowledge.py)

Run:

```powershell
python .\scripts\collect_travel_attractions.py
python .\scripts\clean_travel_knowledge.py
```

## Knowledge Seeding

Seed script:

- [seed-travel-knowledge.ps1](/E:/Internship/program/TravelAgent/scripts/seed-travel-knowledge.ps1)

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\seed-travel-knowledge.ps1
```

Behavior:

- builds the backend jar
- seeds the cleaned dataset into the knowledge vector collection
- rebuilds the knowledge collection before insert
- runs retrieval smoke checks for sample cities by default

## Retrieval Strategy

Travel knowledge retrieval is intentionally separate from long-term memory retrieval.

Current retrieval behavior:

- Milvus-first, local fallback second
- hard destination bias
- city alias resolution
- topic inference
- trip-style inference
- topic-balanced final allocation
- planner-oriented subtype ranking
- retrieval observability exposed to planner and frontend

Structured metadata currently used by retrieval:

- `city`
- `cityAliases`
- `topic`
- `schemaSubtype`
- `qualityScore`
- `tripStyleTags`
- `source`

Additional detail:

- [docs/knowledge-rag.md](/E:/Internship/program/TravelAgent/docs/knowledge-rag.md)

## Testing

### Backend

Run everything:

```powershell
.\mvnw.cmd -q "-Dmaven.repo.local=.m2\repository" test
```

Planner-focused regression set:

```powershell
.\mvnw.cmd -pl travel-agent-infrastructure,travel-agent-app -am "-Dtest=TravelKnowledgeRetrievalSupportTest,TravelKnowledgeVectorStoreRepositoryTest,LocalTravelKnowledgeRepositoryTest,RoutingTravelKnowledgeRepositoryTest,TravelKnowledgeSeedServiceTest,TravelPlannerAgentTest,ConversationWorkflowPlannerDemoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### Frontend

```powershell
cd web
npm.cmd run test
npm.cmd run build
```

## Release Checklist

Release checklist document:

- [docs/release-checklist.md](/E:/Internship/program/TravelAgent/docs/release-checklist.md)

Recommended release order:

1. Fill `.env.travel-agent`
2. Run preflight
3. Run backend + frontend build
4. Run release smoke
5. Start services with the target mode
6. Verify `/actuator/health`

Target deployment paths now supported in-repo:

- Linux server + Docker Compose
- Windows host + NSSM-backed services

## Known Limitations

- Some transit knowledge is still more line-listing shaped than ideal planner guidance.
- Release smoke currently validates `LOCAL` tool mode more easily than full `MCP` production mode.
- Full production packaging still lacks some enterprise-grade targets such as:
  - complete TLS / domain reverse proxy templates
  - secret manager integration
  - containerized full-stack production secrets flow

## Release Notes For GitHub Publication

Before publishing this repository publicly, check:

- secrets are not committed
- `.env.travel-agent` is not committed
- local runtime logs under `data/runtime` are not committed
- generated release smoke logs are not committed
- any real Amap or OpenAI keys are removed from shell history or local notes

Current ignore rules already cover:

- `.env.travel-agent`
- `data/runtime/`
- release smoke log files
