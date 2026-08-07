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

## Nutrition foundation

Phase 4 adds authenticated nutrition endpoints:

```http
GET   /nutrients
GET   /ingredients?query=&hidden=false
GET   /ingredients/{ingredientId}
POST  /ingredients/manual-candidates
POST  /ingredient-candidates/{candidateId}/confirm
PATCH /ingredients/{ingredientId}
PATCH /ingredients/{ingredientId}/visibility
```

Manual candidates require a name and unique, non-negative decimal nutrient amounts
for protein, carbohydrates, and fat. Confirmation requires the candidate version and
creates exactly one user-owned ingredient. Ingredients have no deletion endpoint;
visibility controls whether they appear in the default list while preserving details
and historical snapshots.

## Nutrition workflows

Phase 5 adds authenticated recipe, ration, goal, dashboard, and recipe-search endpoints:

```http
GET    /recipes?query=
POST   /recipes
POST   /recipes/preview
GET    /recipes/{recipeId}
PUT    /recipes/{recipeId}
DELETE /recipes/{recipeId}

POST   /rations
GET    /rations?date=YYYY-MM-DD&cursor=&limit=

PUT    /goals
GET    /goals?date=YYYY-MM-DD
GET    /dashboard?date=YYYY-MM-DD
```

`POST /recipes` and `PUT /recipes/{recipeId}` require `name`, positive decimal
`yieldGrams`, and at least one `{ingredientId, quantityGrams}` component. A recipe
update creates a new immutable version and makes it current; it never modifies an
existing version. `PUT /recipes/{recipeId}` also accepts the loaded `expectedVersion`;
the API returns `409` when that version is stale so the client can reload before editing
again. Recipe deletion is a soft deletion and returns `204`.

`POST /recipes/preview` accepts the same recipe payload and validates the caller-owned
ingredients, then returns the canonical batch nutrient totals and derived calories without
creating or changing a recipe. Clients use this endpoint for pre-save display only; the
subsequent create or update remains the authoritative persisted operation.

`POST /rations` requires a current active `recipeId`, positive decimal `consumedGrams`,
and an ISO-8601 `consumedAt`; `note` is optional. The API reads the authenticated user's
IANA timezone, derives the ration's local date, and persists recipe/name/yield/nutrient
snapshots atomically. Ration history is sorted by local date, instant, then ID in
descending order. `limit` is bounded to 1-100 (default 50); use the opaque
`nextCursor` response value for the following page.

`PUT /goals` requires `effectiveLocalDate` and one or more unique, positive nutrient
targets. Only nutrient definitions marked `goalAllowed` may be targeted. A goal replaces
the profile for that effective local date. `GET /goals` selects the most recent profile
effective on or before the requested date; it returns `404` when none exists.

`GET /dashboard` returns the day-local summed ration nutrients, authoritative derived
calories, and the effective goal profile. All mutable-resource reads and writes are
scoped to the authenticated user in their database query.

## Tracking and profile settings

Phase 9 adds Android-facing ration management and profile timezone endpoints:

```http
POST   /rations/preview
GET    /rations/{rationId}
DELETE /rations/{rationId}
PUT    /me/timezone
```

`POST /rations/preview` accepts an active `recipeId` and positive decimal
`consumedGrams`. It returns that recipe snapshot's proportional nutrients and
calories without persisting a ration. Clients must use this returned value for the
contribution review; only `POST /rations` creates the immutable snapshot.

`GET /rations` also accepts optional `from`, `to`, and `query` parameters. `from` and
`to` are inclusive `YYYY-MM-DD` local-date bounds; `query` searches the ration's
snapshotted recipe name and note. Results remain owner-scoped, keyset-paginated, and
bounded to 100 items per request.

`GET /rations/{rationId}` returns the immutable recipe name, yield, consumption, local
date/timezone, note, nutrient snapshot, and derived calories. `DELETE /rations/{rationId}`
soft-deletes the logged ration. Correction is deliberately delete-then-re-log; it does
not alter a ration snapshot in place.

`PUT /me/timezone` accepts an IANA timezone. It atomically updates the profile and
reclassifies all active ration `consumed_local_date` values from their immutable
instants in the new timezone. Nutrient snapshots and consumption instants do not change.
