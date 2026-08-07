# Domain Model

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Phase 3 implements username/password identity and sessions. `users` has a caller-
supplied UUID primary key, unique case-insensitive normalized username, Argon2id
password hash, IANA timezone, and creation/update timestamps. Usernames are NFKC
normalized, trimmed, lower-cased, limited to 3-64 ASCII letters/numbers/dots/hyphens/
underscores, and never accepted as a mutable ownership field.

`sessions` stores a user ID, refresh-token family, SHA-256 digest of the current opaque
refresh token, immediately previous digest/idempotency marker, bounded recovery time,
idle/fixed expiry, and revocation state. Raw refresh tokens are never persisted.
`login_attempts` stores a SHA-256 username/IP pair key, failure count, next permitted
time, and expiry for durable login backoff without recording a plaintext username.

The planned domain also includes nutrient definitions, ingredients, recipes, rations,
and effective-dated goals. Their tables, constraints, and ownership rules remain
deferred to Phase 4.
