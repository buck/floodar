# floodar Field Test Checklist

**Where:** home street first (teleport test), then **Braesheather Dr at Millbury Dr**,
Meyerland (29.67764, −95.46445). Go in daylight. Bring a charged phone.

**Scale check:** 7.8 ft is just above the top of a typical 7 ft garage door, and about a foot
above a front door (6 ft 8 in).

---

## Test 1 — Launch and pick a flood

1. Open floodar and allow camera and location.
2. Tap **MENU** (bottom right), then **Flood site…**, then **Meyerland: Braesheather Dr at Millbury Dr**, then
   **Hurricane Harvey**.

**Expect:** a toast reading "…7.8 ft above ground", and a blue panel at the top of the screen.

☐ OK  Notes: ______________________________________________

## Test 2 — Ground detection (the original v1 question)

1. Point the phone at the street or lawn, 1–3 m ahead. Sweep slowly side to side.

**Expect:** a white grid on the ground; the **Planes** count goes up; "Ground: detected plane".

- Seconds until the first plane: ____
- Largest area (m²): ____
- Distance where the grid stops (m): ____
- Did it hold while you walked 20 m? ☐ yes ☐ no

## Test 3 — Water surface and gauge

1. **Tap the street** in front of you.

**Expect:** a red/white striped pole at the tap, with 1-ft stripes (count ~8). A translucent
blue sheet above head height, since the phone is about 4.5 ft up and the water 7.8 ft. The
readout says "Ground: tapped point".

- Does the pole stay put when you walk around it? ☐ yes ☐ drifts ____ ft
- Readout "phone X ft up" matches reality? ____ ft

## Test 4 — Waterline on buildings

1. Point at houses across the street. Wait for **VPS: LOCALIZED** (it may say to move the phone
   around).

**Expect:** **Buildings** goes above 0. The houses get blue tint below a **bright cyan line**
at the water level, level with the top of the gauge's water mark.

- Seconds to LOCALIZED: ____  Buildings count: ____
- Is the line level with the gauge? ☐ yes ☐ off by ____ ft
- Is it on the right houses, or shifted sideways? ______________________
- Trees or cars in front of houses: does the line show through them? ☐ (expected)

## Test 5 — Compare scenarios

Open the picker again and switch floods. You don't need to tap the ground again.

| Scenario | Expected depth | Looks right? |
|---|---|---|
| Memorial Day 2015 | 6.4 ft | ☐ |
| Tax Day 2016 | 5.2 ft | ☐ |
| 100-year | 7.7 ft | ☐ |
| 500-year | 9.2 ft | ☐ |

## Test 6 — Clear Lake marker (teleport, works anywhere)

1. Pick **Clear Lake: Clear Lake City Blvd…**, then **2010 marker's "25-ft surge"**. Tap the
   ground.

**Expect:** a 25 ft pole, blue at the bottom and green above it with a grey cap, next to a short
gauge showing only **4.3 ft** of water.

☐ OK  Notes: ______________________________________________

---

## Capture for the demo

☐ **MENU → Record session** before Tests 3–4 at Braesheather, then **Stop recording** after
(about 2.5 MB/s). The walk can then be replayed at home with MENU → Play back recording…

☐ Screen recording (swipe down from the top, then **Screen record**) of Tests 3–4 at Braesheather

☐ Screenshot: marker plus gauge (Test 6)

☐ Photo of the scene *without* the phone, for a before/after comparison

## If something's wrong

| Symptom | Try |
|---|---|
| No planes | More light; aim at textured ground (grass or asphalt, not glare); move slowly |
| Buildings stays 0 | Wait for VPS: LOCALIZED; point at buildings and the skyline, not the ground |
| No water visible | Check the readout for "dry here"; look up, since the surface is above your head |
| Everything floats or drifts | Tap the ground again; restart the app |
| App crashes | Note the exact step; reinstall happens at the desktop |
