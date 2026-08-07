import { Pool } from "pg";
import type { QueryResultRow } from "pg";

export interface Database {
  close(): Promise<void>;
  isReady(): Promise<boolean>;
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
        await pool.query("SELECT 1");
        return true;
      } catch {
        return false;
      }
    },
    async query<Row extends QueryResultRow>(sql: string, values?: readonly unknown[]) {
      const result = values ? await pool.query<Row>(sql, [...values]) : await pool.query<Row>(sql);
      return { rows: result.rows };
    },
  };
}
