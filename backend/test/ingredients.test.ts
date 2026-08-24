import { describe, expect, it } from "vitest";

import { IngredientService } from "../src/ingredients.js";

describe("candidate confirmation", () => {
  it("persists only explicitly reviewed values and external provenance in one transaction", async () => {
    const calls: Array<{ sql: string; values?: readonly unknown[] }> = [];
    const database = {
      transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
        operation(database as never),
      query: (sql: string, values?: readonly unknown[]) => {
        calls.push({ sql, values });
        if (sql.includes("FROM nutrient_candidates")) {
          return Promise.resolve({
            rows: [
              {
                id: "candidate-1",
                suggested_name: "Provider name",
                brand: "Provider brand",
                barcode: "4006381333931",
                nutrients: [{ code: "protein", amount: "1" }],
                version: "version-1",
                status: "ready",
                source: "barcode",
                confidence: "0.800",
                warnings: ["Community supplied"],
                provenance: { provider: "Open Food Facts", productId: "4006381333931" },
              },
            ],
          });
        }
        if (sql.includes("FROM ingredients WHERE")) {
          return Promise.resolve({
            rows: [{ id: values?.[0], name: "Reviewed name", source: "barcode" }],
          });
        }
        if (sql.includes("FROM ingredient_nutrients")) {
          return Promise.resolve({ rows: [{ code: "protein", amount: "12" }] });
        }
        return Promise.resolve({ rows: [{}] });
      },
    };
    const service = new IngredientService(database as never);

    const result = await service.confirmCandidate("user-1", "candidate-1", {
      version: "version-1",
      name: "Reviewed name",
      brand: "Reviewed brand",
      barcode: "4006381333931",
      nutrients: [
        { code: "protein", amount: "12" },
        { code: "carbohydrates", amount: "34" },
        { code: "fat", amount: "5" },
      ],
    });

    expect(result).toMatchObject({ name: "Reviewed name", source: "barcode" });
    const ingredientInsert = calls.find((call) => call.sql.includes("INSERT INTO ingredients"));
    expect(ingredientInsert?.values).toContain("Reviewed name");
    expect(ingredientInsert?.values).toContain("barcode");
    expect(ingredientInsert?.values).toContain(
      JSON.stringify({ provider: "Open Food Facts", productId: "4006381333931" }),
    );
    expect(
      calls.filter((call) => call.sql.includes("INSERT INTO ingredient_nutrients")),
    ).toHaveLength(3);
  });

  it("creates no ingredient before manual candidate confirmation", async () => {
    const calls: string[] = [];
    const database = {
      query: (sql: string) => {
        calls.push(sql);
        return Promise.resolve({ rows: [] });
      },
    };
    const service = new IngredientService(database as never);

    await service.createManualCandidate("user-1", {
      name: "Draft",
      nutrients: [
        { code: "protein", amount: "1" },
        { code: "carbohydrates", amount: "2" },
        { code: "fat", amount: "3" },
      ],
    });

    expect(calls.some((sql) => sql.includes("INSERT INTO ingredients ("))).toBe(false);
    expect(calls.some((sql) => sql.includes("INSERT INTO nutrient_candidates"))).toBe(true);
  });

  it("returns a conflict for a cross-user or already-confirmed candidate", async () => {
    const database = {
      transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
        operation(database as never),
      query: () => Promise.resolve({ rows: [] }),
    };
    const service = new IngredientService(database as never);

    await expect(
      service.confirmCandidate("other-user", "candidate-1", {
        version: "version-1",
        name: "Draft",
        nutrients: [
          { code: "protein", amount: "1" },
          { code: "carbohydrates", amount: "2" },
          { code: "fat", amount: "3" },
        ],
      }),
    ).rejects.toMatchObject({ code: "candidate_conflict", statusCode: 409 });
  });
});
