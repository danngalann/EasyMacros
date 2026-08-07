# Nutrient Calculations

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

Phase 4 calculates all persisted nutrient values with PostgreSQL `numeric(14,6)` and
`decimal.js` in the backend. Floating point is never used for authoritative totals.

Ingredient nutrients are stored per 100 g. The backend calculates recipe and ration
snapshots without intermediate display rounding:

```text
component amount = amount per 100 g * component grams / 100
recipe total = sum(component amounts)
ration amount = recipe total * consumed grams / recipe yield grams
```

Values are rounded half-up to six decimal places only when serialized. Recipe yield,
component weights, consumed weights, and goal amounts must be positive; nutrient
amounts must be finite and non-negative. Calories are derived from the ration snapshot
using `protein_g * 4 + carbohydrates_g * 4 + fat_g * 9`; label energy is not used.

Phase 5 persists recipe and ration calculations as immutable snapshots inside the same
transaction as their parent record. The dashboard sums persisted ration nutrients and
then derives calories from that sum, rather than recalculating from mutable ingredients
or recipes.
