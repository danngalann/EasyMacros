# Architecture

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

EasyMacros is a monorepo with two independently buildable applications:

- `backend/` is a Node.js 24, strict TypeScript Fastify service managed by pnpm.
- `android/` is a Kotlin/Jetpack Compose Gradle project that Android Studio can open
  directly.

The backend currently exposes only a liveness route. PostgreSQL is provisioned for
local development but is not connected to application code until Phase 2.

The Android module has one activity and a Compose root. It contains no navigation,
networking, or product state in Phase 1.
