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

The Android module has one activity and a Compose root. It contains no navigation,
networking, or product state in Phase 2.
