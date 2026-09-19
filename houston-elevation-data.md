# Houston Ground Elevation Data — Sources & Sizing

Notes for the floodar AR app: what ground-height data exists over Houston, where to get it, and how big it is if self-hosted.

Companion doc: [houston-flood-data.md](./houston-flood-data.md) covers water-surface elevation data (Harvey and other flood events), plus a glossary of DEM / NAVD88 / WGS84 / geoid concepts referenced below.

## Key correction

The "high-resolution satellite data" the Rice professor mentioned is almost certainly **airborne LiDAR**, not satellite. Satellite-derived DEMs (SRTM, ASTER GDEM, Copernicus DEM) top out around 30 m resolution — too coarse for flood modeling in a flat city like Houston. All the useful high-res data comes from aircraft-mounted LiDAR, is produced by USGS/state/local agencies, and is free.

## Datasets covering Houston

### USGS 3DEP — TX_Houston_B24 (newest, best)
- Flown 2024, published as "2024 USGS Lidar DEM: Houston, TX"
- **0.5 m** hydro-flattened bare-earth DEM (Class 2 ground returns)
- Coverage ~11,500 sq mi across 4 work units:
  - TX_Houston_1: ~3,734 sq mi
  - TX_Houston_2: ~2,758 sq mi
  - TX_Houston_3: ~3,260 sq mi
  - TX_Houston_4: ~1,812 sq mi
- Spans Harris County and neighbors
- Free GeoTIFFs on AWS S3; point clouds also on AWS (Requester Pays)

### Earlier / complementary
- **2018 TWDB Coastal Texas LiDAR** — 1 m DEM, covers coast including Houston/Galveston
- **Older USGS 3DEP** — 1 m tiles over Harris County
- **Harvey and other flood-event WSE / depth grids** — see [houston-flood-data.md](./houston-flood-data.md)

## Download portals

| Portal | Best for |
|---|---|
| USGS Lidar Explorer — `apps.nationalmap.gov/lidar-explorer/` | Interactive map; click a tile, download DEM |
| USGS 3DEP on AWS — `registry.opendata.aws/usgs-lidar` | Programmatic / bulk access |
| OpenTopography | Nice UI for clipping to an AOI |
| H-GAC (Houston-Galveston Area Council) LiDAR portal | Regional hub, same USGS data with local metadata |
| Harris County Flood Control District (HCFCD) downloads + M3 system | DEMs plus flood models |

USGS standard 3DEP resolutions available nationwide: 1 arc-sec (~30 m), 1/3 arc-sec (~10 m), 1/9 arc-sec (~3 m), 1 m. The 0.5 m Houston product is a regional acquisition, not a standard national tier.

## Size estimates (self-hosting)

Covering the full TX_Houston_B24 area (~11,500 sq mi ≈ 30,000 km²).

### DEM only (bare-earth GeoTIFFs — what the app actually needs)

USGS ships 1/3 arc-second (~10 m) tiles as Float32 with light LZW compression. Real-world size for a 1° land tile is ~465 MB; tiles that are mostly water compress dramatically (n30w095 is 196 MB). Compression on Float32 elevation is limited, so scale roughly linearly with pixel count for the higher-res tiers.

| Resolution | Full TX_Houston_B24 (~30,000 km²) |
|---|---|
| 0.5 m | ~1.8 TB (Float32, light LZW) |
| 1 m | ~450 GB |
| ~10 m (1/3 arc-sec) | ~4.2 GB across 9 tiles (varies with water coverage) |
| ~30 m (1 arc-sec) | ~500 MB |

Per-tile examples (verified via HEAD on `prd-tnm.s3.amazonaws.com`):
- `USGS_13_n30w096.tif` (Houston core): 466 MB
- `USGS_13_n30w095.tif` (eastern Harris County + Gulf): 196 MB

### Full LiDAR point cloud (LAS/LAZ) — much bigger

- 2024 USGS acquisitions typically 8–20 points/m² (QL1/QL0)
- LAZ compression ≈ 2–3 bytes/point
- Ballpark: **1–3 TB** for the full point cloud across all work units

## Architecture recommendations for the AR app

A flood-line renderer only needs "ground elevation at this lat/lon" accurate to ~±0.3 m vertically. Options, easiest first:

1. **Resampled ~30 m DEM, ship in-app** (~500 MB for the whole TX_Houston_B24 extent). Downsample the 10 m tiles once with GDAL; result is small enough to bundle and works offline. Fine for v1.
2. **10 m DEM, server-side or lazy-download** (~4.2 GB full extent, ~660 MB for Harris County core) — GDAL `/elevation?lat=&lon=` endpoint, or pre-fetch tiles the first time a user opens the app in a region.
3. **1 m DEM, server-side** (~450 GB) — only if you want sub-meter terrain edges in AR.
4. **Cloud-Optimized GeoTIFFs directly on S3 (already published by USGS)** — range-request bytes without downloading full tiles. ~$0 storage, pay only egress.

For a v1 that tests plane detection and later renders a flood line on buildings, option 1 is the starting point.

Vertical datum: all these DEMs use **NAVD88** (feet or meters depending on product). Flood elevations from FEMA/HCFCD are also in NAVD88 — you subtract the ground elevation from the target flood elevation to get the flood line height above the user's current location.

Note on AR anchoring: using the DEM to place the ground in world coordinates is likely a stronger anchor than relying on ARCore to visually detect a featureless asphalt street — which may be why prior plane-detection attempts struggled.

## Sources

- <https://www.fisheries.noaa.gov/inport/item/79717> — 2024 USGS Lidar DEM: Houston, TX
- <https://www.usgs.gov/3d-elevation-program> — 3DEP program overview
- <https://apps.nationalmap.gov/lidar-explorer/> — USGS Lidar Explorer map
- <https://registry.opendata.aws/usgs-lidar/> — USGS 3DEP LiDAR on AWS
- <https://equatorstudios.com/texas-coastal-lidar-project-houston-and-surrounds/> — Texas Coastal LiDAR project summary
- <https://www.fisheries.noaa.gov/inport/item/57961> — 2018 TWDB Coastal Texas Lidar
- <https://www.h-gac.com/imagery/lidar> — H-GAC LiDAR & Elevation Data
- <https://www.hcfcd.org/Resources/Downloads/All-Downloads> — HCFCD downloads
- <https://www.hcfcd.org/Resources/Interactive-Mapping-Tools/Model-and-Map-Management-M3-System> — HCFCD M3 system
- <https://www.usgs.gov/3d-elevation-program/about-3dep-products-services> — 3DEP products & services
- <https://pubs.usgs.gov/tm/11/b07/tm11-b7.pdf> — USGS 1-meter DEM specification
