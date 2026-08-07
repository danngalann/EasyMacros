import { describe, expect, it } from "vitest";

import {
  calculateCalories,
  calculateRationNutrients,
  calculateRecipeNutrients,
} from "../src/nutrition.js";

describe("nutrition calculations", () => {
  it("scales ingredient nutrients without intermediate rounding", () => {
    const total = calculateRecipeNutrients([
      {
        quantityGrams: "125.5",
        nutrients: [
          { code: "protein", amount: "12.345678" },
          { code: "fat", amount: "3.2" },
        ],
      },
      { quantityGrams: "50", nutrients: [{ code: "protein", amount: "10" }] },
    ]);

    expect(total).toEqual([
      { code: "fat", amount: "4.016000" },
      { code: "protein", amount: "20.493826" },
    ]);
  });

  it("calculates a ration and calories from the immutable recipe totals", () => {
    const ration = calculateRationNutrients(
      [
        { code: "protein", amount: "30" },
        { code: "carbohydrates", amount: "60" },
        { code: "fat", amount: "15" },
      ],
      "50",
      "300",
    );

    expect(ration).toEqual([
      { code: "protein", amount: "5.000000" },
      { code: "carbohydrates", amount: "10.000000" },
      { code: "fat", amount: "2.500000" },
    ]);
    expect(calculateCalories(ration)).toBe("82.500000");
  });

  it("rejects zero and negative quantities", () => {
    expect(() => calculateRecipeNutrients([])).toThrow("at least one");
    expect(() => calculateRationNutrients([{ code: "protein", amount: "1" }], "0", "100")).toThrow(
      "greater than zero",
    );
  });
});
