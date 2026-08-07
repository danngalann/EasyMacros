import { randomUUID } from "node:crypto";

import type { Database } from "./database.js";
import type { NutrientAmount } from "./nutrition.js";

interface CandidateRow {
  brand: string | null;
  id: string;
  nutrients: NutrientAmount[];
  status: "ready" | "confirmed";
  suggested_name: string;
  version: string;
}

export class IngredientService {
  constructor(private readonly database: Database) {}

  async listNutrients() {
    return (
      await this.database.query(
        `SELECT code, display_name AS "displayName", category, unit,
                display_order AS "displayOrder", goal_allowed AS "goalAllowed"
         FROM nutrient_definitions ORDER BY display_order, code`,
      )
    ).rows;
  }

  async list(userId: string, query: string | undefined, hidden: boolean) {
    return (
      await this.database.query(
        `SELECT id, name, brand, barcode, hidden, source, created_at AS "createdAt", updated_at AS "updatedAt"
         FROM ingredients
         WHERE user_id = $1 AND hidden = $2 AND ($3 = '' OR name ILIKE '%' || $3 || '%')
         ORDER BY name, id LIMIT 50`,
        [userId, hidden, query?.trim() ?? ""],
      )
    ).rows;
  }

  async detail(userId: string, ingredientId: string) {
    const ingredient = (
      await this.database.query(
        `SELECT id, name, brand, barcode, hidden, source, created_at AS "createdAt", updated_at AS "updatedAt"
         FROM ingredients WHERE id = $1 AND user_id = $2`,
        [ingredientId, userId],
      )
    ).rows[0];
    if (!ingredient) {
      return undefined;
    }
    const nutrients = (
      await this.database.query(
        `SELECT nutrient_code AS code, amount_per_100g::text AS amount
         FROM ingredient_nutrients WHERE ingredient_id = $1 ORDER BY nutrient_code`,
        [ingredientId],
      )
    ).rows;
    return { ...ingredient, nutrients };
  }

  async createManualCandidate(
    userId: string,
    input: { brand?: string; name: string; nutrients: NutrientAmount[] },
  ) {
    validateIngredientInput(input);
    const candidate = {
      id: randomUUID(),
      version: randomUUID(),
      ...input,
    };
    await this.database.query(
      `INSERT INTO nutrient_candidates (id, user_id, source, suggested_name, brand, nutrients, version)
       VALUES ($1, $2, 'manual', $3, $4, $5::jsonb, $6)`,
      [
        candidate.id,
        userId,
        candidate.name.trim(),
        candidate.brand?.trim() || null,
        JSON.stringify(candidate.nutrients),
        candidate.version,
      ],
    );
    return { ...candidate, status: "ready" as const };
  }

  async confirmCandidate(userId: string, candidateId: string, version: string) {
    const candidate = (
      await this.database.query<CandidateRow>(
        `SELECT id, suggested_name, brand, nutrients, version, status
         FROM nutrient_candidates WHERE id = $1 AND user_id = $2`,
        [candidateId, userId],
      )
    ).rows[0];
    if (!candidate || candidate.status !== "ready" || candidate.version !== version) {
      throw new IngredientError("candidate_conflict", 409);
    }

    const ingredientId = randomUUID();
    await this.database.query(
      `INSERT INTO ingredients (id, user_id, name, brand, source)
       VALUES ($1, $2, $3, $4, 'manual')`,
      [ingredientId, userId, candidate.suggested_name, candidate.brand],
    );
    for (const nutrient of candidate.nutrients) {
      await this.database.query(
        `INSERT INTO ingredient_nutrients (ingredient_id, nutrient_code, amount_per_100g)
         VALUES ($1, $2, $3)`,
        [ingredientId, nutrient.code, nutrient.amount],
      );
    }
    await this.database.query(
      `INSERT INTO ingredient_revisions (id, ingredient_id, snapshot)
       VALUES ($1, $2, $3::jsonb)`,
      [randomUUID(), ingredientId, JSON.stringify(candidate)],
    );
    const updated = await this.database.query(
      `UPDATE nutrient_candidates SET status = 'confirmed', confirmed_ingredient_id = $3,
       updated_at = CURRENT_TIMESTAMP WHERE id = $1 AND user_id = $2 AND status = 'ready' RETURNING id`,
      [candidateId, userId, ingredientId],
    );
    if (updated.rows.length === 0) {
      throw new IngredientError("candidate_conflict", 409);
    }
    return this.detail(userId, ingredientId);
  }

  async update(userId: string, ingredientId: string, input: { brand?: string; name?: string }) {
    if (input.name !== undefined && !input.name.trim()) {
      throw new IngredientError("invalid_ingredient", 400);
    }
    const result = await this.database.query(
      `UPDATE ingredients SET name = COALESCE($3, name), brand = COALESCE($4, brand), updated_at = CURRENT_TIMESTAMP
       WHERE id = $1 AND user_id = $2 RETURNING id`,
      [ingredientId, userId, input.name?.trim() || null, input.brand?.trim() || null],
    );
    if (!result.rows[0]) {
      throw new IngredientError("ingredient_not_found", 404);
    }
    return this.detail(userId, ingredientId);
  }

  async setVisibility(userId: string, ingredientId: string, hidden: boolean) {
    const result = await this.database.query(
      `UPDATE ingredients SET hidden = $3, updated_at = CURRENT_TIMESTAMP
       WHERE id = $1 AND user_id = $2 RETURNING id`,
      [ingredientId, userId, hidden],
    );
    if (!result.rows[0]) {
      throw new IngredientError("ingredient_not_found", 404);
    }
    return this.detail(userId, ingredientId);
  }
}

export class IngredientError extends Error {
  constructor(
    readonly code: "candidate_conflict" | "ingredient_not_found" | "invalid_ingredient",
    readonly statusCode: 400 | 404 | 409,
  ) {
    super(code);
  }
}

function validateIngredientInput(input: { name: string; nutrients: NutrientAmount[] }): void {
  if (!input.name?.trim() || input.name.trim().length > 200 || !Array.isArray(input.nutrients)) {
    throw new IngredientError("invalid_ingredient", 400);
  }
  const codes = new Set<string>();
  for (const nutrient of input.nutrients) {
    if (!nutrient.code || codes.has(nutrient.code) || !/^\d+(\.\d+)?$/.test(nutrient.amount)) {
      throw new IngredientError("invalid_ingredient", 400);
    }
    codes.add(nutrient.code);
  }
  for (const code of ["protein", "carbohydrates", "fat"]) {
    if (!codes.has(code)) {
      throw new IngredientError("invalid_ingredient", 400);
    }
  }
}
