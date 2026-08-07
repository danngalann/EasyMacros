# Deployment

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Production deployment is not available in Phase 1. `docker-compose.dev.yml` is only a
local PostgreSQL dependency; it must not be used as a production configuration.

Phase 10 will document a non-root API container, PostgreSQL networking, migrations,
backup/restore, TLS reverse proxy requirements, secret injection, and the single API
replica limitation for temporary AI image storage.
