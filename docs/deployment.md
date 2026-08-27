# Deployment

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Production uses [docker-compose.prod.yml](../docker-compose.prod.yml), which contains
only the API and PostgreSQL. The database has no host port and is on an internal Docker
network. The API container is named `easymacros` and joins the external Docker network
named `proxy`, while PostgreSQL remains isolated from it. The API also retains its
loopback-only host port for local readiness and administration.

## Initial deployment

1. Create the external network expected by the reverse proxy if it does not already
   exist:

```sh
docker network create proxy
```

2. Create a root `.env` file outside version control with distinct
   `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `JWT_SECRET` values. Generate
   the JWT value with `openssl rand -base64 32`.
3. Review the rendered configuration without printing secrets:

```sh
docker compose --env-file .env -f docker-compose.prod.yml config --quiet
```

4. Build and start the backend and its PostgreSQL dependency from a clean checkout:

```sh
make backend-prod
curl --fail http://127.0.0.1:3000/v1/health/ready
```

The non-root API image compiles TypeScript, packages SQL migrations, runs pending
migrations before starting the server, uses a read-only root filesystem, and receives a
writable `/tmp` tmpfs. Before the API starts, the one-shot `image-jobs-init` service
creates and assigns its persistent image-job volume to the `node` user; this permits AI
image uploads without granting the API root access. The volume is reserved for temporary
ingestion jobs. Do not scale `api` above one replica in v1: local temporary image storage
is not shared between replicas, and every API process would also race to run startup
migrations.

## VPN-only HTTP or reverse-proxy TLS

Android release builds permit HTTP for private VPN deployments. For this mode, expose the
API only on the VPN interface or through a VPN-restricted proxy, firewall the port from
public and ordinary LAN interfaces, and use an address whose route exists only inside the
encrypted tunnel. The app does not detect VPN loss and cannot prevent an incorrectly
routed HTTP address from exposing bearer tokens and user data.

For any endpoint reachable outside that enforced VPN boundary, terminate TLS at a
maintained reverse proxy attached to the external `proxy` network and forward to
`http://easymacros:3000`. Set `TRUSTED_PROXY_HOPS=1` only when that proxy is the one
directly trusted hop; use a higher value only after verifying the actual topology.
The proxy must enforce request-size limits, preserve `X-Forwarded-For` only from trusted
peers, redirect HTTP to HTTPS, and retain access/error logs according to the operator's
retention policy. Its upstream response timeout must be at least 100 seconds (120 seconds
recommended) because `POST /ration-ai-estimates` can wait up to 90 seconds for the
advanced model. Never expose PostgreSQL or the API's plain HTTP port publicly.

## Backup and restore

Back up before every upgrade and test restoration periodically:

```sh
docker compose --env-file .env -f docker-compose.prod.yml exec -T db \
	sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
	> easymacros-$(date +%F).dump
```

To restore into a stopped, empty replacement database, start only `db`, copy the dump
to a controlled location, then run:

```sh
docker compose --env-file .env -f docker-compose.prod.yml up -d db
docker compose --env-file .env -f docker-compose.prod.yml exec -T db \
	sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
	< easymacros-YYYY-MM-DD.dump
make backend-prod
curl --fail http://127.0.0.1:3000/v1/health/ready
```

Keep encrypted backups with a retention period and restore drill evidence defined by the
operator. A failed migration leaves its transaction uncommitted; stop the rollout,
restore the last verified backup when needed, and investigate before retrying.

## Upgrades, secrets, and incidents

For an upgrade, take a backup, fetch the reviewed revision, rebuild with the initial
deployment command, and check `/v1/health/ready` plus application logs. Migrations are
applied before the API accepts traffic. Never roll back application code across an
already-applied destructive migration without a tested rollback plan.

Rotate database credentials with a coordinated PostgreSQL role/password change and
container restart. Rotate `JWT_SECRET` by replacing the injected value and restarting
the API; existing access tokens become invalid. See [security.md](security.md) for the
session consequence. During an incident, preserve sanitized logs and database backups,
block compromised credentials, rotate affected secrets, and record the timeline and
recovery verification.

External ingestion is disabled until configured. For Open Food Facts, set
`OPEN_FOOD_FACTS_ENABLED=true` and a real contact-bearing
`OPEN_FOOD_FACTS_USER_AGENT`; review current OFF terms, attribution, and shared-IP limits.
For AI, inject `OPENROUTER_API_KEY`, select `OPENROUTER_MODEL` for ingredient work and
`OPENROUTER_ADVANCED_MODEL` for whole-ration estimation, set a provider-side spend cap,
and tune `AI_REQUESTS_PER_HOUR`, `AI_WORKER_CONCURRENCY`, and
`AI_MAX_UPLOAD_BYTES`. Monitor aggregate status/latency/cost metadata only—never prompts,
images, or provider responses.

The image-job volume is temporary private data, not a backup target. Keep one API replica,
restrict host access, and periodically verify that no files outlive candidate expiry.
`docker-compose.dev.yml` remains development PostgreSQL only; run the API locally through
`pnpm`, not through Compose.
