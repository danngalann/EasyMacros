import Fastify from "fastify";
import multipart from "@fastify/multipart";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";

import { AuthError, AuthService } from "./auth.js";
import type { AppConfig } from "./config.js";
import type { Database } from "./database.js";
import { IngredientError, IngredientService } from "./ingredients.js";
import { IngredientCandidateService } from "./ingredient-ingestion.js";
import { RationAiError, RationAiService } from "./ration-ai.js";
import { NutritionWorkflowService, WorkflowError } from "./workflows.js";

export interface BuildAppOptions {
  config?: AppConfig;
  database?: Database;
}

export async function buildApp({ config, database }: BuildAppOptions = {}) {
  const app = Fastify({
    disableRequestLogging: true,
    logger: config ? { level: "info" } : false,
    trustProxy: config?.trustedProxyHops || false,
  });
  const auth = config && database ? new AuthService(database, config) : undefined;
  const ingredients = database ? new IngredientService(database) : undefined;
  const candidates =
    config && database ? new IngredientCandidateService(database, config) : undefined;
  const workflows = database ? new NutritionWorkflowService(database) : undefined;
  const rationAi = config && database ? new RationAiService(database, config) : undefined;

  await app.register(rateLimit, { global: false });
  await app.register(multipart, {
    limits: { files: 4, fields: 4, parts: 8, fileSize: config?.aiMaxUploadBytes ?? 8_388_608 },
  });

  app.addHook("onResponse", async (request, reply) => {
    if (reply.statusCode < 400) return;
    const details = {
      method: request.method,
      requestId: request.id,
      route: request.routeOptions.url,
      statusCode: reply.statusCode,
    };
    if (reply.statusCode >= 500) request.log.error(details, "request failed");
    else request.log.warn(details, "request rejected");
  });

  await app.register(swagger, {
    openapi: {
      info: {
        title: "EasyMacros API",
        version: "0.2.0",
      },
    },
  });
  await app.register(swaggerUi, { routePrefix: "/docs" });

  const healthResponse = {
    type: "object",
    required: ["status"],
    properties: { status: { type: "string", enum: ["ok"] } },
  } as const;

  app.get(
    "/health/live",
    {
      schema: {
        response: { 200: healthResponse },
        tags: ["health"],
      },
    },
    () => ({ status: "ok" }),
  );

  app.get(
    "/v1/health/ready",
    {
      schema: {
        response: {
          200: healthResponse,
          503: {
            type: "object",
            required: ["status"],
            properties: { status: { type: "string", enum: ["unavailable"] } },
          },
        },
        tags: ["health"],
      },
    },
    async (_request, reply) => {
      if (database && (await database.isReady())) {
        return { status: "ok" };
      }

      return reply.code(503).send({ status: "unavailable" });
    },
  );

  const tokensResponse = {
    type: "object",
    required: ["accessToken", "expiresIn", "refreshToken", "sessionId"],
    properties: {
      accessToken: { type: "string" },
      expiresIn: { type: "integer" },
      refreshToken: { type: "string" },
      sessionId: { type: "string" },
    },
  } as const;
  const credentialsBody = {
    type: "object",
    required: ["username", "password"],
    additionalProperties: false,
    properties: { username: { type: "string" }, password: { type: "string" } },
  } as const;
  const publicAuthRateLimit = {
    rateLimit: { max: config?.publicAuthRateLimitMax ?? 20, timeWindow: "1 minute" },
  } as const;

  function requireAuthService(): AuthService {
    if (!auth) {
      throw new Error("Authentication requires database and config");
    }
    return auth;
  }

  function requireIngredientService(): IngredientService {
    if (!ingredients) {
      throw new Error("Nutrition requires database");
    }
    return ingredients;
  }

  function requireWorkflowService(): NutritionWorkflowService {
    if (!workflows) {
      throw new Error("Nutrition requires database");
    }
    return workflows;
  }

  function requireCandidateService(): IngredientCandidateService {
    if (!candidates) {
      throw new Error("External candidates require database and config");
    }
    return candidates;
  }

  function requireRationAiService(): RationAiService {
    if (!rationAi) {
      throw new Error("AI ration estimates require database and config");
    }
    return rationAi;
  }

  function clientIp(request: { ip: string }): string {
    return request.ip;
  }

  async function requireIdentity(request: { headers: { authorization?: string } }) {
    const authorization = request.headers.authorization;
    if (!authorization?.startsWith("Bearer ")) {
      throw new AuthError("invalid_session", 401);
    }
    return requireAuthService().authenticate(authorization.slice("Bearer ".length));
  }

  app.post<{ Body: { username: string; password: string } }>(
    "/auth/register",
    {
      config: publicAuthRateLimit,
      schema: { body: credentialsBody, response: { 200: tokensResponse }, tags: ["auth"] },
    },
    async (request) => requireAuthService().register(request.body.username, request.body.password),
  );

  app.post<{ Body: { username: string; password: string } }>(
    "/auth/login",
    {
      config: publicAuthRateLimit,
      schema: { body: credentialsBody, response: { 200: tokensResponse }, tags: ["auth"] },
    },
    async (request) =>
      requireAuthService().login(request.body.username, request.body.password, clientIp(request)),
  );

  app.post<{ Body: { refreshToken: string; idempotencyKey: string } }>(
    "/auth/refresh",
    {
      schema: {
        body: {
          type: "object",
          required: ["refreshToken", "idempotencyKey"],
          additionalProperties: false,
          properties: {
            refreshToken: { type: "string" },
            idempotencyKey: { type: "string", format: "uuid" },
          },
        },
        response: { 200: tokensResponse },
        tags: ["auth"],
      },
      config: publicAuthRateLimit,
    },
    async (request) =>
      requireAuthService().refresh(request.body.refreshToken, request.body.idempotencyKey),
  );

  app.post("/auth/logout", { schema: { tags: ["auth"] } }, async (request, reply) => {
    const identity = await requireIdentity(request);
    await requireAuthService().logout(identity.userId, identity.sessionId);
    return reply.code(204).send();
  });

  app.post<{ Body: { currentPassword: string; newPassword: string } }>(
    "/auth/change-password",
    {
      schema: {
        body: {
          type: "object",
          required: ["currentPassword", "newPassword"],
          additionalProperties: false,
          properties: { currentPassword: { type: "string" }, newPassword: { type: "string" } },
        },
        response: { 200: tokensResponse },
        tags: ["auth"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireAuthService().changePassword(
        identity.userId,
        request.body.currentPassword,
        request.body.newPassword,
      );
    },
  );

  app.get("/auth/sessions", { schema: { tags: ["auth"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return { sessions: await requireAuthService().listSessions(identity.userId) };
  });

  app.delete<{ Params: { sessionId: string } }>(
    "/auth/sessions/:sessionId",
    { schema: { tags: ["auth"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      await requireAuthService().revokeSession(identity.userId, request.params.sessionId);
      return reply.code(204).send();
    },
  );

  app.get("/me", { schema: { tags: ["auth"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireAuthService().currentUser(identity.userId);
  });

  app.get("/nutrients", { schema: { tags: ["nutrition"] } }, async (request) => {
    await requireIdentity(request);
    return { nutrients: await requireIngredientService().listNutrients() };
  });

  app.get<{ Querystring: { hidden?: string; query?: string } }>(
    "/ingredients",
    { schema: { tags: ["ingredients"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return {
        ingredients: await requireIngredientService().list(
          identity.userId,
          request.query.query,
          request.query.hidden === "true",
        ),
      };
    },
  );

  const candidateParams = {
    type: "object",
    required: ["candidateId"],
    properties: { candidateId: { type: "string", format: "uuid" } },
  } as const;
  const candidateResponse = {
    type: "object",
    required: [
      "id",
      "status",
      "source",
      "suggestedName",
      "brand",
      "barcode",
      "basisQuantity",
      "nutrients",
      "confidence",
      "warnings",
      "provenance",
      "version",
      "expiresAt",
    ],
    properties: {
      id: { type: "string", format: "uuid" },
      status: {
        type: "string",
        enum: ["pending", "processing", "ready", "failed", "cancelled", "expired", "confirmed"],
      },
      source: { type: "string", enum: ["manual", "barcode", "ai"] },
      suggestedName: { type: "string" },
      brand: { anyOf: [{ type: "string" }, { type: "null" }] },
      barcode: { anyOf: [{ type: "string" }, { type: "null" }] },
      basisQuantity: { type: "integer", enum: [100] },
      nutrients: {
        type: "array",
        items: {
          type: "object",
          required: ["code", "amount"],
          properties: {
            code: { type: "string" },
            displayName: { type: "string" },
            amount: { type: "string" },
            unit: { type: "string", enum: ["g", "mg", "ug"] },
            derived: { type: "boolean" },
          },
        },
      },
      confidence: { anyOf: [{ type: "string" }, { type: "null" }] },
      warnings: { type: "array", items: { type: "string" } },
      provenance: { type: "object", additionalProperties: true },
      version: { type: "string", format: "uuid" },
      expiresAt: { type: "string", format: "date-time" },
      errorCode: { type: "string" },
    },
  } as const;

  app.get<{ Params: { ingredientId: string } }>(
    "/ingredients/:ingredientId",
    { schema: { tags: ["ingredients"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      const ingredient = await requireIngredientService().detail(
        identity.userId,
        request.params.ingredientId,
      );
      return ingredient ?? reply.code(404).send({ code: "ingredient_not_found" });
    },
  );

  app.post<{
    Body: { brand?: string; name: string; nutrients: Array<{ amount: string; code: string }> };
  }>(
    "/ingredients/manual-candidates",
    { schema: { response: { 200: candidateResponse }, tags: ["ingredients"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireIngredientService().createManualCandidate(identity.userId, request.body);
    },
  );

  app.post<{ Body: { barcode: string } }>(
    "/ingredient-candidates/barcode",
    {
      schema: {
        body: {
          type: "object",
          additionalProperties: false,
          required: ["barcode"],
          properties: { barcode: { type: "string", minLength: 8, maxLength: 32 } },
        },
        response: { 200: candidateResponse },
        tags: ["ingredients"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireCandidateService().createBarcode(identity.userId, request.body.barcode);
    },
  );

  app.post(
    "/ingredient-candidates/ai",
    {
      schema: {
        consumes: ["multipart/form-data"],
        response: { 202: candidateResponse },
        tags: ["ingredients"],
      },
    },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      const images: Array<{ buffer: Buffer; mime: string }> = [];
      let description: string | undefined;
      let mode: "auto" | "label" | "generic" = "auto";
      let idempotencyKey: string | undefined;
      for await (const part of request.parts()) {
        if (part.type === "file") {
          if (part.fieldname !== "images") throw new IngredientError("invalid_image", 400);
          images.push({ buffer: await part.toBuffer(), mime: part.mimetype });
        } else if (part.fieldname === "description" && typeof part.value === "string")
          description = part.value;
        else if (
          part.fieldname === "mode" &&
          typeof part.value === "string" &&
          ["auto", "label", "generic"].includes(part.value)
        ) {
          mode = part.value as typeof mode;
        } else if (part.fieldname === "idempotencyKey" && typeof part.value === "string")
          idempotencyKey = part.value;
      }
      if (images.length === 0) throw new IngredientError("invalid_image", 400);
      const candidate = await requireCandidateService().createAi(identity.userId, request.ip, {
        images,
        description,
        mode,
        idempotencyKey,
      });
      return reply.code(202).send(candidate);
    },
  );

  app.get<{ Params: { candidateId: string } }>(
    "/ingredient-candidates/:candidateId",
    {
      schema: {
        params: candidateParams,
        response: { 200: candidateResponse },
        tags: ["ingredients"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireCandidateService().get(identity.userId, request.params.candidateId);
    },
  );

  app.post<{ Params: { candidateId: string } }>(
    "/ingredient-candidates/:candidateId/retry",
    {
      schema: {
        params: candidateParams,
        response: { 200: candidateResponse },
        tags: ["ingredients"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireCandidateService().retry(identity.userId, request.params.candidateId);
    },
  );

  app.delete<{ Params: { candidateId: string } }>(
    "/ingredient-candidates/:candidateId",
    { schema: { params: candidateParams, tags: ["ingredients"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      await requireCandidateService().cancel(identity.userId, request.params.candidateId);
      return reply.code(204).send();
    },
  );

  app.post<{
    Body: {
      version: string;
      name: string;
      brand?: string;
      barcode?: string;
      nutrients: Array<{ amount: string; code: string }>;
    };
    Params: { candidateId: string };
  }>(
    "/ingredient-candidates/:candidateId/confirm",
    {
      schema: {
        params: candidateParams,
        body: {
          type: "object",
          additionalProperties: false,
          required: ["version", "name", "nutrients"],
          properties: {
            version: { type: "string", format: "uuid" },
            name: { type: "string", minLength: 1, maxLength: 200 },
            brand: { type: "string", maxLength: 200 },
            barcode: { type: "string", maxLength: 32 },
            nutrients: {
              type: "array",
              minItems: 3,
              items: {
                type: "object",
                additionalProperties: false,
                required: ["code", "amount"],
                properties: {
                  code: { type: "string" },
                  amount: { type: "string", pattern: "^\\d+(\\.\\d+)?$" },
                },
              },
            },
          },
        },
        tags: ["ingredients"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireIngredientService().confirmCandidate(
        identity.userId,
        request.params.candidateId,
        request.body,
      );
    },
  );

  app.patch<{ Body: { brand?: string; name?: string }; Params: { ingredientId: string } }>(
    "/ingredients/:ingredientId",
    { schema: { tags: ["ingredients"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireIngredientService().update(
        identity.userId,
        request.params.ingredientId,
        request.body,
      );
    },
  );

  app.patch<{ Body: { hidden: boolean }; Params: { ingredientId: string } }>(
    "/ingredients/:ingredientId/visibility",
    { schema: { tags: ["ingredients"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireIngredientService().setVisibility(
        identity.userId,
        request.params.ingredientId,
        request.body.hidden,
      );
    },
  );

  app.get<{ Querystring: { query?: string } }>(
    "/recipes",
    { schema: { tags: ["recipes"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return {
        recipes: await requireWorkflowService().listRecipes(identity.userId, request.query.query),
      };
    },
  );

  app.post<{
    Body: {
      components: Array<{ ingredientId: string; quantityGrams: string }>;
      expectedVersion?: number;
      name: string;
      notes?: string;
    };
  }>("/recipes", { schema: { tags: ["recipes"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireWorkflowService().createRecipe(identity.userId, request.body);
  });

  app.post<{
    Body: {
      components: Array<{ ingredientId: string; quantityGrams: string }>;
      name: string;
      notes?: string;
    };
  }>("/recipes/preview", { schema: { tags: ["recipes"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireWorkflowService().previewRecipe(identity.userId, request.body);
  });

  app.get<{ Params: { recipeId: string } }>(
    "/recipes/:recipeId",
    { schema: { tags: ["recipes"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      const recipe = await requireWorkflowService().detailRecipe(
        identity.userId,
        request.params.recipeId,
      );
      return recipe ?? reply.code(404).send({ code: "recipe_not_found" });
    },
  );

  app.put<{
    Body: {
      components: Array<{ ingredientId: string; quantityGrams: string }>;
      name: string;
      notes?: string;
    };
    Params: { recipeId: string };
  }>("/recipes/:recipeId", { schema: { tags: ["recipes"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireWorkflowService().updateRecipe(
      identity.userId,
      request.params.recipeId,
      request.body,
    );
  });

  app.delete<{ Params: { recipeId: string } }>(
    "/recipes/:recipeId",
    { schema: { tags: ["recipes"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      await requireWorkflowService().deleteRecipe(identity.userId, request.params.recipeId);
      return reply.code(204).send();
    },
  );

  const optionalRationWeight = {
    type: "string",
    pattern: "^(?:0*[1-9]\\d*(?:\\.\\d+)?|0+\\.\\d*[1-9]\\d*)$",
  } as const;
  const rationPreviewBody = {
    type: "object",
    required: ["recipeId"],
    additionalProperties: false,
    properties: {
      recipeId: { type: "string", format: "uuid" },
      consumedGrams: optionalRationWeight,
    },
  } as const;
  const rationBody = {
    ...rationPreviewBody,
    required: ["recipeId", "consumedAt"],
    properties: {
      ...rationPreviewBody.properties,
      consumedAt: { type: "string", format: "date-time" },
      note: { type: "string" },
    },
  } as const;
  const rationPreviewResponse = {
    type: "object",
    required: ["recipeName", "consumedGrams", "nutrients", "calories"],
    properties: {
      recipeName: { type: "string" },
      consumedGrams: { type: "string" },
      nutrients: {
        type: "array",
        items: {
          type: "object",
          required: ["code", "amount"],
          properties: {
            code: { type: "string" },
            amount: { type: "string" },
          },
        },
      },
      calories: { type: "string" },
    },
  } as const;

  app.post<{
    Body: { consumedAt: string; consumedGrams?: string; note?: string; recipeId: string };
  }>("/rations", { schema: { body: rationBody, tags: ["rations"] } }, async (request) => {
    const identity = await requireIdentity(request);
    const user = await requireAuthService().currentUser(identity.userId);
    return requireWorkflowService().createRation(identity.userId, user.timezone, request.body);
  });

  const rationEstimateResponse = {
    type: "object",
    required: [
      "id",
      "version",
      "status",
      "suggestedName",
      "estimatedWeightGrams",
      "nutrients",
      "calories",
      "confidence",
      "warnings",
      "provenance",
      "expiresAt",
    ],
    properties: {
      id: { type: "string", format: "uuid" },
      version: { type: "string", format: "uuid" },
      status: { type: "string", enum: ["ready"] },
      suggestedName: { type: "string" },
      estimatedWeightGrams: { type: "string" },
      nutrients: rationPreviewResponse.properties.nutrients,
      calories: { type: "string" },
      confidence: { type: "string" },
      warnings: { type: "array", items: { type: "string" } },
      provenance: { type: "object", additionalProperties: true },
      expiresAt: { type: "string", format: "date-time" },
    },
  } as const;

  app.post(
    "/ration-ai-estimates",
    {
      config: {
        rateLimit: {
          max: config?.aiRequestsPerHour ?? 10,
          timeWindow: "1 hour",
        },
      },
      schema: {
        consumes: ["multipart/form-data"],
        response: { 200: rationEstimateResponse },
        tags: ["rations"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      const images: Array<{ buffer: Buffer; mime: string }> = [];
      let description: string | undefined;
      for await (const part of request.parts()) {
        if (part.type === "file") {
          if (part.fieldname !== "images") throw new RationAiError("invalid_image", 400);
          images.push({ buffer: await part.toBuffer(), mime: part.mimetype });
        } else if (part.fieldname === "description" && typeof part.value === "string") {
          description = part.value;
        }
      }
      return requireRationAiService().estimate(identity.userId, request.ip, {
        images,
        description,
      });
    },
  );

  app.post<{
    Params: { estimateId: string };
    Body: {
      version: string;
      name: string;
      consumedAt: string;
      estimatedWeightGrams: string;
      note?: string;
      nutrients: Array<{ code: string; amount: string }>;
    };
  }>(
    "/ration-ai-estimates/:estimateId/confirm",
    {
      schema: {
        params: {
          type: "object",
          required: ["estimateId"],
          properties: { estimateId: { type: "string", format: "uuid" } },
        },
        body: {
          type: "object",
          additionalProperties: false,
          required: ["version", "name", "consumedAt", "estimatedWeightGrams", "nutrients"],
          properties: {
            version: { type: "string", format: "uuid" },
            name: { type: "string", minLength: 1, maxLength: 200 },
            consumedAt: { type: "string", format: "date-time" },
            estimatedWeightGrams: optionalRationWeight,
            note: { type: "string", maxLength: 2_000 },
            nutrients: rationPreviewResponse.properties.nutrients,
          },
        },
        tags: ["rations"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      const user = await requireAuthService().currentUser(identity.userId);
      return requireRationAiService().confirm(
        identity.userId,
        user.timezone,
        request.params.estimateId,
        request.body,
      );
    },
  );

  app.post<{ Body: { consumedGrams?: string; recipeId: string } }>(
    "/rations/preview",
    {
      schema: {
        body: rationPreviewBody,
        response: { 200: rationPreviewResponse },
        tags: ["rations"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireWorkflowService().previewRation(identity.userId, request.body);
    },
  );

  app.get<{
    Querystring: {
      cursor?: string;
      date?: string;
      from?: string;
      limit?: string;
      query?: string;
      to?: string;
    };
  }>("/rations", { schema: { tags: ["rations"] } }, async (request) => {
    const identity = await requireIdentity(request);
    const limit = request.query.limit === undefined ? undefined : Number(request.query.limit);
    return requireWorkflowService().listRations(identity.userId, {
      cursor: request.query.cursor,
      date: request.query.date,
      from: request.query.from,
      limit,
      query: request.query.query,
      to: request.query.to,
    });
  });

  app.get<{ Params: { rationId: string } }>(
    "/rations/:rationId",
    { schema: { tags: ["rations"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      const ration = await requireWorkflowService().detailRation(
        identity.userId,
        request.params.rationId,
      );
      return ration ?? reply.code(404).send({ code: "ration_not_found" });
    },
  );

  app.delete<{ Params: { rationId: string } }>(
    "/rations/:rationId",
    { schema: { tags: ["rations"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      await requireWorkflowService().deleteRation(identity.userId, request.params.rationId);
      return reply.code(204).send();
    },
  );

  app.put<{ Body: { timezone: string } }>(
    "/me/timezone",
    { schema: { tags: ["profile"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireWorkflowService().updateTimezone(identity.userId, request.body.timezone);
    },
  );

  const nutrientAmountsResponse = {
    type: "array",
    items: {
      type: "object",
      required: ["code", "amount"],
      properties: {
        code: { type: "string" },
        amount: { type: "string" },
      },
    },
  } as const;
  const goalResponse = {
    type: "object",
    required: ["effectiveLocalDate", "nutrients", "calories"],
    properties: {
      effectiveLocalDate: { type: "string", format: "date" },
      nutrients: nutrientAmountsResponse,
      calories: { type: "string" },
    },
  } as const;

  app.put<{
    Body: { effectiveLocalDate: string; nutrients: Array<{ amount: string; code: string }> };
  }>(
    "/goals",
    { schema: { response: { 200: goalResponse }, tags: ["goals"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireWorkflowService().setGoal(identity.userId, request.body);
    },
  );

  app.get<{ Querystring: { date: string } }>(
    "/goals",
    { schema: { response: { 200: goalResponse }, tags: ["goals"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      const goal = await requireWorkflowService().goalForDate(identity.userId, request.query.date);
      return goal ?? reply.code(404).send({ code: "goal_not_found" });
    },
  );

  app.get<{ Querystring: { date: string } }>(
    "/dashboard",
    {
      schema: {
        response: {
          200: {
            type: "object",
            required: ["date", "nutrients", "calories"],
            properties: {
              date: { type: "string", format: "date" },
              nutrients: nutrientAmountsResponse,
              calories: { type: "string" },
              goal: {
                anyOf: [goalResponse, { type: "null" }],
              },
            },
          },
        },
        tags: ["dashboard"],
      },
    },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireWorkflowService().dashboard(identity.userId, request.query.date);
    },
  );

  app.setErrorHandler((error, request, reply) => {
    if (error instanceof AuthError) {
      if (error.retryAfterSeconds) {
        reply.header("Retry-After", String(error.retryAfterSeconds));
      }
      return reply
        .code(error.statusCode)
        .send({ code: error.code, message: "Authentication request could not be completed" });
    }
    if (error instanceof IngredientError) {
      return reply.code(error.statusCode).send({ code: error.code });
    }
    if (error instanceof WorkflowError) {
      return reply.code(error.statusCode).send({ code: error.code });
    }
    if (error instanceof RationAiError) {
      return reply.code(error.statusCode).send({ code: error.code });
    }
    const errorDetails =
      typeof error === "object" && error !== null
        ? (error as { code?: unknown; name?: unknown })
        : {};
    request.log.error(
      {
        errorCode: typeof errorDetails.code === "string" ? errorDetails.code : undefined,
        errorName: typeof errorDetails.name === "string" ? errorDetails.name : "UnknownError",
      },
      "unhandled request error",
    );
    return reply.code(500).send({ code: "internal_error" });
  });

  app.addHook("onClose", async () => {
    await database?.close();
  });

  if (candidates) {
    app.addHook("onReady", async () => candidates.recover());
  }

  return app;
}
