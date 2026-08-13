# ATAK2Drone — Project Summary (for AI context)

Repo: https://github.com/cfd2474/ATAK2Drone (single branch: `master`, 11 commits, 5 stars, 2 forks, no LICENSE file found)

## What it does

ATAK2Drone is a native Android app (Kotlin) that converts a polygon drawn/exported in ATAK (Android Team Awareness Kit, a military/tactical mapping tool) as a KML file into a DJI WPML flight-mission package (`.kmz`) that DJI Pilot 2 can load and fly. It closes the gap between "an analyst draws an area of interest in ATAK" and "a drone operator has a loadable DJI mission."

Flow: user picks a KML → app extracts the polygon vertices → user sets a mission name, altitude (200 ft or 400 ft), and camera mode (EO / IR / Both) → app injects the vertices into a prebuilt DJI WPML template as waypoints → writes the result as `<missionName>.kmz` to a user-chosen destination folder (via Storage Access Framework) → optionally launches DJI Pilot 2 (`com.dji.industry.pilot`) if installed.

Important nuance: the generated mission is a **waypoint path through the polygon's vertices**, not a lawnmower/grid survey pattern over the polygon's interior. It literally re-uses the polygon corner points as flight waypoints.

## Tech stack

- Kotlin, Android (minSdk 26, targetSdk/compileSdk 36)
- AndroidX (AppCompat, DocumentFile, Activity Result APIs), Material
- View binding enabled
- No DJI Mobile SDK dependency — the app never talks to the drone/DJI SDK directly. It only produces a KMZ file in DJI's WPML format and, optionally, launches the DJI Pilot 2 app via package manager intent. All DJI-format knowledge is encoded in prebuilt template KMZ files bundled as assets, not generated from an SDK spec.
- Standard XML parsing: `XmlPullParser` for reading input KML, `javax.xml.parsers`/DOM + `Transformer` for editing/writing the WPML XML inside templates.
- Gradle Kotlin DSL (`build.gradle.kts`), version catalogs (`libs.*`).

## Build variants (product flavors)

Defined in `app/build.gradle.kts`, dimension `"drone"`:

| Flavor | applicationId | app name | DRONE_ENUM | PAYLOAD_ENUM |
|---|---|---|---|---|
| `mavic3t` | com.taksolutions.atak2drone.m3t | ATAK2M3T | 77 | 67 |
| `matrice300m350` | com.taksolutions.atak2drone.m300m350 | ATAK2M300/M350 | 999 | 998 |
| `m4t` | com.taksolutions.atak2drone.m4t | ATAK2M4T | 1001 | 1000 |

Note: `DRONE_ENUM`/`PAYLOAD_ENUM` are `BuildConfig` fields but are **not currently referenced anywhere in the Kotlin source** I found (`WpmlGenerator`, `MissionController`, `MainActivity`) — they look like placeholders for future per-aircraft logic. The M4T values are explicitly marked `TODO: replace with real DJI enum values for M4T when known`. So today, all three flavors ship identical behavior/templates; only branding differs.

## Package structure (`app/src/main/java/com/example/atak2drone/`)

- `MainActivity.kt` — the entire UI layer (single Activity, no fragments/MVVM). Handles: SAF file/folder pickers, mission-name regex validation (`^[A-Za-z][A-Za-z0-9_-]{0,31}$`), enabling the Generate button only once a valid KML + destination + name are all present, calling `MissionController`, copying the result to the user's chosen folder, and prompting to launch DJI Pilot 2.
- `controller/MissionController.kt` — single entry point `generateMission(context, kmlInputStream, missionName, altitudeFt, cameraType): Result<String>`. Orchestrates: persist input stream to temp file → parse polygon via `KmlUtils` → require ≥3 points → convert ft→m → call `WpmlGenerator.generateFromTemplateKmz` → return path to generated KMZ or a `Result.failure`.
- `model/CameraType.kt` — enum `EO | IR | BOTH`.
- `model/Coordinate.kt` — `data class Coordinate(latitude: Double, longitude: Double)`.
- `utils/KmlUtils.kt` — single-pass, namespace-aware KML parser. Looks for `<Polygon>` first, falls back to bare `<LinearRing>`, and explicitly rejects a lone `<LineString>` (tells the user to draw a Polygon in ATAK, not a line). Drops a duplicated closing point if the ring is closed. Also has a `buildMinimalPolygonKml` helper for generating a minimal standalone KML (used for template consistency, not the main flow).
- `utils/WpmlGenerator.kt` — the core mission-building logic. See below.

