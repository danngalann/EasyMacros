# Domain Model

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Phase 3 implements username/password identity and sessions. `users` has a caller-
supplied UUID primary key, unique case-insensitive normalized username, Argon2id
password hash, IANA timezone, and creation/update timestamps. Usernames are NFKC
normalized, trimmed, lower-cased, limited to 3-64 ASCII letters/numbers/dots/hyphens/
underscores, and never accepted as a mutable ownership field.

`sessions` stores a user ID, refresh-token family, SHA-256 digest of the current opaque
refresh token, immediately previous digest/idempotency marker, bounded recovery time,
idle/fixed expiry, and revocation state. Raw refresh tokens are never persisted.
`login_attempts` stores a SHA-256 username/IP pair key, failure count, next permitted
time, and expiry for durable login backoff without recording a plaintext username.

Phase 4 adds an extensible, seeded nutrient catalog and user-owned ingredients with
confirmed per-100-g nutrient values. Ingredients are never deleted; `hidden` removes
them from default selection while preserving history. Manual nutrient candidates are
user-owned and can be confirmed exactly once into an ingredient.

Candidates now have `manual`, `barcode`, or `ai` source and durable
`pending/processing/ready/failed/cancelled/expired/confirmed` state. They preserve
confidence, warnings, barcode, attribution provenance, an optimistic version, and a
24-hour expiry. AI candidates have a durable ingestion job containing only private
temporary-path metadata, bounded attempts, and a lease. Successful confirmation stores
the reviewed values—not a hidden provider copy—in the ingredient and immutable revision.
Ingredients retain source, confidence, warnings, and provenance for later attribution.

Recipes have stable identities and immutable versions. Each version snapshots its
components, ingredient names/nutrients, batch yield, and calculated nutrient totals.
Batch yield is derived when the version is created as the exact sum of its component
gram quantities; it is not independently entered by the user.
Deleting a recipe is a soft delete that prevents new rations without changing old
snapshots. Rations snapshot a version's name, yield, nutrients, consumed UTC instant,
and immutable local date derived in the user's IANA timezone. Goals are versioned by
effective local date, so historical progress remains interpretable.

Phase 5 exposes these models through owner-scoped workflows. Recipe creation and updates
run transactionally: each version snapshots every selected ingredient's name and nutrient
values, calculates six-decimal totals, and atomically changes the recipe's current
version. Ration creation similarly snapshots the then-current recipe version and derives
`consumed_local_date` from the user's stored IANA timezone. An omitted ration weight
means the whole dish: the backend resolves it to the current recipe version's yield
before persisting the non-null consumed-weight snapshot. Ration history uses the
`(consumed_local_date, consumed_at, id)` ordering as its opaque cursor boundary.

Goal replacement keeps one profile per user/effective local date. Dashboard totals are
the PostgreSQL sum of non-deleted ration snapshots for a requested local date; calories
are calculated from those totals using the canonical macro formula.

Phase 9 exposes snapshot details and a delete-then-re-log correction workflow. A
deleted ration no longer contributes to history or dashboard totals, but its nutrient
snapshot is never recalculated or modified in place. Ration contribution previews are
calculated server-side from the current active recipe and create no record.

Changing a user's IANA timezone atomically reclassifies active rations' local dates from
their stored UTC instants and records the new timezone on those rows. This deliberately
changes historical dashboard-day grouping while preserving each ration's consumption
instant and nutrient snapshot.
