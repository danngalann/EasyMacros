import Fastify from "fastify";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";

import { AuthError, AuthService } from "./auth.js";
import type { AppConfig } from "./config.js";
import type { Database } from "./database.js";

export interface BuildAppOptions {
  config?: AppConfig;
  database?: Database;
}

export async function buildApp({ config, database }: BuildAppOptions = {}) {
  const app = Fastify({ logger: false, trustProxy: config?.trustedProxyHops || false });
  const auth = config && database ? new AuthService(database, config) : undefined;

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

  app.setErrorHandler((error, _request, reply) => {
    if (error instanceof AuthError) {
      if (error.retryAfterSeconds) {
        reply.header("Retry-After", String(error.retryAfterSeconds));
      }
      return reply
        .code(error.statusCode)
        .send({ code: error.code, message: "Authentication request could not be completed" });
    }
    return reply.send(error);
  });

  app.addHook("onClose", async () => {
    await database?.close();
  });

  return app;
}
