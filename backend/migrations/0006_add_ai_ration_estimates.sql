ALTER TABLE rations
  ALTER COLUMN recipe_version_id DROP NOT NULL,
  ADD COLUMN source TEXT NOT NULL DEFAULT 'recipe'
    CHECK (source IN ('recipe', 'ai')),
  ADD COLUMN confidence NUMERIC(4, 3),
  ADD COLUMN warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN provenance JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE ration_ai_estimates (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'ready' CHECK (status IN ('ready', 'confirmed', 'expired')),
  suggested_name TEXT NOT NULL CHECK (char_length(trim(suggested_name)) BETWEEN 1 AND 200),
  estimated_weight_grams NUMERIC(14, 6) NOT NULL CHECK (estimated_weight_grams > 0),
  nutrients JSONB NOT NULL,
  confidence NUMERIC(4, 3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
  version UUID NOT NULL,
  confirmed_ration_id UUID REFERENCES rations(id),
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ration_ai_estimates_user_status_idx
  ON ration_ai_estimates (user_id, status, created_at DESC);
