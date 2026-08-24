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

The generated local `.env` binds the API to `0.0.0.0:3000`, allowing a device on the
trusted LAN to reach `http://<development-machine-LAN-IP>:3000`. The PostgreSQL port
remains bound only to `127.0.0.1`. Stop the API when LAN access is no longer needed.

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

## Production artifact checks

Build the compiled, non-root API image with:

```sh
docker build --tag easymacros-api:local .
```

Validate production Compose interpolation with synthetic local values only:

```sh
POSTGRES_DB=easymacros POSTGRES_USER=easymacros POSTGRES_PASSWORD=test-password \
JWT_SECRET=ci-secret-that-is-at-least-thirty-two-bytes make compose-prod-check
```

See [deployment.md](deployment.md) for production startup, backup, restore, and upgrade
procedures. Do not use `docker-compose.prod.yml` as a development substitute.

## Development database

The development Compose file intentionally contains PostgreSQL only. Stop it with
`make dev-db-down`. Removing the named volume is destructive and must be an explicit
operator action; no reset target exists yet. Apply outstanding SQL migrations with
`make migrate`; applied migration names are recorded in `schema_migrations`.

## Authentication bootstrap

Set `JWT_SECRET` to a unique random value of at least 32 bytes before starting the API.
Registration is disabled by default. Create a first or recovery user interactively,
without passing a password through shell history:

```sh
pnpm --filter @easymacros/backend user:create
```

The command prompts for a username and a non-echoed password. It requires the database
to be migrated and the same runtime configuration as the API. Keep registration
disabled after bootstrap unless deliberately opening a short, supervised registration
window. For a deployment behind a reverse proxy, set `TRUSTED_PROXY_HOPS` to the
number of directly trusted hops; leave it at `0` for direct connections. Set
`PUBLIC_AUTH_RATE_LIMIT_MAX` to tune the per-minute public authentication limit.
