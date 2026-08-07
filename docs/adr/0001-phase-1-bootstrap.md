# ADR 0001: Phase 1 bootstrap layout

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

## Status

Accepted

## Context

EasyMacros needs a reproducible shared repository while preserving an independently
openable Android project.

## Decision

Use a pnpm workspace for the TypeScript backend and tooling. Keep `android/` as a
complete Gradle root with a checked-in wrapper and TOML version catalog. Run PostgreSQL
only in development Compose; run the backend directly through pnpm.

## Consequences

JavaScript tooling and Android builds have separate dependency managers, but share root
documentation, CI, ignore rules, and Make targets. Product behavior is intentionally
deferred until later phases.
