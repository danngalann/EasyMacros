import { describe, expect, it, vi } from "vitest";

import { loadConfig } from "../src/config.js";
import { parseRationAiOutput, RationAiService } from "../src/ration-ai.js";

const output = {
  name: "Restaurant curry with rice",
  estimatedWeightGrams: 620,
  nutrients: [
    { code: "protein", amount: 28 },
    { code: "carbohydrates", amount: 94 },
    { code: "fat", amount: 31 },
  ],
  confidence: 0.62,
  warnings: ["Hidden cooking oil may change the fat estimate."],
};

describe("ration AI boundary", () => {
  it("requires complete whole-ration macros and rejects unsafe values", () => {
    expect(parseRationAiOutput(output)).toEqual(output);
    expect(() =>
      parseRationAiOutput({
        ...output,
        nutrients: output.nutrients.filter((nutrient) => nutrient.code !== "fat"),
      }),
    ).toThrowError("malformed_ai_response");
    expect(() =>
      parseRationAiOutput({
        ...output,
        nutrients: [{ code: "medical_advice", amount: 1 }],
      }),
    ).toThrowError("malformed_ai_response");
  });

  it("uses the advanced model, strict structured output, and no tools", async () => {
    const database = fakeDatabase();
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ choices: [{ message: { content: JSON.stringify(output) } }] }),
          { status: 200 },
        ),
      );
    const service = new RationAiService(
      database as never,
      loadConfig({
        DATABASE_URL: "postgresql://example.test/easymacros",
        JWT_SECRET: "a-synthetic-secret-that-is-long-enough",
        OPENROUTER_API_KEY: "synthetic",
        OPENROUTER_MODEL: "standard-model",
        OPENROUTER_ADVANCED_MODEL: "advanced-model",
      }),
      fetcher,
    );
    const image = Buffer.alloc(24);
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]).copy(image);
    image.writeUInt32BE(10, 16);
    image.writeUInt32BE(20, 20);

    const result = await service.estimate("user-1", "127.0.0.1", {
      images: [{ buffer: image, mime: "image/png" }],
      description: "Curry, rice, and sauce",
    });

    const requestBody = fetcher.mock.calls[0]?.[1]?.body;
    if (typeof requestBody !== "string") throw new Error("expected JSON request body");
    const request = JSON.parse(requestBody) as {
      model: string;
      tools: unknown[];
      response_format: { json_schema: { strict: boolean } };
    };
    expect(request.model).toBe("advanced-model");
    expect(request.tools).toEqual([]);
    expect(request.response_format.json_schema.strict).toBe(true);
    expect(result).toMatchObject({
      suggestedName: output.name,
      estimatedWeightGrams: "620.000000",
      confidence: "0.620000",
    });
  });

  it("confirms reviewed totals exactly once with owner-scoped lookup", async () => {
    const calls: Array<{ sql: string; values?: readonly unknown[] }> = [];
    const database = {
      ...fakeDatabase(),
      transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
        operation(database as never),
      query: (sql: string, values?: readonly unknown[]) => {
        calls.push({ sql, values });
        if (sql.includes("FROM ration_ai_estimates") && sql.includes("FOR UPDATE")) {
          return Promise.resolve({
            rows: [
              {
                confidence: "0.62",
                warnings: ["AI estimate"],
                provenance: { provider: "OpenRouter", model: "advanced-model" },
              },
            ],
          });
        }
        return Promise.resolve({ rows: [] });
      },
    };
    const service = new RationAiService(
      database as never,
      loadConfig({
        DATABASE_URL: "postgresql://example.test/easymacros",
        JWT_SECRET: "a-synthetic-secret-that-is-long-enough",
      }),
    );

    const result = await service.confirm("user-1", "UTC", "estimate-1", {
      version: "version-1",
      name: "Reviewed curry",
      estimatedWeightGrams: "600",
      consumedAt: "2026-08-27T12:00:00.000Z",
      nutrients: [
        { code: "protein", amount: "30" },
        { code: "carbohydrates", amount: "90" },
        { code: "fat", amount: "25" },
      ],
    });

    expect(calls[0]?.values).toEqual(["estimate-1", "user-1", "version-1"]);
    expect(calls[0]?.sql).toContain("user_id = $2");
    expect(
      calls.some((call) => call.sql.includes("recipe_version_id") && call.sql.includes("NULL")),
    ).toBe(true);
    expect(result.calories).toBe("705.000000");
  });
});

function fakeDatabase() {
  return {
    close: () => Promise.resolve(),
    isReady: () => Promise.resolve(true),
    transaction: async <T>(operation: (transaction: never) => Promise<T>) =>
      operation({ query: () => Promise.resolve({ rows: [] }) } as never),
    query: (sql: string) =>
      Promise.resolve({
        rows: sql.includes("RETURNING request_count") ? [{ request_count: 1 }] : [],
      }),
  };
}
