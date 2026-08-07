# EasyMacros

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

EasyMacros is an open-source, self-hostable nutrient tracker. It will pair a native
Android application with a TypeScript API and PostgreSQL. Phase 1 provides development
scaffolding only; product features are not implemented yet.

## Prerequisites

- Node.js 24 or later
- pnpm 10 or later
- Docker Compose
- JDK 21 and an Android SDK with API 35

## Quick start

```sh
pnpm install --frozen-lockfile
cp .env.example .env
make dev-db-up
make test
```

Run the backend locally with `make backend-dev`. It listens on
`http://127.0.0.1:3000`; `GET /health/live` returns `{"status":"ok"}`.

Open the `android/` directory directly in Android Studio, or run
`./android/gradlew lint test assembleDebug` from the repository root.

## Project layout

- `backend/`: Fastify application and Vitest tests
- `android/`: independent Gradle/Compose Android project
- `docs/`: current architecture, development, and operations documentation
- `docker-compose.dev.yml`: development PostgreSQL only

## Quality commands

```sh
make format-check
make lint
make test
make build
```

See [docs/development.md](docs/development.md) for the complete command reference.

## License

EasyMacros is licensed under the MIT License. See [LICENSE](LICENSE).

## Project status

EasyMacros is open source and currently maintained as a personal project. The repository
is shared for transparency and learning; feature development remains maintainer-led for
now.
