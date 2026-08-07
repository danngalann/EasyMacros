import { Decimal } from "decimal.js";

export interface NutrientAmount {
  amount: string;
  code: string;
}

export interface RecipeComponent {
  nutrients: NutrientAmount[];
  quantityGrams: string;
}

function decimal(value: string, label: string): Decimal {
  const parsed = new Decimal(value);
  if (!parsed.isFinite() || parsed.isNegative()) {
    throw new Error(`${label} must be a finite non-negative decimal`);
  }
  return parsed;
}

function serialize(value: Decimal): string {
  return value.toDecimalPlaces(6, Decimal.ROUND_HALF_UP).toFixed(6);
}

export function calculateRecipeNutrients(components: RecipeComponent[]): NutrientAmount[] {
  if (components.length === 0) {
    throw new Error("a recipe requires at least one component");
  }

  const totals = new Map<string, Decimal>();
  for (const component of components) {
    const quantity = decimal(component.quantityGrams, "component quantity");
    if (quantity.isZero()) {
      throw new Error("component quantity must be greater than zero");
    }
    for (const nutrient of component.nutrients) {
      const scaled = decimal(nutrient.amount, `nutrient ${nutrient.code}`).mul(quantity).div(100);
      totals.set(nutrient.code, (totals.get(nutrient.code) ?? new Decimal(0)).add(scaled));
    }
  }

  return [...totals.entries()]
    .map(([code, amount]) => ({ code, amount: serialize(amount) }))
    .sort((left, right) => left.code.localeCompare(right.code));
}

export function calculateRationNutrients(
  recipeNutrients: NutrientAmount[],
  consumedGrams: string,
  yieldGrams: string,
): NutrientAmount[] {
  const consumed = decimal(consumedGrams, "consumed grams");
  const yieldAmount = decimal(yieldGrams, "recipe yield");
  if (consumed.isZero() || yieldAmount.isZero()) {
    throw new Error("consumed grams and recipe yield must be greater than zero");
  }
  return recipeNutrients.map((nutrient) => ({
    code: nutrient.code,
    amount: serialize(
      decimal(nutrient.amount, `nutrient ${nutrient.code}`).mul(consumed).div(yieldAmount),
    ),
  }));
}

export function calculateCalories(nutrients: NutrientAmount[]): string {
  const amounts = new Map(
    nutrients.map((nutrient) => [nutrient.code, decimal(nutrient.amount, nutrient.code)]),
  );
  return serialize(
    (amounts.get("protein") ?? new Decimal(0))
      .mul(4)
      .add((amounts.get("carbohydrates") ?? new Decimal(0)).mul(4))
      .add((amounts.get("fat") ?? new Decimal(0)).mul(9)),
  );
}
