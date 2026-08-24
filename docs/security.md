# Security

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

| Boundary             | Phase 3 control                                                                                |
| -------------------- | ---------------------------------------------------------------------------------------------- |
| Repository secrets   | `.env*`, keystores, and local Android configuration are ignored.                               |
| CI source history    | Gitleaks scans Git history in CI.                                                              |
| Development database | Docker binds PostgreSQL only to `127.0.0.1`.                                                   |
| Runtime secrets      | `DATABASE_URL` and a minimum 32-byte `JWT_SECRET` are required at startup.                     |
| Credentials          | Argon2id hashes; generic login failures; JWT access tokens and digest-only refresh rows.       |
| Refresh replay       | Rotation, one-minute same-key lost-response recovery, and family revocation on reuse.          |
| Brute force          | Durable username/IP-pair backoff after three failures with `Retry-After`.                      |
| Protected routes     | Bearer JWT validation checks issuer, audience, expiry, subject, and active session.            |
| Production database  | Private internal Compose network; no PostgreSQL host port.                                     |
| Production API       | Non-root image, read-only root filesystem, bounded writable `/tmp`, and readiness probe.       |
| Release pipeline     | CI runs backend/Android checks, OpenAPI coverage, dependency audit, image build, and Gitleaks. |

Generate a signing secret outside the repository with:

```sh
openssl rand -base64 32
```

Rotate `JWT_SECRET` by deploying a new value and restarting the API. This invalidates
all access tokens; refresh tokens remain usable unless separately revoked. The current
pair-keyed backoff limits one source's attempts without globally locking an account;
deployments must also configure rate limiting at a trusted reverse proxy to address
distributed IP rotation. `TRUSTED_PROXY_HOPS` defaults to `0`; set it only to the
number of directly trusted reverse-proxy hops. `PUBLIC_AUTH_RATE_LIMIT_MAX` sets each
client IP's per-minute cap independently for registration, login, and refresh.

Production secrets are injected through an operator-controlled Compose env file outside
the repository. Never place it in an image, command line, CI log, or issue. The API logs
neither HTTP bodies nor tokens. Android release builds do not install transport logging;
the main manifest permits cleartext HTTP for VPN-only deployments. Cleartext HTTP does
not provide transport encryption itself: bearer tokens, ingredient data, and images are
protected only by the VPN tunnel. Operators must bind/firewall the API so it is
unreachable from public and non-VPN networks; otherwise they must use HTTPS. Android
cannot enforce or attest that VPN routing boundary. Debug builds log failed transport requests using only
the method, host, path, and non-success HTTP status, never query values, bodies, headers,
or tokens. Configured backend runtime instances disable Fastify's raw request logging and
emit only the request ID, method, route template, and status for rejected or failed
responses. Unexpected exceptions return only `{ "code": "internal_error" }`; application
logs retain the exception type and machine-readable code, such as a PostgreSQL SQLSTATE,
without the exception message, SQL, or values. Retain reverse-proxy and application logs
only for the period needed for operations and incident response, with restricted access.
Follow the rotation and recovery instructions in [deployment.md](deployment.md) when a
credential is suspected compromised.

External ingestion keeps provider traffic server-side. Open Food Facts receives only the
normalized barcode plus configured locale/country and the required operator-contact
User-Agent. OpenRouter receives a validated image and optional description delimited as
untrusted data. Tools are not enabled; output must match a strict local schema. Normal
logs contain no images, prompts, provider bodies, API keys, authorization headers, or
full barcodes.

AI uploads are limited to four files, each bounded by encoded size, MIME/signature
agreement, decoded dimensions, pixel count, and basic polyglot indicators. Files use random names in a mode-0700
directory and are removed on every terminal path. PostgreSQL enforces per-user/IP hourly
quotas; the worker has bounded global concurrency and attempts. Candidates and every
mutation are owner-scoped in SQL. Reverse proxies must apply a matching request-body
limit and must not expose the image-job volume.
