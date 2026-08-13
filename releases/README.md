# ATAK2Drone — Public Signed Release APKs

This directory contains digitally signed, production-ready release builds of **ATAK2Drone** for each supported DJI enterprise aircraft variant.

---

## Release Variants

| Aircraft Series | Application ID | APK Package | Size | Target Drone / Payload Enums |
|---|---|---|---|---|
| **Matrice 30 / 30T** | `com.taksolutions.atak2drone.m30` | [`ATAK2M30-release.apk`](./ATAK2M30-release.apk) | ~5.03 MB | `DRONE_ENUM = 67`, `DRONE_SUB_ENUM = 1`, `PAYLOAD_ENUM = 53` |
| **Mavic 3 Enterprise / 3T** | `com.taksolutions.atak2drone.m3t` | [`ATAK2M3T-release.apk`](./ATAK2M3T-release.apk) | ~5.03 MB | `DRONE_ENUM = 77`, `DRONE_SUB_ENUM = 0`, `PAYLOAD_ENUM = 67` |
| **Matrice 300 / 350 RTK** | `com.taksolutions.atak2drone.m300m350` | [`ATAK2M300M350-release.apk`](./ATAK2M300M350-release.apk) | ~5.02 MB | `DRONE_ENUM = 999`, `DRONE_SUB_ENUM = 0`, `PAYLOAD_ENUM = 998` |
| **Matrice 4T** | `com.taksolutions.atak2drone.m4t` | [`ATAK2M4T-release.apk`](./ATAK2M4T-release.apk) | ~5.03 MB | `DRONE_ENUM = 1001`, `DRONE_SUB_ENUM = 0`, `PAYLOAD_ENUM = 1000` |

---

## Features & Capabilities
- **Area Mapping**: Lawnmower grid flight path planning with minimum flight duration $\theta_{\text{min}}$ optimization via the **Rotating Calipers** algorithm.
- **Perimeter Survey**: Multi-ring concentric polygon buffer offset flights with configurable **Interior Width** (0–500 ft, default 100 ft) and **Exterior Width** (0–500 ft, default 50 ft) controls.
- **Dynamic Altitude**: Free-type altitude (20–400 ft) or rapid 200 ft / 400 ft presets.
- **Requirements Indicator**: Real-time validation warning banner displaying missing criteria until ready to generate.
- **Direct DJI Pilot 2 Integration**: Generates standalone `.kmz` packages conforming to DJI WPML schema version `1.0.6` / `1.0.2`+.

---

## Installation & Sideloading
1. Download the APK corresponding to your aircraft model onto your Android device or Smart Controller (e.g. DJI RC Pro Enterprise, DJI RC Plus).
2. Open the APK with the file manager to install (enable "Install unknown apps" if prompted).
3. Open ATAK2Drone, pick your ATAK KML polygon, configure flight parameters, and tap **Generate Flight KMZ**.
