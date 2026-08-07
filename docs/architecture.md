# Architecture

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

EasyMacros is a monorepo with two independently buildable applications:

- `backend/` is a Node.js 24, strict TypeScript Fastify service managed by pnpm.
- `android/` is a Kotlin/Jetpack Compose Gradle project that Android Studio can open
  directly.

The backend loads and validates its runtime configuration at startup. It connects to
PostgreSQL through a small `pg` pool boundary, which provides the database readiness
check. Timestamped SQL migrations are applied transactionally and tracked in the
`schema_migrations` table. The API publishes its OpenAPI document and interactive
documentation through Fastify plugins.

Phase 4 adds a PostgreSQL nutrition domain. The seeded nutrient catalog is shared by
user-owned ingredients, immutable recipe-version snapshots, ration snapshots, and
effective-dated goal profiles. Decimal calculation utilities use `decimal.js`; the
database stores authoritative amounts as `numeric(14,6)`. Manual candidate confirmation
creates an ingredient and an immutable revision. The Android module still has one
activity and a Compose root; Android nutrition UI is deferred to later phases.
