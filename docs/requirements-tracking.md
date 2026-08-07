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

## Deferred requirements

Authentication and all nutrient/product behavior are explicitly deferred to their
documented later phases.
