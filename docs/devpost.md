# floodar: Devpost submission draft

*Paste each section into the matching Devpost field. Edit anything marked ✏️.*

---

## Project name

**floodar**

## Elevator pitch (tagline, 200 characters max)

> See Houston's real floods where you stand. floodar uses your phone's camera to fill your street, or your living room, with muddy water at the depth Harvey actually reached.

*(172 characters)*

---

## Project story ("About the project")

### Inspiration

On August 27, 2017, during Hurricane Harvey, I waded into the middle of my street near Brays
Bayou and filmed a slow pan of the flood. The water was just above my knees and pushing past my
legs; kayaks were going by down the block. The peak had come within about an inch and a half of
my slab. Two blocks east, where the ground sits a couple of feet lower, neighbors had water
inside their homes. I filmed it because I thought I would never see that scene again in my
lifetime. I hope I was right.

In late 2018 I heard a short talk by Jim Blackburn of Rice University about storm-surge markers
that FEMA and the State of Texas had put up in Clear Lake after Hurricane Ike, tall poles
showing how high the water could reach. They were taken down within months. The message I took
away was that the truth about flood risk had been too uncomfortable to leave standing by the
road.

My reaction was simple: if the physical markers could be removed, it should be possible to put
them back up, virtually, for anyone who wants to see. That idea has been on my list since 2018.
Phone AR has finally caught up with it.

Researching the markers for this hackathon taught me something I hadn't known. Their
"Category 4" and "Category 5" labels were themselves misleading, because a hurricane's wind
category is a poor predictor of how high the water rises. So floodar doesn't just put the marker
back. It shows the water levels that were actually measured, in Harvey and in the floods before
and since, and it shows them where people live.

Houston's weather is changing, and its storms are getting wetter and more intense: three floods
described as 500-year events in three years, from 2015 through 2017. When the water goes down and
the drywall is replaced, it is easy to forget what it looked like. floodar is meant to help us
remember, and to let people who never saw it understand.

### The problem: flood depth is a number nobody can picture

