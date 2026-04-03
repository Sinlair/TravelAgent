# Travel Knowledge RAG

## Data Sources

The system currently uses two knowledge files:

- `travel-knowledge.json`: small hand-curated seed knowledge.
- `travel-knowledge.cleaned.json`: large cleaned dataset collected from Wikivoyage.

The raw collector output is stored in `travel-knowledge.collected.json` and is not used directly when the cleaned file exists.

## Collection Strategy

Script: `scripts/collect_travel_attractions.py`

Source:

- Wikivoyage page sections for Chinese cities.

Collected sections are mapped to six knowledge topics:

- `see -> scenic`
- `do -> activity`
- `eat -> food`
- `drink -> nightlife`
- `sleep -> hotel`
- `get around -> transit`

### Chunking Strategy

The current chunking unit is one first-level list item from a city section.

Each chunk is stored as:

- `city`
- `topic`
- `title`
- `content`
- `tags`
- `source`

This means the dataset is currently chunked by **city section item**, not by token windows.

## Cleaning Strategy

Script: `scripts/clean_travel_knowledge.py`

Cleaning keeps the six supported topics and applies topic-specific filtering.

Main rules:

- remove noisy or very short records
- remove CSS / parser artifacts
- keep more structured scenic / food / activity records
- keep hotel records that still carry area or location value
- cap the number of records per city per topic

Current per-city caps:

- `scenic`: 12
- `activity`: 8
- `food`: 8
- `nightlife`: 6
- `transit`: 4
- `hotel`: 6

## Retrieval Strategy

Primary repository: `RoutingTravelKnowledgeRepository`

Lookup order:

1. Milvus vector search
2. local file-based fallback

### Query Planning

Shared planner: `TravelKnowledgeRetrievalSupport`

The retrieval plan extracts:

- `destination`
- inferred `topics`
- combined semantic query text
- structured filter expression

Topic inference uses keywords from:

- user query
- user preferences

Default inferred topics when nothing is explicit:

- `scenic`
- `food`

### Milvus Search

Repository: `TravelKnowledgeVectorStoreRepository`

Milvus search now uses:

- semantic query: `destination + userMessage + preferences`
- `topK = max(limit * 4, 12)`
- structured filter on metadata fields:
  - `city == destination`
  - `topic in inferredTopics`

Then results are filtered again in Java as a safety layer.

### Local Fallback Search

Repository: `LocalTravelKnowledgeRepository`

The fallback now uses the same retrieval plan:

- destination filtering first
- topic filtering second
- lexical scoring on topic, tags, title, and content

## Accuracy Improvements Already Applied

Compared with the earlier version, retrieval is now more accurate because:

- destination is treated as a hard constraint instead of only a text hint
- topics are inferred and used as structured filtering
- local fallback and vector search use the same retrieval plan
- cleaned data is preferred over raw collected data

## Remaining Accuracy Gaps

Still worth improving later:

- hotel and transit should eventually use dedicated schemas rather than generic text chunks
- very long list items should be split into summary-oriented sub-chunks
- topic recall can be improved with richer preference normalization
- Milvus filtering can later be extended with explicit source and city aliases