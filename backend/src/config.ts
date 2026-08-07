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