Houston floods again and again. In Allison (2001), the Memorial Day flood (2015), the Tax Day
flood (2016), Harvey (2017) and Imelda (2019), water rose into neighborhoods whose residents had
never pictured it there. Harvey alone flooded more than 150,000 homes in Harris County ✏️ *(verify: HCFCD's post-Harvey figure)*.

The data about those floods is excellent. Harris County Flood Control District crews surveyed
over a thousand high-water marks, and USGS surveyed 2,123 more across Texas after Harvey. But
that data arrives as maps, tables and numbers like "53.9 ft NAVD88". Few people can translate
that into "the water will be over my front door." People underestimate floods they've never
seen, and that shapes whether they buy flood insurance, elevate or rebuild, move valuables, or
leave early.

### Why it hasn't been solved: we tried once, and took it down

After Hurricane Ike, FEMA and the State of Texas put up tall storm-surge markers in Clear Lake,
blue and green poles showing "Category 4" and "Category 5" water levels. Residents and
real-estate interests objected, and the City of Houston removed them in early 2011. The markers
also had a scientific problem. Hurricane category is a poor predictor of surge height. In NOAA's
own comparison, Hurricane Ike, the storm that prompted the markers, was only a **Category 2**
but drove a **20 ft** surge, while the small **Category 4** Hurricane Charley produced **about
7 ft**. The markers' "25-foot surge" was also never tied to a vertical reference. At the marker
sites the ground is 21–23 ft above sea level, so a 25 ft water level would be only 2–4 ft deep
there.

Making risk visible at human scale is the right idea. Doing it with permanent, one-size-fits-all
signs was a poor fit, both politically and scientifically.

### The solution: floodar

floodar is an Android augmented-reality app that shows a **real, documented flood at full scale
wherever you point your phone**:

- **On site:** stand on Braesheather Dr in Meyerland and see Harvey's 7 ft 10 in of water,
  above the front doors, with a debris line on the houses.
- **In your own street:** "teleport" any documented depth to where you are. In testing, a
  resident's street filled with muddy water at knee height, matching their own video from
  Aug 27, 2017.
- **Any depth, anywhere:** "Custom depth" takes a number like "8 inches", the water a friend's
  house took, and shows it in the room.
- **In your home:** tap the living-room floor and the room fills with water that reflects the
  furniture and walls. It was unsettling in testing, because thousands of Houstonians saw exactly
  this.

How it works:

1. **Water levels from public data.** A PostGIS pipeline combines HCFCD high-water marks for
   Memorial Day 2015, Tax Day 2016, Harvey 2017 and Imelda 2019, HCFCD's 100-year and 500-year
   levels, and USGS 3DEP elevation data. Every scenario records which surveyed marks it came
   from.
2. **Depth, not altitude.** Phone GPS altitude can be off by 25 m. floodar never trusts it. You
   tap a local surface (the street, a porch, the floor), and the app places the water relative
   to it. The AR tracking keeps it there as you walk.
3. **Water you believe.** The water surface is opaque mud that **reflects the live camera
   image**: trees, houses and sky appear in it, with flow ripples and more reflection toward the
   horizon. It runs on an ordinary phone (Pixel 6a), with no LiDAR.
4. **Waterlines on real buildings.** Google's 3D building data gets a mud stain and a debris line
   at the water level, so houses down the block show how high the water reached.
5. **A 6 ft depth gauge** with foot labels stands where you tap, for human scale.

### Proof: we checked it against the real thing

The team member who built floodar lived through Harvey in this neighborhood and kept evidence:

- **A video from the middle of their street during Harvey** (Aug 27, 2017), with water just above
  the knees.
- **A photo of the debris line at their front door.** Measured against the siding courses, the
  peak came to 4.0 in above the porch and within about 1.4 in of the slab.

We filmed floodar in the same street in 2026 and put the two side by side (see the gallery and
video). We also measured where the approach falls short:
- The water level estimated from bayou high-water marks 800 m away **understated street depth by
  more than 2 ft**. Local elevation differences, though, correctly explained why houses two
  blocks east had about 2 ft of water inside.
- ARCore's height measurement over a 15 m walk was not survey-grade.

Our conclusion: anchor the flood to one trusted local observation, then use elevation data to
spread it across the neighborhood.

### How we'll sustain and grow it

- **Cost to run is close to zero.** The data is public (HCFCD, USGS, FEMA). The app runs on the
  phone. Google's ARCore Geospatial API is free at this scale (usage quotas apply) ✏️ *(verify current terms)*. Distribution costs a one-time $25
  Play Store fee, plus $99 a year for Apple if ported to iOS.
- **Maintenance:** each new flood adds a data layer. HCFCD and USGS survey high-water marks after
  every major event, so the pipeline re-runs with new points.
- **Tracking:** app usage by site and scenario, and, in a pilot, short before-and-after surveys:
  perceived risk, remembered depth, and preparedness or insurance intentions.

### Who would fund it

- **Cities and counties.** FEMA's Community Rating System gives residents flood-insurance
  discounts when their community runs flood-risk outreach. A tool like Flood AR could support
  that outreach ✏️ *(verify how CRS credits outreach projects)*.
- **Flood-control and emergency-management outreach**, public libraries, schools and museums,
  as a hands-on exhibit.
- **Buyers, renters and homeowners** deciding where to live, whether to insure, and whether to
  elevate.

### Impact

- **Who it serves:** Harris County's roughly 4.8 million residents, especially the hundreds of
  thousands in or near floodplains. Also buyers and renters deciding where to live,
  homeowners deciding on insurance and elevation, and schools, libraries and neighborhood
  associations teaching preparedness.
- **Beyond Houston:** USGS surveys high-water marks after major floods nationwide. Any community
  with those marks and elevation data can use the same pipeline.
- **Grounded in research:** first-person AR flood experiences increase perceived vulnerability
  and preparedness intentions (Mirza et al. 2025), and a virtual flood increased protective
  investment (Mol et al. 2022). Prior projects such as Disaster Scope (Japan) showed floods in
  people's own rooms. floodar adds **documented local water levels** and **validation against
  real footage of the same street**, a combination the background research found uncommon.

### Team

✏️ **A. Lester Buck III**: Houston resident who lived through Allison and Harvey in a
Brays Bayou–area neighborhood. Researched the Clear Lake FEMA markers, built the data pipeline
and field-tested the app. Developed with Claude Code (Anthropic) as an AI pair programmer.

**With help from:** a friend who filmed the Meyerland field test over my shoulder; a friend
whose home took 8 in of water and let me test the indoor view there; friends who lived on
Braesheather Dr, whose story is in this project; and Jim Blackburn (Rice University), whose 2018
talk started it. The next stage is to bring in HCFCD outreach staff, a research partner, and
residents who can share their own flood evidence.

### The ask

- **Partners:** Harris County Flood Control District or the City of Houston, for flood-risk
  outreach and to validate site data. Also a library or school system for a pilot.
- **Research collaborators** (for example Rice, UH, or the Baker Institute) to run the small study
  the research literature calls for: map vs. plain AR waterline vs. full AR flood in the
  participant's own surroundings.
- **Modest funding** for an iOS port, USGS Harvey depth rasters and HCFCD 1 m lidar for better
  street-level depths, and a public app release.
- **Residents' evidence:** photos and videos of debris lines and flooded streets, like the ones
  that validated floodar, to build ground truth across more neighborhoods.

### Challenges we ran into

- **Outdoor plane detection only reaches about 20 m**, so floodar uses it only for the height
  under your feet. Water on distant buildings comes from Google's 3D building geometry.
- **Heights and datums:** GPS altitude, ellipsoid vs. NAVD88, and a terrain mesh that can sit
  3 ft off until localization finishes. We solved it by always measuring from a surface you tap.
- **The 2010 marker's "25 ft" was never tied to a datum**, so its intended meaning can't be
  recovered. We show several possible readings instead.
- **Real-world limits:** an overheating phone in Houston sun, and a USB port that couldn't keep up
  with the power drain.

### Accomplishments we're proud of

- A **side-by-side of the same Houston street**: Harvey in 2017, floodar in 2026.
- A depth pipeline that traces every scenario back to specific surveyed high-water marks.
- Reflective, rippling muddy water on a mid-range Android phone, with no special hardware.
- A reconstructed history of the Clear Lake FEMA storm-surge markers.

### What we learned

Accuracy and presence both matter. A correct number that nobody can picture changes nothing,
and a convincing flood at the wrong height teaches the wrong lesson. As our background research
put it: *"Hydrology determines where the water is; the shader determines whether you believe it."*

### What's next

- An iOS version.
- Occlusion by nearby objects (cars, fences, furniture) using the ARCore Depth API.
- Floating debris, and an underwater view when the water is over your head.
- USGS Harvey depth rasters and 1 m lidar for street-accurate depths.
- A small user study with a Houston partner.

---

## Built with

`android` · `java` · `arcore` · `arcore-geospatial-api` · `streetscape-geometry` · `opengl-es` ·
`glsl` · `postgis` · `postgresql` · `docker` · `gdal` · `usgs-3dep` · `hcfcd-high-water-marks` ·
`python` · `ffmpeg` · `claude-code`

## "Try it out" links

- GitHub repository: https://github.com/buck/floodar (open source, Apache 2.0)
- APK download: https://github.com/buck/floodar/releases/tag/v0.1-hackathon (free; works on most Android phones from the last few years (the ones Google supports for AR; list: https://developers.google.com/ar/devices))
- Demo video: https://youtu.be/eTixT4tzWrQ
- Write-up: `docs/how-it-works.md` (link from the public repo)

## Image gallery (suggested order)

1. `data/comparison/street-2017-vs-2026.jpg`: Harvey 2017 vs. floodar 2026, same street
2. Indoor flood screenshot (living room)
3. Meyerland: water over the front doors, gauge to 10 ft ✏️ *(to shoot)*
4. Depth gauge with foot labels
5. Clear Lake: reconstructed 2010 marker next to the actual-depth gauge
6. Blackburn photo of the original 2010 Clear Lake marker (credit: Jim Blackburn / Baker Institute)
7. Debris-line photo at the door, with the measurement annotated ✏️ *(optional)*
