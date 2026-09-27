# floodar — How It Works

*Houston Hackathon 2026. Written 2026-09-27.*

floodar is an Android augmented-reality app. You hold up the phone outdoors and it shows how
deep a past or modeled flood would be where you're standing. It draws a water surface over
the street and a waterline on the surrounding buildings, and you can drop a depth gauge on the
ground. It can also bring back a virtual copy of the Category 4/5 storm-surge marker that
FEMA and the State of Texas put up in Clear Lake in 2010. The City of Houston removed that
marker in early 2011.

---

## 1. The core idea: depth above ground

What you see depends on two numbers:

1. **Where the ground is.** ARCore measures this live from the phone's camera.
2. **How deep the water is above that ground**, in the chosen place and flood:

   ```
   depth_ft = water-surface elevation (ft NAVD88) − ground elevation (ft NAVD88)
   ```

Number 2 is computed ahead of time from public data, so the app doesn't need GPS to render a
flood. This enables two ways to use it:

- **On site:** stand at the location and you see its actual flood depth, on its actual houses.
- **Teleport:** stand anywhere, such as your own street. You see the depth from a chosen
  Houston site at your own curb, against your own neighbors' houses. The display says clearly
  which site the depth comes from.

**Why the app can't fake the GPS location instead:** ARCore's Geospatial API finds the phone's
position mostly by matching the camera image against Google Street View imagery (Google calls
this VPS). GPS is only a secondary input. Faking the GPS with Android's mock-location feature
while the camera sees a different street would either fail to lock on or snap back to the real
location. Anchoring objects at exact real-world coordinates only works when you are physically
there. Teleport mode avoids this problem: it moves the *depth*, not the phone.

**Why not trust the phone's altitude?** GPS/VPS altitude is WGS84 ellipsoidal height with
meter-scale error. Flood data is in NAVD88, which in Houston differs from WGS84 by roughly
26 m (the geoid offset). Measuring depth from the locally detected ground avoids both problems.
Across a neighborhood, the error then comes down to ground-elevation data accuracy (about
±1 ft).

---

## 2. Data pipeline

### 2.1 Sources

| Dataset | Content | Vertical reference |
|---|---|---|
| USGS 3DEP 1/3 arc-second DEM (`USGS_13_n30w096`) | Bare-earth ground elevation, ~10 m grid | NAVD88, metres |
| HCFCD High-Water Marks (HWM) — 4 events | Surveyed peak water surface at points: Memorial Day 2015 (92), Tax Day 2016 (295), Harvey 2017 (574), Imelda 2019 (203) | NAVD88, feet |
| HCFCD modeled surfaces (fields `SE100YR`, `SE500YR` on each HWM) | 100-year (1%) and 500-year (0.2%) water-surface elevations at each mark | NAVD88, feet |
| City of Houston street centerlines (pavement-rating layer) | Street geometry | — |
| HCFCD channel centerlines | Bayous and ditches, used to exclude channels and bridges | — |

The HWM, street and channel layers were originally loaded into the shared civic-data warehouse
(`houston-db`, in a separate project). To keep projects separate, they are **copied** into this
project's own database.

### 2.2 `floodar-db`

- A PostGIS 17 container, `floodar-db`, on `127.0.0.1:5443`, defined in `docker-compose.yml`.
  Credentials are in `.env`, which git ignores.
- `db/load_floodar_db.sh` rebuilds it from scratch:
  1. Clips the DEM to two areas with GDAL — SW Houston/Meyerland (−95.52…−95.41,
     29.64…29.72) and Clear Lake (−95.17…−95.08, 29.56…29.61) — and loads them as a tiled
     PostGIS raster (`dem_10m`).
  2. Copies all four HWM events (`hwm`), plus the streets (`street`) and channels (`channel`)
     inside the SW Houston box, from `houston-db`.
  3. Computes Harvey flood depth along every Meyerland street (`harvey_street_depth`).

### 2.3 Finding the worst-flooded street in Meyerland

HCFCD high-water marks are almost all taken at **bayou bridges**, so they give the water
surface along the channel but not the depth on residential streets. To get street depths:

