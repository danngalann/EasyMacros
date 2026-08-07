import { randomUUID } from "node:crypto";

import type { QueryDatabase, Database } from "./database.js";
import {
  calculateCalories,
  calculateRationNutrients,
  calculateRecipeNutrients,
  type NutrientAmount,
} from "./nutrition.js";

export interface RecipeInput {
  components: Array<{ ingredientId: string; quantityGrams: string }>;
  name: string;
  notes?: string;
  yieldGrams: string;
}

interface IngredientSnapshot {
  id: string;
  name: string;
  nutrients: NutrientAmount[];
}

interface RecipeVersionRow {
  id: string;
  name: string;
  notes: string | null;
  recipeId: string;
  versionNumber: number;
  yieldGrams: string;
}

export class WorkflowError extends Error {
  constructor(
    readonly code:
      | "invalid_goal"
      | "invalid_ration"
      | "invalid_recipe"
      | "invalid_timezone"
      | "recipe_not_found",
    readonly statusCode: 400 | 404,
  ) {
    super(code);
  }
}

export class NutritionWorkflowService {
  constructor(private readonly database: Database) {}

  async createRecipe(userId: string, input: RecipeInput) {
    validateRecipe(input);
    return this.database.transaction(async (database) => {
      const recipeId = randomUUID();
      const versionId = randomUUID();
      const snapshot = await ingredientSnapshots(database, userId, input.components);
      const nutrients = calculateRecipeNutrients(
        input.components.map((component) => ({
          quantityGrams: component.quantityGrams,
          nutrients: snapshot.get(component.ingredientId)!.nutrients,
        })),
      );
      await database.query(
        `INSERT INTO recipes (id, user_id, current_version_id) VALUES ($1, $2, $3)`,
        [recipeId, userId, versionId],
      );
      await insertRecipeVersion(database, versionId, recipeId, 1, input, nutrients, snapshot);
      return recipeDetail(database, userId, recipeId);
    });
  }

  async updateRecipe(userId: string, recipeId: string, input: RecipeInput) {
    validateRecipe(input);
    return this.database.transaction(async (database) => {
      const recipe = (
        await database.query<{ id: string }>(
          `SELECT id FROM recipes WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL FOR UPDATE`,
          [recipeId, userId],
        )
      ).rows[0];
      if (!recipe) throw new WorkflowError("recipe_not_found", 404);
      const snapshot = await ingredientSnapshots(database, userId, input.components);
      const nutrients = calculateRecipeNutrients(
        input.components.map((component) => ({
          quantityGrams: component.quantityGrams,
          nutrients: snapshot.get(component.ingredientId)!.nutrients,
        })),
      );
      const version = await database.query<{ versionNumber: number }>(
        `SELECT COALESCE(MAX(version_number), 0) + 1 AS "versionNumber"
         FROM recipe_versions WHERE recipe_id = $1`,
        [recipeId],
      );
      const versionId = randomUUID();
      await insertRecipeVersion(
        database,
        versionId,
        recipeId,
        version.rows[0]!.versionNumber,
        input,
        nutrients,
        snapshot,
      );
      await database.query(
        `UPDATE recipes SET current_version_id = $3, updated_at = CURRENT_TIMESTAMP
         WHERE id = $1 AND user_id = $2`,
        [recipeId, userId, versionId],
      );
      return recipeDetail(database, userId, recipeId);
    });
  }

  async listRecipes(userId: string, query: string | undefined) {
    return (
      await this.database.query(
        `SELECT r.id, v.name, v.yield_grams::text AS "yieldGrams", v.version_number AS "versionNumber",
                r.updated_at AS "updatedAt"
         FROM recipes r JOIN recipe_versions v ON v.id = r.current_version_id
         WHERE r.user_id = $1 AND r.deleted_at IS NULL AND ($2 = '' OR v.name ILIKE '%' || $2 || '%')
         ORDER BY r.updated_at DESC, r.id DESC LIMIT 50`,
        [userId, query?.trim() ?? ""],
      )
    ).rows;
  }

  async detailRecipe(userId: string, recipeId: string) {
    return recipeDetail(this.database, userId, recipeId);
  }

  async deleteRecipe(userId: string, recipeId: string): Promise<void> {
    const result = await this.database.query(
      `UPDATE recipes SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
       WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL RETURNING id`,
      [recipeId, userId],
    );
    if (!result.rows[0]) throw new WorkflowError("recipe_not_found", 404);
  }