## WpmlGenerator internals (the interesting part)

1. Picks an altitude "bucket" (`FT200` if <300 ft equivalent, else `FT400`) and a camera-specific template asset path: `assets/templates/{200ft|400ft}/Test3correct{,"IR","Both"}.kmz`. So there are 6 bundled template KMZ files total (2 altitudes × 3 camera modes), and the app doesn't generate WPML from scratch — it mutates known-good DJI templates.
2. Unzips the chosen template into a temp working dir.
3. Opens `wpmz/waylines.wpml` (or a couple of fallback paths) as XML, and does a broad find/replace of every height-related tag (`executeHeight`, `takeOffAlt`, `takeOffSecurityHeight`, `globalHeight`, `height`, `uavHeight`, `goHomeHeight`, plus a generic "any element whose local name ends in `Height`" pass) to the requested altitude in meters.
4. Rewrites the Placemark list inside the template's `<Folder>`: it clones the *first* existing Placemark as a prototype (to preserve DJI-specific wpml children like gimbal/speed actions it doesn't understand), then for each polygon vertex produces a clone with updated `<name>`, `<Point><coordinates>`, and `<wpml:index>`. All other DJI-specific metadata on the prototype is left untouched and reused for every waypoint — i.e., every generated waypoint inherits whatever action/gimbal/speed config the template's first sample waypoint had.
5. Also walks the unzipped template tree and rewrites the polygon coordinates in any loose `.kml` files and in the `<coordinates>` of the first `<LinearRing>` (or first `<coordinates>` tag) inside any nested `.kmz` (e.g. a `doc.kml` preview), so the visual preview polygon matches the actual mission.
6. Re-zips the mutated directory into `<missionName>.kmz` and returns it.
7. Lat/lon extraction from the generic `polygon: List<Any>` is done via **reflection** (`readLatLon`), checking for fields/getters named `lat`/`latitude`/`latDeg` and `lon`/`lng`/`long`/`longitude`. In practice the list passed in is always `List<Coordinate>`, so this reflection layer is more general/defensive than strictly necessary today.

## UI details worth knowing

- Single screen: destination-folder picker, mission-name field (with an inline "?" help dialog explaining naming rules), KML file picker + status label, altitude radio group (200/400 ft only — 400 ft is the string-declared max per `error_invalid_altitude`), camera radio group (EO/IR/Both, EO default), Generate button.
- Generate button is disabled until: a KML with a valid ≥3-point polygon is loaded, a destination folder is chosen, and the mission name passes the regex.
- After a successful generate, it copies the KMZ from internal `filesDir` into the user-selected SAF destination tree, then shows an AlertDialog offering to launch DJI Pilot 2 (`com.dji.industry.pilot`) via `PackageManager.getLaunchIntentForPackage`, or tells the user it isn't installed.
- Uses persisted URI permissions (`takePersistableUriPermission`) so previously picked KML files/folders stay accessible across app restarts.

## Manifest / permissions

No runtime permissions declared — everything goes through Storage Access Framework document/tree pickers, so no `READ/WRITE_EXTERNAL_STORAGE` needed. Includes a `FileProvider` (for internal/external file exposure) and a `VIEW` intent-filter so the app can be launched by tapping a KML file with a compatible MIME type from another app (e.g., ATAK's export).

## Known limitations / likely next steps (inferred, not stated by the author)

- Only two altitude presets (200/400 ft) and only three camera modes; both are hardcoded to the 6 bundled templates rather than computed.
- Waypoints = polygon vertices only; there's no area-coverage/grid-survey path generation — for real mapping coverage the polygon shape itself has to already approximate the desired flight path, or the DJI templates must already encode a scan pattern the user isn't aware of.
- `DRONE_ENUM`/`PAYLOAD_ENUM` build flags exist but are unused in code — the three "drone flavors" are currently cosmetic (different app name/package only).
- M4T enum values are explicit placeholders (`TODO`), so the M4T flavor's differentiation from the others is essentially unimplemented.
- All mission "intelligence" (gimbal actions, speeds, per-waypoint behavior) comes from whatever the first Placemark in the bundled template happens to contain — every generated waypoint clones that one prototype's settings, so template authoring quality directly controls generated-mission quality.
- No automated tests found beyond default `androidx.test`/`espresso` scaffolding dependencies (no test source files were surfaced in the file tree browsed).
- No README/LICENSE in the repo as of this summary.
