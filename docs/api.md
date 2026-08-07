# API

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Phase 1 exposes one unversioned development smoke endpoint:

```http
GET /health/live
```

It responds with `200` and `{"status":"ok"}` when the Fastify process is running.
Versioned product endpoints, OpenAPI generation, request validation, and database
readiness checks begin in Phase 2.