1. Place a point every 15 m along every street segment in Meyerland (~15,000 points).
2. Drop points within 60 m of an HCFCD channel. The bare-earth DEM removes bridges, so the
   "ground" under a bridge reads as the bayou bottom.
3. Look up the DEM ground elevation at each point.
4. Estimate the Harvey water-surface elevation at each point as an **inverse-distance-weighted
   (IDW) blend** of the Brays Bayou high-water marks within 4 km, weighted by 1/distance²
   (distances under 50 m count as 50 m).
5. Depth = water surface − ground.

**Result:** the deepest street flooding is at **Braesheather Dr at Millbury Dr (29.67764,
−95.46445)**. Ground there is 46.1 ft NAVD88 and the Harvey water surface was 53.9 ft, so
**about 7.8 ft of water** stood on the street. Nearby streets were close behind: Cranleigh Ct 7.4 ft,
Braeswood Blvd 7.3–7.6 ft, Heatherglen Dr 7.1 ft, Meyerwood/Cliffwood 7.0 ft. About 1,000 of
the ~15,000 street points had more than 5 ft.

This agrees with a field note on HWM D-0028 at Chimney Rock: "debris line in bushes in median
that corresponds to seed line on households in area."

### 2.4 The site list (`app/src/main/assets/sites.json`)

`db/sites.sql` defines the sites. `db/export_sites.sh` computes every site's scenarios in the
database and writes the JSON file the app loads. For each site and scenario:

- `ground_ft` = DEM at the site.
- `wse_ft` = IDW blend of that event's high-water marks. It only uses marks in the watershed of
  the nearest mark and within 2.5 km, and the scenario is skipped if the nearest mark is more
  than 1.5 km away.
- `depth_ft` = `wse_ft − ground_ft`. A negative value means the site stayed dry.
- Each scenario lists the high-water marks it was built from, with their distances.

| Site | Ground (ft NAVD88) | Harvey 2017 | Memorial Day 2015 | Tax Day 2016 | 100-yr | 500-yr | Marker "25 ft" as elevation |
|---|---|---|---|---|---|---|---|
| Braesheather Dr at Millbury Dr (Meyerland) | 46.1 | **7.8** | 6.4 | 5.2 | 7.7 | 9.2 | — |
| S Braeswood Blvd near Rice Blvd (Meyerland) | 46.0 | 7.6 | 5.5 | 3.8 | 7.3 | 8.8 | — |
| Meyerwood Dr at Cliffwood Dr (Meyerland) | 45.9 | 7.0 | 5.0 | 3.3 | 6.2 | 7.7 | — |
| Clear Lake City Blvd near Space Center Blvd | 20.7 | 0.3 | — | — | −0.9 | 0.9 | 4.3 |
| Bay Area Blvd near Clear Lake High School | 23.3 | −7.8 (dry) | — | — | −9.4 | −7.7 | 1.7 |

Values are feet of water above the ground. The file also includes Imelda 2019 (0.1–1.8 ft for
the Meyerland sites). Those figures come only from marks taken at the bayou, so they are low
confidence.

At the Harvey high-water mark nearest Braesheather (D-0027, Rice Blvd bridge), HCFCD's
**100-year** level (54.3 ft) is essentially the same as Harvey's observed peak (54.1 ft). The
**500-year** level (55.7 ft) would put about 9–10 ft of water on the street.

### 2.5 Ground truth from a resident: the model understates street depth

One team member lived through Harvey in a house near Brays Bayou, about 800 m from the
nearest HCFCD high-water mark (D-0022, Buffalo Speedway). They left three pieces of evidence:

1. **Debris-line photo at the front door** (2017-09-03, a week after the peak). The faux-brick
   siding courses measure 7⅝ in per 3 courses (2.54 in each), which makes the wall a ruler at
   ~85 px/in in the photo. The debris line sits **4.0 in above the porch floor** and **3.1 in
   below the door threshold**. The concrete slab is 1¾ in below the threshold (¾ in wood floor
   plus ~1 in to the threshold strip), so the peak came within **about 1.4 in of the slab**.
2. **Street video** (2017-08-27, 1:41 pm CDT, somewhat after the peak). Standing mid-street,
   the water is **just above the top of the kneecap**: 23¼ in off the pavement in the sandals
   worn that day (24 in measured in running shoes), so **about 24 in ± 1 in**. A small bow
   wave forms around the legs. Water covers the street curb to curb and reaches the lawn edges.
   Kayaks pass farther down the street.
