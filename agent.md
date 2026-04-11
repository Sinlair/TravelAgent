# TravelAgent - AI Agent Guidelines

## Project Overview

TravelAgent is a full-stack travel planning workspace built with Spring Boot 4, Spring AI 2.0, and Vue 3. It transforms natural language travel requests into structured travel plans with specialist-agent routing, Amap data enrichment, plan validation and repair, knowledge retrieval, timeline streaming, and recommendation feedback.

## Core Principles

### Development Principles
- **Understand module boundaries**: Clearly understand module responsibilities before modifying code
- **Fix root causes**: Avoid simple patch stacking; solve fundamental problems
- **Maintain consistency**: Documentation, configuration samples, and runtime behavior must align
- **Security first**: Never commit secrets, local environment files, logs, or build artifacts
- **Small increments**: Keep changes reviewable; prefer small, coherent modifications

### Architecture Principles
- Follow DDD layered design to keep domain logic pure
- Use ports-and-adapters pattern to isolate external dependencies
- Specialist-agent routing instead of single generic processing
- Explicit planning pipeline rather than black-box prompt chains

## Repository Structure

### Backend Modules

#### `travel-agent-domain` - Domain Layer
- **Responsibility**: Domain contract definitions
- **Contains**: Entities, value objects, repository interfaces, gateways, service contracts
- **Rules**: Domain rules must reside here; never leak into controllers or infrastructure layers

#### `travel-agent-app` - Application Layer
- **Responsibility**: Application orchestration and coordination
- **Contains**: API controllers, DTOs, bootstrap configuration, health checks, workflow coordination
- **Core**: Main workflows such as `ConversationWorkflow`

#### `travel-agent-infrastructure` - Infrastructure Layer
- **Responsibility**: Concrete implementations
- **Contains**: LLM agents, retrieval, persistence, validators, repairers, vector integrations, adapters

#### `travel-agent-amap` - Amap Integration
- **Responsibility**: Amap HTTP integration
- **Rules**: Provided through domain gateway interfaces; transparent to upper layers

#### `travel-agent-amap-mcp-server` - MCP Server
- **Responsibility**: Standalone MCP server
- **Features**: Exposes Amap-backed tool services

#### `travel-agent-types` - Shared Types
- **Responsibility**: Common response, error, and transport types
- **Rules**: Type definitions shared across modules

### Frontend and Support Directories

#### `web` - Frontend Workspace
- **Tech Stack**: Vue 3 + TypeScript + Vite + Pinia + Vitest
- **Features**: User interface, client-side state management, interaction logic

#### `scripts` - Script Utilities
- **Contents**: Python knowledge processing helpers and data target files

#### `docs` - Documentation
- **Contents**: Architecture notes, operations guides, release checklists, etc.

#### `data` - Data Directory
- **Contents**: Local SQLite data, exported files, vector store state

#### `logs` - Log Directory
- **Contents**: Runtime logs and archived logs

## Multi-Agent Workflow

### Architecture Pattern
This project uses **orchestrated multi-agent execution**, not free-form agent-to-agent conversations.

### Core Components

| Component | Responsibility |
|-----------|----------------|
| `ConversationWorkflow` | Main orchestrator: intent recognition, context assembly, routing, specialist execution, persistence, and timeline publishing |
| `AgentRouter` | Agent router: selects specialist agent for current turn; requests clarification when needed |
| `SpecialistAgent` | Unified contract for specialist agents |
| `AgentExecutionResult` | Standardized result object returned by specialist agents |
| `TimelinePublisher` & `ConversationStreamHub` | Publish backend execution events to frontend SSE timeline |

### Specialist Agent Roles

| Agent Type | Responsibility |
|------------|----------------|
| `WEATHER` | City weather and weather-aware advice |
| `GEO` | Place resolution, geocoding, reverse geocoding, and coordinate processing |
| `TRAVEL_PLANNER` | Itinerary generation, Amap enrichment, validation, repair, and retrieval-supported planning |
| `GENERAL` | Travel-related questions outside structured planner scope |

