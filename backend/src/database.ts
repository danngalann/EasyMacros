import { Pool } from "pg";
import type { QueryResultRow } from "pg";

export interface Database {
  close(): Promise<void>;
  isReady(): Promise<boolean>;
  query<Row extends QueryResultRow = QueryResultRow>(
    sql: string,
    values?: readonly unknown[],
  ): Promise<{ rows: Row[] }>;
  transaction<T>(callback: (database: QueryDatabase) => Promise<T>): Promise<T>;
}

export interface QueryDatabase {
  query<Row extends QueryResultRow = QueryResultRow>(
    sql: string,
    values?: readonly unknown[],
  ): Promise<{ rows: Row[] }>;
}

export function createDatabase(connectionString: string): Database {
  const pool = new Pool({ connectionString, max: 5 });

  return {
    async close() {
      await pool.end();
    },
    async isReady() {
      try {
        const result = await pool.query<{ ready: boolean }>(
          "SELECT EXISTS (SELECT 1 FROM schema_migrations WHERE name = $1) AS ready",
          ["0003_add_nutrition_domain.sql"],
        );
        return result.rows[0]?.ready === true;
      } catch {
        return false;
      }
    },
    async query<Row extends QueryResultRow>(sql: string, values?: readonly unknown[]) {
      const result = values ? await pool.query<Row>(sql, [...values]) : await pool.query<Row>(sql);
      return { rows: result.rows };
    },
    async transaction<T>(callback: (database: QueryDatabase) => Promise<T>) {
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const result = await callback({
          async query<Row extends QueryResultRow>(sql: string, values?: readonly unknown[]) {
            const result = values
              ? await client.query<Row>(sql, [...values])
              : await client.query<Row>(sql);
            return { rows: result.rows };
          },
        });
        await client.query("COMMIT");
        return result;
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
    },
  };
}
