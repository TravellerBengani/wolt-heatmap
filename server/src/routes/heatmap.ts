import { Router } from "express";
import { z } from "zod";
import { pool } from "../db/pool.js";

const QuerySchema = z.object({
  city: z.string().min(1).max(64),
  vehicle_class: z.enum(["fast", "slow"]),
  day_of_week: z.coerce.number().int().min(0).max(6),
  hour_bucket: z.coerce.number().int().min(0).max(23),
});

export const heatmapRouter = Router();

heatmapRouter.get("/", async (req, res) => {
  const parsed = QuerySchema.safeParse(req.query);
  if (!parsed.success) {
    res.status(400).json({ error: "invalid_query", details: parsed.error.issues });
    return;
  }

  const { city, vehicle_class, day_of_week, hour_bucket } = parsed.data;

  try {
    const cfgRows = await pool.query<{ key: string; value: string }>(
      "SELECT key, value FROM config WHERE key IN ('min_sample_threshold', 'decay_half_life_days', 'max_heatmap_results')"
    );
    const cfg = Object.fromEntries(cfgRows.rows.map((r) => [r.key, r.value]));
    const threshold = parseInt(cfg["min_sample_threshold"] ?? "8", 10);
    const halfLifeDays = parseFloat(cfg["decay_half_life_days"] ?? "21");
    const maxResults = parseInt(cfg["max_heatmap_results"] ?? "25", 10);

    // Apply decay forward to now so ranking reflects current relevance, not the
    // weight as it was at last_updated (which could be months ago).
    const result = await pool.query<{ geohash: string; decayed_weight: number }>(
      `SELECT geohash,
              decayed_weight * pow(0.5,
                EXTRACT(EPOCH FROM (now() - last_updated)) / 86400.0 / $5
              ) AS decayed_weight
       FROM heatmap_cells
       WHERE city          = $1
         AND vehicle_class = $2
         AND day_of_week   = $3
         AND hour_bucket   = $4
         AND raw_count     >= $6
       ORDER BY 2 DESC
       LIMIT $7`,
      [city, vehicle_class, day_of_week, hour_bucket, halfLifeDays, threshold, maxResults]
    );

    // Return a bare array — matches what the Android Retrofit interface expects.
    res.json(result.rows);
  } catch (err) {
    console.error("heatmap query failed:", err);
    res.status(500).json({ error: "internal_error" });
  }
});
