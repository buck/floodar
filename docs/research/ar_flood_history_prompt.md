I am participating in the 2026 Houston Hackathon and developing a mobile augmented-reality application that lets a person experience what a flood would look like in their immediate surroundings.

The intended scale is modest: a room, a house, a yard, a street, or perhaps an eighth of a mile of roadway. I am **not primarily trying to reproduce an entire city-scale flood**.

During testing, we discovered something important: when the application was used indoors, a virtual water surface with ripples and reflections made it appear that the room and house were actually filled with floodwater. The effect was surprisingly visceral and emotionally powerful.

I want a deep research report on the history, current state of the art, and prior art for this kind of experience.

The central questions are:

**Who has previously used augmented reality to make flooding appear physically present in a person's real surroundings?**

**Has AR flood visualization been used specifically to make flood risk emotionally or viscerally understandable, rather than simply displaying flood data?**

**What technical and perceptual techniques make the illusion convincing?**

## 1. History of AR flood visualization

Trace the history of projects that have attempted to visualize flooding using augmented reality, mixed reality, mobile AR, situated visualization, or related technology.

Look for:

* academic projects
* government disaster-preparedness projects
* FEMA or emergency-management demonstrations
* climate-change and sea-level-rise projects
* flood-risk communication research
* museums and science centers
* architectural visualization
* urban planning
* university prototypes
* civic-tech projects
* hackathons
* commercial AR demonstrations
* art installations
* public-engagement projects

Search back into the 1990s and early 2000s for relevant precursors.

For each substantial project, identify:

* project name
* organization/researchers
* date
* location
* purpose
* hardware
* software/framework
* localization/tracking method
* how the water surface was produced
* whether it worked indoors, outdoors, or both
* visual realism
* whether the user could walk through the flooded scene
* whether the water interacted visually with walls, furniture, streets, buildings, or terrain
* whether the project evaluated users' reactions
* whether papers, videos, screenshots, demos, or source code survive

## 2. Indoor AR flooding

Investigate indoor flood visualization specifically.

Look for systems in which virtual water appears to fill:

* a room
* a house
* a basement
* an office
* a building
* an architectural model

Search terms should include combinations such as:

"augmented reality flooded room"
"AR flood room"
"augmented reality house flooding"
"mixed reality flood house"
"virtual flood inside house"
"AR water filling room"
"AR water level room"
"augmented reality disaster simulation house"
"mixed reality disaster preparedness flooding"
"AR flood risk home"
"immersive flood risk visualization"
"augmented reality water surface interior"

Look also for technically similar demonstrations not explicitly related to disasters, such as:

* AR rooms filling with water
* virtual swimming pools
* aquarium effects
* water rising inside buildings
* AR liquid simulation
* portals or submerged-room effects

These may contain techniques directly applicable to flood visualization.

For these systems, investigate:

* detection of floors and walls
* scene meshing
* depth sensing
* occlusion
* reflections
* screen-space reflections
* environment probes
* refraction
* ripples
* wave animation
* waterline rendering
* interaction of virtual water with walls and furniture
* underwater camera effects
* lighting changes
* particles/debris
* audio

Explain which effects contribute most to the perception that the real room is actually flooded.

## 3. Street-scale outdoor flooding

Investigate AR flood visualization covering approximately tens to a few hundred meters.

The target application might let a person walk along a real street and see a historical or hypothetical flood at the correct height.

Research systems using:

* GPS
* ARCore
* ARKit
* VPS
* visual-inertial odometry
* geospatial anchors
* cloud anchors
* geographic anchors
* Streetscape Geometry
* depth APIs
* terrain models
* building models
* OpenStreetMap
* Google Maps data
* Mapbox
* Cesium
* LiDAR
* photogrammetry

Determine what accuracy can realistically be achieved over roughly 50–250 meters.

Pay particular attention to **vertical accuracy**, because an error of even one meter can severely distort the apparent flood depth.

Investigate:

* GPS altitude error
* barometric altitude
* DEM elevation
* ellipsoid versus orthometric height
* geoid corrections
* AR anchor drift
* relocalization
* heading errors
* alignment with buildings and curbs
* techniques for manually calibrating water height against a known physical reference

## 4. Floodwater rendering

