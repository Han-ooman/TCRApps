/**
 * Migrasi sederhana: jalankan isi schema.sql ke DATABASE_URL.
 * Dipakai sekali runtime (npm run migrate) di alur deploy lokal.
 */
require("dotenv").config();
const fs = require("fs");
const path = require("path");
const { Pool } = require("pg");

const DATABASE_URL = process.env.DATABASE_URL || "";
if (!DATABASE_URL) {
  console.error("DATABASE_URL belum di-set di .env");
  process.exit(1);
}

const pool = new Pool({
  connectionString: DATABASE_URL,
  ssl: /localhost|127\.0\.0\.1/.test(DATABASE_URL) ? false : { rejectUnauthorized: false },
});

(async () => {
  const sql = fs.readFileSync(path.join(__dirname, "schema.sql"), "utf8");
  await pool.query(sql);
  console.log("Migrasi selesai.");
  await pool.end();
})().catch((e) => {
  console.error("Migrasi gagal:", e.message);
  process.exit(1);
});
