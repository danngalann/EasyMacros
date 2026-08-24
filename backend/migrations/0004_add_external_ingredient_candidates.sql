ALTER TABLE nutrient_candidates DROP CONSTRAINT nutrient_candidates_source_check;
ALTER TABLE nutrient_candidates DROP CONSTRAINT nutrient_candidates_status_check;
ALTER TABLE nutrient_candidates
  ALTER COLUMN suggested_name DROP NOT NULL,
  ADD COLUMN confidence NUMERIC(4, 3),
  ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours'),
  ADD COLUMN deleted_at TIMESTAMPTZ,
  ADD CONSTRAINT nutrient_candidates_source_check CHECK (source IN ('manual', 'barcode', 'ai')),
  ADD CONSTRAINT nutrient_candidates_status_check
    CHECK (status IN ('pending', 'processing', 'ready', 'failed', 'cancelled', 'expired', 'confirmed'));

ALTER TABLE ingredients
  ADD COLUMN confidence NUMERIC(4, 3),
  ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN provenance JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE ingredient_ingestion_jobs (
  candidate_id UUID PRIMARY KEY REFERENCES nutrient_candidates(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind = 'ai'),
  status TEXT NOT NULL CHECK (status IN ('pending', 'processing', 'failed', 'cancelled', 'completed')),
  image_path TEXT,
  image_mime TEXT,
  description TEXT,
  mode TEXT NOT NULL CHECK (mode IN ('auto', 'label', 'generic')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  max_attempts INTEGER NOT NULL DEFAULT 2 CHECK (max_attempts BETWEEN 1 AND 5),
  lease_until TIMESTAMPTZ,
  last_error_code TEXT,
  idempotency_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, idempotency_key)
);
CREATE INDEX ingredient_ingestion_jobs_claim_idx
  ON ingredient_ingestion_jobs (status, lease_until, created_at);

CREATE TABLE ingredient_ingestion_quota (
  subject_key TEXT NOT NULL,
  window_start TIMESTAMPTZ NOT NULL,
  request_count INTEGER NOT NULL CHECK (request_count >= 0),
  PRIMARY KEY (subject_key, window_start)
);
