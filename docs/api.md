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

External ingestion adds:

```http
POST   /ingredient-candidates/barcode
POST   /ingredient-candidates/ai
GET    /ingredient-candidates/{candidateId}
POST   /ingredient-candidates/{candidateId}/retry
DELETE /ingredient-candidates/{candidateId}
```

Barcode creation accepts `{ "barcode": "..." }`. The server validates UPC-A/EAN-13,
queries Open Food Facts, and returns a shared candidate. Not-found, throttled, malformed,
and unavailable outcomes remain candidates with safe error codes so the app can retain
the barcode and offer AI/manual fallback. Incomplete products remain editable and keep
missing nutrients absent rather than converting them to zero.

AI creation is multipart with one to four repeated `images` parts, optional
`description`, `mode` (`auto`, `label`, or `generic`), and an idempotency key. Front,
back, and nutrition-panel images are analyzed together. It returns `202`; clients poll the
candidate until `ready` or `failed`, may retry within the bounded attempt count, and
cancel with `DELETE`. Candidate responses share `source`, editable identity and
nutrients, confidence, warnings, provenance, version, and expiry.

Confirmation now requires the complete reviewed `{version,name,brand,barcode,nutrients}`
body. The server persists those reviewed values together with source, confidence,
warnings, and provider provenance exactly once. Provider payloads never create an
ingredient automatically. The generated contract is committed at
`backend/openapi.json`.

Recipe create, update, and preview requests no longer accept `yieldGrams`. The backend
derives and snapshots final recipe weight as the decimal-safe sum of component
`quantityGrams`; responses continue returning `yieldGrams` for ration scaling and display.

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

`POST /rations` requires a current active `recipeId` and an ISO-8601 `consumedAt`;
`note` and positive decimal `consumedGrams` are optional. When `consumedGrams` is
omitted, the API uses the current recipe yield and logs the whole dish. The API reads the authenticated user's
IANA timezone, derives the ration's local date, and persists recipe/name/yield/nutrient
snapshots atomically. Ration history is sorted by local date, instant, then ID in
descending order. `limit` is bounded to 1-100 (default 50); use the opaque
`nextCursor` response value for the following page.

`PUT /goals` requires `effectiveLocalDate` and one or more unique, positive nutrient
targets. Only nutrient definitions marked `goalAllowed` may be targeted. A goal replaces
the profile for that effective local date. `GET /goals` selects the most recent profile
effective on or before the requested date; it returns `404` when none exists.

`GET /dashboard` returns the day-local summed ration nutrients, authoritative derived
calories, and the effective goal profile. Goal responses include authoritative target
calories derived from their protein, carbohydrate, and fat targets with the canonical
formula. Android displays this value rather than calculating it independently. All
mutable-resource reads and writes are scoped to the authenticated user in their database
query.

## Tracking and profile settings

Phase 9 adds Android-facing ration management and profile timezone endpoints:

```http
POST   /rations/preview
POST   /ration-ai-estimates
POST   /ration-ai-estimates/{estimateId}/confirm
GET    /rations/{rationId}
DELETE /rations/{rationId}
PUT    /me/timezone
```

`POST /rations/preview` accepts an active `recipeId` and optional positive decimal
`consumedGrams`. Omission previews the whole dish using the current recipe yield. It
returns the resolved `consumedGrams`, proportional nutrients, and calories without persisting a ration. Clients must use this returned value for the
contribution review; only `POST /rations` creates the immutable snapshot.

`POST /ration-ai-estimates` is protected multipart input with one to four repeated
`images` files and a required `description` (maximum 1,000 characters). The server
validates every image, consumes the shared per-user/IP AI quota, and sends all views
together to `OPENROUTER_ADVANCED_MODEL`. Strict structured output returns a whole-ration
name, estimated edible weight, total nutrients, confidence, and warnings. These are
editable review data; no ration is logged yet, and images are never stored.

`POST /ration-ai-estimates/{estimateId}/confirm` requires the estimate `version`, reviewed
`name`, positive `estimatedWeightGrams`, ISO-8601 `consumedAt`, optional note, and unique
non-negative nutrient totals including protein, carbohydrates, and fat. It owner-scopes
and locks the unexpired estimate, creates one immutable `source: "ai"` ration snapshot,
and marks the estimate confirmed in the same transaction. A repeated or stale
confirmation returns `409`. Recipe ration behavior is unchanged.

`GET /rations` also accepts optional `from`, `to`, and `query` parameters. `from` and
`to` are inclusive `YYYY-MM-DD` local-date bounds; `query` searches the ration's
snapshotted recipe name and note. Results remain owner-scoped, keyset-paginated, and
bounded to 100 items per request.

`GET /rations/{rationId}` returns the immutable recipe name, yield, consumption, local
date/timezone, note, nutrient snapshot, and derived calories. `DELETE /rations/{rationId}`
soft-deletes the logged ration. Correction is deliberately delete-then-re-log; it does
not alter a ration snapshot in place.

Ration list/detail responses identify `source` as `recipe` or `ai`. AI details also
return confidence and warnings so clients label estimated values explicitly.

`PUT /me/timezone` accepts an IANA timezone. It atomically updates the profile and
reclassifies all active ration `consumed_local_date` values from their immutable
instants in the new timezone. Nutrient snapshots and consumption instants do not change.
