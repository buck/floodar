# floodar — Project Status

Snapshot for picking up after a reboot. Last updated 2026-09-22.

## Goal (from CLAUDE.md)

Flutter AR app for Android + iOS that, when held up outdoors, detects the ground plane and renders past/future flood water lines on surrounding structures. GPS-driven lookup of high-res ground elevation from external sources. Focus of v1: how well outdoor plane detection works and stays locked — flood rendering comes later.

## Repo state

- Branch: `main` (clean history so far, no branches in progress)
- Recent commits:
  - `be4af58` Docs: add Houston flood-level data companion + concepts glossary
  - `552ac72` V1: fork geospatial_java sample as com.compact.floodar
- Uncommitted changes:
  - `houston-flood-data.md` — modified, not staged
- Untracked:
  - `data/` — contains downloaded DEM tiles (see below). Not currently in `.gitignore`; large binaries should probably stay out of git.

## What's in the repo

### Code
- `app/`, `build.gradle`, `gradle*`, `settings.gradle` — Android Java scaffold forked from the ARCore `geospatial_java` sample, package `com.compact.floodar`. **Not yet migrated to Flutter.**

### Docs (research, not committed to yet)
- `houston-elevation-data.md` — ground-elevation data sources & sizing (USGS 3DEP, TWDB, HCFCD, H-GAC). Corrected 10 m size numbers based on real HEAD requests.
- `houston-flood-data.md` — Harvey and other flood-event water-surface elevation data, plus glossary of DEM / NAVD88 / WGS84 / geoid concepts. Modified this session.

### Data (downloaded, ~662 MB, untracked)
- `data/dem/USGS_13_n30w096.tif` — 466 MB. USGS 3DEP 1/3 arc-second (~10 m) DEM. Tile covers 29–30°N × 95–96°W (Houston core + western Harris County).
- `data/dem/USGS_13_n30w095.tif` — 196 MB. Same product; tile covers 29–30°N × 94–95°W (eastern Harris County + upper Galveston Bay). Smaller because ~half is water.

Both are Float32 GeoTIFFs, 10,812 × 10,812, LZW-compressed. Vertical datum NAVD88.

## Where we left off

Research phase mostly done. **Not yet started on the app rewrite.** Immediate open questions / next moves:

1. **Toolkit decision** — Flutter with `ar_flutter_plugin` or `arcore_flutter_plugin`? Or keep Java + ARCore Geospatial API and skip Flutter for v1? The existing fork is Java. Flutter cross-platform is the CLAUDE.md preference but adds friction on top of ARCore's Java-first APIs.
2. **DEM handling** — three viable paths, documented in `houston-elevation-data.md`:
   - Resample the 2 downloaded tiles to ~30 m and bundle in-app (~500 MB → shippable if trimmed to Harris County only, probably <100 MB).
   - Stand up a tiny elevation-lookup service (GDAL / `rio-tiler`) and query by lat/lon.
   - Range-read USGS Cloud-Optimized GeoTIFFs directly from S3.
3. **Datum plumbing** — GPS gives WGS84 ellipsoidal height; DEMs and flood elevations are NAVD88 orthometric. Need geoid model (GEOID18 for Houston) to convert. Details in the flood-data glossary.
4. **Plane-detection experiment** — the actual v1 goal. Get ARCore's outdoor plane detection working reliably on Houston streets/lawns before wiring in DEM data or flood levels.

## Suggested first move after reboot

Decide toolkit direction (Flutter vs. keep Java), then either:
- start the Flutter port of the geospatial sample, or
- add a minimal DEM-lookup helper to the existing Java app so you can compute "meters above ground" at the phone's current GPS point — a small, testable increment.

## Housekeeping

- Consider adding `data/` to `.gitignore` before the next commit — the DEM tiles shouldn't land in git.
- `houston-flood-data.md` edits are unsaved to git; commit if the changes were intentional.
