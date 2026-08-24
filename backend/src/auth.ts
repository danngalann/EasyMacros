import { createHash, randomBytes, randomUUID } from "node:crypto";

import argon2 from "argon2";
import { SignJWT, jwtVerify } from "jose";

import type { AppConfig } from "./config.js";
import type { Database } from "./database.js";

const passwordOptions = {
  type: argon2.argon2id,
  memoryCost: 19_456,
  timeCost: 2,
  parallelism: 1,
} as const;
const previousRefreshWindowMs = 60_000;

export class AuthError extends Error {
  constructor(
    readonly code:
      | "invalid_credentials"
      | "registration_disabled"
      | "rate_limited"
      | "invalid_session",
    readonly statusCode: 401 | 403 | 429,
    readonly retryAfterSeconds?: number,
  ) {
    super(code);
  }
}

export interface AuthTokens {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  sessionId: string;
}

interface UserRow {
  id: string;
  username: string;
  password_hash: string;
  timezone: string;
}

interface SessionRow {
  id: string;
  user_id: string;
  refresh_family_id: string;
  current_token_digest: string;
  previous_token_digest: string | null;
  previous_idempotency_key: string | null;
  previous_rotated_at: Date | null;
  recovery_used: boolean;
  expires_at: Date | null;
  idle_expires_at: Date;
  revoked_at: Date | null;
}

export interface AuthenticatedUser {
  sessionId: string;
  userId: string;
}

export class AuthService {
  private readonly jwtSecret: Uint8Array;
  private readonly dummyPasswordHash = argon2.hash(
    "EasyMacros constant-shape credential check",
    passwordOptions,
  );

  constructor(
    private readonly database: Database,
    private readonly config: AppConfig,
  ) {
    this.jwtSecret = new TextEncoder().encode(config.jwtSecret);
  }

  async register(usernameInput: string, password: string): Promise<AuthTokens> {
    if (!this.config.registrationEnabled) {
      throw new AuthError("registration_disabled", 403);
    }

    const user = await this.createUser(usernameInput, password);
    return this.createSession(user);
  }

  async createUser(usernameInput: string, password: string): Promise<UserRow> {
    const username = normalizeUsername(usernameInput);
    validatePassword(password);
    const passwordHash = await argon2.hash(password, passwordOptions);
    const user: UserRow = {
      id: randomUUID(),
      username,
      password_hash: passwordHash,
      timezone: "UTC",
    };

    try {
      await this.database.query(
        "INSERT INTO users (id, username, password_hash) VALUES ($1, $2, $3)",
        [user.id, user.username, user.password_hash],
      );
      return user;
    } catch (error: unknown) {
      if (isUniqueViolation(error)) {
        throw new Error("username is already in use");
      }
      throw error;
    }
  }

  async login(usernameInput: string, password: string, ip: string): Promise<AuthTokens> {
    const username = tryNormalizeUsername(usernameInput);
    const attemptKey = hashAttemptKey(username ?? "invalid", ip);
    const throttled = await this.getThrottle(attemptKey);
    if (throttled) {
      throw new AuthError("rate_limited", 429, throttled);
    }

    const result = await this.database.query<UserRow>(
      "SELECT id, username, password_hash, timezone FROM users WHERE LOWER(username) = LOWER($1)",
      [username ?? ""],
    );
    const user = result.rows[0];
    const valid = await argon2.verify(
      user?.password_hash ?? (await this.dummyPasswordHash),
      password,
    );
    if (!user || !valid) {
      const retryAfter = await this.recordFailure(attemptKey);
      if (retryAfter) {
        throw new AuthError("rate_limited", 429, retryAfter);
      }
      throw new AuthError("invalid_credentials", 401);
    }

    await this.database.query("DELETE FROM login_attempts WHERE username_ip_key = $1", [
      attemptKey,
    ]);
    if (argon2.needsRehash(user.password_hash, passwordOptions)) {
      await this.database.query(
        "UPDATE users SET password_hash = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $1",
        [user.id, await argon2.hash(password, passwordOptions)],
      );
    }
    return this.createSession(user);
  }

