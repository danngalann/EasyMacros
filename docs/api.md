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
