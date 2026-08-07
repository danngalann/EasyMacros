# Security

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

| Boundary             | Phase 3 control                                                                          |
| -------------------- | ---------------------------------------------------------------------------------------- |
| Repository secrets   | `.env*`, keystores, and local Android configuration are ignored.                         |
| CI source history    | Gitleaks scans Git history in CI.                                                        |
| Development database | Docker binds PostgreSQL only to `127.0.0.1`.                                             |
| Runtime secrets      | `DATABASE_URL` and a minimum 32-byte `JWT_SECRET` are required at startup.               |
| Credentials          | Argon2id hashes; generic login failures; JWT access tokens and digest-only refresh rows. |
| Refresh replay       | Rotation, one-minute same-key lost-response recovery, and family revocation on reuse.    |
| Brute force          | Durable username/IP-pair backoff after three failures with `Retry-After`.                |
| Protected routes     | Bearer JWT validation checks issuer, audience, expiry, subject, and active session.      |

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
