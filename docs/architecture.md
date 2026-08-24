# Architecture

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

EasyMacros is a monorepo with two independently buildable applications:

- `backend/` is a Node.js 24, strict TypeScript Fastify service managed by pnpm.
- `android/` is a Kotlin/Jetpack Compose Gradle project that Android Studio can open
  directly.

The backend loads and validates its runtime configuration at startup. It connects to
PostgreSQL through a small `pg` pool boundary, which provides the database readiness
check. Readiness requires both a working PostgreSQL connection and the latest tracked
schema migration; connectivity alone is not sufficient. Timestamped SQL migrations are
applied transactionally and tracked in the `schema_migrations` table. The API publishes
its OpenAPI document and interactive documentation through Fastify plugins.

Phase 4 adds a PostgreSQL nutrition domain. The seeded nutrient catalog is shared by
user-owned ingredients, immutable recipe-version snapshots, ration snapshots, and
effective-dated goal profiles. Decimal calculation utilities use `decimal.js`; the
database stores authoritative amounts as `numeric(14,6)`. Manual candidate confirmation
creates an ingredient and an immutable revision. The Android module still has one
activity and a Compose root; Android nutrition UI is deferred to later phases.

Phase 5 adds a transactional backend workflow service. Recipe version creation, ration
logging, and goal replacement each run on a single PostgreSQL transaction. The service
performs the calculation and snapshot work; Fastify routes only authenticate callers,
obtain the stored timezone when logging rations, and map domain errors to API responses.
Ration pagination is keyset-based on the history index ordering rather than offset-based.

Phase 10 packages the backend in a multi-stage Node 24 image. TypeScript output and SQL
migrations are built before runtime, then only production dependencies are copied into a
non-root process image. Production Compose has one API process and one PostgreSQL process;
the database network is internal and the API is locally bound for a separate TLS reverse
proxy. Startup runs migrations before listening, and SIGINT/SIGTERM close Fastify and the
PostgreSQL pool. The v1 API must remain a single replica because its temporary image-job
volume is local rather than shared.
