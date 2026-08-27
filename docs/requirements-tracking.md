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
- [x] Recipe versioning/search, ration logging/history, effective-dated goals, and daily
      dashboard API workflows
- [ ] PostgreSQL integration tests for snapshot, timezone, pagination, and concurrency behavior

## Phase 5 checklist

- [x] Owner-scoped recipe creation, immutable version updates, search, detail, and soft deletion
- [x] Atomic ration snapshots with user-timezone local dates and deterministic cursor pagination
- [x] Effective-dated goal profiles and authoritative daily dashboard totals/calories
- [x] Published authenticated API contracts and focused workflow route regression coverage

## Phase 6 checklist

- [x] Android Hilt, Retrofit, DataStore, encrypted Keystore session, and repository foundation
- [x] Login/register and authenticated app-shell selection with loading and preserved errors
- [x] Process-restart session restoration and single-flight refresh coordination
- [x] Focused ViewModel authentication regression tests

## Phase 7 checklist

- [x] Authenticated Android daily dashboard with date navigation and authoritative server totals
- [x] Dashboard DTO-to-domain mapping with protected Retrofit access and refresh support
- [x] Loading, empty, retry, offline, and session-expiry states with focused ViewModel tests

## Phase 8 checklist

- [x] Authenticated Android recipe search, detail, composition, and nutrient-total displays
- [x] Recipe editor with owner-scoped ingredient search, optional hidden ingredients, validation,
      server-authoritative preview, immutable version updates, and duplicate-submit prevention
- [x] Reviewed manual ingredient candidate creation and confirmation from the Android recipe editor
- [x] Searchable Android ingredient catalog with repeated standalone creation and recipe access
- [x] Recipe deletion confirmation explaining active-recipe removal and retained ration snapshots
- [x] Focused recipe ViewModel regression tests and protected backend preview workflow coverage

## Phase 9 checklist

- [x] Android date dashboard with authoritative macro progress, calories, day rations,
      loading, empty, retry, and manual refresh states
- [x] Server-authoritative ration preview and Android log confirmation flow without meal categories
- [x] Effective-dated Android goal editor and protected profile timezone setting
- [x] Owner-scoped ration history search/date filters, keyset paging, immutable detail, and
      delete-then-re-log correction flow
- [x] Coordinated dashboard, recipe, log, history, goals, and settings navigation
- [x] Dark-first Material 3 shell, locale-formatted dates, guided forms, and distinct
      loading, empty, retry, and content states across authenticated Android workflows
- [x] Focused backend workflow/route and Android tracking ViewModel regression coverage
- [ ] Local end-to-end, DST, over-goal, and Compose network-error coverage from the Phase 9 gate

## Phase 10 checklist

- [x] Multi-stage non-root backend image with compiled runtime, packaged migrations,
      read-only filesystem, tmpfs, health probe, and graceful shutdown
- [x] Production Compose API/PostgreSQL topology with a private database network,
      persistent volumes, restart policies, resource guidance, and single-replica limit
- [x] Production deployment, TLS/reverse-proxy, backup/restore, upgrade, secret rotation,
      incident, log-retention, and AI-provider operating guidance
- [x] CI backend, Android, migration, OpenAPI, dependency-audit, container-build, and
      secret-scan gates
- [ ] Restore drill, load/accessibility review, Android release journey, and documented
      performance/AI-concurrency measurements require a production-like environment

## Skipped barcode and AI ingestion requirements

- [x] Shared manual, Open Food Facts, and OpenRouter candidate contract with explicit,
      exactly-once reviewed confirmation
- [x] Server-side OFF v2 adapter with checksum normalization, minimal fields, attribution,
      missing-versus-zero mapping, timeout/retry, coalescing, cache, and shared budget
- [x] Durable multimodal job state, strict structured output, private validated uploads,
      bounded retries/concurrency/quotas, recovery, cancellation, expiry, and cleanup
- [x] Android ingredient management, creation chooser, CameraX/ML Kit scan, photo capture/
      picker, process-restorable polling, shared review, and barcode-to-AI fallback
- [x] Focused provider, validation, ownership/confirmation, and Android state tests
- [ ] Real local OFF → AI → confirm → ingredient search → recipe journey requires valid
      provider configuration and physical/emulated camera execution

## Ingredient and recipe UX corrections

- [x] Confirmation returns to ingredient search with visible success feedback
- [x] Recipe creation uses the shared manual/barcode/AI chooser and resumes its draft
- [x] Recipe weight is derived from component weights; no redundant yield input remains
- [x] Ration weight is optional; omission logs the whole recipe yield
- [x] Log ration action is hidden while the log flow is active
- [x] Candidate fields show required/optional status and field-relevant warnings
- [x] AI ingestion accepts up to four jointly analyzed images
- [x] Ingredient success feedback expires and is cleared when leaving the screen
- [x] Recipe-origin creation Back returns directly to the preserved recipe
- [x] Android system Back navigates subordinate/app destinations before activity exit
- [x] Release builds permit HTTP for an operator-enforced VPN-only API boundary
- [x] Log ration branches to Recipe or AI + Images without changing recipe behavior
- [x] Whole-ration AI uses the advanced model, strict totals schema, multi-image context,
      explicit editable review, and exactly-once immutable snapshot confirmation
- [x] AI ration history preserves and displays estimate source, confidence, and warnings
