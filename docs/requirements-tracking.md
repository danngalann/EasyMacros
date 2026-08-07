# Requirements Tracking

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

## Phase 1 checklist

- [x] pnpm workspace and strict TypeScript backend scaffold
- [x] Fastify liveness smoke test
- [x] Android Gradle/Compose application with version catalog and wrapper
- [x] PostgreSQL-only development Compose with health check and named volume
- [x] Root, backend, and Android ignore rules plus safe environment example
- [x] Formatter, linter, commit hook, CI, Dependabot, and secret scanning skeleton
- [x] Permanent architecture, development, deployment, security, API, domain, nutrient,
      Android, and ADR documentation
- [x] Open-source license, contribution, conduct, and security-reporting guidance

## Phase 2 checklist

- [x] Typed runtime configuration with required database URL validation
- [x] PostgreSQL connection pool and database readiness endpoint
- [x] Transactional, tracked SQL migration runner and initial users schema
- [x] Versioned readiness endpoint with OpenAPI documentation and response schemas

## Phase 3 checklist

- [x] Username/password users, sessions, refresh families, and durable login attempts
- [x] Argon2id password hashing, signed JWT access tokens, rotating opaque refresh tokens,
      bounded lost-response recovery, family revocation, and session listing/revocation
- [x] Registration switch, username normalization, password policy, operator user bootstrap,
      and password-change session revocation
- [x] Username/IP-pair exponential login backoff with `Retry-After` and documented
      IP-rotation tradeoff
- [x] Authentication API contract, environment configuration, and secret rotation guidance

## Phase 4 checklist

- [x] Extensible, idempotently seeded nutrient definitions with canonical units
- [x] Decimal-safe recipe/ration scaling and authoritative $4/4/9$ calorie calculation
- [x] User-owned ingredient storage, immutable revisions, manual candidate confirmation,
      visibility controls, and owner-scoped nutrient/ingredient API reads
- [x] Immutable recipe/ration snapshot and effective-dated goal database model with
      ownership, positivity, soft-deletion, and history indexes
- [ ] Recipe, ration, goal, dashboard, and search API workflows
- [ ] PostgreSQL integration tests for snapshot, timezone, pagination, and concurrency behavior
