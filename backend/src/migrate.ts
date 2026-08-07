import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

import { Pool } from "pg";

import { loadConfig } from "./config.js";

const migrationsDirectory = fileURLToPath(new URL("../migrations", import.meta.url));
const pool = new Pool({ connectionString: loadConfig().databaseUrl });

try {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      name TEXT PRIMARY KEY,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    )
  `);

  const migrationNames = (await readdir(migrationsDirectory))
    .filter((name) => name.endsWith(".sql"))
    .sort();

  const appliedResult = await pool.query<{ name: string }>("SELECT name FROM schema_migrations");
  const appliedNames = new Set(appliedResult.rows.map((row) => row.name));

  for (const migrationName of migrationNames) {
    if (appliedNames.has(migrationName)) {
      continue;
    }

    const sql = await readFile(`${migrationsDirectory}/${migrationName}`, "utf8");
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(sql);
      await client.query("INSERT INTO schema_migrations (name) VALUES ($1)", [migrationName]);
      await client.query("COMMIT");
      console.log(`Applied migration ${migrationName}`);
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }
} finally {
  await pool.end();
}
