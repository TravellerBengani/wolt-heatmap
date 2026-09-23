import { Router } from "express";
import { z } from "zod";
import { pool } from "../db/pool.js";

const StopSchema = z.object({
  city: z.string().min(1).max(64),
  geohash: z.string().length(7),
  day_of_week: z.number().int().min(0).max(6),
  hour_bucket: z.number().int().min(0).max(23),
  vehicle_class: z.enum(["fast", "slow"]),
});

const BatchSchema = z.array(StopSchema).min(1).max(500);

export const stopsRouter = Router();

stopsRouter.post("/batch", async (req, res) => {
  const parsed = BatchSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "invalid_body", details: parsed.error.issues });
    return;
  }

  const stops = parsed.data;
  let client;

  try {
    client = await pool.connect();

    const cfgRow = await client.query<{ value: string }>(
      "SELECT value FROM config WHERE key = 'decay_half_life_days'"
    );
    const halfLifeDays = parseFloat(cfgRow.rows[0]?.value ?? "21");

    await client.query("BEGIN");

    for (const stop of stops) {
      // On conflict: decay existing weight by time elapsed since last write, then add 1.
      // This implements write-time exponential decay with the configured half-life.
      await client.query(
        `INSERT INTO heatmap_cells
           (city, geohash, day_of_week, hour_bucket, vehicle_class,
            raw_count, decayed_weight, last_updated)
         VALUES ($1, $2, $3, $4, $5, 1, 1.0, now())
         ON CONFLICT (city, geohash, day_of_week, hour_bucket, vehicle_class) DO UPDATE
           SET raw_count      = heatmap_cells.raw_count + 1,
               decayed_weight = heatmap_cells.decayed_weight
                                  * pow(0.5,
                                      EXTRACT(EPOCH FROM (now() - heatmap_cells.last_updated))
                                      / 86400.0 / $6)
                                + 1.0,
               last_updated   = now()`,
        [stop.city, stop.geohash, stop.day_of_week, stop.hour_bucket, stop.vehicle_class, halfLifeDays]
      );
    }

    await client.query("COMMIT");
    res.json({ accepted: stops.length });
  } catch (err) {
    await client?.query("ROLLBACK").catch(() => {});
    console.error("stops/batch failed:", err);
    res.status(500).json({ error: "internal_error" });
  } finally {
    client?.release();
  }
});