3. **The same method as the site list** gives a Harvey water surface of 47.85 ft NAVD88 at the
   nearest mark and 47.8 ft of ground from the 10 m DEM, i.e. **about 0 ft of water**.

**Conclusion:** at this house the model understates street depth by **more than 2 ft**, since
the video was taken after the peak. The two observations together also bound the house's
elevation: the porch sits at least ~20 in above the street crown (24 in − 4 in, plus however
much the water fell between the peak and the video). Likely causes of the model error:

- The 10 m DEM averages the low, crowned street together with the higher yards and house pads.
- The high-water mark is at the bayou, 800 m away. With the bayou full, rainfall in the
  neighborhood couldn't drain, so local water may have stood higher than the bayou itself.

This is why the app supports **observed** scenarios measured from a local reference surface
(section 4.4). They don't depend on the DEM or the datum at all. The resident's site is kept
in a gitignored `sites_local.json` so the home address never reaches the repository.

The video also set the **look** of the rendered water (section 4.3). Flood water in a Houston
street is opaque muddy khaki, not blue. Looking toward the horizon it's a mirror of the overcast
sky, and the flow shows as long ripple streaks.

---

## 3. The Clear Lake marker and the datum question

The 2010 marker was labeled "Category 4" (blue, lower) and "Category 5" (green, upper). The
public message was a **"25-foot storm surge"**. See `fema_storm_surge_markers_report.md` for the
history. Two problems:

1. **Hurricane category is a poor predictor of surge height.** In 2009 the National Hurricane
   Center removed storm-surge ranges from the Saffir-Simpson scale. Surge depends on storm
   size, track, forward speed and landfall location, not only on wind speed.
2. **"25 feet" was never tied to a vertical reference.** It could mean surge above normal tide,
   a water-surface elevation in a datum, or depth above local ground. At these sites the
   difference is huge:

   - Ground at the Clear Lake City Blvd site is **20.7 ft NAVD88**. Ground at the Bay Area Blvd
     site is **23.3 ft**.
   - If "25 ft" meant a water surface of 25 ft NAVD88, the water would have been only
     **4.3 ft and 1.7 ft deep** at those spots, far below the ~20 ft the pole visually implied.

Harvey barely reached these sites: 0.3 ft of water at Clear Lake City Blvd, and Bay Area Blvd
stayed dry. **Caveat:** the Clear Lake high-water marks and the 100/500-year levels are
*rainfall* (riverine) floods on Armand Bayou, not storm surge. A real surge scenario needs
NHC SLOSH products (MOM/MEOW worst-case maps by hurricane category), which are not yet loaded.

In the app, the marker is shown as a **reconstruction**. It's drawn as a provisional 25 ft pole
with band proportions estimated from Jim Blackburn's photo (Baker Institute 2023, Fig. 3):
about 38% blue, 47% green and 15% grey cap. Next to it, the gauge shows the depth the data
actually supports.

---

## 4. The app

### 4.1 Platform

- Android, Java. Forked from Google's ARCore `geospatial_java` sample as package
  `com.compact.floodar`.
- ARCore features used: **plane detection** (horizontal ground), the **Geospatial API** (VPS
  localization), and **Streetscape Geometry** (Google's 3D building and terrain meshes around
  the user).
- Tested device: Pixel 6a.

### 4.2 Choosing a flood

**MENU** button (bottom right, always visible) → **Flood site…** opens a two-step picker. First choose a site,
grouped by area. Then choose a scenario; each one shows its depth and the surface it's measured
from ("7 ft 10 in above ground", "4 in above porch"), or "dry". The toast tells you which
surface to tap. The choice is saved and restored the next time the app starts.

