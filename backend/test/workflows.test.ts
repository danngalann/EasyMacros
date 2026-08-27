import { describe, expect, it } from "vitest";

import { derivedRecipeYield, NutritionWorkflowService } from "../src/workflows.js";

describe("recipe previews", () => {
  it("derives final weight from all component grams with decimal-safe arithmetic", () => {
    expect(
      derivedRecipeYield({
        components: [
          { ingredientId: "one", quantityGrams: "100.1" },
          { ingredientId: "two", quantityGrams: "50.2" },
        ],
      }),
    ).toBe("150.300000");
  });

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
      components: [{ ingredientId: "ingredient-1", quantityGrams: "150" }],
    });

    expect(preview).toEqual({
      name: "Rice bowl",
      yieldGrams: "150.000000",
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
        expectedVersion: 1,
        components: [{ ingredientId: "ingredient-1", quantityGrams: "150" }],
      }),
    ).rejects.toMatchObject({ code: "recipe_conflict", statusCode: 409 });
  });
});

describe("ration history", () => {
  it("defaults an omitted ration weight to the full recipe yield", async () => {
    const insertedRationValues: unknown[][] = [];
    const database = {
      transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
        operation(database as never),
      query: (sql: string, values?: unknown[]) => {
        if (sql.includes("FROM recipes r JOIN recipe_versions")) {
          return Promise.resolve({
            rows: [
              {
                id: "version-1",
                recipeId: "recipe-1",
                name: "Rice bowl",
                yieldGrams: "300.000000",
                notes: null,
                versionNumber: 1,
              },
            ],
          });
        }
        if (sql.includes("FROM recipe_version_nutrients")) {
          return Promise.resolve({ rows: [{ code: "protein", amount: "21.000000" }] });
        }
        if (sql.includes("INSERT INTO rations")) insertedRationValues.push(values ?? []);
        return Promise.resolve({ rows: [] });
      },
    };
    const service = new NutritionWorkflowService(database as never);

    const preview = await service.previewRation("user-1", { recipeId: "recipe-1" });
    const created = await service.createRation("user-1", "UTC", {
      recipeId: "recipe-1",
      consumedAt: "2026-08-27T07:00:00.000Z",
    });

    expect(preview).toMatchObject({
      consumedGrams: "300.000000",
      nutrients: [{ code: "protein", amount: "21.000000" }],
    });
    expect(created.nutrients).toEqual([{ code: "protein", amount: "21.000000" }]);
    expect(insertedRationValues[0]?.[3]).toBe("300.000000");
  });

  it("scopes deletion to the owner and rejects a missing active ration", async () => {
    const queries: Array<{ sql: string; values: unknown[] | undefined }> = [];
    const database = {
      query: (sql: string, values?: unknown[]) => {
        queries.push({ sql, values });
        return Promise.resolve({ rows: [] });
      },
    };
    const service = new NutritionWorkflowService(database as never);

    await expect(service.deleteRation("user-1", "ration-1")).rejects.toMatchObject({
      code: "ration_not_found",
      statusCode: 404,
    });
    expect(queries[0]).toMatchObject({ values: ["ration-1", "user-1"] });
    expect(queries[0]?.sql).toContain("user_id = $2");
  });

  it("reclassifies active ration dates when the timezone changes", async () => {
    const queries: Array<{ sql: string; values: unknown[] | undefined }> = [];
    const database = {
      transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
        operation(database as never),
      query: (sql: string, values?: unknown[]) => {
        queries.push({ sql, values });
        return Promise.resolve({ rows: [{}] });
      },
    };
    const service = new NutritionWorkflowService(database as never);

    await expect(service.updateTimezone("user-1", "America/New_York")).resolves.toEqual({
      timezone: "America/New_York",
    });
    expect(queries[1]?.sql).toContain("consumed_at AT TIME ZONE $2");
    expect(queries[1]).toMatchObject({ values: ["user-1", "America/New_York"] });
  });
});
