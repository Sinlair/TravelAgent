# Release Checklist

## Environment

- Copy [.env.travel-agent.example](/E:/Internship/program/TravelAgent/.env.travel-agent.example) to `.env.travel-agent`.
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

## Preflight

- Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

- The script loads `.env.travel-agent` automatically when the file exists.
- Confirm:
  - `Java` is `PASS`
  - `KnowledgeData` is `PASS`
  - `MCP` is `PASS` when using MCP mode
  - `OpenAI` is configured for full LLM behavior

## Build

- Backend:

```powershell
.\mvnw.cmd -pl travel-agent-app -am -DskipTests package
```

- Frontend:

```powershell
cd web
npm.cmd run build
```

## Smoke

- Run release smoke:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1
```

- Confirm the smoke output includes:
  - backend startup
  - `TRAVEL_PLANNER`
  - structured `travelPlan`
  - weather snapshot
  - knowledge hints

## Runtime

- Start services:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend
```

- Stop services:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

### Windows service mode

- Install via NSSM:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-windows-services.ps1 -NssmPath C:\tools\nssm\nssm.exe -Build
```

- Remove:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\uninstall-windows-services.ps1 -NssmPath C:\tools\nssm\nssm.exe
```

- Health endpoint:

```text
GET /actuator/health
```

- In `prod`, actuator health details are reduced unless the caller is authorized.

## Release Gate

- Do not release when any of these are true:
  - preflight has `FAIL`
  - release smoke fails
  - backend cannot return a structured `travelPlan`
  - frontend build output is missing
  - knowledge dataset is missing or empty
