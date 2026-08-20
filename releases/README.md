# ATAK2Drone — Public Signed Releases

This directory manages digitally signed, production-ready release builds of **ATAK2Drone** for each supported DJI enterprise aircraft variant.

---

## Current Release: **v2.1.0**

Located in [`releases/current/`](./current/):

| Aircraft Series | Application ID | Signed Release APK | Target Drone / Payload Enums |
|---|---|---|---|
| **Matrice 30 / 30T** | `com.taksolutions.atak2drone.m30` | [`ATAK2M30-release.apk`](./current/ATAK2M30-release.apk) | `DRONE_ENUM = 67`, `DRONE_SUB_ENUM = 1`, `PAYLOAD_ENUM = 53` |
| **Mavic 3 Enterprise / 3T** | `com.taksolutions.atak2drone.m3t` | [`ATAK2M3T-release.apk`](./current/ATAK2M3T-release.apk) | `DRONE_ENUM = 77`, `DRONE_SUB_ENUM = 0`, `PAYLOAD_ENUM = 67` |
| **Matrice 300 / 350 RTK** | `com.taksolutions.atak2drone.m300m350` | [`ATAK2M300M350-release.apk`](./current/ATAK2M300M350-release.apk) | `DRONE_ENUM = 999`, `DRONE_SUB_ENUM = 0`, `PAYLOAD_ENUM = 998` |
| **Matrice 4T** | `com.taksolutions.atak2drone.m4t` | [`ATAK2M4T-release.apk`](./current/ATAK2M4T-release.apk) | `DRONE_ENUM = 1001`, `DRONE_SUB_ENUM = 0`, `PAYLOAD_ENUM = 1000` |

---

## Archived Releases

Previous release binaries are archived in subfolders labeled by version number inside [`releases/archive/`](./archive/):

- **v2.0.2**: [`releases/archive/v2.0.2/`](./archive/v2.0.2/)
  - `ATAK2M30-release.apk`
  - `ATAK2M3T-release.apk`
  - `ATAK2M300M350-release.apk`
  - `ATAK2M4T-release.apk`

---

## Key Features in v2.1.0
- **Automated Open-Source DEM Terrain Slope Correction**: Queries terrain elevation along flight path tangents (using Open-Meteo DEM, Open-Topo-Data, USGS 3DEP, and Open-Elevation with multi-tier failover).
- **100% Automated Datum Conversion Protocol**: Auto-detects and transforms imported KML input coordinates (NAD83, NAD27, GCJ02) to standard WGS 84 (`<wpml:useGcj02>0</wpml:useGcj02>`) behind the scenes without user setup.
- **Mission Processing Progress Dialog**: Displays live status during KML parsing, DEM queries, and WPML KMZ packaging with graceful fallback to Flat 2D mission mode if offline.
- **Area Mapping & Perimeter Survey**: Rotating Calipers minimum flight duration optimization & concentric polygon buffer offset flights.

---

## Installation & Sideloading
1. Download the APK corresponding to your aircraft model from `releases/current/` onto your Android device or Smart Controller (e.g. DJI RC Pro Enterprise, DJI RC Plus).
2. Open the APK with the file manager to install (enable "Install unknown apps" if prompted).
3. Open ATAK2Drone, pick your ATAK KML boundary polygon, configure flight parameters, and tap **Generate Flight KMZ**.
