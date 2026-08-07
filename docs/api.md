# API

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

The backend exposes an unversioned process liveness endpoint:

```http
GET /health/live
```

It responds with `200` and `{"status":"ok"}` when the Fastify process is running.

Phase 2 adds a versioned database readiness endpoint:

```http
GET /v1/health/ready
```

It returns `200` and `{"status":"ok"}` only after a PostgreSQL probe succeeds. It
returns `503` and `{"status":"unavailable"}` when the database cannot be reached.

The OpenAPI 3 document is served at `/docs/json`; interactive documentation is served
at `/docs`. Fastify response schemas provide the currently published API contract.

## Authentication

Phase 3 adds JSON authentication endpoints:

```http
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
POST /auth/change-password
GET  /auth/sessions
DELETE /auth/sessions/{sessionId}
GET  /me
```

Registration and login accept `username` and `password`, and return an access JWT,
opaque refresh token, access expiry in seconds, and session ID. Registration returns
`403` when `REGISTRATION_ENABLED` is not `true`. Login returns a generic `401`
credential failure whether the username exists or not.

`/auth/refresh` requires `refreshToken` and a UUID `idempotencyKey`. Refresh tokens
rotate on use. One retry of the immediately previous token with the same idempotency
key is accepted for one minute to recover a lost mobile response; other reuse revokes
the refresh family. Authenticated requests use `Authorization: Bearer <access JWT>`.
Logout and session deletion revoke only the caller-owned session. Password change
requires the current password and revokes all existing sessions before returning a new
one.

After three failed login attempts for a normalized username/IP pair, the API responds
with `429`, a generic problem response, and `Retry-After`. A successful login clears
that pair's state. This is intentionally pair-keyed, so reverse-proxy limits remain
necessary for IP-rotating attacks.
