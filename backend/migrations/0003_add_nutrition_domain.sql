CREATE TABLE nutrient_definitions (
  code TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('macro', 'sugar', 'fat', 'vitamin', 'mineral', 'other')),
  unit TEXT NOT NULL CHECK (unit IN ('g', 'mg', 'ug')),
  display_order INTEGER NOT NULL,
  goal_allowed BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO nutrient_definitions (code, display_name, category, unit, display_order, goal_allowed) VALUES
  ('protein', 'Protein', 'macro', 'g', 10, TRUE),
  ('carbohydrates', 'Carbohydrates', 'macro', 'g', 20, TRUE),
  ('fat', 'Fat', 'macro', 'g', 30, TRUE),
  ('sugars', 'Sugars', 'sugar', 'g', 40, FALSE),
  ('saturated_fat', 'Saturated fat', 'fat', 'g', 50, FALSE),
  ('fiber', 'Fiber', 'other', 'g', 60, FALSE),
  ('sodium', 'Sodium', 'mineral', 'mg', 70, FALSE),
  ('vitamin_a', 'Vitamin A', 'vitamin', 'ug', 80, FALSE),
  ('vitamin_c', 'Vitamin C', 'vitamin', 'mg', 90, FALSE),
  ('calcium', 'Calcium', 'mineral', 'mg', 100, FALSE),
  ('iron', 'Iron', 'mineral', 'mg', 110, FALSE)
ON CONFLICT (code) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  category = EXCLUDED.category,
  unit = EXCLUDED.unit,
  display_order = EXCLUDED.display_order,
  goal_allowed = EXCLUDED.goal_allowed;

CREATE TABLE ingredients (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 200),
  brand TEXT,
  barcode TEXT,
  hidden BOOLEAN NOT NULL DEFAULT FALSE,
  source TEXT NOT NULL DEFAULT 'manual',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ingredients_user_search_idx ON ingredients (user_id, hidden, name, id);

CREATE TABLE ingredient_nutrients (
  ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  nutrient_code TEXT NOT NULL REFERENCES nutrient_definitions(code),
  amount_per_100g NUMERIC(14, 6) NOT NULL CHECK (amount_per_100g >= 0),
  PRIMARY KEY (ingredient_id, nutrient_code)
);

CREATE TABLE ingredient_revisions (
  id UUID PRIMARY KEY,
  ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  snapshot JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE nutrient_candidates (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source TEXT NOT NULL CHECK (source = 'manual'),
  status TEXT NOT NULL CHECK (status IN ('ready', 'confirmed')) DEFAULT 'ready',
  suggested_name TEXT NOT NULL,
  brand TEXT,
  barcode TEXT,
  nutrients JSONB NOT NULL,
  version UUID NOT NULL,
  confirmed_ingredient_id UUID REFERENCES ingredients(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX nutrient_candidates_user_status_idx ON nutrient_candidates (user_id, status, created_at DESC);

CREATE TABLE recipes (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  current_version_id UUID,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX recipes_user_active_idx ON recipes (user_id, deleted_at, updated_at DESC, id);

CREATE TABLE recipe_versions (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
  version_number INTEGER NOT NULL CHECK (version_number > 0),
  name TEXT NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 200),
  yield_grams NUMERIC(14, 6) NOT NULL CHECK (yield_grams > 0),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (recipe_id, version_number)
);
ALTER TABLE recipes ADD CONSTRAINT recipes_current_version_fk
  FOREIGN KEY (current_version_id) REFERENCES recipe_versions(id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE recipe_components (
  id UUID PRIMARY KEY,
  recipe_version_id UUID NOT NULL REFERENCES recipe_versions(id) ON DELETE CASCADE,
  ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
  ingredient_name TEXT NOT NULL,
  quantity_grams NUMERIC(14, 6) NOT NULL CHECK (quantity_grams > 0),
  nutrient_snapshot JSONB NOT NULL
);

CREATE TABLE recipe_version_nutrients (
  recipe_version_id UUID NOT NULL REFERENCES recipe_versions(id) ON DELETE CASCADE,
  nutrient_code TEXT NOT NULL REFERENCES nutrient_definitions(code),
  amount NUMERIC(14, 6) NOT NULL CHECK (amount >= 0),
  PRIMARY KEY (recipe_version_id, nutrient_code)
);

CREATE TABLE rations (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipe_version_id UUID NOT NULL REFERENCES recipe_versions(id) ON DELETE RESTRICT,
  consumed_grams NUMERIC(14, 6) NOT NULL CHECK (consumed_grams > 0),
  consumed_at TIMESTAMPTZ NOT NULL,
  consumed_local_date DATE NOT NULL,
  timezone TEXT NOT NULL,
  recipe_name TEXT NOT NULL,
  recipe_yield_grams NUMERIC(14, 6) NOT NULL CHECK (recipe_yield_grams > 0),
  note TEXT,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX rations_user_history_idx ON rations (user_id, consumed_local_date, consumed_at DESC, id) WHERE deleted_at IS NULL;

CREATE TABLE ration_nutrients (
  ration_id UUID NOT NULL REFERENCES rations(id) ON DELETE CASCADE,
  nutrient_code TEXT NOT NULL REFERENCES nutrient_definitions(code),
  amount NUMERIC(14, 6) NOT NULL CHECK (amount >= 0),
  PRIMARY KEY (ration_id, nutrient_code)
);

CREATE TABLE goal_profiles (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  effective_local_date DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (user_id, effective_local_date)
);

CREATE TABLE goal_profile_nutrients (
  goal_profile_id UUID NOT NULL REFERENCES goal_profiles(id) ON DELETE CASCADE,
  nutrient_code TEXT NOT NULL REFERENCES nutrient_definitions(code),
  target_amount NUMERIC(14, 6) NOT NULL CHECK (target_amount > 0),
  PRIMARY KEY (goal_profile_id, nutrient_code)
);
CREATE INDEX goal_profiles_effective_idx ON goal_profiles (user_id, effective_local_date DESC);