# ATAK2Drone

**ATAK2Drone** is an Android application that converts ATAK (Android Tactical Assault Kit) polygon boundaries and survey KMLs into optimized, production-ready **DJI WPML 1.0.6 (`.kmz`)** flight plans for **DJI Pilot 2**.

It features automated datum conversion, minimum-duration flight path optimization, terrain slope correction via open-source DEM elevation data, and dynamic 3D segment-maximum height tracking to keep tactical UAV operations safe over complex topography.

---

## Download Releases & Signed APK Products

Production-ready signed APK binaries for all supported DJI enterprise aircraft models are hosted directly in the [`releases/`](./releases/) directory:

### 📦 Current Signed Release: **[v2.1.5](./releases/current/)**

| Aircraft Series | Application ID | Signed Release APK | MSDK V5 Target Enums |
|---|---|---|---|
| **Mavic 3 Enterprise / 3T** | `com.taksolutions.atak2drone.m3t` | [`ATAK2M3T-release.apk`](./releases/current/ATAK2M3T-release.apk) | `DRONE = 77`, `SUB = 0`, `PAYLOAD = 67` |
| **Matrice 30 / 30T** | `com.taksolutions.atak2drone.m30` | [`ATAK2M30-release.apk`](./releases/current/ATAK2M30-release.apk) | `DRONE = 67`, `SUB = 1`, `PAYLOAD = 53` |
| **Matrice 300 / 350 RTK** | `com.taksolutions.atak2drone.m300m350` | [`ATAK2M300M350-release.apk`](./releases/current/ATAK2M300M350-release.apk) | `DRONE = 999`, `SUB = 0`, `PAYLOAD = 998` |
| **Matrice 4T** | `com.taksolutions.atak2drone.m4t` | [`ATAK2M4T-release.apk`](./releases/current/ATAK2M4T-release.apk) | `DRONE = 1001`, `SUB = 0`, `PAYLOAD = 1000` |

> 📁 **Archived Version Releases**: Historical version-sequenced releases (`v2.1.4`, `v2.1.3`, `v2.1.2`, `v2.1.1`, `v2.1.0`, `v2.0.2`) are preserved in version-labeled subfolders in [`releases/archive/`](./releases/archive/).

---

## Key Capabilities

### 🚁 Mission Survey Modes
- **Perimeter Fly-Around Survey**: Generates concentric exterior (outset) and interior (inset) fly-around perimeter rings with customizable radius sliders (0–500 ft) and typable text fields.
- **Area Mapping Survey Grid**: Generates optimal lawnmower grid transects inside arbitrary boundary polygons using Rotating Calipers minimum-duration angle optimization.

### ⛰️ Advanced DEM Terrain Tracking & Slope Correction
- **Segment-Maximum Terrain Height Tracking ($Z_{\text{max\_ground}}$)**: Evaluates peak ground elevation along every 100ft sub-segment and calculates 3D waypoint altitudes $H_{\text{waypoint}} = H_{\text{target}} + \Delta Z_{\text{rise}}$. The aircraft maintains at least $H_{\text{target}}$ above ground throughout every flight segment without stopping or hovering.
- **Quantization-Free Slope Calculation**: Uses a 60m symmetric central-difference sampling baseline ($30\text{m}$ outward + $30\text{m}$ inward) combined with 3-tap weighted moving average slope smoothing ($\hat{S}_i = 0.25 S_{i-1} + 0.50 S_i + 0.25 S_{i+1}$) to eliminate 30m DEM cell boundary quantization noise.
- **Two-Stage Adaptive Edge Subdivision**: Automatically subdivides input boundary edges into $100\text{ ft}$ ($30.48\text{ m}$) baseline sub-segments, re-subdividing steep slope zones ($\ge 50\%$) into $30\text{ ft}$ ($9.144\text{ m}$) micro-segments.
- **Takeoff-Independent GPS Elevation Alignment**: References terrain height rise to the survey area's lowest ground baseline ($Z_{\text{poly\_min}}$), enabling onboard GPS/RTK elevation anchoring regardless of launch point location.

### 🌐 Datum & WPML Standard Compliance
- **100% Automated Datum Conversion Protocol**: Auto-detects and transforms imported KML input coordinates (NAD83, NAD27, GCJ02) to standard WGS 84 (`<wpml:useGcj02>0</wpml:useGcj02>`) with US locale formatting (`Locale.US`), eliminating map shifts in DJI Pilot 2.
- **DJI WPML 1.0.6 Schema**: Fully compatible with DJI Pilot 2 (`com.dji.industry.pilot`) on Smart Controllers (DJI RC Pro Enterprise, DJI RC Plus).

---

## Operating Instructions

1. **Export Boundary KML from ATAK / CIVTAK**: Draw a polygon covering your mission area in ATAK and export it as `.kml`.
2. **Install ATAK2Drone**: Sideload the release APK matching your drone model from [`releases/current/`](./releases/current/) onto your Android device or DJI Smart Controller.
3. **Configure Survey Parameters**:
   - Select your exported `.kml` file.
   - Choose **Perimeter Survey** or **Grid Survey**.
   - Set Target Flight Altitude (preset 200ft / 400ft or custom free-type).
   - Set **Slope Mode** to **Auto DEM** (requires active network connection for elevation lookup) or **Off (Flat 2D)**.
   - Adjust Interior/Exterior perimeter radius sliders.
4. **Generate & Fly**:
   - Tap **Generate Flight KMZ**.
   - Import the generated `.kmz` into **DJI Pilot 2** and execute the flight plan.

---

## Developer Guide & Building from Source

### Prerequisites
- **JDK 17**
- **Android SDK 36** (Compile SDK 36, Min SDK 26)
- **Gradle 8.13**

### Build Commands
```bash
# Clone the repository
git clone https://github.com/cfd2474/ATAK2Drone.git
cd ATAK2Drone

# Run full unit test suite (36 tests across all 4 product flavors)
./gradlew test

# Compile release APKs for all aircraft variants
./gradlew assembleRelease
```

Generated APKs will be located under `app/build/outputs/apk/<flavor>/release/`.

---

## Project State & Versioning

- **State File**: See [`PROJECT_STATE.md`](./PROJECT_STATE.md) for architectural constraints, task history, and decision logs.
- **Release Documentation**: See [`releases/README.md`](./releases/README.md) for detailed aircraft enum mappings and release history.

---

## License

Copyright © 2026 TAK Solutions. All rights reserved.
