-- Aggregated, anonymized stop counts. No raw coordinates, no
-- timestamps tied to an individual, no user identifiers, ever.

CREATE TABLE IF NOT EXISTS heatmap_cells (
    id              BIGSERIAL PRIMARY KEY,
    city            TEXT        NOT NULL,
    geohash         TEXT        NOT NULL,   -- ~7 chars, roughly block-sized
    day_of_week     SMALLINT    NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    hour_bucket     SMALLINT    NOT NULL CHECK (hour_bucket BETWEEN 0 AND 23),
    vehicle_class   TEXT        NOT NULL CHECK (vehicle_class IN ('fast', 'slow')),

    raw_count       INTEGER     NOT NULL DEFAULT 0,
    decayed_weight  DOUBLE PRECISION NOT NULL DEFAULT 0,

    last_updated    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (city, geohash, day_of_week, hour_bucket, vehicle_class)
);

-- Primary lookup pattern: "give me cells for this city/vehicle/day/hour"
CREATE INDEX IF NOT EXISTS idx_heatmap_lookup
    ON heatmap_cells (city, vehicle_class, day_of_week, hour_bucket);

-- Server-tunable config, not hardcoded in app logic.
CREATE TABLE IF NOT EXISTS config (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO config (key, value) VALUES
    ('min_sample_threshold', '8'),
    ('decay_half_life_days', '21'),
    ('max_heatmap_results', '25')
ON CONFLICT (key) DO NOTHING;
