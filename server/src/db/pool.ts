import { Pool } from "pg";

// TODO: load DATABASE_URL via dotenv in index.ts before this is imported.
export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});
