# Domain Model

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Phase 2 persists the `users` identity foundation. It has a caller-supplied UUID primary
key, a unique email address, and creation and update timestamps. Authentication and all
user-facing access to this table remain deferred.

The planned domain also includes nutrient definitions, ingredients, recipes, rations,
and effective-dated goals. Their tables, constraints, and ownership rules remain
deferred to Phase 4.
