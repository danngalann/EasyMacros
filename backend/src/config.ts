export interface AppConfig {
  accessTokenTtlSeconds: number;
  databaseUrl: string;
  host: string;
  jwtAudience: string;
  jwtIssuer: string;
  jwtSecret: string;
  port: number;
  publicAuthRateLimitMax: number;
  registrationEnabled: boolean;
  sessionIdleDays: number;
  sessionMaxAgeDays: number;
  trustedProxyHops: number;
  openFoodFactsBaseUrl: string;
  openFoodFactsEnabled: boolean;
  openFoodFactsUserAgent?: string;
  openFoodFactsCountry: string;
  openFoodFactsLanguage: string;
  openRouterApiKey?: string;
  openRouterBaseUrl: string;
  openRouterModel: string;
  imageJobDirectory: string;
  aiWorkerConcurrency: number;
  aiRequestsPerHour: number;
  aiMaxUploadBytes: number;
}

export function loadConfig(environment: NodeJS.ProcessEnv = process.env): AppConfig {
  const databaseUrl = environment.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error("DATABASE_URL must be set");
  }

  try {
    new URL(databaseUrl);
  } catch {
    throw new Error("DATABASE_URL must be a valid URL");
  }

  const port = Number.parseInt(environment.PORT ?? "3000", 10);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("PORT must be an integer between 1 and 65535");
  }

  const jwtSecret = environment.JWT_SECRET;
  if (!jwtSecret || Buffer.byteLength(jwtSecret) < 32) {
    throw new Error("JWT_SECRET must be at least 32 bytes");
  }

  const accessTokenTtlSeconds = parsePositiveInteger(
    environment.ACCESS_TOKEN_TTL_SECONDS ?? "900",
    "ACCESS_TOKEN_TTL_SECONDS",
  );
  const sessionIdleDays = parsePositiveInteger(
    environment.SESSION_IDLE_DAYS ?? "30",
    "SESSION_IDLE_DAYS",
  );
  const sessionMaxAgeDays = parseNonNegativeInteger(
    environment.SESSION_MAX_AGE_DAYS ?? "0",
    "SESSION_MAX_AGE_DAYS",
  );
  const publicAuthRateLimitMax = parsePositiveInteger(
    environment.PUBLIC_AUTH_RATE_LIMIT_MAX ?? "20",
    "PUBLIC_AUTH_RATE_LIMIT_MAX",
  );
  const trustedProxyHops = parseNonNegativeInteger(
    environment.TRUSTED_PROXY_HOPS ?? "0",
    "TRUSTED_PROXY_HOPS",
  );
  const openFoodFactsBaseUrl =
    environment.OPEN_FOOD_FACTS_BASE_URL ?? "https://world.openfoodfacts.org";
  const openRouterBaseUrl = environment.OPENROUTER_BASE_URL ?? "https://openrouter.ai/api/v1";
  for (const [name, value] of [
    ["OPEN_FOOD_FACTS_BASE_URL", openFoodFactsBaseUrl],
    ["OPENROUTER_BASE_URL", openRouterBaseUrl],
  ] as const) {
    try {
      new URL(value);
    } catch {
      throw new Error(`${name} must be a valid URL`);
    }
  }
  const openFoodFactsUserAgent = environment.OPEN_FOOD_FACTS_USER_AGENT;
  if (
    environment.OPEN_FOOD_FACTS_ENABLED === "true" &&
    (!openFoodFactsUserAgent ||
      !/^EasyMacros\/\S+ \([^@\s]+@[^)\s]+\)$/.test(openFoodFactsUserAgent) ||
      openFoodFactsUserAgent.includes(".invalid"))
  ) {
    throw new Error(
      "OPEN_FOOD_FACTS_USER_AGENT must identify EasyMacros and contain a real contact email",
    );
  }

  return {
    accessTokenTtlSeconds,
    databaseUrl,
    host: environment.HOST ?? "127.0.0.1",
    jwtAudience: environment.JWT_AUDIENCE ?? "easymacros-mobile",
    jwtIssuer: environment.JWT_ISSUER ?? "easymacros",
    jwtSecret,
    port,
    publicAuthRateLimitMax,
    registrationEnabled: environment.REGISTRATION_ENABLED === "true",
    sessionIdleDays,
    sessionMaxAgeDays,
    trustedProxyHops,
    openFoodFactsBaseUrl,
    openFoodFactsEnabled: environment.OPEN_FOOD_FACTS_ENABLED === "true",
    openFoodFactsUserAgent,
    openFoodFactsCountry: environment.OPEN_FOOD_FACTS_COUNTRY ?? "de",
    openFoodFactsLanguage: environment.OPEN_FOOD_FACTS_LANGUAGE ?? "en",
    openRouterApiKey: environment.OPENROUTER_API_KEY,
    openRouterBaseUrl,
    openRouterModel: environment.OPENROUTER_MODEL ?? "google/gemini-2.5-flash",
    imageJobDirectory: environment.IMAGE_JOB_DIRECTORY ?? "/tmp/easymacros-images",
    aiWorkerConcurrency: parsePositiveInteger(
      environment.AI_WORKER_CONCURRENCY ?? "2",
      "AI_WORKER_CONCURRENCY",
    ),
    aiRequestsPerHour: parsePositiveInteger(
      environment.AI_REQUESTS_PER_HOUR ?? "10",
      "AI_REQUESTS_PER_HOUR",
    ),
    aiMaxUploadBytes: parsePositiveInteger(
      environment.AI_MAX_UPLOAD_BYTES ?? "8388608",
      "AI_MAX_UPLOAD_BYTES",
    ),
  };
}

function parsePositiveInteger(value: string, name: string): number {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function parseNonNegativeInteger(value: string, name: string): number {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
  return parsed;
}
