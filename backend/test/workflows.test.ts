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

describe("ration history", () => {
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
