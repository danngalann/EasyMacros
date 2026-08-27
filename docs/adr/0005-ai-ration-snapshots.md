# ADR 0005: AI ration estimates become reviewed snapshots

## Status

Accepted.

## Decision

AI + Images is a second ration input source, not an implicit recipe generator. The
backend sends one to four validated images and required food context to
`OPENROUTER_ADVANCED_MODEL` using strict structured output with tools disabled. It
returns total edible weight and nutrient totals for the complete pictured ration.

The result is stored as an owner-scoped, 24-hour review estimate. Confirmation requires
the estimate version and copies the reviewed values, confidence, warnings, and provider
provenance into the existing immutable ration snapshot in one transaction. AI rations
have no recipe-version reference. Images are processed in memory and never persisted.

## Consequences

Dashboard and history aggregation remain source-agnostic and authoritative in
PostgreSQL. Recipe editing and ingredient catalogs are not polluted by one-off restaurant
meals. Clients must label AI records as estimates and require review. Synchronous
advanced-model latency is visible to the user; retrying creates a new estimate and
consumes quota, while confirmation itself is exactly once.