Investigate how convincing floodwater has been rendered in AR and real-time graphics.

Compare techniques ranging from:

* simple transparent blue planes
* animated normal maps
* reflection probes
* planar reflections
* screen-space reflections
* real-time reflections
* refraction
* Fresnel effects
* ripples
* waves
* foam
* floating debris
* murky water
* sediment coloration
* wet surfaces
* underwater post-processing

Identify approaches practical on current consumer phones.

Determine whether physically simulated water is necessary or whether convincing flood visualization can be achieved largely with shaders and animation.

Look particularly for Unity, Unreal, ARCore, ARKit, WebXR, and native-mobile implementations.

## 5. Occlusion and interaction with real geometry

A key visual requirement is making water appear to occupy the actual environment rather than simply drawing a translucent layer over the camera image.

Investigate techniques for making floodwater appear:

* behind walls
* behind furniture
* behind cars
* behind buildings
* around curbs
* around trees
* against real terrain
* through doorways
* through windows

Research:

* LiDAR scene meshes
* ARKit Scene Reconstruction
* ARCore Depth API
* Streetscape Geometry
* semantic scene understanding
* manually constructed geometry
* building footprints
* photogrammetric meshes
* depth masks

Explain the limitations of each.

## 6. Perceptual and emotional impact

This is an especially important part of the research.

Find studies examining whether immersive AR or VR representations of natural disasters make risk more understandable or emotionally salient.

Search for work involving:

* flooding
* storm surge
* sea-level rise
* tsunami
* hurricanes
* wildfire
* earthquakes
* climate-change visualization

Search terms should include:

"augmented reality flood risk perception"
"immersive flood risk communication"
"virtual reality flood risk perception"
"AR natural disaster risk communication"
"immersive climate risk visualization"
"virtual flood experience risk perception"
"presence flood simulation"
"embodied flood visualization"
"experiential flood risk communication"

Determine whether researchers measured:

* perceived risk
* emotional response
* fear or concern
* recall
* comprehension
* willingness to prepare
* evacuation intentions
* insurance intentions
* climate-risk perception
* sense of presence
* spatial understanding

Report controlled experiments where available.

Compare AR flood visualization with:

* flood maps
* FEMA maps
* high-water markers
* photographs
* videos
* 3-D models
* conventional VR

I am particularly interested in whether researchers have observed the same phenomenon we noticed informally: **seeing floodwater occupying one's own familiar physical surroundings can make the potential disaster feel dramatically more real.**

## 7. "Your own home" and personalized disaster visualization

Look specifically for research in which a person's **actual home, room, street, or neighborhood** is incorporated into a risk visualization.

Examples could include:

* AR flood depth at the user's house
* VR reconstruction of the user's neighborhood
* climate-change visualization tied to one's property
* wildfire visualization around one's home
* storm-surge visualization on familiar streets

Investigate whether personalization or familiarity increases emotional impact or risk comprehension.

## 8. Related VR work

Include important virtual-reality flood simulations even when they are not AR.

VR flood simulations may contain relevant research on:

* visual realism
* presence
* flood-risk communication
* human behavior
* evacuation
* emotional response
* water rendering

Clearly distinguish VR from AR, but identify techniques and experimental findings transferable to AR.

## 9. Open-source projects and repositories

Search GitHub, GitLab, university repositories, research supplements, and archived projects.

Find repositories related to:

* AR flooding
* VR flooding
* AR water
* Unity AR water shaders
* Unreal AR water
* AR room water
* geospatial AR
* ARCore Geospatial
* ARKit location anchors
* AR occlusion
* scene reconstruction
* depth APIs
* flood visualization
* DEM-to-Unity workflows
* GIS-to-Unity workflows

For each useful repository provide:

* repository URL
* project name
* author/organization
* purpose
* language
* engine/framework
* license
* date of last substantial activity
* whether it appears to still build
* what code or techniques could be reused

Include abandoned projects when they contain historically significant or useful techniques.

## 10. Adjacent AR experiences

Search outside flood research for AR experiences solving the same perceptual problem.

Especially examine:

* rooms filling with virtual water
* AR aquariums
* submerged-room effects
* portals showing underwater environments
* architectural water visualization
* AR swimming pools
* virtual liquids
* AR fire/smoke simulations
* wildfire simulations
* tsunami visualizations

