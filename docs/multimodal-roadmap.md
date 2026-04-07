# Multimodal Roadmap

This project now supports text input plus image attachments.

The planner is still text-first internally: uploaded images are converted into extracted travel context and then fed into the existing routing, memory, validation, repair, and plan generation flow.

That does not mean multimodal support is a bad fit. For a travel planning assistant, multimodal is valuable when it reduces user input effort and turns messy travel artifacts into structured planning context.

## Why It Is Worth Doing

High-value examples:

- hotel booking screenshots
- flight or train ticket screenshots
- attraction posters or event notices
- map screenshots
- social media itinerary screenshots

These inputs can help the system extract:

- origin and destination
- dates and times
- hotel names and stay areas
- booked activities
- candidate POIs to include or avoid

## Recommended Scope

The first version should be image input only.

Do not start with:

- audio chat
- video understanding
- generic all-media chat

Those modes are much more expensive and less central to itinerary planning than image-based intake.

## Phase 1 Status

The first useful slice is now in place:

1. image upload exists in the web chat flow
2. the backend request contract accepts image attachments
3. attachment metadata is persisted with the conversation message
4. image content is summarized into travel-relevant text context
5. extracted facts are merged into the existing planner context
6. users can confirm or dismiss extracted image facts before planning continues

What is still missing:

1. stronger structured extraction instead of only summarized context
2. richer attachment lifecycle policies such as retention and deletion controls
3. evaluation that proves image intake improves planner acceptance

## Non-Goals

- replacing the planner with end-to-end vision prompting
- supporting arbitrary media types before image intake proves useful
- treating multimodal as a UI-only feature without persistence or traceability

## Risks

- OCR noise can corrupt dates, prices, or place names
- screenshots may include private booking data
- images without structured extraction become hard to debug
- multimodal support can create the illusion of better planning while the actual planner logic remains unchanged

## Guardrails

- validate extracted dates, budgets, and POIs before trusting them
- show users the parsed facts before they are committed to the itinerary
- keep the raw attachment separate from the normalized planner fields
- preserve the current validation and repair loop instead of bypassing it

## Suggested Success Criteria

- users can upload a booking or itinerary screenshot and get usable planning context without manual retyping
- extracted facts improve planner acceptance without increasing constraint violations
- attachment handling remains optional; the text-only path stays first-class

## Recommended Order

1. stabilize text planning and feedback analysis
2. refine image extraction into stronger structured fields
3. evaluate whether image input materially improves planner acceptance
4. only then consider broader multimodal support
