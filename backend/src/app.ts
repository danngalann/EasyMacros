import Fastify from "fastify";

export function buildApp() {
  const app = Fastify({ logger: false });

  app.get("/health/live", () => ({ status: "ok" }));

  return app;
}
