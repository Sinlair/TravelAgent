# System Architecture

Architecture assets (layered runtime view):

- **Source of truth (editable):** `docs/assets/travelagent-system-architecture-v2.drawio`
- **Rendered preview (SVG export):** `docs/assets/travelagent-system-architecture-v2.svg`
- **Draw.io MCP session URL snapshot:** `docs/assets/travelagent-system-architecture-v2.mcp-url.txt`

![TravelAgent System Architecture v3](./assets/travelagent-system-architecture-v2.svg)

## Reading Guide

1. Traveler interactions enter from the `web` client (`chat`, `image`, `feedback`).
2. `travel-agent-app` handles REST + SSE and orchestrates each turn in `ConversationWorkflow`.
3. `travel-agent-domain` defines contracts (`AgentRouter`, `SpecialistAgent`, repositories, gateways).
4. `travel-agent-infrastructure` supplies adapters for LLM, persistence/retrieval, and tool calls.
5. `travel-agent-amap` is the dedicated Amap integration module; optional MCP sidecar is shown as dashed.
6. External providers are consumed through adapters:
   - OpenAI-compatible model provider
   - SQLite (default persistence)
   - Milvus (optional vector retrieval)
   - Amap OpenAPI

## Runtime Switches

- `travel.agent.tool-provider`: `LOCAL` (Amap HTTP) or `MCP` (Amap MCP tools)
- `travel.agent.memory-provider`: `AUTO`, `SQLITE`, or `MILVUS`

Use draw.io Desktop or [app.diagrams.net](https://app.diagrams.net/) to edit the `.drawio` source.
