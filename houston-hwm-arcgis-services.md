# Houston HWM ArcGIS Services — per-event flood-event data points

*Companion to [houston-flood-data.md](./houston-flood-data.md), which covers
the broader USGS/FEMA/HCFCD data ecosystem. This doc is narrowly about the
four **HCFCD-published High-Water-Mark FeatureServer** layers on ArcGIS
Online — the primary point-observation dataset for named Houston flood
events. Also documents how to query them out of the shared civic-data
warehouse (`houston-db`, PostGIS on port 5436) where they are now ingested.*

Discovered 2026-09-22 during a full DCAT scan of the Houston open-data hubs.
Loaded into `houston-db` on 2026-09-23.

## The four services

All four sit on a single HCFCD AGOL organization at
`services2.arcgis.com/nLl0k0Mja5hnSeSl`, one FeatureServer per event, each
with a single point layer (`layer 0`).

| Event | Date | Service URL | Points | HWM_ELEV range (ft NAVD88) |
|---|---|---|---:|---|
| Memorial Day flood | 2015-05-26 | `.../HWM20150526/FeatureServer/0` | **92** | 4.8 – 85.8 |
| Tax Day flood | 2016-04-18 | `.../HWM20160418/FeatureServer/0` | **295** | 4.5 – 275.7 |
| Hurricane Harvey | 2017-08-27 | `.../HWM20170827/FeatureServer/0` | **574** | 5.25 – 275.1 |
| Tropical Storm Imelda | 2019-09-19 | `.../HWM20190919/FeatureServer/0` | **203** | 4.4 – 139.5 |

**Total: 1,164 HWM points across four events.**

Each point is one HCFCD field crew observation of the peak water surface —
mud lines, debris lines, seed lines in vegetation, staining on structures,
plus benchmark ties for elevation control. This is the calibration data that
would feed a hydraulic model producing the actual WSE raster; it is **not**
a continuous flood surface by itself. See the *Interpolating to a surface*
section for options.

The max ~275 ft NAVD88 readings on Tax Day and Harvey are not errors —
they're from far-upstream sites in western / northwestern Harris County
where ground elevations run 200+ ft. HWM elevation = water surface
elevation, not depth.

