# Contributing

Thanks for considering a contribution.

## Before You Start

- Open an issue first for larger changes, refactors, or new features.
- Keep changes scoped. Small, reviewable pull requests are preferred.
- Do not commit secrets, local env files, runtime logs, or generated local data.

## Local Setup

1. Copy `.env.travel-agent.example` to `.env.travel-agent`.
2. Fill only the variables you actually need.
3. Run the preflight check:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

4. Start the app locally if needed:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -RunPreflight -ToolProvider LOCAL
```

## Development Expectations

- Preserve the existing module boundaries.
- Prefer fixing root causes over adding one-off patches.
- Keep public behavior and scripts consistent with the README.
- If you add configuration, update `.env.travel-agent.example` and relevant docs.
- If you add tests or deployment behavior, update the README or release checklist when needed.

## Testing

Run the relevant validation before opening a pull request.

Backend:

```powershell
.\mvnw.cmd -B test
```

Frontend:

```powershell
cd web
npm.cmd ci
npm.cmd run test
npm.cmd run build
```

For release-oriented changes, also run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1
```

## Pull Requests

Please include:

- a clear summary of what changed
- why the change was needed
- what you tested
- screenshots or API examples when UI or contract behavior changed

## High-Value Contribution Areas

- planner robustness under real map data
- retrieval quality and ranking
- deployment hardening and production templates
- frontend usability and plan visualization
- travel knowledge cleaning and coverage improvements
