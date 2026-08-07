import { Pool } from "pg";

export interface Database {
  close(): Promise<void>;
  isReady(): Promise<boolean>;
  query(sql: string): Promise<void>;
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
    async query(sql) {
      await pool.query(sql);
    },
  };
}