The elevations are all **NAVD88 feet**. For ARCore rendering (WGS84
ellipsoidal metres) subtract ~26 m and convert units — see
[houston-flood-data.md](./houston-flood-data.md#navd88-and-why-houston-is-off-by-26-m)
for the datum conversion.

## Extent per event

Bounding boxes in WGS84 (`lon_min, lat_min → lon_max, lat_max`):

| Event | Lon min | Lat min | Lon max | Lat max |
|---|---:|---:|---:|---:|
| Memorial Day 2015 | -95.66 | 29.65 | -95.21 | 29.85 |
| Tax Day 2016      | -95.94 | 29.65 | -95.06 | 30.17 |
| Harvey 2017       | -95.94 | 29.50 | -94.90 | 30.24 |
| Imelda 2019       | -95.69 | 29.65 | -94.90 | 30.26 |

Harvey covers essentially the full Harris County + eastward into the
San Jacinto watershed. Memorial Day 2015 was a much more localized event
(central/west Harris only, ~half the extent).

## Field schema (as observed, per record)

Schema is nearly identical across all four events. Consistent core fields:

| Field | Type | AR-render use | Notes |
|---|---|---|---|
| `HWMID` | text | none | unique HWM identifier |
| `HWM_ELEV` | float | **primary WSE input** | water surface elevation, NAVD88 ft |
| `FINAL_HWM` | float | preferred over HWM_ELEV | post-QC finalized elevation |
| `HWM_CONF` | text | filter | confidence: Excellent / Good / Medium / Poor |
| `HWM_DESC` | text | tooltip | what the surveyor observed ("Debris line in rocks", "Mud line on wall", ...) |
| `HWM_TYPE` | text | context | BRIDGE / STRUCTURE / VEGETATION / SENSOR ... |
| `HWM_LOC` | text | context | Upstream / Downstream / Left Bank / ... |
| `POINT_X` | float | redundant | longitude (WGS84) — same info as geometry |
| `POINT_Y` | float | redundant | latitude (WGS84) |
| `FLD_DATE` | date | context | official event date (matches service name) |
| `SRVY_DATE` | date | context | when the crew visited (usually 3–14 days after peak) |
| `WTSHNAME` | text | grouping | watershed name (CLEAR CREEK, WHITE OAK BAYOU, ...) |
| `WTSHUNIT` | text | grouping | HCFCD watershed unit letter |
| `CHAN_NAME` | text | grouping | specific channel |
| `ROAD_NAME` | text | UI | nearest road (for tooltip) |
| `BM_NO`, `BM_ELEV`, `BM_DESC` | mixed | provenance | tied benchmark: number, its elevation, its description |
| `SE10YR`, `SE50YR`, `SE100YR`, `SE500YR` | float | **bonus** | modeled surface elevations at 10/50/100/500-year recurrence at this location. Directly usable as "future / hypothetical flood" WSE inputs at the HWM sites |
| `STAGEGAGE`, `SENSOR_PEAK_ELEV` | mixed | cross-ref | linked USGS/HCFCD gauge id and peak reading if collocated |
| `SRVY_CO`, `PARTY_CHIEF`, `TEAM_MEM` | text | provenance | who surveyed (HCFCD staff or contractor) |
| `CHK_DATE`, `EditDate`, `CreationDate` | date | provenance | QC and edit history |

Imelda 2019 adds a few extra fields (43 total vs Harvey's 42); Tax Day 2016
has fewer (39). All four share the primary `HWM_ELEV` + geometry that the
AR app cares about.

The **`SE100YR` / `SE500YR` bonus fields deserve a callout**: at every HWM
point, the record carries HCFCD's own regulatory model outputs for the 100-
and 500-year recurrence-interval water surface elevations. That is a
ready-made *future/hypothetical* flood data source, colocated with the
observed event data. For the AR app's "hypothetical floods" mode you can
render an SE100YR plane just as easily as a Harvey HWM_ELEV plane, with no
extra ingestion.

## Query paths

### From the warehouse (recommended for the AR app)

The four events are loaded into `houston-db` as:

```
curated.hazard_hwm_memorial_day_2015    -- 92 points
curated.hazard_hwm_tax_day_2016         -- 295 points
curated.hazard_hwm_harvey_2017          -- 574 points
curated.hazard_hwm_imelda_2019          -- 203 points
```

Each table has a uniform shape:

```sql
objectid          bigint PRIMARY KEY
attrs             jsonb           -- all 39–43 source fields
geom              geometry(Point, 4326)
source_layer_id   bigint  -> catalog.source
updated_at        timestamptz
```

Both `geom` and `attrs->>'HWM_ELEV'` are indexed (gist / gin). Typical AR
lookup — HWMs near a target point, ranked by distance:

```sql
SELECT
    attrs->>'HWM_ELEV'      AS wse_ft_navd88,
    attrs->>'HWM_CONF'      AS confidence,
    attrs->>'HWM_DESC'      AS description,
    attrs->>'WTSHNAME'      AS watershed,
    ST_Distance(geom::geography, ST_MakePoint(:lon, :lat)::geography) AS m
FROM curated.hazard_hwm_harvey_2017
WHERE ST_DWithin(geom::geography, ST_MakePoint(:lon, :lat)::geography, 2000)
ORDER BY m
LIMIT 20;
```

Cross-event query (any of the four floods within 500 m):

```sql
SELECT '2015 Memorial' AS event, attrs->>'HWM_ELEV' AS wse_ft, ST_Distance(geom::geography, ST_MakePoint(:lon,:lat)::geography) AS m
FROM curated.hazard_hwm_memorial_day_2015 WHERE ST_DWithin(geom::geography, ST_MakePoint(:lon,:lat)::geography, 500)
UNION ALL SELECT '2016 Tax Day', attrs->>'HWM_ELEV', ST_Distance(geom::geography, ST_MakePoint(:lon,:lat)::geography)
FROM curated.hazard_hwm_tax_day_2016 WHERE ST_DWithin(geom::geography, ST_MakePoint(:lon,:lat)::geography, 500)
UNION ALL SELECT '2017 Harvey', attrs->>'HWM_ELEV', ST_Distance(geom::geography, ST_MakePoint(:lon,:lat)::geography)
FROM curated.hazard_hwm_harvey_2017 WHERE ST_DWithin(geom::geography, ST_MakePoint(:lon,:lat)::geography, 500)
UNION ALL SELECT '2019 Imelda', attrs->>'HWM_ELEV', ST_Distance(geom::geography, ST_MakePoint(:lon,:lat)::geography)
FROM curated.hazard_hwm_imelda_2019 WHERE ST_DWithin(geom::geography, ST_MakePoint(:lon,:lat)::geography, 500);
```

### Directly from the ArcGIS REST endpoints (no warehouse)

Raw point-count check:

```
GET https://services2.arcgis.com/nLl0k0Mja5hnSeSl/arcgis/rest/services/HWM20170827/FeatureServer/0/query
    ?where=1=1&returnCountOnly=true&f=json
```

Bounded pull for a target area (10 km around Meyerland):

```
GET .../HWM20170827/FeatureServer/0/query
    ?where=1=1
    &geometry=-95.46,29.66,-95.36,29.76
    &geometryType=esriGeometryEnvelope
    &inSR=4326
    &outFields=HWM_ELEV,HWM_CONF,HWM_DESC,HWM_TYPE,SE100YR,SE500YR,WTSHNAME
    &outSR=4326
    &returnGeometry=true
    &f=geojson
```

Rate limits: none observed at 3 req/s with a real User-Agent. `maxRecordCount`
is 1000 (Harvey/Imelda) or 2000 (Tax Day/Memorial Day), so full pulls per
event are 1 request each.

## Interpolating to a surface for AR rendering

The AR app needs a WSE value at *any* (lat, lon), not just at the ~200 HWM
sites. A few options ordered by fidelity:

1. **Nearest-HWM lookup with distance-weighted blend of the top N** — trivial
   to implement in SQL (`ORDER BY ST_Distance LIMIT 5`, take a weighted
   mean). Cheap, but only meaningful within ~500 m of an HWM; gets weird
   across drainage divides.

2. **Inverse-distance weighting (IDW) with a hydrologic mask** — restrict
   the neighbourhood used for interpolation to HWMs in the *same watershed*
   (`WTSHNAME` field). Cross-watershed averaging is meaningless — Buffalo
   Bayou HWMs shouldn't inform White Oak Bayou coverage even if they're
   500 m apart across a divide. Same-watershed IDW handles the bulk case.

3. **Ordinary kriging** — proper geostatistical interpolation with an
   uncertainty estimate. Overkill for 574 sparse points across ~2000 km²,
   and the semi-variogram is anisotropic along stream networks anyway.

4. **Constrained interpolation over the channel network** — use HCFCD's
   channel centerlines (`Stormdrain Open Drains` etc. in the stormwater
   service) as a 1D backbone, interpolate along the channel, then extend
   perpendicular using ground DEM contours until you hit a "no flood
   here" cell. This is what HCFCD's own hydraulic models do internally.
   Best fidelity, most work.

5. **Just use the USGS Harvey raster** — for Harvey specifically, don't
   interpolate; use USGS Texas Water Science Center's continuous WSE
   raster ([houston-flood-data.md §Harvey WSE / depth
   data](./houston-flood-data.md#harvey-wse--depth-data-for-harris-county)).
   HWMs are still useful for QC and for the tooltip layer ("USGS-measured
   peak here: 47.2 ft NAVD88").

For a v1 of the four-event AR mode, option (2) — same-watershed IDW —
strikes the right balance. Fall back to option (5) for Harvey when the app
needs regional fidelity.

## What is *not* in the Houston ArcGIS catalog

Two entries caught my eye during the scan; both turned out to be dead ends:

- **"Floodplain"** in the `geohub` DCAT feed is a link to
  `geogimsprod.houstontx.gov/Html5Viewer/index.html?viewer=PublicFloodplain` —
  a webapp viewer, not a queryable REST service. FEMA FIRM 100/500 yr
  floodplain polygons live at
  `hazards.fema.gov/gis/nfhl/rest/services/public/NFHL/MapServer` (FEMA's
  own ArcGIS), not on Houston's hubs.
- **"Houston Water Flood Hazards"** in the `houston-mycity` DCAT feed is
  similarly a webappviewer URL, no data endpoint.
- **"2008 Ponding"** (layer 28 of the `TDO/StormwaterUtilities` MapServer)
  is *not* an event map. Its own description: *"These areas do not represent
  actual ponding from a given storm, but the minimum depth of ponding
  required to produce overland flow when a storm sewer is absent or
  rainfall is above the storm sewer's designed capacity."* It's a
  design-basis hydrologic model output with **10,451,343** polygons. Not
  usable as a flood-event dataset.

Actual per-event inundation *polygons* for Memorial Day / Tax Day / Harvey /
Imelda are published by HCFCD on the M3 map viewer at hcfcd.org, but only
as downloadable shapefile ZIPs — no REST/FeatureServer API. That is a
separate ingestion job outside this ArcGIS scan.

## Warehouse provenance

Ingested via the generic loader at
`~/projects/civicdata/etl/arcgis_loader.py`, driven by
`~/projects/civicdata/etl/starter_layers.yaml`. Provenance rows in
`catalog.source` and per-run log in `catalog.ingest_run`:

```sql
SELECT slug, service_url, layer_id, row_count
FROM catalog.source
WHERE slug LIKE 'hwm_%'
ORDER BY slug;
```

All four came in via Path B (paginated `/query`) since Path A (Hub Downloads
API) rejects items registered outside the queried hub — the HCFCD org is
distinct from the `geohub.houstontx.gov` Hub that publishes them via DCAT.

## Sources

- HCFCD FeatureServer landing: <https://services2.arcgis.com/nLl0k0Mja5hnSeSl/arcgis/rest/services>
- Companion doc — datums, WSE vs depth, subsidence, USGS/FEMA sources: [houston-flood-data.md](./houston-flood-data.md)
- Companion doc — ground DEM: [houston-elevation-data.md](./houston-elevation-data.md)
- Warehouse ETL: `~/projects/civicdata/etl/` (loader, YAML, migrations)
- ArcGIS layer catalog scan: `~/projects/civicdata/catalog/CATALOG.md`
