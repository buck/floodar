-- Build the AR app's site list as a single JSON document.
-- Run via db/export_sites.sh; output goes to app/src/main/assets/sites.json.
--
-- For each site and scenario:
--   ground_ft   = USGS 10 m DEM at the site (ft NAVD88)
--   wse_ft      = water-surface elevation (ft NAVD88): inverse-distance-weighted
--                 HCFCD high-water marks of that event, restricted to the watershed
--                 of the nearest mark, within 2.5 km; skipped if nearest mark > 1.5 km
--   depth_ft    = wse_ft - ground_ft  (negative = site stayed dry)

WITH site(id, name, area, lat, lon, note, marker) AS (VALUES
  ('meyerland-braesheather', 'Braesheather Dr at Millbury Dr', 'Meyerland',
   29.67764, -95.46445,
   'Deepest Harvey street flooding found in Meyerland (street-point analysis, 10 m DEM).', false),
  ('meyerland-braeswood', 'S Braeswood Blvd near Rice Blvd bridge', 'Meyerland',
   29.67938, -95.45898,
   'Major street along Brays Bayou; easier parking than the residential sites.', false),
  ('meyerland-meyerwood', 'Meyerwood Dr at Cliffwood Dr', 'Meyerland',
   29.68100, -95.45195,
   'North of Brays Bayou near I-610.', false),
  ('clearlake-clc-spacecenter', 'Clear Lake City Blvd near Space Center Blvd', 'Clear Lake',
   29.5914, -95.1395,
   'Approximate site of a 2010 FEMA/Texas Category 4/5 surge marker, removed Jan 2011. Coordinate is the intersection vicinity, not the surveyed footing.', true),
  ('clearlake-bayarea-clhs', 'Bay Area Blvd near Clear Lake High School', 'Clear Lake',
   29.5821, -95.1056,
   'Approximate site of a 2010 FEMA/Texas Category 4/5 surge marker, removed Jan 2011. Coordinate is the school reference point, not the surveyed footing.', true)
),
scenario_def(sid, label, event, field, kind, ord) AS (VALUES
  ('harvey_2017',       'Hurricane Harvey (Aug 2017)',        'harvey_2017',       'HWM_ELEV', 'observed', 1),
  ('memorial_day_2015', 'Memorial Day flood (May 2015)',      'memorial_day_2015', 'HWM_ELEV', 'observed', 2),
  ('tax_day_2016',      'Tax Day flood (Apr 2016)',           'tax_day_2016',      'HWM_ELEV', 'observed', 3),
  ('imelda_2019',       'Tropical Storm Imelda (Sep 2019)',   'imelda_2019',       'HWM_ELEV', 'observed', 4),
  ('hcfcd_100yr',       '100-year flood (1% annual chance)',  'harvey_2017',       'SE100YR',  'modeled',  5),
  ('hcfcd_500yr',       '500-year flood (0.2% annual chance)', 'harvey_2017',      'SE500YR',  'modeled',  6)
),
s AS (
  SELECT site.*, ST_SetSRID(ST_MakePoint(lon, lat), 4326) AS geom,
         (SELECT ST_Value(d.rast, ST_SetSRID(ST_MakePoint(lon, lat), 4269)) * 3.28084
          FROM dem_10m d WHERE ST_Intersects(d.rast, ST_SetSRID(ST_MakePoint(lon, lat), 4269))
          LIMIT 1) AS ground_ft
  FROM site
),
nearest AS (   -- nearest usable mark per site/scenario fixes the watershed
  SELECT s.id, sd.sid, n.ws, n.m
  FROM s CROSS JOIN scenario_def sd
  CROSS JOIN LATERAL (
    SELECT h.attrs->>'WTSHNAME' AS ws, ST_Distance(h.geom::geography, s.geom::geography) AS m
    FROM hwm h
    WHERE h.event = sd.event AND (h.attrs->>sd.field) IS NOT NULL
    ORDER BY h.geom <-> s.geom LIMIT 1) n
  WHERE n.m <= 1500
),
scen AS (
  SELECT s.id, sd.sid, sd.label, sd.kind, sd.ord, n.ws,
         sum((h.attrs->>sd.field)::float / pow(greatest(ST_Distance(h.geom::geography, s.geom::geography), 50), 2))
       / sum(1 / pow(greatest(ST_Distance(h.geom::geography, s.geom::geography), 50), 2)) AS wse_ft,
         jsonb_agg(jsonb_build_object(
           'hwm_id', h.attrs->>'HWMID', 'road', h.attrs->>'ROAD_NAME',
           'elev_ft', round((h.attrs->>sd.field)::numeric, 2),
           'distance_m', round(ST_Distance(h.geom::geography, s.geom::geography)::numeric))
           ORDER BY ST_Distance(h.geom::geography, s.geom::geography)) AS marks
  FROM s
  JOIN nearest n ON n.id = s.id
  JOIN scenario_def sd ON sd.sid = n.sid
  JOIN hwm h ON h.event = sd.event AND h.attrs->>'WTSHNAME' = n.ws
            AND (h.attrs->>sd.field) IS NOT NULL
            AND ST_DWithin(h.geom::geography, s.geom::geography, 2500)
  GROUP BY s.id, sd.sid, sd.label, sd.kind, sd.ord, n.ws
),
scen_json AS (
  SELECT s.id, jsonb_agg(x.j ORDER BY x.ord) AS scenarios
  FROM s
  JOIN LATERAL (
    SELECT sc.ord, jsonb_build_object(
      'id', sc.sid, 'label', sc.label, 'kind', sc.kind,
      'wse_ft_navd88', round(sc.wse_ft::numeric, 1),
      'depth_ft', round((sc.wse_ft - s.ground_ft)::numeric, 1),
      'source', 'HCFCD high-water marks, ' || initcap(sc.ws) || ' watershed',
      'marks', sc.marks) AS j
    FROM scen sc WHERE sc.id = s.id
    UNION ALL
    -- The 2010 marker's public "25-ft surge" figure, read as a water elevation.
    -- One of several possible readings; see fema_storm_surge_markers_report.md.
    SELECT 10, jsonb_build_object(
      'id', 'marker_25ft_as_elevation',
      'label', '2010 marker''s "25-ft surge", read as 25 ft NAVD88',
      'kind', 'interpretation',
      'wse_ft_navd88', 25.0,
      'depth_ft', round((25.0 - s.ground_ft)::numeric, 1),
      'source', 'Bay Area Citizen, 2011-02-01; datum never documented')
    WHERE s.marker
  ) x ON true
  GROUP BY s.id
)
SELECT jsonb_pretty(jsonb_build_object(
  'generated', to_char(now(), 'YYYY-MM-DD'),
  'vertical_datum', 'NAVD88 feet',
  'ground_source', 'USGS 3DEP 1/3 arc-second DEM (~10 m), tile USGS_13_n30w096',
  'method', 'depth_ft = water-surface elevation - ground elevation at the site. Water surface is an inverse-distance-weighted blend of HCFCD high-water marks in the same watershed within 2.5 km. Negative depth = site stayed dry. Expect about +/-1 ft from DEM smoothing.',
  'sites', (SELECT jsonb_agg(jsonb_build_object(
      'id', s.id, 'name', s.name, 'area', s.area,
      'lat', s.lat, 'lon', s.lon,
      'ground_ft_navd88', round(s.ground_ft::numeric, 1),
      'note', s.note,
      'marker', CASE WHEN s.marker THEN jsonb_build_object(
          'kind', 'clear_lake_surge_marker_2010',
          'height_ft', 25,
          'height_note', 'Provisional; later reporting describes ~25-ft-tall signs, no drawing recovered.',
          'bands', jsonb_build_array(
            jsonb_build_object('label', 'CATEGORY 4', 'color', '#2f5fd0'),
            jsonb_build_object('label', 'CATEGORY 5', 'color', '#2e8b57')),
          'band_note', 'Band order and colors from the Blackburn photo (Baker Institute 2023, Fig. 3); band heights unknown.')
        END,
      'scenarios', coalesce(j.scenarios, '[]'::jsonb))
      ORDER BY s.area DESC, s.id)
    FROM s LEFT JOIN scen_json j ON j.id = s.id)
));
