# System Architecture

Architecture assets:

- **Repository architecture (editable):** `docs/assets/travelagent-repository-architecture.drawio`
- **Repository architecture (SVG):** `docs/assets/travelagent-repository-architecture.svg`
- **Runtime workflow (editable):** `docs/assets/travelagent-runtime-workflow.drawio`
- **Runtime workflow (SVG):** `docs/assets/travelagent-runtime-workflow.svg`

The older `travelagent-system-architecture-v2.*` assets are superseded by the stable names above.

## Repository Architecture

![TravelAgent Repository Architecture](./assets/travelagent-repository-architecture.svg)

Reading guide:

1. Delivery stays explicit: `web` owns chat, image intake, plan/map panels, and feedback actions.
2. `travel-agent-app` remains the orchestration center for HTTP, SSE, workflow execution, health checks, and knowledge seeding.
3. `travel-agent-domain` keeps the ports and repository contracts separate from concrete adapters.
4. `travel-agent-infrastructure` implements routing, specialists, summarization, image interpretation, validation, repair, and switchable tool/memory adapters.
5. `travel-agent-amap` and `travel-agent-amap-mcp-server` are shown independently so the `LOCAL` and `MCP` tool paths are visible.
6. External providers are split by responsibility instead of being collapsed into one generic box:
   - OpenAI-compatible chat and embedding provider
   - SQLite for conversation state and outputs
   - Local travel knowledge JSON
   - Optional Milvus vector storage
   - Amap OpenAPI

## Runtime Workflow

![TravelAgent Runtime Workflow](./assets/travelagent-runtime-workflow.svg)

Reading guide:

1. The UI sends text, image uploads, or image confirm/dismiss actions to `/api/conversations/chat`.
2. `ConversationWorkflow` stages pending image context, saves user messages, rebuilds memory context, and delegates routing.
3. `AgentRouter` selects `WEATHER`, `GEO`, `GENERAL`, or `TRAVEL_PLANNER`.
4. The planner path is explicit: build draft, enrich with Amap, validate, repair, then attach knowledge and weather context before finalization.
5. Conversation state, long-term memory, knowledge retrieval, feedback, and timeline events are persisted through repository adapters.
6. Timeline events stream back to the frontend through `ReactiveTimelinePublisher` and `ConversationStreamHub`, while feedback summary/export reads from the same stored conversation records.

## Response Contract

The chat and conversation detail APIs now expose a stable result contract for all specialists:

- `agentType`, `answer`, `taskMemory`, `travelPlan`, and `timeline` remain the core payload blocks.
- `feedbackTarget` provides a stable answer target id, default scope, available scopes, and a plan version when a structured itinerary exists.
- `issues` carries top-level user-facing flags such as clarification needed, pending image confirmation, repaired plan, or unresolved plan risk.
- `missingInformation` exposes structured missing slots such as destination, trip length, budget, or preferences.
- `constraintSummary` distinguishes `PASS`, `REPAIRED`, `RISK`, and `NONE`, plus structured constraint issues.

See [`docs/herness-contract.md`](./herness-contract.md) for the field-level guide and verification commands.

## Timeline Semantics

Timeline events now carry both a stage and a normalized status:

- `STARTED`
- `COMPLETED`
- `FAILED`
- `REPAIRED`

Each event also includes `startedAt` and `endedAt`. These values are persisted with the timeline payload so SSE and stored history stay aligned without requiring a separate database migration.

## Runtime Switches

- `travel.agent.tool-provider`: `LOCAL` routes through `AmapTravelTools` and `AmapHttpGateway`; `MCP` routes through `AmapMcpGateway` and the standalone MCP server.
- `travel.agent.memory-provider`: `AUTO`, `SQLITE`, or `MILVUS` controls the active long-term memory repository.
- Travel knowledge retrieval prefers the vector store when enabled, then falls back to the local curated JSON dataset.

Use draw.io Desktop or [app.diagrams.net](https://app.diagrams.net/) to edit the `.drawio` source files.
