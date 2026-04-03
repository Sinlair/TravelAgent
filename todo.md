# TODO

## Recently Completed

- [x] Schema-ize `hotel` knowledge chunks into:
  - `hotel_area`
  - `hotel_listing`

- [x] Schema-ize `transit` knowledge chunks into:
  - `transit_arrival`
  - `transit_hub`
  - `transit_district`

- [x] Improve planner-facing retrieval quality for:
  - area-level hints
  - route-planning hints
  - neighborhood food clustering hints

- [x] Extend structured retrieval beyond raw `city + topic` with persisted metadata such as:
  - `source`
  - hotel / transit `schemaSubtype`
  - `qualityScore`

- [x] Add topic-balanced retrieval instead of a generic first-`N` cut.

- [x] Add retrieval observability, including:
  - city match
  - inferred topics
  - vector vs local fallback source

- [x] Add source quality scoring during cleaning and feed it into retrieval ranking.

- [x] Add seed verification flow.
  After seeding Milvus, run retrieval smoke checks and print matched snippets for sample cities.

- [x] Show retrieved knowledge snippets in the UI for planner responses.

- [x] Add planner weather validity note in the UI.

- [x] Add “closest feasible alternative” explanation UI.

## High Priority

- [x] Clean parser artifacts and mojibake from collected / cleaned knowledge.
  Removed noisy items such as:
  - `.listi`
  - CSS / parser artifact titles
  - mojibake title fragments like `hÃ³ng shÄo rÃ²u`

- [x] Add a second-stage summarization pass for collected chunks.
  Long records are now normalized into planner-oriented summaries before seeding.

- [ ] Improve hotel chunk normalization further.
  Current subtype inference works, but some hotel entries are still too listing-shaped and should be rewritten into:
  - area guidance for where to stay
  - representative examples only when useful

- [ ] Improve transit chunk normalization further.
  Current subtype inference works, but some records are still route-listing style text. Demote or rewrite:
  - raw metro line enumerations
  - weak station listings without planner guidance

## Retrieval

- [x] Extend structured retrieval with:
  - city aliases
  - trip style tags such as `relaxed`, `family`, `nightlife`, `museum`

- [ ] Extend structured retrieval further with:
  - richer preference normalization

- [ ] Add stronger ranking penalties for noisy but structurally valid records.
  Even with `qualityScore`, some malformed records still rank too high because they match topic or query terms.

## Data Pipeline

- [ ] Add incremental collection mode.
  Only re-collect specified cities or changed pages instead of re-running the full city list every time.

- [ ] Add a lightweight data quality report after cleaning.
  Print counts for:
  - parser-artifact drops
  - encoding-noise drops
  - subtype distribution
  - quality score buckets

## Product

- [x] Add low-quality knowledge suppression in the UI.
  Low-confidence or noisy knowledge is now suppressed behind a dedicated collapsed section instead of being shown first.

- [x] Expose inferred trip styles and matched trip styles in the planner UI.

- [ ] Add planner-side explanation for why a knowledge hint was selected.
  The UI already shows metadata; the next step is a more user-facing explanation like:
  - chosen for stay-area guidance
  - chosen for airport-arrival advice
  - chosen for nearby food clustering

## Engineering

- [x] Separate knowledge-vector configuration from long-term-memory vector configuration more explicitly.
  Knowledge vector store now has its own `enabled/uri/username/password/database/collection/index` config surface.

- [x] Add end-to-end planner demo test.
  Keep a reproducible sample request that verifies:
  - planner generation
  - weather hint injection
  - knowledge hint injection
  - validation / repair behavior
  - UI-facing planner metadata presence

- [x] Add launch preflight and runtime health signals.
  The project now includes:
  - preflight PowerShell checks
  - actuator health contributors for core dependencies

- [x] Add frontend or API-level release smoke script.
  One command now verifies:
  - backend starts
  - `/actuator/health` responds
  - planner API returns a structured response
  - frontend build artifacts are present

- [x] Add environment template and production-style start / stop scripts.
  The repository now includes:
  - `.env.travel-agent.example`
  - `scripts/start-travel-agent.ps1`
  - `scripts/stop-travel-agent.ps1`

- [x] Add deployment-target-specific packaging.
  The repository now includes:
  - containerized full-stack launch assets
  - frontend reverse proxy container config
  - release checklist documentation

- [ ] Add final production deployment templates.
  Still worth adding later:
  - Windows service packaging
  - TLS / domain reverse proxy template
  - production secret management template
