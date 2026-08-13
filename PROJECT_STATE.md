# ATAK2Drone — Project State

## 1. Project Status Overview
- **Current Goal**: Build and publish digitally signed public APK releases for all supported DJI enterprise variants (**M30/M30T**, **M3T**, **M300/M350**, **M4T**).
- **Current Phase**: Released & Published (`v1.0.0` tagged and pushed to GitHub with `releases/` directory).

## 2. Completed Steps
- Configured Android SDK path in `local.properties` and verified JDK 17 environment.
- Implemented pure Kotlin domain models and SOLID interfaces (`IMissionStrategy`, `IKmlParser`, `IWpmlBuilder`, `IKmzPackager`, `SurveyConfig`, `OptimizationMetrics`, `MissionPlan`).
- Implemented geographic math, local Cartesian projection, and convex hull algorithms (`GeometryUtils`, `ConvexHull`).
- Implemented Rotating Calipers minimum flight duration angle optimization algorithm (`RotatingCalipers`).
- Implemented Area Mapping survey grid generator (`GridSurveyGenerator`).
- Implemented Perimeter Survey with 2D polygon buffer offset / inset / outset algorithm and customizable Interior/Exterior radius sliders + typable fields.
- Implemented dynamic `WpmlBuilder`, `KmzPackager`, `KmlParser`, and refactored `MissionController`.
- Implemented `matrice30` product flavor in `app/build.gradle.kts` (`DRONE_ENUM = 67`, `DRONE_SUB_ENUM = 1`, `PAYLOAD_ENUM = 53`).
- Added real-time missing requirements warning indicator next to **Generate Flight KMZ** button.
- Configured secure keystore signing in `local.properties` (never committed to git) and generated verified signed release APKs.
- Published release APKs to `releases/` in git repository, tagged `v1.0.0`, and pushed to remote `dev` and `master` branches.

## 3. In Progress
- All requested features, builds, and releases complete.

## 4. Architectural Constraints & Rules
- **No Direct Drone Connection**: ATAK2Drone generates standalone DJI WPML `.kmz` mission packages imported by DJI Pilot 2 (`com.dji.industry.pilot`).
- **MSDK Source of Truth**: Aircraft (`droneEnumValue`, `droneSubEnumValue`) and payload (`payloadEnumValue`) enums are strictly aligned with MSDK V5 / Cloud API standards.
- **SOLID Principles**: Code architecture refactoring adheres strictly to Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion.
- **WPML Schema Compliance**: Generated missions conform to DJI WPML schema version `http://www.dji.com/wpmz/1.0.6` (or `1.0.2`+) so DJI Pilot 2 parses them without error.

## 5. Decisions Made & Rationale
| Decision | Rationale |
|---|---|
| Domain-Driven Pure Kotlin Optimization Engine | Isolating geometric projection, convex hull, rotating calipers, and transect generation in pure Kotlin allows fast JVM unit testing and clean separation of concerns. |
| Minimum Duration Flight Angle Optimization | Uses Rotating Calipers algorithm to find minimum bounding box width angle $\theta_{\text{min}}$, minimizing turn penalty count $N_{\text{turns}}(\theta)$ for fastest mission completion. |
| Strategy Pattern for Flight Planning | `IMissionStrategy` enables seamless swapping between `VertexPathStrategy` (perimeter fly-around) and `GridSurveyStrategy` (optimal lawnmower survey). |
| Preserve 200ft/400ft Presets + Add Free-Type Input | Preserves rapid field deployment via 200 ft / 400 ft quick-select options while giving operators flexibility to input any custom altitude. |
| Concentric Polygon Offset Buffer Strategy | Inset / Outset parallel edge offset with miter limit clamping allows generating multiple perimeter survey rings covering variable corridor widths. |
| Synchronized Slider & Typable EditText Controls | Operators can swiftly drag sliders or type exact values with instant two-way synchronization, defaulting to 100 ft Interior and 50 ft Exterior. |
| DocumentBuilderFactory for KmlParser | Standard Java XML parsing provides universal execution in pure JVM test environments and Android runtime without external pull-parser mocks. |
| DJI M30 / M30T Source of Truth Enums | `DRONE_ENUM = 67`, `DRONE_SUB_ENUM = 1`, `PAYLOAD_ENUM = 53` ensure DJI Pilot 2 recognizes the mission package natively for Matrice 30 series aircraft. |
| Dynamic Secure Keystore Signing | Read release signing credentials from unversioned `local.properties` to ensure zero secret leakage to git repositories. |
| In-Repository Releases Catalog | Placing signed release APKs in `releases/` with a comprehensive `README.md` and `v1.0.0` git tag provides easy access directly within GitHub. |
