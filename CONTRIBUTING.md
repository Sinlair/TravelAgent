# Contributing

Thanks for considering a contribution.

## Before You Start

- Open an issue first for larger changes, refactors, or new features.
- Keep changes scoped. Small, reviewable pull requests are preferred.
- Do not commit secrets, local env files, runtime logs, or generated local data.

## Local Setup

1. Copy `.env.travel-agent.example` to `.env.travel-agent`.
2. Fill only the variables you actually need.
3. Start the backend in one terminal:

```bash
./mvnw -pl travel-agent-app -am spring-boot:run
```

4. Start the frontend in another terminal if needed:

```bash
cd web
npm ci
npm run dev
```

Runtime layout and local output directories are documented in [`docs/operations.md`](docs/operations.md).

## Development Expectations

- Preserve the existing module boundaries.
- Prefer fixing root causes over adding one-off patches.
- Keep public behavior and docs consistent with the README.
- If you add configuration, update `.env.travel-agent.example` and relevant docs.
- If you add tests or deployment behavior, update the README or release checklist when needed.

## Testing

Run the relevant validation before opening a pull request.

Backend:

```bash
./mvnw -B test
```

Frontend:

```bash
cd web
npm ci
npm run test
npm run build
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
