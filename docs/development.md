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
The Android manifest permits cleartext HTTP in debug and release builds. Release HTTP is
intended only for an API address restricted to an encrypted VPN; Android does not verify
that restriction.

Android debug failures use the `EasyMacrosNetwork` Logcat tag. Entries contain only the
method, host, path, and, for HTTP failures, status code. If Android Studio Live Edit
crashes in `com.android.tools.deploy.liveedit` with `NoSuchFieldException: receiver`,
disable Live Edit and replace the incrementally patched app with a clean install:

```sh
cd android
./gradlew clean :app:installDebug
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
On a production host with a root `.env` and the external `proxy` Docker network,
`make backend-prod` builds and starts the `easymacros` API container plus PostgreSQL.

## Development database

The development Compose file intentionally contains PostgreSQL only. Stop it with
`make dev-db-down`. To delete every local user, session, ingredient, candidate, recipe,
ration, and goal, recreate the PostgreSQL volume, wait for health, and reapply all
migrations:

```sh
make dev-db-reset
```

This target is intentionally destructive and is scoped to `docker-compose.dev.yml`; do
not use it for production. Android may still hold the old encrypted access/refresh
tokens, but the server-side refresh session no longer exists. The next authenticated
request/refresh fails with `401`, and the app clears its local session and returns to
login. Apply only outstanding migrations without deleting data with `make migrate`;
applied migration names are recorded in `schema_migrations`.

The bare-metal backend still uses PostgreSQL from `docker-compose.dev.yml` through its
loopback port. A healthy container does not imply a current schema. If `/recipes`,
`/ingredients`, and `/rations` all fail together, apply migrations and verify readiness:

```sh
make migrate
curl --fail http://127.0.0.1:3000/v1/health/ready
```

Readiness returns `503` when PostgreSQL is unreachable or the latest tracked migration
has not been applied.

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

## External ingredient ingestion

OFF and OpenRouter are optional in development. Copy `.env.example`, replace the OFF
placeholder with an operator-controlled contact address, then enable it:

```sh
OPEN_FOOD_FACTS_ENABLED=true
OPEN_FOOD_FACTS_USER_AGENT='EasyMacros/0.1.0 (operator@example.com)'
```

Set `OPENROUTER_API_KEY` only in the ignored local `.env`; never put it in Gradle,
source, screenshots, or shell history. `OPENROUTER_MODEL` handles ingredient candidates;
`OPENROUTER_ADVANCED_MODEL` handles whole-ration estimates and defaults to
`google/gemini-3.7-flash`. Synthetic tests mock both providers:

```sh
pnpm --filter @easymacros/backend test -- ingredient-ingestion.test.ts ingredients.test.ts ration-ai.test.ts
cd android
./gradlew :app:testDebugUnitTest --tests 'com.danngalann.easymacros.ui.IngredientsViewModelTest'
```

Apply migrations through `0006_add_ai_ration_estimates.sql` before exercising the Android flow.
The committed API contract is `backend/openapi.json`.