  async refresh(refreshToken: string, idempotencyKey: string): Promise<AuthTokens> {
    const digest = digestToken(refreshToken);
    const result = await this.database.query<SessionRow>(
      `SELECT id, user_id, refresh_family_id, current_token_digest, previous_token_digest,
              previous_idempotency_key, previous_rotated_at, recovery_used, expires_at, idle_expires_at, revoked_at
       FROM sessions WHERE current_token_digest = $1 OR previous_token_digest = $1`,
      [digest],
    );
    const session = result.rows[0];
    const now = Date.now();
    if (
      !session ||
      session.revoked_at ||
      session.idle_expires_at.getTime() <= now ||
      (session.expires_at && session.expires_at.getTime() <= now)
    ) {
      throw new AuthError("invalid_session", 401);
    }

    const isRecovery = session.previous_token_digest === digest;
    if (
      isRecovery &&
      (session.previous_idempotency_key !== idempotencyKey ||
        session.recovery_used ||
        !session.previous_rotated_at ||
        now - session.previous_rotated_at.getTime() > previousRefreshWindowMs)
    ) {
      await this.revokeFamily(session.refresh_family_id);
      throw new AuthError("invalid_session", 401);
    }

    const tokens = await this.issueTokens(session.user_id, session.id);
    await this.rotateSession(session, tokens.refreshToken, idempotencyKey, isRecovery);
    return tokens;
  }

  async logout(userId: string, sessionId: string): Promise<void> {
    await this.database.query(
      "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL",
      [sessionId, userId],
    );
  }

  async listSessions(userId: string): Promise<Array<Record<string, unknown>>> {
    const result = await this.database.query(
      `SELECT id, device_label AS "deviceLabel", created_at AS "createdAt", last_used_at AS "lastUsedAt",
              expires_at AS "expiresAt" FROM sessions
       WHERE user_id = $1 AND revoked_at IS NULL ORDER BY last_used_at DESC`,
      [userId],
    );
    return result.rows;
  }

  async revokeSession(userId: string, sessionId: string): Promise<void> {
    await this.logout(userId, sessionId);
  }

  async currentUser(userId: string): Promise<{ id: string; username: string; timezone: string }> {
    const result = await this.database.query<{ id: string; username: string; timezone: string }>(
      "SELECT id, username, timezone FROM users WHERE id = $1",
      [userId],
    );
    const user = result.rows[0];
    if (!user) {
      throw new AuthError("invalid_session", 401);
    }
    return user;
  }

  async changePassword(
    userId: string,
    currentPassword: string,
    newPassword: string,
  ): Promise<AuthTokens> {
    validatePassword(newPassword);
    const result = await this.database.query<UserRow>(
      "SELECT id, username, password_hash, timezone FROM users WHERE id = $1",
      [userId],
    );
    const user = result.rows[0];
    if (!user || !(await argon2.verify(user.password_hash, currentPassword))) {
      throw new AuthError("invalid_credentials", 401);
    }
    await this.database.query(
      "UPDATE users SET password_hash = $2, updated_at = CURRENT_TIMESTAMP WHERE id = $1",
      [userId, await argon2.hash(newPassword, passwordOptions)],
    );
    await this.database.query(
      "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = $1 AND revoked_at IS NULL",
      [userId],
    );
    return this.createSession(user);
  }

  async authenticate(accessToken: string): Promise<AuthenticatedUser> {
    try {
      const verified = await jwtVerify(accessToken, this.jwtSecret, {
        audience: this.config.jwtAudience,
        issuer: this.config.jwtIssuer,
      });
      const userId = verified.payload.sub;
      const sessionId = verified.payload.sid;
      if (!userId || typeof sessionId !== "string") {
        throw new Error("token claims missing");
      }
      const result = await this.database.query<{ id: string }>(
        "SELECT id FROM sessions WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL",
        [sessionId, userId],
      );
      if (!result.rows[0]) {
        throw new Error("session revoked");
      }
      return { userId, sessionId };
    } catch {
      throw new AuthError("invalid_session", 401);
    }
  }

