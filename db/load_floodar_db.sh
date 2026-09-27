#!/usr/bin/env bash
# Rebuild floodar-db (PostGIS, 127.0.0.1:5443) from local DEM + houston-db.
# Prereqs: `docker compose up -d` in repo root; houston-db container running;
# data/dem/USGS_13_n30w096.tif present; gdal available via nix-shell.
set -euo pipefail
cd "$(dirname "$0")/.."

FD="docker exec -i floodar-db psql -q -U floodar -d floodar -v ON_ERROR_STOP=1"
HD="docker exec -i houston-db sh -c"
BBOX="ST_MakeEnvelope(-95.52,29.64,-95.41,29.72,4326)"   # SW Houston incl. Meyerland

# 1. Clip DEM (metres NAVD88, NAD83) and load as tiled raster.
mkdir -p data/dem/clip
nix-shell -p gdal --run 'gdal_translate -q -projwin -95.52 29.72 -95.41 29.64 \
    data/dem/USGS_13_n30w096.tif data/dem/clip/meyerland_10m.tif &&
  gdal_translate -q -projwin -95.17 29.61 -95.08 29.56 \
    data/dem/USGS_13_n30w096.tif data/dem/clip/clearlake_10m.tif'
$FD <<'SQL'
CREATE EXTENSION IF NOT EXISTS postgis_raster;
DROP TABLE IF EXISTS dem_10m, hwm, street, channel, harvey_street_depth;
SET postgis.gdal_enabled_drivers = 'GTiff';
CREATE TABLE dem_10m AS
  SELECT row_number() over () AS rid, t AS rast
  FROM (SELECT ST_Tile(ST_FromGDALRaster(pg_read_binary_file('/data/dem/clip/' || f)), 100, 100) t
        FROM unnest(ARRAY['meyerland_10m.tif', 'clearlake_10m.tif']) f) x;
CREATE INDEX ON dem_10m USING gist (ST_ConvexHull(rast));
COMMENT ON TABLE dem_10m IS 'USGS 3DEP 1/3 arc-sec DEM (USGS_13_n30w096), clips: Meyerland -95.52..-95.41 x 29.64..29.72; Clear Lake -95.17..-95.08 x 29.56..29.61. Metres NAVD88.';
CREATE TABLE hwm (event text, objectid bigint, attrs jsonb, geom geometry(Point,4326), PRIMARY KEY(event, objectid));
CREATE TABLE street (objectid bigint PRIMARY KEY, attrs jsonb, geom geometry(MultiLineString,4326));
CREATE TABLE channel (objectid bigint PRIMARY KEY, attrs jsonb, geom geometry(MultiLineString,4326));
SQL

# 2. Copy HWMs (all events), streets, and HCFCD channels from houston-db.
for ev in memorial_day_2015 tax_day_2016 harvey_2017 imelda_2019; do
  $HD "psql -U houston -d houston -c \"COPY (SELECT '$ev', objectid, attrs, geom FROM curated.hazard_hwm_$ev) TO STDOUT\"" \
    | $FD -c "COPY hwm FROM STDIN"
done
$HD "psql -U houston -d houston -c \"COPY (SELECT objectid, attrs, geom FROM curated.streets_pavement_rating WHERE geom && $BBOX) TO STDOUT\"" \
  | $FD -c "COPY street FROM STDIN"
$HD "psql -U houston -d houston -c \"COPY (SELECT objectid, attrs, geom FROM curated.infra_hcfcd_channel WHERE geom && $BBOX) TO STDOUT\"" \
  | $FD -c "COPY channel FROM STDIN"
$FD <<'SQL'
CREATE INDEX ON hwm USING gist(geom);
CREATE INDEX ON street USING gist(geom);
CREATE INDEX ON channel USING gist(geom);
COMMENT ON TABLE hwm IS 'From houston-db curated.hazard_hwm_* (HCFCD HWM FeatureServers). HWM_ELEV = ft NAVD88.';
COMMENT ON TABLE street IS 'From houston-db curated.streets_pavement_rating, clipped to SW Houston bbox.';
COMMENT ON TABLE channel IS 'From houston-db curated.infra_hcfcd_channel, clipped to SW Houston bbox.';
SQL

# 3. Harvey depth along Meyerland streets: points every 15 m, skip within 60 m of
#    a channel (bare-earth DEM under bridges = bayou bottom), WSE = inverse-distance
#    weighted Brays Bayou HWMs, depth = WSE - ground.
$FD <<'SQL'
SET client_min_messages = warning;
CREATE TABLE harvey_street_depth AS
WITH pts AS (
  SELECT s.objectid, s.attrs->>'RoadName' AS road, s.attrs->>'FUNCTIONAL' AS func,
         (ST_DumpPoints(ST_Segmentize(s.geom::geography, 15)::geometry)).geom AS geom
  FROM street s
  WHERE s.geom && ST_MakeEnvelope(-95.49,29.662,-95.44,29.697,4326)
),
pts2 AS (
  SELECT p.* FROM pts p
  WHERE NOT EXISTS (SELECT 1 FROM channel c WHERE ST_DWithin(c.geom::geography, p.geom::geography, 60))
),
g AS (
  SELECT p.*, ST_Value(d.rast, ST_SetSRID(p.geom,4269)) AS ground_m
  FROM pts2 p JOIN dem_10m d ON ST_Intersects(d.rast, ST_SetSRID(p.geom,4269))
),
w AS (
  SELECT g.*,
    (SELECT sum(((h.attrs->>'HWM_ELEV')::float) / pow(greatest(ST_Distance(h.geom::geography, g.geom::geography),50),2))
          / sum(1/pow(greatest(ST_Distance(h.geom::geography, g.geom::geography),50),2))
     FROM hwm h WHERE h.event='harvey_2017' AND h.attrs->>'WTSHNAME' = 'BRAYS BAYOU'
       AND ST_DWithin(h.geom::geography, g.geom::geography, 4000)) AS wse_ft
  FROM g
)
SELECT *, wse_ft - ground_m*3.28084 AS depth_ft FROM w WHERE ground_m IS NOT NULL AND wse_ft IS NOT NULL;
SQL
echo "done"