### Steps to Add a New Specialist Agent
1. Implement the `SpecialistAgent` interface
2. Update router selection rules
3. Maintain consistency with shared execution contract
4. Update configuration samples and documentation when introducing new configurations

## Editing Boundaries

### Choose Modification Location by Layer

| Issue Type | Starting Module |
|------------|-----------------|
| API or workflow orchestration issues | `travel-agent-app` |
| Domain rules, interfaces, or value objects | `travel-agent-domain` |
| LLM, retrieval, persistence, Amap integration, validation, or repair logic | `travel-agent-infrastructure` |
| External map provider integration | `travel-agent-amap` |
| MCP exposure or tool-serving behavior | `travel-agent-amap-mcp-server` |
| UI, client state, or interaction issues | `web` |

### Prohibited Modifications to Generated or Runtime Outputs

Avoid editing the following directories unless explicitly required by the task:

- `**/target/` - Maven build output
- `web/dist/` - Frontend build output
- `web/node_modules/` - Frontend dependencies
- `logs/` - Runtime logs
- `data/exports/` - Exported data
- `data/milvus/` - Milvus vector store
- `data/travel-agent.db` - SQLite database
- `.env.travel-agent` - Backend environment configuration
- `web/.env.local` - Frontend environment configuration

### Output File Rules
- Local workflow outputs should be placed in `logs/` or `data/exports/`
- Do not create new dump files in the root directory
- Runtime-generated root-level files should be treated as repository hygiene issues

## Build, Run, and Test Commands

### Environment Initialization
```bash
# Create environment configuration from example
cp .env.travel-agent.example .env.travel-agent
```

### Backend Commands

```bash
# Start backend service
./mvnw -pl travel-agent-app -am spring-boot:run

# Run all backend tests
./mvnw -B test

# Run integration smoke test
./mvnw -pl travel-agent-app -am -Dtest=TravelAgentSmokeIntegrationTest test

# Package backend application (skip tests)
./mvnw -pl travel-agent-app -am -DskipTests package

# Start with specific API Key
SPRING_AI_OPENAI_API_KEY="<your-openai-key>" ./mvnw -pl travel-agent-app -am spring-boot:run
```

### Frontend Commands

```bash
cd web

# Install dependencies
npm ci

# Start development server
npm run dev

# Run tests
npm run test

# Build production version
npm run build
```

### MCP Server

```bash
# Start optional MCP server
./mvnw -pl travel-agent-amap-mcp-server -am spring-boot:run
```

### Data Analysis

```bash
# Offline feedback analysis
python scripts/analyze_feedback_loop.py
```

### Default Endpoints

| Service | Address |
|---------|---------|
| Backend | http://localhost:8080 |
| Frontend | http://localhost:5173 |

## Change Expectations

### Documentation Alignment
- Behavioral changes must align with `README.md`, `README.zh-CN.md`, and `docs/operations.md`
- Configuration changes require updating `.env.travel-agent.example`
- Startup or release flow changes require updating relevant documentation

### Verification Requirements
- UI or API-visible behavior changes must include minimal verification evidence
- Maintain existing module boundaries; do not move domain decisions to transport or infrastructure layers without clear justification
- Prefer incremental changes that match existing naming and structure

### Testing Strategy
Run the narrowest relevant validation first, then expand when changes affect shared flows:

| Change Type | Testing Requirement |
|-------------|---------------------|
| Backend logic changes | Related Maven tests; run full `./mvnw -B test` for cross-cutting changes |
| Frontend changes | `npm run test` and `npm run build` |
| Startup or configuration changes | Backend startup, related frontend startup, update affected documentation |

**Important**: Do not claim tests passed unless they were actually run.

## Configuration and Security

### Secret Management
- **Never commit**: `.env.travel-agent`, `web/.env.local`, logs, SQLite files, or export data
- **Keep secrets away from**: source code, documentation, examples, and test fixtures
- **Use example files**: `.env.travel-agent.example` and `web/.env.example` for documented configurations

