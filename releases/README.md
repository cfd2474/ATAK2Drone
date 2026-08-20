# ATAK2Drone — Public Signed Releases

This directory manages digitally signed, production-ready release builds of **ATAK2Drone** for each supported DJI enterprise aircraft variant.

---

## Current Release: **v2.1.5**

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

- **v2.1.4**: [`releases/archive/v2.1.4/`](./archive/v2.1.4/)
- **v2.1.3**: [`releases/archive/v2.1.3/`](./archive/v2.1.3/)
- **v2.1.2**: [`releases/archive/v2.1.2/`](./archive/v2.1.2/)
- **v2.1.1**: [`releases/archive/v2.1.1/`](./archive/v2.1.1/)
- **v2.1.0**: [`releases/archive/v2.1.0/`](./archive/v2.1.0/)
- **v2.0.2**: [`releases/archive/v2.0.2/`](./archive/v2.0.2/)

---

## Key Features in v2.1.5
- **Eliminated DEM Grid Quantization Artifacts**: Upgraded slope calculation to use a 60m symmetric central-difference sampling baseline ($30\text{m}$ outward + $30\text{m}$ inward), eliminating false slope spikes caused by 30m DEM grid cell boundary step changes.
- **Perimeter 3-Tap Weighted Moving Average Slope Smoothing**: Smooths calculated slopes across adjacent perimeter sub-segments ($\hat{S}_i = 0.25 S_{i-1} + 0.50 S_i + 0.25 S_{i+1}$), producing clean, parallel concentric perimeter rings without unnatural pinching or converging lines.
- **Segment-Maximum Terrain Height Tracking**: Evaluates $Z_{\text{max\_ground}}$ across every sub-segment and assigns 3D waypoint altitudes $H_{\text{waypoint}} = H_{\text{target}} + \Delta Z_{\text{rise}}$, ensuring the drone maintains at least $H_{\text{target}}$ above ground throughout every flight segment without stopping or hovering.
- **100% Automated Datum Conversion Protocol**: Auto-detects and transforms imported KML input coordinates (NAD83, NAD27, GCJ02) to standard WGS 84 (`<wpml:useGcj02>0</wpml:useGcj02>`) behind the scenes without user setup.

---

## Installation & Sideloading
1. Download the APK corresponding to your aircraft model from `releases/current/` onto your Android device or Smart Controller (e.g. DJI RC Pro Enterprise, DJI RC Plus).
2. Open the APK with the file manager to install (enable "Install unknown apps" if prompted).
3. Open ATAK2Drone, pick your ATAK KML boundary polygon, configure flight parameters, and tap **Generate Flight KMZ**.
