import Fastify from "fastify";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";

import { AuthError, AuthService } from "./auth.js";
import type { AppConfig } from "./config.js";
import type { Database } from "./database.js";
import { IngredientError, IngredientService } from "./ingredients.js";
import { NutritionWorkflowService, WorkflowError } from "./workflows.js";

export interface BuildAppOptions {
  config?: AppConfig;
  database?: Database;
}

export async function buildApp({ config, database }: BuildAppOptions = {}) {
  const app = Fastify({ logger: false, trustProxy: config?.trustedProxyHops || false });
  const auth = config && database ? new AuthService(database, config) : undefined;
  const ingredients = database ? new IngredientService(database) : undefined;
  const workflows = database ? new NutritionWorkflowService(database) : undefined;

  await app.register(rateLimit, { global: false });

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
  }>("/ingredients/manual-candidates", { schema: { tags: ["ingredients"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireIngredientService().createManualCandidate(identity.userId, request.body);
  });

  app.post<{ Body: { version: string }; Params: { candidateId: string } }>(
    "/ingredient-candidates/:candidateId/confirm",
    { schema: { tags: ["ingredients"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireIngredientService().confirmCandidate(
        identity.userId,
        request.params.candidateId,
        request.body.version,
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
      name: string;
      notes?: string;
      yieldGrams: string;
    };
  }>("/recipes", { schema: { tags: ["recipes"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireWorkflowService().createRecipe(identity.userId, request.body);
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
      yieldGrams: string;
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

  app.post<{
    Body: { consumedAt: string; consumedGrams: string; note?: string; recipeId: string };
  }>("/rations", { schema: { tags: ["rations"] } }, async (request) => {
    const identity = await requireIdentity(request);
    const user = await requireAuthService().currentUser(identity.userId);
    return requireWorkflowService().createRation(identity.userId, user.timezone, request.body);
  });

  app.get<{ Querystring: { cursor?: string; date?: string; limit?: string } }>(
    "/rations",
    { schema: { tags: ["rations"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      const limit = request.query.limit === undefined ? undefined : Number(request.query.limit);
      return requireWorkflowService().listRations(identity.userId, {
        cursor: request.query.cursor,
        date: request.query.date,
        limit,
      });
    },
  );

  app.put<{
    Body: { effectiveLocalDate: string; nutrients: Array<{ amount: string; code: string }> };
  }>("/goals", { schema: { tags: ["goals"] } }, async (request) => {
    const identity = await requireIdentity(request);
    return requireWorkflowService().setGoal(identity.userId, request.body);
  });

  app.get<{ Querystring: { date: string } }>(
    "/goals",
    { schema: { tags: ["goals"] } },
    async (request, reply) => {
      const identity = await requireIdentity(request);
      const goal = await requireWorkflowService().goalForDate(identity.userId, request.query.date);
      return goal ?? reply.code(404).send({ code: "goal_not_found" });
    },
  );

  app.get<{ Querystring: { date: string } }>(
    "/dashboard",
    { schema: { tags: ["dashboard"] } },
    async (request) => {
      const identity = await requireIdentity(request);
      return requireWorkflowService().dashboard(identity.userId, request.query.date);
    },
  );

  app.setErrorHandler((error, _request, reply) => {
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
    return reply.send(error);
  });

  app.addHook("onClose", async () => {
    await database?.close();
  });

  return app;
}
