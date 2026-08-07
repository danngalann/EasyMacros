# Security

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

| Boundary             | Phase 1 control                                                  |
| -------------------- | ---------------------------------------------------------------- |
| Repository secrets   | `.env*`, keystores, and local Android configuration are ignored. |
| CI source history    | Gitleaks scans Git history in CI.                                |
| Development database | Docker binds PostgreSQL only to `127.0.0.1`.                     |
| Runtime secrets      | `.env.example` contains placeholders only.                       |

No application authentication or personal-data processing exists yet. Threat controls are
added to this table in the same change as each new boundary.