### Critical Environment Variables

| Variable | Purpose |
|----------|---------|
| `SPRING_AI_OPENAI_API_KEY` | OpenAI API key |
| `SPRING_AI_OPENAI_BASE_URL` | OpenAI API base URL |
| `SPRING_AI_OPENAI_CHAT_MODEL` | Chat model name |
| `TRAVEL_AGENT_TOOL_PROVIDER` | Tool provider |
| `TRAVEL_AGENT_AMAP_API_KEY` | Amap API key |
| `VITE_AMAP_WEB_KEY` | Frontend Amap web key |
| `VITE_AMAP_SECURITY_JS_CODE` | Amap security JS code |

## Commit and Review Standards

### Commit Message Format
Recent history uses short, descriptive messages like `docs: expand architecture overview in README`.

**Preferred Characteristics**:
- Scoped
- Descriptive
- Written in imperative mood

### Pre-Commit Checklist
- [ ] Relevant tests have been run
- [ ] Documentation and configuration samples match implementation
- [ ] No generated or runtime artifacts added to version control
- [ ] Multi-agent routing and shared execution contract remain sound

## Planning Pipeline

The planner is designed as an explicit pipeline for easy inspection and evolution:

1. **Build Draft**: Construct itinerary draft from extracted travel facts
2. **Amap Enrichment**: Enrich POIs, districts, hotels, weather, and routes with Amap
3. **Validate Constraints**: Validate cost, opening hours, pacing, and duplicates
4. **Repair Plan**: Repair plan if constraints fail
5. **Retrieve Knowledge**: Retrieve destination knowledge to support planner
6. **Render Answer**: Render structured answer and persist `TravelPlan`

## Quality Assessment

### CI/CD Coverage
- CI covers backend tests, frontend tests, and production builds
- GitHub Actions workflow: `.github/workflows/ci.yml`

### Test Suite
- **Backend**: Includes in-process smoke integration tests
  - `TravelAgentSmokeIntegrationTest` boots the app, checks `/actuator/health`, and verifies `/api/conversations/chat` returns `agentType=TRAVEL_PLANNER` with structured `travelPlan`
  
### Offline Analysis
- Feedback analysis: `python scripts/analyze_feedback_loop.py`
- Reads `data/travel-agent.db` by default
- Outputs JSON and Markdown reports to `data/exports/`

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 4, Spring WebFlux, Actuator |
| AI Orchestration | Spring AI, OpenAI-compatible chat integration, MCP |
| Storage | SQLite, optional Milvus |
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vitest |
| Mapping | Amap (Gaode) |
| Operations | Docker, Docker Compose, Nginx, GitHub Actions |

## Known Limitations

- Current strongest grounding path remains China-focused due to Amap dependency
- Some retrieval snippets still need planner-friendly structure improvements
- Hotel and route fallback behavior is pragmatic but cannot replace live booking-grade inventory
- End-to-end quality depends on valid model-provider and map-provider configurations

## Future Improvements

- Stronger planner-facing RAG schemas for hotels, transit, and trip styles
- Better offline evaluation for usefulness and hard constraints
- More explicit handoff policies between planner, weather, and geo specialists
- Improved multimodal extraction quality for booking screenshots
- Stronger production deployment templates for secrets, TLS, and observability

## Reference Documentation

- [README.md](./README.md) - English project guide
- [README.zh-CN.md](./README.zh-CN.md) - Chinese project guide
- [CONTRIBUTING.md](./CONTRIBUTING.md) - Contributing guidelines
- [docs/system-architecture.md](./docs/system-architecture.md) - System architecture
- [docs/knowledge-rag.md](./docs/knowledge-rag.md) - Knowledge retrieval
- [docs/multimodal-roadmap.md](./docs/multimodal-roadmap.md) - Multimodal roadmap
- [docs/operations.md](./docs/operations.md) - Operations guide
- [docs/release-checklist.md](./docs/release-checklist.md) - Release checklist
- [SECURITY.md](./SECURITY.md) - Security policy

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.
