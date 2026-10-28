/**
 * TCRApps API — gerbang data untuk Android app.
 * Semua kredensial dibaca dari env (Fly.io secrets / file .env lokal).
 */
require("dotenv").config();
const express = require("express");
const { Pool } = require("pg");

const PORT = process.env.PORT || 8080;
const API_TOKEN = process.env.API_TOKEN || "";
const DATABASE_URL = process.env.DATABASE_URL || "";

if (!API_TOKEN) console.warn("[warn] API_TOKEN belum di-set: endpoint data terbuka!");
if (!DATABASE_URL) console.warn("[warn] DATABASE_URL belum di-set: fitur DB tidak aktif!");

const pool = DATABASE_URL
  ? new Pool({
      connectionString: DATABASE_URL,
      ssl: /localhost|127\.0\.0\.1/.test(DATABASE_URL)
        ? false
        : { rejectUnauthorized: false },
      max: 5,
    })
  : null;

// Inisialisasi skema otomatis (idempotent) saat boot.
async function initSchema() {
  if (!pool) return;
  try {
    const sql = require("fs").readFileSync(require("path").join(__dirname, "schema.sql"), "utf8");
    await pool.query(sql);
    console.log("[init] schema ready");
  } catch (e) {
    console.error("[init] schema error:", e.message);
  }
}
initSchema();

const app = express();
app.use(express.json({ limit: "1mb" }));

// Auth sederhana: header x-api-key
function auth(req, res, next) {
  if (!API_TOKEN || req.get("x-api-key") === API_TOKEN) return next();
  return res.status(401).json({ error: "unauthorized" });
}

function requireDb(_req, res, next) {
  if (!pool) return res.status(503).json({ error: "database not configured" });
  next();
}

app.get("/health", async (_req, res) => {
  let db = "unconfigured";
  if (pool) {
    try {
      await pool.query("select 1");
      db = "ok";
    } catch {
      db = "error";
    }
  }
  res.json({ ok: true, db, version: "0.1.0-alpha01" });
});

// Buat trip baru
app.post("/trips", auth, requireDb, async (req, res) => {
  const deviceId = String(req.body?.deviceId || "unknown").slice(0, 64);
  try {
    const { rows } = await pool.query(
      `insert into trips (device_id) values ($1) returning id, device_id, started_at`,
      [deviceId]
    );
    res.status(201).json(rows[0]);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Selesaikan trip (simpan total jarak)
app.post("/trips/:id/finish", auth, requireDb, async (req, res) => {
  const distance = Number(req.body?.distanceMeters || 0);
  try {
    const { rows } = await pool.query(
      `update trips set finished_at = now(),
              distance_m = $2
        where id = $1 and finished_at is null
      returning id, distance_m, started_at, finished_at`,
      [req.params.id, distance]
    );
    if (!rows.length) return res.status(404).json({ error: "trip not found or already finished" });
    res.json(rows[0]);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Kirim titik GPS (per-supported batch array, atau satu objek)
app.post("/trips/:id/points", auth, requireDb, async (req, res) => {
  const items = Array.isArray(req.body) ? req.body : [req.body];
  const insertOne = async (t) => {
    await pool.query(
      `insert into track_points (trip_id, lat, lon, speed_kmh, accuracy_m, heading)
       values ($1,$2,$3,$4,$5,$6)`,
      [
        req.params.id,
        Number(t.lat),
        Number(t.lon),
        t.speedKmh != null ? Number(t.speedKmh) : null,
        t.accuracyM != null ? Number(t.accuracyM) : null,
        t.heading != null ? Number(t.heading) : null,
      ]
    );
  };
  try {
    for (const it of items.slice(0, 1000)) await insertOne(it);
    res.status(201).json({ inserted: items.length });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Lihat isi trip
app.get("/trips/:id", auth, requireDb, async (req, res) => {
  try {
    const trip = (await pool.query(`select * from trips where id = $1`, [req.params.id])).rows[0];
    if (!trip) return res.status(404).json({ error: "trip not found" });
    const points = (
      await pool.query(
        `select id, ts, lat, lon, speed_kmh, accuracy_m, heading
           from track_points where trip_id = $1 order by ts asc limit 10000`,
        [req.params.id]
      )
    ).rows;
    res.json({ trip, points });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Daftar trip terbaru
app.get("/trips", auth, requireDb, async (req, res) => {
  try {
    const { rows } = await pool.query(
      `select id, device_id, started_at, finished_at, distance_m
         from trips order by started_at desc limit 50`
    );
    res.json(rows);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.listen(PORT, () => {
  console.log(`TCRApps API listening on :${PORT}`);
});
