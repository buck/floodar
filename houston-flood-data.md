# Houston Flood-Level Data — Sources & Concepts

Companion to [houston-elevation-data.md](./houston-elevation-data.md). That doc
covers **ground** elevation (the DEM). This one covers **water surface**
elevation (flood levels, mostly Harvey), plus the concepts needed to combine
the two into a flood height the AR app can render.

## What the app is actually computing

For a given (lat, lon) the app needs to place a horizontal plane at the water
surface and intersect it against Streetscape's building/terrain mesh. Three
quantities are involved:

- **Ground elevation** at that point — from the DEM (see companion doc).
- **Water surface elevation (WSE)** at that point during the event — from a
  USGS/FEMA/HCFCD flood product.
- **Depth** = WSE − ground elevation. Useful for the UI ("4.2 ft at this
  house"), but not what the renderer consumes.

The renderer wants **WSE in the same vertical reference as ARCore's altitude**
(WGS84 ellipsoidal metres). That means every WSE value from a US flood product
(published in NAVD88 feet) has to be datum-converted before use. See the
Concepts section for what those words mean.

## Concepts (glossary for the newcomer)

### DEM (Digital Elevation Model)
A raster — a grid of pixels — where each pixel's value is the ground-surface
elevation at that spot. "Bare earth" DEMs (what floodar uses) strip out
buildings and trees; DSMs (Digital Surface Models) keep them. LiDAR produces
both because the laser returns include tree canopy, building tops, and the
ground under them; post-processing classifies each point.

### Raster vs GeoTIFF
"Raster" = pixel grid. "GeoTIFF" = a TIFF image file with extra tags telling
you which real-world coordinates each pixel corresponds to (CRS, pixel size,
origin). Everything in this pipeline — DEM, WSE grid, depth grid — is a
GeoTIFF with Float32 values (elevation in metres or feet).

### CRS (Coordinate Reference System) and datum
A CRS is a full spec for turning coordinates into a location on Earth. It has
a horizontal part (lat/lon or projected x/y) and a vertical part (the
**datum** — the zero surface heights are measured from). Two datasets can use
the same lat/lon but different vertical datums and disagree by tens of
metres. This is the source of the 26 m surprise below.

### Ellipsoid vs geoid — the two "sea levels"
Earth is not a sphere and its surface gravity isn't uniform, so there are two
different reference surfaces people use for "zero height":

- **Ellipsoid** — a mathematically smooth, slightly flattened sphere (WGS84
  is the standard one). Purely geometric, has no physical meaning, but it's
  what GPS satellites naturally compute against. **Ellipsoidal height** =
  perpendicular distance from this smooth ellipsoid to your point.

- **Geoid** — the actual bumpy equipotential surface that mean sea level
  would follow if the oceans were connected under the continents. Bumpy
  because gravity varies (denser rock pulls water higher, deep ocean
  trenches pull it lower). **Orthometric height** = height above the geoid,
  which matches everyday intuition — water flows downhill relative to it, a
  lake at rest has constant orthometric height.

The geoid and ellipsoid differ by the **geoid separation N** (also called
geoid undulation): N = h − H, where h is ellipsoidal height and H is
orthometric. N varies from about +85 m to −100 m worldwide.

### NAVD88 and why Houston is off by ~26 m
**NAVD88** (North American Vertical Datum of 1988) is the US standard
orthometric datum — every USGS/FEMA/HCFCD flood elevation, every benchmark,
every building permit is in NAVD88 feet. It approximates height above the
geoid.

**WGS84 ellipsoidal** is what ARCore Geospatial returns from
`Earth.getCameraGeospatialPose().getAltitude()` — height above the WGS84
ellipsoid.

At Houston (~29.75°N, 95.4°W) the geoid model **GEOID18** gives N ≈ **−26 m**
— the geoid sits about 26 m below the ellipsoid here. Rearranging:

    h_ellipsoidal = H_NAVD88 + N ≈ H_NAVD88 − 26 m

So a Harvey water-surface elevation of, say, 47 ft NAVD88 (14.3 m) is really
at ~−11.7 m WGS84 ellipsoidal. If you skip the conversion and feed the raw
NAVD88 metres into ARCore, your flood plane will render 26 m above the actual
water line — the whole neighbourhood underwater.

Use `pyproj` with `Transformer.from_crs("EPSG:6318+EPSG:5703",
"EPSG:4979", …)` (or GEOID18/GEOID12B directly) to do this properly. The
correction is not constant — it varies by a few centimetres across Harris
County — but a fixed −26 m is good to within ~10 cm for a v1.

### WSE vs depth grids
Flood products come in two flavours:

- **WSE grid** — each pixel is the elevation of the water surface (NAVD88 ft
  or m). Continuous surface across the flood extent.
- **Depth grid** — each pixel is `WSE − ground_DEM`. Zero (or nodata) outside
  the flood extent.

For AR rendering, WSE is the right input (see "What the app is computing"
above). Depth grids are the derived product; they're what you see in every
news graphic ("6 feet of water in Meyerland"). If a dataset only publishes
depth, you can reconstruct WSE by adding your ground DEM back — but expect
the two DEMs (theirs vs. yours) to disagree slightly and produce edge
artifacts.

### HWM (High Water Mark)
A single measured point where surveyors observed the peak water line after
the event — mud line on a wall, seed lines in vegetation, debris caught in
fences. USGS collected ~2,100 for Harvey across TX/LA. HWMs are the
ground-truth data used to calibrate and validate the WSE rasters above.

### Subsidence
Parts of Harris County have literally sunk — several feet in the worst spots
(Jersey Village, FM 1960 corridor, north-central Harris) since the 1970s —
due to groundwater withdrawal. Historically the highest rates were in
north-central and northwest Harris; after HGSD's Regulatory Areas 1/2/3
forced conversion to surface water in the 2000s, those rates dropped
sharply and the active-subsidence hotspot has migrated west/southwest
(Katy, western Fort Bend, Waller County), where groundwater pumping
continues.

Implication for floodar: if you use a 2024 DEM with a 2017 Harvey WSE, the
depth computation is biased by whatever subsidence occurred between those
dates. In modern north-central Harris this is typically 5–20 cm total over
that 7-year window (not disastrous, but visible in AR at the sidewalk
scale). In the active-subsidence west/southwest hotspot it can exceed
30 cm and is worth explicitly correcting.

**Data sources — north-central Harris and countywide:**

- **HGSD interactive subsidence rate maps** (ArcGIS Online): the most recent
  is the 2021–2025 InSAR+GPS-combined rate map. Contour lines in cm/yr; the
  underlying feature service is queryable via the ArcGIS REST API, so you
  can pull polygons/rasters programmatically.
  - Landing: <https://hgsubsidence.org/science-research/district-research/monitoring-the-land-surface-from-space/>
  - Map: <https://hgsd.maps.arcgis.com/apps/mapviewer/index.html?webmap=c1d181be5e8446fca29885fb439defd4>
- **HGSD historical InSAR (1992–2023)** — multi-temporal InSAR processed in
  partnership with SMU. Covers the whole 30-year record; useful if you want
  cumulative subsidence at a point (e.g., for a "how much has this house
  dropped since Harvey" overlay).
- **HGSD PAM (Port-A-Measure) borehole extensometers** — ~30 stations across
  the district including several in north-central Harris (Addicks, Lake
  Houston, East End). Provides point-source ground-truth for the InSAR
  rasters. Annual reports at <https://hgsubsidence.org/science-research/>.
- **USGS Houston-Galveston Subsidence Studies** — publishes periodic fact
  sheets and data releases with rate maps. Search USGS ScienceBase for
  "Houston-Galveston subsidence."
- **Wang et al. (University of Houston GPS lab)** — publishes GNSS-based
  vertical velocity time series for stations across the region; some of the
  earliest peer-reviewed cm/yr rate maps specifically for north-central
  Harris are from this group.

For app use: pull the current HGSD rate raster (or query the ArcGIS
FeatureServer at a point) → integrate rate × Δt between DEM epoch and
event epoch → apply as a small vertical correction before the flood-plane
render. Constant-rate approximation is fine; the actual rate has changed
year-to-year but the residual error is tiny compared to the datum
conversion.

### COG (Cloud-Optimized GeoTIFF)
A GeoTIFF laid out so an HTTP client can `Range:`-request just the tile
covering a bounding box, without downloading the whole file. USGS 3DEP and
some FEMA products publish COGs on S3. Ideal for a mobile app that only
needs elevation at one lat/lon — you can pull ~100 KB instead of a 500 MB
tile.

## Harvey WSE / depth data for Harris County

All open, all downloadable as GeoTIFF unless noted.

### USGS Harvey inundation products (best fit for floodar)
The USGS Texas Water Science Center published a Data Release with
water-surface elevation rasters and depth grids for Harvey across the
Houston metro. This is the closest thing to a canonical dataset.

- Search ScienceBase for "Hurricane Harvey water-surface elevation" or the
  USGS publication "Documenting flood inundation … Hurricane Harvey."
- Coverage: Harris County thickly; extends into surrounding counties.
- Format: GeoTIFF, NAVD88 feet, ~3 m or 10 m grid depending on subarea.

### FEMA MOTF Harvey depth grids
Wider TX/LA coverage, produced by FEMA's Modeling Task Force during the
response. Depth-only (no separate WSE raster); you'd reconstruct WSE by
adding a DEM.
- FEMA open data portal / HIFLD; also mirrored on ArcGIS Hub.

### HCFCD Harvey inundation model
From HCFCD's post-Harvey "Immediate Flood Report" and the follow-on
**MAAPnext** study. County-scale, methodology-documented.
- <https://www.hcfcd.org> — Downloads and M3 map viewer.

### USGS Flood Event Viewer — HWMs and rapid deployment gauges
~2,100 high-water marks + 15-minute sensor traces from ~200 rapid-deployment
gauges. Point data (not a raster), but ground-truth for the products above,
and useful if you want to render specific plaques in AR ("USGS-measured
peak here: 47.2 ft NAVD88").
- <https://stn.wim.usgs.gov/fev/>

## Beyond Harvey — broader Houston flood data

For future events, real-time overlays, or the "historical floods" mode:

- **HCFCD Flood Warning System** (`harriscountyfws.org`) — ~150 rain and
  stream gauges, live 15-min data plus historical archive. Every major event
  since ~2000 is queryable.
- **USGS NWIS** — federal gauges, real-time stream stage across Harris
  County. Live API at `https://waterservices.usgs.gov/nwis/`.
- **NOAA/NWS AHPS** — river forecast points with historical crests, useful
  for "the Bayou has hit N feet 12 times since 1990" context.
- **FEMA NFHL** (National Flood Hazard Layer) — the 100-yr / 500-yr
  floodplain polygons, published as GeoTIFF and shapefile.
- **Copernicus EMS EMSR229** — satellite-derived Harvey flood-extent
  polygons (Sentinel-1).
- **Dartmouth Flood Observatory** — global historical archive; Harvey
  shapefile available.

## Pipeline for the app

1. **Pre-compute**: rasterize each event's WSE grid to a resolution that
   matches or is coarser than your ground DEM. Store as COG on S3 (or bundle
   for offline).
2. **At runtime**: given user (lat, lon, event),
   a. Query WSE raster → get value in NAVD88 ft.
   b. Convert to WGS84 ellipsoidal metres: `h = 0.3048 * H_ft − 26.0`
      (constant −26 m is fine to ~10 cm across Harris County; use pyproj
      +GEOID18 for anything more precise).
   c. Pass `h` to ARCore as the flood-plane altitude. Intersect against
      Streetscape meshes as planned in the [floodar memory][floodar-mem].
3. **UI**: separately query ground DEM at (lat, lon) → compute
   `depth = WSE − ground` → display "4.2 ft" alongside the AR view. This is
   for the user, not the renderer.

Gotcha checklist:

- [ ] Units: NAVD88 products come in feet OR metres depending on publisher.
      Check the metadata.
- [ ] Datum: never assume; always convert NAVD88 → WGS84 ellipsoidal.
- [ ] Subsidence: for 2017 Harvey WSE + 2024 DEM, apply an HGSD-rate-map
      correction (5–20 cm in north-central Harris, 20–30+ cm in the western
      hotspot) if you want < 0.5 ft accuracy. See Concepts §Subsidence for
      HGSD/USGS/UH-GPS data sources.
- [ ] Extent: WSE rasters have holes / nodata outside the flood. Handle
      "point not in flood" cleanly rather than reading garbage.

## Sources

- <https://www.usgs.gov/mission-areas/water-resources/science/hurricane-harvey> — USGS Harvey portal
- <https://stn.wim.usgs.gov/fev/> — USGS Flood Event Viewer (HWMs, RDG data)
- <https://www.fema.gov/about/openfema/data-sets> — FEMA MOTF depth grids
- <https://www.hcfcd.org/Resources/Interactive-Mapping-Tools/Model-and-Map-Management-M3-System> — HCFCD M3
- <https://harriscountyfws.org> — HCFCD live/historical gauges
- <https://waterservices.usgs.gov/nwis/> — USGS NWIS live API
- <https://water.weather.gov/ahps/> — NOAA/NWS AHPS forecast points
- <https://msc.fema.gov/nfhl> — FEMA NFHL floodplain layer
- <https://emergency.copernicus.eu/mapping/list-of-components/EMSR229> — Copernicus Harvey activation
- <https://geoidobs.ngs.noaa.gov/GEOID18/> — NOAA NGS GEOID18 (for datum conversion)
- <https://hgsubsidence.org/> — Harris–Galveston Subsidence District (landing)
- <https://hgsubsidence.org/science-research/district-research/monitoring-the-land-surface-from-space/> — HGSD InSAR land-surface monitoring program
- <https://hgsd.maps.arcgis.com/apps/mapviewer/index.html?webmap=c1d181be5e8446fca29885fb439defd4> — HGSD 2021–2025 subsidence-rate map (ArcGIS; FeatureServer queryable via REST)
- <https://hgsubsidence.org/science-research/> — HGSD annual reports and PAM extensometer data

[floodar-mem]: ../../.claude/projects/-home-buck/memory/project_floodar.md