  private async createSession(user: UserRow): Promise<AuthTokens> {
    const sessionId = randomUUID();
    const tokens = await this.issueTokens(user.id, sessionId);
    const expiresAt =
      this.config.sessionMaxAgeDays === 0
        ? null
        : new Date(Date.now() + this.config.sessionMaxAgeDays * 86_400_000);
    const idleExpiresAt = new Date(Date.now() + this.config.sessionIdleDays * 86_400_000);
    await this.database.query(
      `INSERT INTO sessions (id, user_id, refresh_family_id, current_token_digest, expires_at, idle_expires_at)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [
        sessionId,
        user.id,
        randomUUID(),
        digestToken(tokens.refreshToken),
        expiresAt,
        idleExpiresAt,
      ],
    );
    return tokens;
  }

  private async issueTokens(userId: string, sessionId: string): Promise<AuthTokens> {
    const accessToken = await new SignJWT({ sid: sessionId })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setIssuer(this.config.jwtIssuer)
      .setAudience(this.config.jwtAudience)
      .setSubject(userId)
      .setJti(randomUUID())
      .setIssuedAt()
      .setExpirationTime(`${this.config.accessTokenTtlSeconds}s`)
      .sign(this.jwtSecret);
    return {
      accessToken,
      expiresIn: this.config.accessTokenTtlSeconds,
      refreshToken: randomBytes(32).toString("base64url"),
      sessionId,
    };
  }

  private async rotateSession(
    session: SessionRow,
    refreshToken: string,
    idempotencyKey: string,
    isRecovery: boolean,
  ): Promise<void> {
    const idleExpiresAt = new Date(Date.now() + this.config.sessionIdleDays * 86_400_000);
    await this.database.query(
      `UPDATE sessions SET current_token_digest = $2, previous_token_digest = $3,
       previous_idempotency_key = $4, previous_rotated_at = CURRENT_TIMESTAMP, recovery_used = $5, last_used_at = CURRENT_TIMESTAMP,
       idle_expires_at = $6 WHERE id = $1`,
      [
        session.id,
        digestToken(refreshToken),
        session.current_token_digest,
        idempotencyKey,
        isRecovery,
        idleExpiresAt,
      ],
    );
  }

  private async revokeFamily(familyId: string): Promise<void> {
    await this.database.query(
      "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE refresh_family_id = $1 AND revoked_at IS NULL",
      [familyId],
    );
  }

  private async getThrottle(attemptKey: string): Promise<number | undefined> {
    const result = await this.database.query<{ next_allowed_at: Date | null }>(
      "SELECT next_allowed_at FROM login_attempts WHERE username_ip_key = $1 AND expires_at > CURRENT_TIMESTAMP",
      [attemptKey],
    );
    const nextAllowedAt = result.rows[0]?.next_allowed_at;
    if (!nextAllowedAt || nextAllowedAt.getTime() <= Date.now()) {
      return undefined;
    }
    return Math.ceil((nextAllowedAt.getTime() - Date.now()) / 1000);
  }

  private async recordFailure(attemptKey: string): Promise<number | undefined> {
    const existing = await this.database.query<{ failure_count: number }>(
      "SELECT failure_count FROM login_attempts WHERE username_ip_key = $1",
      [attemptKey],
    );
    const failures = (existing.rows[0]?.failure_count ?? 0) + 1;
    const delay = failures <= 3 ? 0 : Math.min(900, 2 ** (failures - 3));
    await this.database.query(
      `INSERT INTO login_attempts (username_ip_key, failure_count, next_allowed_at, expires_at)
       VALUES ($1, $2, CURRENT_TIMESTAMP + ($3 * INTERVAL '1 second'), CURRENT_TIMESTAMP + INTERVAL '1 day')
       ON CONFLICT (username_ip_key) DO UPDATE SET failure_count = EXCLUDED.failure_count,
       next_allowed_at = EXCLUDED.next_allowed_at, expires_at = EXCLUDED.expires_at, updated_at = CURRENT_TIMESTAMP`,
      [attemptKey, failures, delay],
    );
    return delay || undefined;
  }
}

export function normalizeUsername(value: string): string {
  const normalized = value.normalize("NFKC").trim().toLocaleLowerCase("en-US");
  if (normalized.length < 3 || normalized.length > 64 || !/^[a-z0-9._-]+$/.test(normalized)) {
    throw new Error(
      "username must be 3-64 characters using letters, numbers, dots, hyphens, or underscores",
    );
  }
  return normalized;
}

function tryNormalizeUsername(value: string): string | undefined {
  try {
    return normalizeUsername(value);
  } catch {
    return undefined;
  }
}

export function validatePassword(value: string): void {
  if (value.length < 8 || value.length > 256) {
    throw new Error("password must be between 8 and 256 characters");
  }
}

function digestToken(token: string): string {
  return createHash("sha256").update(token).digest("base64url");
}

function hashAttemptKey(username: string, ip: string): string {
  return createHash("sha256").update(`${username}\u0000${ip}`).digest("base64url");
}

function isUniqueViolation(error: unknown): error is { code: string } {
  return typeof error === "object" && error !== null && "code" in error && error.code === "23505";
}
