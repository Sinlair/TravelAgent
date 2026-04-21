# Herness Contract Guide

## Response Shape

The Herness renovation standardizes the conversation result shape across chat execution and persisted conversation detail views.

Important fields:

- `feedbackTarget`: stable answer target id, default scope, available scopes, and `planVersion` when a structured itinerary exists
- `issues`: top-level user-facing flags such as clarification needed, pending image confirmation, repaired plan, or unresolved plan risk
- `missingInformation`: structured missing travel slots
- `constraintSummary`: normalized planner outcome with `PASS`, `REPAIRED`, `RISK`, or `NONE`
- `timeline[*].status`, `timeline[*].startedAt`, `timeline[*].endedAt`

## Feedback Payload

Feedback submissions can now include:

- `targetId`
- `targetScope`
- `planVersion`
- `reasonLabels`
- `note`

The current SQLite persistence keeps these values inside `conversation_feedback.metadata_json` for backward compatibility.

## Verification Commands

These commands assume macOS Terminal with `zsh` or `bash`.

```bash
./mvnw -B test
cd web && npm run build
cd web && npm run test
```

## Notes

- The frontend derives a shared result view model from the persisted detail response so chat, plan, map, timeline, and feedback UI stay in sync.
- The smoke integration test enables Spring bean overriding because the test suite replaces the default `vectorStore` bean with a stub.
