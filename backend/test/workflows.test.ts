import { describe, expect, it } from "vitest";

import { NutritionWorkflowService } from "../src/workflows.js";

describe("recipe previews", () => {
  it("calculates owner-scoped ingredient totals without creating a recipe", async () => {
    const queries: string[] = [];
    const database = {
      query: (sql: string) => {
        queries.push(sql);
        return Promise.resolve({
          rows: [
            {
              id: "ingredient-1",
              name: "Rice",
              nutrients: [
                { code: "carbohydrates", amount: "80" },
                { code: "protein", amount: "7" },
              ],
            },
          ],
        });
      },
    };
    const service = new NutritionWorkflowService(database as never);

    const preview = await service.previewRecipe("user-1", {
      name: "Rice bowl",
      yieldGrams: "300",
      components: [{ ingredientId: "ingredient-1", quantityGrams: "150" }],
    });

    expect(preview).toEqual({
      name: "Rice bowl",
      yieldGrams: "300",
      nutrients: [
        { code: "carbohydrates", amount: "120.000000" },
        { code: "protein", amount: "10.500000" },
      ],
      calories: "522.000000",
    });
    expect(queries.some((sql) => sql.includes("INSERT INTO recipes"))).toBe(false);
  });
});

describe("recipe updates", () => {
  it("rejects a stale immutable version before changing a recipe", async () => {
    const database = {
      transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
        operation(database as never),
      query: () => Promise.resolve({ rows: [{ id: "recipe-1", versionNumber: 2 }] }),
    };
    const service = new NutritionWorkflowService(database as never);

    await expect(
      service.updateRecipe("user-1", "recipe-1", {
        name: "Rice bowl",
        yieldGrams: "300",
        expectedVersion: 1,
        components: [{ ingredientId: "ingredient-1", quantityGrams: "150" }],
      }),
    ).rejects.toMatchObject({ code: "recipe_conflict", statusCode: 409 });
  });
});
