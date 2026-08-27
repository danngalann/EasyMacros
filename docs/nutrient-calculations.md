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
ration amount = recipe total * resolved consumed grams / recipe yield grams
```

For the current product semantics, `recipe yield = sum(component grams)`. This keeps
ration scaling deterministic without a second user-entered weight. Modeling cooking
water loss or gain would require a future explicit product decision.
When ration weight is omitted, `resolved consumed grams = recipe yield grams`, so the
whole dish is logged. An explicit positive ration weight scales the snapshot for a
portion, such as one serving from a meal-prepped batch.

Values are rounded half-up to six decimal places only when serialized. Recipe yield,
component weights, explicit consumed weights, and goal amounts must be positive; nutrient
amounts must be finite and non-negative. Calories are derived from the ration snapshot
using `protein_g * 4 + carbohydrates_g * 4 + fat_g * 9`; label energy is not used.

Open Food Facts imports only as-sold `_100g` values. Missing keys remain absent. Provider
sodium is converted from grams to the catalog's milligrams; when sodium is absent but
salt is present, the review candidate may derive `sodium_mg = salt_g / 2.5 * 1000` and
marks the value as derived. Confirmation still persists the user's reviewed decimal.
AI output is normalized from its positive `basisGrams` with
`per100 = reportedAmount * 100 / basisGrams` before review.

Phase 5 persists recipe and ration calculations as immutable snapshots inside the same
transaction as their parent record. The dashboard sums persisted ration nutrients and
then derives calories from that sum, rather than recalculating from mutable ingredients
or recipes.
