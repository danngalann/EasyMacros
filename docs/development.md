# Development

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

## Setup

```sh
pnpm install --frozen-lockfile
cp .env.example .env
make dev-db-up
make migrate
```

Wait for the database health check with:

```sh
docker compose -f docker-compose.dev.yml ps
```

Run the backend outside Docker:

```sh
make backend-dev
```

## Validation

```sh
make format-check
make lint
make test-backend
make test-android
make build
```

`make test-android` runs JVM tests. The Phase 1 Android gate is
`./android/gradlew lint test assembleDebug`.

## Development database

The development Compose file intentionally contains PostgreSQL only. Stop it with
`make dev-db-down`. Removing the named volume is destructive and must be an explicit
operator action; no reset target exists yet. Apply outstanding SQL migrations with
`make migrate`; applied migration names are recorded in `schema_migrations`.
