import { randomUUID } from "node:crypto";

import type { Database } from "./database.js";
import type { NutrientAmount } from "./nutrition.js";

interface CandidateRow {
  barcode: string | null;
  brand: string | null;
  confidence: string | null;
  id: string;
  nutrients: NutrientAmount[];
  provenance: Record<string, unknown>;
  source: "manual" | "barcode" | "ai";
  status: "ready" | "confirmed";
  suggested_name: string;
  version: string;
  warnings: string[];
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
        `SELECT id, name, brand, barcode, hidden, source, confidence::text, warnings,
                provenance, created_at AS "createdAt", updated_at AS "updatedAt"
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
        `SELECT id, name, brand, barcode, hidden, source, confidence::text, warnings,
                provenance, created_at AS "createdAt", updated_at AS "updatedAt"
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
      `INSERT INTO nutrient_candidates
         (id, user_id, source, suggested_name, brand, nutrients, version, provenance)
       VALUES ($1, $2, 'manual', $3, $4, $5::jsonb, $6, $7::jsonb)`,
      [
        candidate.id,
        userId,
        candidate.name.trim(),
        candidate.brand?.trim() || null,
        JSON.stringify(candidate.nutrients),
        candidate.version,
        JSON.stringify({ provider: "user" }),
      ],
    );
    return {
      id: candidate.id,
      status: "ready" as const,
      source: "manual" as const,
      suggestedName: candidate.name.trim(),
      brand: candidate.brand?.trim() || null,
      barcode: null,
      basisQuantity: 100,
      nutrients: candidate.nutrients,
      confidence: null,
      warnings: [],
      provenance: { provider: "user" },
      version: candidate.version,
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    };
  }

  async confirmCandidate(
    userId: string,
    candidateId: string,
    input: {
      version: string;
      name: string;
      brand?: string;
      barcode?: string;
      nutrients: NutrientAmount[];
    },
  ) {
    validateIngredientInput(input);
    return this.database.transaction(async (transaction) => {
      const candidate = (
        await transaction.query<CandidateRow>(
          `SELECT id, suggested_name, brand, barcode, nutrients, version, status, source,
                  confidence::text, warnings, provenance
           FROM nutrient_candidates
           WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL
             AND expires_at > CURRENT_TIMESTAMP
           FOR UPDATE`,
          [candidateId, userId],
        )
      ).rows[0];
      if (!candidate || candidate.status !== "ready" || candidate.version !== input.version) {
        throw new IngredientError("candidate_conflict", 409);
      }

      const ingredientId = randomUUID();
      const reviewed = {
        name: input.name.trim(),
        brand: input.brand?.trim() || null,
        barcode: input.barcode?.trim() || null,
        nutrients: input.nutrients,
      };
      await transaction.query(
        `INSERT INTO ingredients
           (id, user_id, name, brand, barcode, source, confidence, warnings, provenance)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9::jsonb)`,
        [
          ingredientId,
          userId,
          reviewed.name,
          reviewed.brand,
          reviewed.barcode,
          candidate.source,
          candidate.confidence,
          JSON.stringify(candidate.warnings),
          JSON.stringify(candidate.provenance),
        ],
      );
      for (const nutrient of reviewed.nutrients) {
        await transaction.query(
          `INSERT INTO ingredient_nutrients (ingredient_id, nutrient_code, amount_per_100g)
           VALUES ($1, $2, $3)`,
          [ingredientId, nutrient.code, nutrient.amount],
        );
      }
      await transaction.query(
        `INSERT INTO ingredient_revisions (id, ingredient_id, snapshot)
         VALUES ($1, $2, $3::jsonb)`,
        [
          randomUUID(),
          ingredientId,
          JSON.stringify({
            reviewed,
            source: candidate.source,
            confidence: candidate.confidence,
            warnings: candidate.warnings,
            provenance: candidate.provenance,
          }),
        ],
      );
      await transaction.query(
        `UPDATE nutrient_candidates SET status = 'confirmed', confirmed_ingredient_id = $3,
         updated_at = CURRENT_TIMESTAMP WHERE id = $1 AND user_id = $2`,
        [candidateId, userId, ingredientId],
      );
      return this.detailWith(transaction, userId, ingredientId);
    });
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

  private async detailWith(
    database: Pick<Database, "query">,
    userId: string,
    ingredientId: string,
  ) {
    const ingredient = (
      await database.query(
        `SELECT id, name, brand, barcode, hidden, source, confidence::text, warnings,
                provenance, created_at AS "createdAt", updated_at AS "updatedAt"
         FROM ingredients WHERE id = $1 AND user_id = $2`,
        [ingredientId, userId],
      )
    ).rows[0];
    if (!ingredient) return undefined;
    const nutrients = (
      await database.query(
        `SELECT nutrient_code AS code, amount_per_100g::text AS amount
         FROM ingredient_nutrients WHERE ingredient_id = $1 ORDER BY nutrient_code`,
        [ingredientId],
      )
    ).rows;
    return { ...ingredient, nutrients };
  }
}

export class IngredientError extends Error {
  constructor(
    readonly code:
      | "ai_provider_unavailable"
      | "ai_rate_limited"
      | "candidate_conflict"
      | "candidate_not_found"
      | "ingredient_not_found"
      | "invalid_barcode"
      | "invalid_image"
      | "invalid_ingredient"
      | "invalid_prompt"
      | "malformed_ai_response",
    readonly statusCode: 400 | 404 | 409 | 429 | 502 | 503,
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
