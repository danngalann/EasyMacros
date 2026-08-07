import Fastify from "fastify";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";

import type { Database } from "./database.js";

export interface BuildAppOptions {
  database?: Database;
}

export async function buildApp({ database }: BuildAppOptions = {}) {
  const app = Fastify({ logger: false });

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

  app.addHook("onClose", async () => {
    await database?.close();
  });

  return app;
}
