# Release Checklist

## Environment

- Create `.env.travel-agent` from [`.env.travel-agent.example`](../.env.travel-agent.example).
- Set:
  - `SPRING_PROFILES_ACTIVE=prod`
  - `SPRING_AI_OPENAI_API_KEY`
  - `SPRING_AI_OPENAI_BASE_URL` when using an OpenAI-compatible gateway
  - `TRAVEL_AGENT_AMAP_API_KEY`
  - `TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND` to stay within your Amap quota
  - `VITE_AMAP_WEB_KEY`
  - `VITE_AMAP_SECURITY_JS_CODE`
- Decide whether the deployment uses:
  - `TRAVEL_AGENT_TOOL_PROVIDER=LOCAL`
  - or `TRAVEL_AGENT_TOOL_PROVIDER=MCP`
- If using vector retrieval, confirm:
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED=true`
  - Milvus URI and collection settings are correct

## Build and Test

- Backend tests:

```bash
./mvnw -B test
```

This suite now includes an in-process smoke integration that boots the app and verifies:

- `GET /actuator/health`
- `POST /api/conversations/chat` can return `agentType=TRAVEL_PLANNER`
- the response includes a structured `travelPlan`

- Backend package:

```bash
./mvnw -pl travel-agent-app -am -DskipTests package
```

- Frontend tests and build:

```bash
cd web
npm ci
npm run test
npm run build
```

## Manual Smoke

Use this section for environment-level verification after build/test pass. The backend test suite already covers a local HTTP smoke flow in-process.

Start the backend:

```bash
java -jar travel-agent-app/target/travel-agent-app.jar --server.port=18080
```

Optional MCP sidecar:

```bash
./mvnw -pl travel-agent-amap-mcp-server -am spring-boot:run
```

Confirm:

- `GET /actuator/health` returns healthy status
- `POST /api/conversations/chat` returns wrapper code `0000`
- the response contains `agentType=TRAVEL_PLANNER`
- the response includes a structured `travelPlan`
- the plan includes weather and knowledge data when the required providers are configured

## Release Gate

- Do not release when any of these are true:
  - backend tests fail
  - frontend tests or build fail
  - backend cannot return a structured `travelPlan`
  - frontend build output is missing
  - knowledge dataset is missing or empty
