import { afterEach, describe, expect, it } from "vitest";

import { buildApp } from "../src/app.js";

const apps = new Set<ReturnType<typeof buildApp>>();

afterEach(async () => {
  await Promise.all([...apps].map((app) => app.close()));
  apps.clear();
});

describe("health endpoint", () => {
  it("reports that the process is live", async () => {
    const app = buildApp();
    apps.add(app);

    const response = await app.inject({ method: "GET", url: "/health/live" });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ status: "ok" });
  });
});
