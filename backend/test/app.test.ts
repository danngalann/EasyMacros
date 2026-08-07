import { afterEach, describe, expect, it } from "vitest";

import { buildApp } from "../src/app.js";
import type { Database } from "../src/database.js";

const apps = new Set<Awaited<ReturnType<typeof buildApp>>>();

afterEach(async () => {
  await Promise.all([...apps].map((app) => app.close()));
  apps.clear();
});

describe("health endpoint", () => {
  it("reports that the process is live", async () => {
    const app = await buildApp();
    apps.add(app);

    const response = await app.inject({ method: "GET", url: "/health/live" });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
  });

  it("reports ready when PostgreSQL responds", async () => {
    const database: Database = {
      close: () => Promise.resolve(),
      isReady: () => Promise.resolve(true),
      query: () => Promise.resolve({ rows: [] }),
      transaction: (callback) => callback(database),
    };
    const app = await buildApp({ database });
    apps.add(app);

    const response = await app.inject({ method: "GET", url: "/v1/health/ready" });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
  });

  it("reports unavailable when PostgreSQL cannot be reached", async () => {
    const database: Database = {
      close: () => Promise.resolve(),
      isReady: () => Promise.resolve(false),
      query: () => Promise.resolve({ rows: [] }),
      transaction: (callback) => callback(database),
    };
    const app = await buildApp({ database });
    apps.add(app);

    const response = await app.inject({ method: "GET", url: "/v1/health/ready" });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toEqual({ status: "unavailable" });
  });

  it("publishes the versioned readiness contract in OpenAPI", async () => {
    const app = await buildApp();
    apps.add(app);
    await app.ready();

    const document = app.swagger();

    expect(document.info.version).toBe("0.2.0");
    expect(document.paths).toHaveProperty("/v1/health/ready");
  });

  it("publishes the nutrition workflow contracts in OpenAPI", async () => {
    const app = await buildApp();
    apps.add(app);
    await app.ready();

    const paths = app.swagger().paths;

    expect(paths).toHaveProperty("/recipes");
    expect(paths).toHaveProperty("/recipes/{recipeId}");
    expect(paths).toHaveProperty("/rations");
    expect(paths).toHaveProperty("/rations/preview");
    expect(paths).toHaveProperty("/rations/{rationId}");
    expect(paths).toHaveProperty("/goals");
    expect(paths).toHaveProperty("/dashboard");
    expect(paths).toHaveProperty("/me/timezone");
  });
});