The goal is to identify techniques capable of making an ordinary familiar environment appear transformed by a disaster.

## 11. Technical evolution

Explain how the field evolved through:

* marker-based AR
* plane detection
* SLAM
* visual-inertial odometry
* ARKit/ARCore
* depth sensing
* phone LiDAR
* scene reconstruction
* VPS
* geospatial anchors
* 3-D building geometry

Explain what each advance made possible for flood visualization.

## 12. Current state of the art in 2026

Describe what can realistically be implemented today on consumer phones.

Evaluate architectures such as:

* Unity + AR Foundation
* Unity + ARCore
* native ARCore
* native ARKit
* Unity + ARKit Scene Reconstruction
* ARCore Geospatial API
* ARCore Streetscape Geometry
* Cesium for Unity
* Unreal Engine
* WebXR where relevant

For each approach discuss:

* indoor capability
* outdoor capability
* visual realism
* depth/occlusion
* street-scale positioning
* vertical accuracy
* phone compatibility
* computational cost
* implementation effort
* API/licensing restrictions

The intended scale is approximately **a room to a few hundred meters**, not an entire metropolitan area.

## 13. Houston and hurricane relevance

Look for relevant work connected with:

* Houston
* Hurricane Harvey
* Hurricane Ike
* Galveston
* Clear Lake
* storm surge
* bayou flooding
* Gulf Coast hurricanes
* FEMA high-water marks
* flood-depth markers
* flood-risk communication in Houston

Include both AR projects and other visualization projects that might provide datasets or design inspiration.

## 14. Historical timeline

Produce a chronological timeline showing:

* early flood/AR visualization
* important academic projects
* public demonstrations
* disaster-risk communication experiments
* major AR platform advances
* important open-source releases

For each milestone explain why it mattered.

## 15. Distinguish several classes of systems

Classify prior work into categories:

1. **Flood-height annotations**

   * markers, labels, lines, or gauges showing water height

2. **Simple virtual water plane**

   * a local water surface placed into the AR scene

3. **Environment-aware flood simulation**

   * water visually interacts with floors, walls, furniture, buildings, or terrain

4. **Geographically registered flood visualization**

   * flood elevation is tied to actual geographic coordinates

5. **Immersive risk communication**

   * primary purpose is emotional, experiential, educational, or behavioral impact

A project can belong to several categories.

## 16. Identify especially relevant precedents

At the end, identify prior projects that are most closely analogous to this specific concept:

**A person points a phone around their own room, house, yard, or street and sees convincing floodwater occupying the real environment at approximately the historically or hypothetically correct height.**

For each close precedent explain:

* what it did
* how similar it is
* what differs
* whether it was publicly deployed
* whether the technical implementation is available
* whether anyone studied the user's psychological response

Do not claim novelty merely because no identical project is found.

Instead identify which combinations of features appear common, uncommon, or poorly explored.

## 17. Final report structure

Produce:

1. Executive summary
2. Definition and taxonomy
3. Historical timeline
4. Major flood-AR projects
5. Indoor flooding and room-scale AR
6. Street-scale outdoor flooding
7. Floodwater rendering techniques
8. Occlusion and scene geometry
9. Psychological and risk-communication research
10. Personalized/home-based disaster visualization
11. Related VR research
12. Open-source repositories
13. Technical evolution
14. State of the art in 2026
15. Houston/Gulf Coast examples
16. Closest prior art
17. Remaining technical problems
18. Areas that appear comparatively unexplored
19. Annotated bibliography
20. Direct links to papers, repositories, videos, and archived project pages

## Research-method instruction

Do not search only for the literal phrase "augmented reality flood."

Terminology may include:

* mixed reality
* situated visualization
* immersive visualization
* experiential visualization
* flood-risk communication
* climate-risk visualization
* sea-level-rise visualization
* disaster simulation
* environmental AR
* mobile GIS
* virtual water
* location-based AR
* immersive analytics

Follow citation chains backward and forward.

Also search video platforms, project pages, conference demonstrations, student theses, GitHub, and Internet Archive material, because visually impressive AR prototypes may never have produced conventional journal papers.

Where possible, find videos and screenshots. For this subject, **seeing what the system actually looked like is almost as important as reading its technical description.**

