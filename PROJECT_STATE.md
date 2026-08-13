# ATAK2Drone — Project State

## 1. Project Status Overview
- **Current Goal**: Establish reference documentation for DJI SDKs (`https://github.com/dji-sdk`) & WPML specifications in `Docs/`, and formulate an optimization roadmap.
- **Current Phase**: Documentation Completed & Ready for Optimization Phase.

## 2. Completed Steps
- Analyzed `Docs/Project_summary.md`, build configurations (`app/build.gradle.kts`), asset templates, and package structure.
- Researched DJI SDK GitHub ecosystem (`Mobile-SDK-Android-V5`, `Cloud-API`, `WPML` schemas, `IWPMZManager`, drone and payload enums).
- Created `Docs/DJI_SDK_Reference.md` with complete GitHub repos, MSDK V5 API structure, and aircraft/payload enum mappings.
- Created `Docs/WPML_Specification_Guide.md` detailing KMZ bundle hierarchy (`template.kml`, `waylines.wpml`), namespaces, action groups, lens indices, and validation bounds.
- Created `Docs/Optimization_Roadmap.md` with SOLID architectural refactoring blueprint, dynamic altitude scaling, lawnmower/grid pattern generation with minimum flight duration angle optimization ($\theta_{\text{min}}$ rotating calipers algorithm), and unit testing strategy.

## 3. In Progress
- Completed Chunk 1 documentation buildout. Ready for approval / next chunk planning.

## 4. Architectural Constraints & Rules
- **No Direct Drone Connection**: ATAK2Drone does not use MSDK V5 at runtime on the device to fly the drone directly; it generates standalone DJI WPML `.kmz` mission packages imported by DJI Pilot 2 (`com.dji.industry.pilot`).
- **MSDK Source of Truth**: Aircraft (`droneEnumValue`) and payload (`payloadEnumValue`) enums are strictly aligned with MSDK V5 / Cloud API standards.
- **SOLID Principles**: Code architecture refactoring must adhere strictly to Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion.
- **WPML Schema Compliance**: Generated missions must conform to DJI WPML schema version `http://www.dji.com/wpmz/1.0.6` (or `1.0.2`+) so DJI Pilot 2 parses them without error.

## 5. Decisions Made & Rationale
| Decision | Rationale |
|---|---|
| Maintain Template + Dynamic Builder Hybrid | Preserves custom DJI Pilot 2 actions while allowing arbitrary altitude, dynamic waypoint generation, and custom payloads without hardcoding 200ft/400ft templates. |
| Separate SDK Docs into specialized files | `DJI_SDK_Reference.md` (repos & enums), `WPML_Specification_Guide.md` (XML schema & KMZ spec), and `Optimization_Roadmap.md` (refactoring blueprint) ensure modular readability. |
| Minimum Duration Flight Angle Optimization | Uses Rotating Calipers algorithm to find minimum bounding box width angle $\theta_{\text{min}}$, minimizing turn penalty count $N_{\text{turns}}(\theta)$ for fastest mission completion. |
| Preserve 200ft/400ft Presets + Add Free-Type Input | Preserves rapid field deployment via 200 ft / 400 ft quick-select options while giving operators flexibility to input any custom altitude. |

## 6. Task Decomposition (Chunk 1: Reference Documentation & Optimization Strategy)
- [x] Step 1: Create `PROJECT_STATE.md` in repository root.
- [x] Step 2: Build `Docs/DJI_SDK_Reference.md` detailing GitHub repos (`dji-sdk`), MSDK V5, Cloud API, enums (`droneEnumValue`, `payloadEnumValue`), and integration patterns.
- [x] Step 3: Build `Docs/WPML_Specification_Guide.md` detailing `.kmz` bundle hierarchy (`template.kml`, `waylines.wpml`), namespace rules, waypoint placemarks, gimbal/camera actions, and validation.
- [x] Step 4: Build `Docs/Optimization_Roadmap.md` with SOLID refactoring plan, dynamic altitude support, survey grid generation with optimal flight angle algorithm for minimum flight duration, and unit testing strategy.
- [x] Step 5: Update `PROJECT_STATE.md` and present summary for approval.
