ALTER TABLE ingredient_ingestion_jobs
  ADD COLUMN image_paths JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN image_mimes JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE ingredient_ingestion_jobs
SET image_paths = jsonb_build_array(image_path),
    image_mimes = jsonb_build_array(image_mime)
WHERE image_path IS NOT NULL AND image_mime IS NOT NULL;
