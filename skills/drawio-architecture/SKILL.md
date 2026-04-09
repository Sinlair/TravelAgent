---
name: drawio-architecture
description: Use when creating or revising draw.io architecture diagrams, system diagrams, layered module diagrams, or README-linked SVG diagrams for this repository. Apply strict anti-overlap, anti-overflow, and orthogonal edge-routing rules, and prefer layered architecture views for TravelAgent.
---

# Draw.io Architecture

Use this skill when the task is to create, redraw, or refine a `draw.io` architecture diagram for this repository.

Default output for vague requests like "architecture diagram":

- create a layered repository architecture diagram
- keep an editable `.drawio` source and a README-friendly `.svg` export

Project defaults:

- Access / delivery: `web`, `travel-agent-types`
- Application orchestration: `travel-agent-app`
- Domain: `travel-agent-domain`
- Infrastructure / integration: `travel-agent-infrastructure`, `travel-agent-amap`
- Optional sidecar: `travel-agent-amap-mcp-server`
- External systems: OpenAI-compatible LLM, Amap / Gaode APIs, SQLite, optional Milvus

## Workflow

1. Decide the diagram scope before drawing.
   Default to a layered repository view.
   If runtime flow and module layering are both needed, split them into two diagrams instead of overloading one.

2. Inventory the modules and group them before placing shapes.
   Do not place boxes one by one and "figure it out later".

3. Reserve routing channels before adding edges.
   Leave visible whitespace between columns and layers for arrows.

4. Draw the layout first, then labels, then connectors.

5. Run a final visual quality pass against the checklist below.

## Layout Rules

- Keep the whole diagram inside a single page / single viewport.
- Prefer top-to-bottom layers for architecture diagrams in this repo.
- Keep at least `50px` gaps between neighboring boxes. Use more when a routing corridor is needed.
- Use larger boxes instead of shrinking text aggressively.
- Keep text inside its box at all times.
- Split long titles across lines before reducing font size.
- Keep body text to short phrases, usually `2-4` lines per box.
- If a box needs more than `4` body lines, the diagram is too dense. Widen the box or split the diagram.
- Put external systems in a separate band or column, usually on the right.
- Keep optional paths visually distinct with dashed borders or dashed connectors.

## Connector Rules

- Use orthogonal connectors by default.
- Do not let an edge pass through a box that is not its source or target.
- Do not let two edges share the same route if they can be separated.
- Use natural sides for connections:
  - left-to-right flow: right side to left side
  - top-to-bottom flow: bottom side to top side
- Avoid corner-to-corner connections.
- For long or diagonal relationships, route around the perimeter instead of through the middle.
- Keep edge labels short and place them away from box borders.

## Text Rules

- Titles must remain fully visible within the shape.
- Module names may wrap onto a second line if the box is narrow.
- Do not let labels touch borders.
- If a title and module path cannot fit cleanly, shorten the body text first, then widen the box.
- Prefer readable structure over exhaustive detail.

## TravelAgent-Specific Rules

- The layered repository diagram should show module ownership first, not every runtime event.
- `travel-agent-app` is the orchestration center.
- `travel-agent-domain` should stay visually below the application layer and above concrete adapters.
- `travel-agent-infrastructure` should implement domain ports and connect to persistence, retrieval, and model providers.
- `travel-agent-amap` should be shown as a dedicated integration module, not merged into generic infrastructure text.
- `travel-agent-amap-mcp-server` should be optional unless the user explicitly wants MCP emphasized.
- SQLite and Milvus belong in external or persistence-facing infrastructure context, not in the domain layer.

## Edit Strategy

- If the current diagram is structurally messy, redraw it instead of patching small coordinates.
- Use targeted edits only for isolated label or spacing fixes.
- Keep `docs/assets/*.drawio` as the editable source of truth.
- Keep `docs/assets/*.svg` aligned with the latest `.drawio` layout.

## Quality Checklist

Reject the diagram and redraw if any of these are true:

- shapes overlap
- text is clipped, crowded, or outside its box
- arrows cross through unrelated shapes
- labels sit on top of arrows or borders
- the diagram needs zooming just to read the primary structure
- one diagram tries to explain both repository layering and full runtime flow in too much detail

## References

For the summarized prompt and validation rules extracted from `next-ai-draw-io`, read:

- `references/next-ai-drawio-summary.md`
