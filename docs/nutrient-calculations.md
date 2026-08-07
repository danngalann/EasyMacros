# Nutrient Calculations

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Phase 1 implements no nutrient calculations. Future authoritative calculations will use
decimal values in the backend and database, never floating-point values as persisted
totals. Formulae and rounding rules will be added with their implementation and tests.