  async createRation(
    userId: string,
    timezone: string,
    input: { consumedAt: string; consumedGrams: string; note?: string; recipeId: string },
  ) {
    if (
      !positiveDecimal(input.consumedGrams) ||
      !input.recipeId ||
      !validInstant(input.consumedAt)
    ) {
      throw new WorkflowError("invalid_ration", 400);
    }
    const localDate = dateInTimezone(input.consumedAt, timezone);
    return this.database.transaction(async (database) => {
      const version = (
        await database.query<RecipeVersionRow>(
          `SELECT v.id, v.recipe_id AS "recipeId", v.name, v.yield_grams::text AS "yieldGrams",
                  v.notes, v.version_number AS "versionNumber"
           FROM recipes r JOIN recipe_versions v ON v.id = r.current_version_id
           WHERE r.id = $1 AND r.user_id = $2 AND r.deleted_at IS NULL`,
          [input.recipeId, userId],
        )
      ).rows[0];
      if (!version) throw new WorkflowError("recipe_not_found", 404);
      const nutrients = (
        await database.query<NutrientAmount>(
          `SELECT nutrient_code AS code, amount::text AS amount
           FROM recipe_version_nutrients WHERE recipe_version_id = $1 ORDER BY nutrient_code`,
          [version.id],
        )
      ).rows;
      const rationNutrients = calculateRationNutrients(
        nutrients,
        input.consumedGrams,
        version.yieldGrams,
      );
      const rationId = randomUUID();
      await database.query(
        `INSERT INTO rations (id, user_id, recipe_version_id, consumed_grams, consumed_at, consumed_local_date,
                              timezone, recipe_name, recipe_yield_grams, note)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
        [
          rationId,
          userId,
          version.id,
          input.consumedGrams,
          input.consumedAt,
          localDate,
          timezone,
          version.name,
          version.yieldGrams,
          input.note?.trim() || null,
        ],
      );
      await insertNutrients(database, "ration_nutrients", "ration_id", rationId, rationNutrients);
      return {
        id: rationId,
        consumedLocalDate: localDate,
        nutrients: rationNutrients,
        calories: calculateCalories(rationNutrients),
      };
    });
  }

  async listRations(userId: string, input: { cursor?: string; date?: string; limit?: number }) {
    const requestedLimit = input.limit ?? 50;
    if (!Number.isInteger(requestedLimit) || requestedLimit < 1) {
      throw new WorkflowError("invalid_ration", 400);
    }
    const limit = Math.min(requestedLimit, 100);
    if (input.date && !dateOnly(input.date)) throw new WorkflowError("invalid_ration", 400);
    const cursor = input.cursor ? decodeCursor(input.cursor) : undefined;
    const rows = (
      await this.database.query<{
        consumedAt: string;
        consumedGrams: string;
        consumedLocalDate: string;
        id: string;
        note: string | null;
        recipeName: string;
      }>(
        `SELECT id, consumed_grams::text AS "consumedGrams", consumed_at AS "consumedAt",
                consumed_local_date::text AS "consumedLocalDate", recipe_name AS "recipeName", note
         FROM rations WHERE user_id = $1 AND deleted_at IS NULL
           AND ($2::date IS NULL OR consumed_local_date = $2::date)
           AND ($3::date IS NULL OR (consumed_local_date, consumed_at, id) < ($3::date, $4::timestamptz, $5::uuid))
         ORDER BY consumed_local_date DESC, consumed_at DESC, id DESC LIMIT $6`,
        [
          userId,
          input.date ?? null,
          cursor?.date ?? null,
          cursor?.instant ?? null,
          cursor?.id ?? null,
          limit + 1,
        ],
      )
    ).rows;
    const page = rows.slice(0, limit);
    const last = page.at(-1);
    return {
      rations: page,
      nextCursor:
        rows.length > limit && last
          ? encodeCursor(last.consumedLocalDate, last.consumedAt, last.id)
          : undefined,
    };
  }

  async setGoal(
    userId: string,
    input: { effectiveLocalDate: string; nutrients: NutrientAmount[] },
  ) {
    if (
      !dateOnly(input.effectiveLocalDate) ||
      !Array.isArray(input.nutrients) ||
      input.nutrients.length === 0 ||
      new Set(input.nutrients.map((nutrient) => nutrient.code)).size !== input.nutrients.length ||
      input.nutrients.some((nutrient) => !positiveDecimal(nutrient.amount))
    ) {
      throw new WorkflowError("invalid_goal", 400);
    }
    return this.database.transaction(async (database) => {
      const allowed = await database.query<{ code: string }>(
        `SELECT code FROM nutrient_definitions WHERE goal_allowed AND code = ANY($1::text[])`,
        [input.nutrients.map((nutrient) => nutrient.code)],
      );
      if (allowed.rows.length !== input.nutrients.length)
        throw new WorkflowError("invalid_goal", 400);
      const profileId = randomUUID();
      await database.query(
        `INSERT INTO goal_profiles (id, user_id, effective_local_date) VALUES ($1, $2, $3)
         ON CONFLICT (user_id, effective_local_date) DO UPDATE SET created_at = CURRENT_TIMESTAMP
         RETURNING id`,
        [profileId, userId, input.effectiveLocalDate],
      );
      const profile = (
        await database.query<{ id: string }>(
          `SELECT id FROM goal_profiles WHERE user_id = $1 AND effective_local_date = $2`,
          [userId, input.effectiveLocalDate],
        )
      ).rows[0]!;
      await database.query(`DELETE FROM goal_profile_nutrients WHERE goal_profile_id = $1`, [
        profile.id,
      ]);
      await insertNutrients(
        database,
        "goal_profile_nutrients",
        "goal_profile_id",
        profile.id,
        input.nutrients,
        "target_amount",
      );
      return goalForDate(database, userId, input.effectiveLocalDate);
    });
  }

  async goalForDate(userId: string, date: string) {
    if (!dateOnly(date)) throw new WorkflowError("invalid_goal", 400);
    return goalForDate(this.database, userId, date);
  }

  async dashboard(userId: string, date: string) {
    if (!dateOnly(date)) throw new WorkflowError("invalid_ration", 400);
    const nutrients = (
      await this.database.query<NutrientAmount>(
        `SELECT rn.nutrient_code AS code, SUM(rn.amount)::text AS amount
         FROM rations r JOIN ration_nutrients rn ON rn.ration_id = r.id
         WHERE r.user_id = $1 AND r.consumed_local_date = $2::date AND r.deleted_at IS NULL
         GROUP BY rn.nutrient_code ORDER BY rn.nutrient_code`,
        [userId, date],
      )
    ).rows;
    return {
      date,
      nutrients,
      calories: calculateCalories(nutrients),
      goal: await this.goalForDate(userId, date),
    };
  }
}

async function ingredientSnapshots(
  database: QueryDatabase,
  userId: string,
  components: RecipeInput["components"],
) {
  const ids = [...new Set(components.map((component) => component.ingredientId))];
  const rows = await database.query<IngredientSnapshot>(
    `SELECT i.id, i.name, COALESCE(jsonb_agg(jsonb_build_object('code', n.nutrient_code, 'amount', n.amount_per_100g::text)
             ORDER BY n.nutrient_code) FILTER (WHERE n.nutrient_code IS NOT NULL), '[]'::jsonb) AS nutrients
     FROM ingredients i LEFT JOIN ingredient_nutrients n ON n.ingredient_id = i.id
     WHERE i.user_id = $1 AND i.id = ANY($2::uuid[]) GROUP BY i.id, i.name`,
    [userId, ids],
  );
  if (rows.rows.length !== ids.length) throw new WorkflowError("invalid_recipe", 400);
  return new Map(rows.rows.map((row) => [row.id, row]));
}

async function insertRecipeVersion(
  database: QueryDatabase,
  versionId: string,
  recipeId: string,
  versionNumber: number,
  input: RecipeInput,
  nutrients: NutrientAmount[],
  ingredients: Map<string, IngredientSnapshot>,
) {
  await database.query(
    `INSERT INTO recipe_versions (id, recipe_id, version_number, name, yield_grams, notes)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [
      versionId,
      recipeId,
      versionNumber,
      input.name.trim(),
      input.yieldGrams,
      input.notes?.trim() || null,
    ],
  );
  for (const component of input.components) {
    const ingredient = ingredients.get(component.ingredientId)!;
    await database.query(
      `INSERT INTO recipe_components (id, recipe_version_id, ingredient_id, ingredient_name, quantity_grams, nutrient_snapshot)
       VALUES ($1, $2, $3, $4, $5, $6::jsonb)`,
      [
        randomUUID(),
        versionId,
        ingredient.id,
        ingredient.name,
        component.quantityGrams,
        JSON.stringify(ingredient.nutrients),
      ],
    );
  }
  await insertNutrients(
    database,
    "recipe_version_nutrients",
    "recipe_version_id",
    versionId,
    nutrients,
  );
}

async function insertNutrients(
  database: QueryDatabase,
  table: "goal_profile_nutrients" | "ration_nutrients" | "recipe_version_nutrients",
  key: "goal_profile_id" | "ration_id" | "recipe_version_id",
  id: string,
  nutrients: NutrientAmount[],
  amountColumn = "amount",
) {
  for (const nutrient of nutrients) {
    await database.query(
      `INSERT INTO ${table} (${key}, nutrient_code, ${amountColumn}) VALUES ($1, $2, $3)`,
      [id, nutrient.code, nutrient.amount],
    );
  }
}

async function recipeDetail(database: QueryDatabase, userId: string, recipeId: string) {
  const version = (
    await database.query<RecipeVersionRow>(
      `SELECT v.id, v.recipe_id AS "recipeId", v.name, v.yield_grams::text AS "yieldGrams", v.notes,
              v.version_number AS "versionNumber" FROM recipes r JOIN recipe_versions v ON v.id = r.current_version_id
       WHERE r.id = $1 AND r.user_id = $2 AND r.deleted_at IS NULL`,
      [recipeId, userId],
    )
  ).rows[0];
  if (!version) return undefined;
  const [components, nutrients] = await Promise.all([
    database.query(
      `SELECT ingredient_id AS "ingredientId", ingredient_name AS "ingredientName",
                           quantity_grams::text AS "quantityGrams", nutrient_snapshot AS nutrients
                    FROM recipe_components WHERE recipe_version_id = $1 ORDER BY id`,
      [version.id],
    ),
    database.query<NutrientAmount>(
      `SELECT nutrient_code AS code, amount::text AS amount FROM recipe_version_nutrients
                                    WHERE recipe_version_id = $1 ORDER BY nutrient_code`,
      [version.id],
    ),
  ]);
  return {
    ...version,
    components: components.rows,
    nutrients: nutrients.rows,
    calories: calculateCalories(nutrients.rows),
  };
}

async function goalForDate(database: QueryDatabase, userId: string, date: string) {
  const profile = (
    await database.query<{ effectiveLocalDate: string; id: string }>(
      `SELECT id, effective_local_date::text AS "effectiveLocalDate" FROM goal_profiles
       WHERE user_id = $1 AND effective_local_date <= $2::date ORDER BY effective_local_date DESC LIMIT 1`,
      [userId, date],
    )
  ).rows[0];
  if (!profile) return undefined;
  const nutrients = (
    await database.query<NutrientAmount>(
      `SELECT nutrient_code AS code, target_amount::text AS amount FROM goal_profile_nutrients
       WHERE goal_profile_id = $1 ORDER BY nutrient_code`,
      [profile.id],
    )
  ).rows;
  return { effectiveLocalDate: profile.effectiveLocalDate, nutrients };
}

function validateRecipe(input: RecipeInput) {
  if (
    !input.name?.trim() ||
    input.name.trim().length > 200 ||
    !positiveDecimal(input.yieldGrams) ||
    !Array.isArray(input.components) ||
    input.components.length === 0 ||
    input.components.some(
      (component) => !component.ingredientId || !positiveDecimal(component.quantityGrams),
    )
  ) {
    throw new WorkflowError("invalid_recipe", 400);
  }
}

function positiveDecimal(value: string): boolean {
  return /^\d+(\.\d+)?$/.test(value) && !/^0(?:\.0+)?$/.test(value);
}

function validInstant(value: string): boolean {
  return !Number.isNaN(new Date(value).getTime());
}

function dateOnly(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`));
}

function dateInTimezone(instant: string, timezone: string): string {
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: timezone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date(instant));
    const part = (type: string) => parts.find((candidate) => candidate.type === type)?.value;
    return `${part("year")}-${part("month")}-${part("day")}`;
  } catch {
    throw new WorkflowError("invalid_timezone", 400);
  }
}

function encodeCursor(date: string, instant: string, id: string): string {
  return Buffer.from(JSON.stringify({ date, instant, id })).toString("base64url");
}

function decodeCursor(value: string): { date: string; instant: string; id: string } {
  try {
    const parsed: unknown = JSON.parse(Buffer.from(value, "base64url").toString("utf8"));
    if (
      !parsed ||
      typeof parsed !== "object" ||
      !("date" in parsed) ||
      !("instant" in parsed) ||
      !("id" in parsed) ||
      typeof parsed.date !== "string" ||
      typeof parsed.instant !== "string" ||
      typeof parsed.id !== "string" ||
      !dateOnly(parsed.date) ||
      !validInstant(parsed.instant)
    )
      throw new Error();
    return { date: parsed.date, instant: parsed.instant, id: parsed.id };
  } catch {
    throw new WorkflowError("invalid_ration", 400);
  }
}
