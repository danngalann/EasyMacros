ALTER TABLE users RENAME COLUMN email TO username;
ALTER TABLE users ADD COLUMN password_hash TEXT;
ALTER TABLE users ADD COLUMN timezone TEXT NOT NULL DEFAULT 'UTC';
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;

CREATE UNIQUE INDEX users_username_normalized_key ON users (LOWER(username));

CREATE TABLE sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  refresh_family_id UUID NOT NULL,
  current_token_digest TEXT NOT NULL UNIQUE,
  previous_token_digest TEXT,
  previous_idempotency_key UUID,
  previous_rotated_at TIMESTAMPTZ,
  recovery_used BOOLEAN NOT NULL DEFAULT FALSE,
  device_label TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ,
  idle_expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX sessions_user_id_idx ON sessions (user_id, revoked_at, last_used_at DESC);
CREATE INDEX sessions_family_id_idx ON sessions (refresh_family_id, revoked_at);

CREATE TABLE login_attempts (
  username_ip_key TEXT PRIMARY KEY,
  failure_count INTEGER NOT NULL CHECK (failure_count > 0),
  next_allowed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX login_attempts_expiry_idx ON login_attempts (expires_at);