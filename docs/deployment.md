# Deployment

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Production uses [docker-compose.prod.yml](../docker-compose.prod.yml), which contains
only the API and PostgreSQL. The database has no host port and is on an internal Docker
network. The API is bound to `127.0.0.1` by default; place a TLS-terminating reverse
proxy on the same host in front of it.

## Initial deployment

1. Create a production environment file outside version control with distinct
   `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `JWT_SECRET` values. Generate
   the JWT value with `openssl rand -base64 32`.
2. Review the rendered configuration without printing secrets:

```sh
set -a
. /secure/path/easymacros.env
set +a
docker compose --env-file /secure/path/easymacros.env -f docker-compose.prod.yml config >/dev/null
```

3. Start the service from a clean checkout:

```sh
docker compose --env-file /secure/path/easymacros.env -f docker-compose.prod.yml up -d --build
curl --fail http://127.0.0.1:3000/v1/health/ready
```

The non-root API image compiles TypeScript, packages SQL migrations, runs pending
migrations before starting the server, uses a read-only root filesystem, and receives a
writable `/tmp` tmpfs. Its persistent image-job volume is reserved for temporary
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
maintained reverse proxy and forward only to
`http://127.0.0.1:3000`. Set `TRUSTED_PROXY_HOPS=1` only when that proxy is the one
directly trusted hop; use a higher value only after verifying the actual topology.
The proxy must enforce request-size limits, preserve `X-Forwarded-For` only from trusted
peers, redirect HTTP to HTTPS, and retain access/error logs according to the operator's
retention policy. Never expose PostgreSQL or the API's plain HTTP port publicly.

## Backup and restore

Back up before every upgrade and test restoration periodically:

```sh
docker compose --env-file /secure/path/easymacros.env -f docker-compose.prod.yml exec -T db \
	pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom > easymacros-$(date +%F).dump
```

To restore into a stopped, empty replacement database, start only `db`, copy the dump
to a controlled location, then run:

```sh
docker compose --env-file /secure/path/easymacros.env -f docker-compose.prod.yml up -d db
docker compose --env-file /secure/path/easymacros.env -f docker-compose.prod.yml exec -T db \
	pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists < easymacros-YYYY-MM-DD.dump
docker compose --env-file /secure/path/easymacros.env -f docker-compose.prod.yml up -d --build api
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
For AI, inject `OPENROUTER_API_KEY`, select `OPENROUTER_MODEL`, set a provider-side spend
cap, and tune `AI_REQUESTS_PER_HOUR`, `AI_WORKER_CONCURRENCY`, and
`AI_MAX_UPLOAD_BYTES`. Monitor aggregate status/latency/cost metadata only—never prompts,
images, or provider responses.

The image-job volume is temporary private data, not a backup target. Keep one API replica,
restrict host access, and periodically verify that no files outlive candidate expiry.
`docker-compose.dev.yml` remains development PostgreSQL only; run the API locally through
`pnpm`, not through Compose.