Scenario kinds: **observed** (a high-water mark or a resident's observation), **modeled**
(HCFCD 100/500-year levels), and **interpretation** (the 2010 marker's "25 ft" read as an
elevation).

### 4.3 What gets drawn

With a site and scenario selected:

- **Water surface.** A sheet at the water level, centered on the phone, extending ~80 m and
  fading out from 15 m to 80 m. Its look is modeled on the Harvey street video:
  - **opaque muddy khaki** when you look down (88% opaque; you can't see the pavement)
  - it becomes a **mirror of the grey sky** toward the horizon (a Fresnel term)
  - **long flow streaks** and finer chop move across it
  - seen **from below** (water over your head), it's a darker, murky ceiling
  It's hidden if the spot stayed dry.
- **Flood line on buildings.** Google's Streetscape building and terrain meshes are colored by
  height relative to the water: a **muddy stain** below the water level and a **pale debris
  line** at it. Above the water they're invisible but still hide the water surface behind them,
  so houses correctly block the water beyond them. The line's width grows with distance so it
  stays a few pixels thick far away. Because this uses Google's 3D city model, it works on
  buildings at any distance, well beyond where the phone can detect planes.
- **Depth gauge.** Where you tap the ground, a pole with alternating red and white **1-foot
  stripes** rises to 1 ft above the water level.
- **Marker reconstruction** (Clear Lake sites only). At the tapped point, a 25 ft
  blue/green/grey pole next to the gauge.

### 4.4 Reference surface and "water at your feet"

Water height = reference surface + scenario depth. Each scenario names its reference: `ground`
(the default for DEM-based sites), `street`, or `porch` (for observations like "the debris line
was 4.0 in above the porch floor").

1. **Tapped reference.** Tap the named surface: a detected plane, or Google's terrain mesh. An
   ARCore anchor keeps that point fixed as tracking refines. Tapping again moves it.
2. Without a tap, the reference is the **ground under the phone**. Every 10 frames the app casts
   a ray straight down from the phone and takes the first upward-facing detected plane, or else
   Google's terrain mesh, smoothed (80% old value, 20% new). If nothing is detected, it
   **assumes the phone is 1.4 m above the ground**.

The downward probe runs all the time, so the readout also shows **water at your feet**: water
level minus the ground detected under the phone. Tap the porch at the debris-line height, walk
into the street, and the app reports the street depth itself. At the resident's house that
should come out near the knee-deep water in the 2017 video, a direct check of ARCore's height
accuracy against a known flood.

**Why plane detection alone isn't enough:** in earlier tests, ARCore plane detection outdoors
only covered about 20 m around the phone. It didn't reach the buildings, up lawns or down
streets. The design therefore uses detection only for the **height** of the ground under the
user. A single patch is enough, because the water surface is flat. Everything farther away,
including buildings and distant terrain, comes from Google's Streetscape Geometry.

### 4.5 Localization and tracking

The original sample drew no 3D content until VPS localization finished. floodar changes this:

- **Planes, the water surface and the gauge** need only normal ARCore camera tracking. They
  appear right away, even where VPS fails.
- **The building flood line** needs Streetscape Geometry, which comes from the Geospatial
  service. In testing, meshes arrived while VPS was still localizing, but they only line up
  with the real buildings once VPS reports LOCALIZED. That requires Street View coverage,
  which most Houston streets have.

### 4.6 On-screen readout

A blue panel at the top of the screen updates four times a second:

```
Meyerland — Braesheather Dr at Millbury Dr
Hurricane Harvey (Aug 2017): 7 ft 10 in above ground
Reference: tapped ground
Water at your feet: 7 ft 9 in  (phone 4.6 ft up)
Planes: 3 (42 m²)  Buildings: 17  VPS: LOCALIZED
```

`● REC` or `▶ REPLAY` is prefixed while recording or playing back (section 4.7).

The **Planes** line reports how many upward-facing planes are tracked and their total area.
This is the measurement for the project's original question: how well ARCore detects and keeps
the outdoor ground.

### 4.7 Session recording and playback

The menu can record an AR session and replay it later, without going back to the site:

- **Record session / Stop recording.** Uses ARCore Recording & Playback to save the camera
  video plus motion-sensor and location data as an MP4 file in
  `/sdcard/Android/data/com.compact.floodar/files/recordings/floodar-YYYYMMDD-HHMMSS.mp4`.
  Files are about **2.5 MB per second**, so a 2-minute walk is about 300 MB. Recording stops
  automatically if the app is paused.
- **Play back recording…** lists recordings, newest first. Choosing one restarts the AR session
  on that recording instead of the live camera. Tracking, planes, Geospatial data and the flood
  rendering all run as if you were there again, so you can **pick a different flood during
  playback** or re-render after a code change. The readout shows `▶ REPLAY`.
- **Back to live camera** closes the playback session and opens a fresh live session. ARCore
  1.56 throws an error if you try to clear the playback dataset instead.
- Copy recordings to a computer with
  `adb pull /sdcard/Android/data/com.compact.floodar/files/recordings/`.

For demo **video**, use Android's built-in screen recorder (Quick Settings → Screen record).
The app draws the camera image and the overlay together, so the screen recording captures
exactly what is on screen. Combining the two: record the session once at the site, then
screen-record its playback later with the final rendering.

---

## 5. Limitations

- **Building shapes are simplified.** Streetscape buildings are extruded box footprints. Trees,
  fences, cars and porches are not in them, so the waterline can appear "through" objects that
  stand in front of a building.
- **The building overlay is only as good as Google's localization.** If VPS is off by a meter,
  the building meshes and their waterline are off by the same amount.
- **Ground elevation is from a 10 m DEM.** It smooths out the street crown and gutter, so expect
  ±1 ft. HCFCD's 1 m lidar would improve this.
- **The water surface between marks is an estimate.** It's an inverse-distance blend of nearby
  high-water marks, not a hydraulic model. That's reasonable for Meyerland's broad, flat
  flooding during Harvey, and less so in steep or channelized terrain.
- **Teleport mode shows depth, not terrain.** Your street's own slope is not the slope at the
  source site.
- **The marker's dimensions are provisional.** No drawing, surveyed location or datum
  documentation has been found. See the report's list of recommended records requests.
- **Clear Lake has no surge scenario yet.** Only rainfall floods and the literal
  "25 ft NAVD88" reading are available.

---

## 6. Field test procedure

1. Go outdoors in daylight. ARCore needs light and visible texture on the ground.
2. Open the app, accept the camera and location permissions, and pick a flood site and scenario.
3. Slowly sweep the phone across the ground until planes appear. Watch the **Planes** count.
4. Point the phone at nearby buildings until **VPS** reads `LOCALIZED` and **Buildings** is
   above 0.
5. Tap the street to lock the ground. The gauge appears there.
6. Check that the waterline sits at the gauge's water level and on the houses.
7. Record the screen for the demo.

---

## 7. Build and install

```bash
# JDK 17 comes from NixOS (environment.sessionVariables.JAVA_HOME = pkgs.jdk17.home).
# Gradle 8.6 / AGP 8.4 — JDK 17 or 21. Gradle 8.6 can't run on JDK 22+.
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

An ARCore API key is required for Geospatial. It goes in `local.properties` as
`arcore.api.key`.

Rebuild the data:

```bash
docker compose up -d            # floodar-db on 127.0.0.1:5443
./db/load_floodar_db.sh         # DEM clips + copy from houston-db + street depths
./db/export_sites.sh            # regenerate app/src/main/assets/sites.json
```

---

## 8. Code map

| Path | Role |
|---|---|
| `app/src/main/java/com/compact/floodar/GeospatialActivity.java` | AR session, render loop, ground estimate, tap handling, site picker, readout |
| `app/src/main/java/com/compact/floodar/FloodRenderer.java` | Draws the water surface, building/terrain flood coloring, gauge and marker |
| `app/src/main/java/com/compact/floodar/FloodSite.java` | Model and loader for `sites.json` |
| `app/src/main/assets/shaders/flood*.{vert,frag}` | Flood shaders (mesh coloring by height, water surface, solid color) |
| `app/src/main/assets/sites.json` | Generated site list |
| `app/src/main/assets/sites_local.json` | Personal sites (resident's home); **gitignored**, loaded if present |
| `db/load_floodar_db.sh`, `db/sites.sql`, `db/export_sites.sh` | Data pipeline |
| `docker-compose.yml` | `floodar-db` container |
| `fema_storm_surge_markers_report.md` | History of the Clear Lake marker |
| `houston-hwm-arcgis-services.md`, `houston-flood-data.md`, `houston-elevation-data.md` | Data-source research |
