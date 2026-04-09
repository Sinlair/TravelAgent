# next-ai-draw-io Summary

This note summarizes the most reusable parts of the `DayuanJiang/next-ai-draw-io` prompt design for repository architecture diagrams.

Source files reviewed:

- `lib/system-prompts.ts`
- `lib/validation-prompts.ts`
- `app/api/chat/route.ts`
- `packages/mcp-server/src/index.ts`

Repository:

- <https://github.com/DayuanJiang/next-ai-draw-io>

## Core Prompt Ideas

- Treat the model as a specialized `draw.io XML` diagram assistant.
- Before generating a diagram, state a short layout plan focused on avoiding overlap and bad edge routing.
- Prefer a full redraw for major structural changes.
- Keep the diagram inside a single page / viewport.
- Use shape libraries only after looking up the library syntax instead of guessing.

## Layout Guidance Worth Reusing

- Constrain the diagram to a compact page.
- Use grouped zones or layers before drawing edges.
- Leave explicit whitespace for routing channels.
- Build visual hierarchy with spacing and placement first, styling second.
- For crowded content, switch to vertical stacking, columns, or split into multiple diagrams.

## Edge Routing Guidance Worth Reusing

- Use orthogonal routing.
- Always think about source side and target side explicitly.
- Do not let multiple edges share the same exact path if separation is possible.
- Route around intermediate obstacles.
- For distant diagonal relationships, prefer perimeter routing over cutting through the center.
- Avoid unnatural corner connections.

## XML / Edit Workflow Ideas

- Keep a current XML snapshot as the source of truth.
- Use full redraws when the existing structure is too broken.
- Use smaller targeted edits only for local fixes.
- Preserve user changes when editing instead of regenerating blindly.

## Validation Criteria

Critical problems:

- overlapping elements
- lines crossing through unrelated shapes
- broken or incomplete rendering

Warnings:

- clipped or crowded text
- poor spacing
- cramped or misaligned layout

## How To Apply This In TravelAgent

For TravelAgent repository diagrams:

- default to a layered architecture view
- show module boundaries before runtime details
- keep `travel-agent-app`, `travel-agent-domain`, and `travel-agent-infrastructure` as separate visual layers
- isolate `travel-agent-amap` as an explicit integration module
- show `travel-agent-amap-mcp-server` only when MCP matters
- keep external providers in a separate region

When one diagram becomes too busy, split it into:

1. repository layered architecture
2. runtime interaction flow
