# DJI WPML (Waypoint Markup Language) Specification Guide

This guide details the internal file architecture, XML schema, namespace definitions, and validation rules for **DJI WPML `.kmz` packages**, as required by DJI Pilot 2 and MSDK V5.

---

## 1. Package File Structure (`.kmz` Bundle)

A DJI WPML `.kmz` mission file is a standard ZIP archive containing the following directory hierarchy:

```text
<mission_name>.kmz
├── wpmz/
│   ├── template.kml       # Planning template used by DJI Pilot 2 GUI
│   └── waylines.wpml      # Executable waylines loaded into aircraft flight controller
└── res/                   # (Optional) Static resources, overlays, or preview images
```

---

## 2. Namespace & Schema Declarations

All WPML documents must include standard KML 2.2 and DJI WPML extension namespaces in the root `<kml>` tag:

```xml
<kml xmlns="http://www.opengis.net/kml/2.2"
     xmlns:wpml="http://www.dji.com/wpmz/1.0.6">
  <Document>
    ...
  </Document>
</kml>
```

> [!NOTE]
> WPML schema versions `1.0.2` through `1.0.6` are supported by DJI Pilot 2. ATAK2Drone targets schema version `1.0.6` for maximum enterprise aircraft compatibility.

---

## 3. `template.kml` — Mission Planning Structure

`template.kml` defines global mission parameters, aircraft hardware constraints, and template waypoint nodes.

### 3.1 Global Mission Configuration (`<wpml:missionConfig>`)

```xml
<wpml:missionConfig>
  <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>
  <wpml:finishAction>goHome</wpml:finishAction>
  <wpml:exitOnRCLost>executeLostAction</wpml:exitOnRCLost>
  <wpml:executeRCLostAction>goBack</wpml:executeRCLostAction>
  <wpml:takeOffSecurityHeight>20</wpml:takeOffSecurityHeight>
  <wpml:globalTransitionalSpeed>10</wpml:globalTransitionalSpeed>
  
  <wpml:droneInfo>
    <wpml:droneEnumValue>77</wpml:droneEnumValue>
    <wpml:droneSubEnumValue>0</wpml:droneSubEnumValue>
  </wpml:droneInfo>
  
  <wpml:payloadInfo>
    <wpml:payloadEnumValue>67</wpml:payloadEnumValue>
    <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>
  </wpml:payloadInfo>
</wpml:missionConfig>
```

### 3.2 Waypoint Node Specification (`<Placemark>`)

Each waypoint is defined as a `<Placemark>` within the `<Folder>`:

```xml
<Placemark>
  <Point>
    <coordinates>-117.16108,32.71571</coordinates>
  </Point>
  <wpml:index>0</wpml:index>
  <wpml:executeHeight>60.96</wpml:executeHeight>
  <wpml:waypointSpeed>5.0</wpml:waypointSpeed>
  <wpml:waypointHeadingParam>
    <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>
  </wpml:waypointHeadingParam>
  <wpml:waypointTurnParam>
    <wpml:waypointTurnMode>toPointAndStopWithDiscontinuityCurvature</wpml:waypointTurnMode>
  </wpml:waypointTurnParam>
</Placemark>
```

---

## 4. `waylines.wpml` — Executable Waylines & Action Groups

`waylines.wpml` contains the exact executable instructions sent to the aircraft flight controller, including automated camera/payload action triggers.

### 4.1 Action Group Hierarchy (`<wpml:actionGroup>`)

Action groups link specific payload actions (such as taking photos, tilting the gimbal, or zooming) to waypoints:

```xml
<wpml:actionGroup>
  <wpml:actionGroupId>0</wpml:actionGroupId>
  <wpml:actionGroupStartIndex>0</wpml:actionGroupStartIndex>
  <wpml:actionGroupEndIndex>0</wpml:actionGroupEndIndex>
  <wpml:actionGroupMode>sequence</wpml:actionGroupMode>
  <wpml:actionTrigger>
    <wpml:actionTriggerType>reachPoint</wpml:actionTriggerType>
  </wpml:actionTrigger>
  
  <wpml:action>
    <wpml:actionId>0</wpml:actionId>
    <wpml:actionActuatorFunc>gimbalPitch</wpml:actionActuatorFunc>
    <wpml:actionActuatorFuncParam>
      <wpml:gimbalPitchRotateAngle>-90</wpml:gimbalPitchRotateAngle>
    </wpml:actionActuatorFuncParam>
  </wpml:action>
  
  <wpml:action>
    <wpml:actionId>1</wpml:actionId>
    <wpml:actionActuatorFunc>takePhoto</wpml:actionActuatorFunc>
    <wpml:actionActuatorFuncParam>
      <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>
      <wpml:payloadLensIndex>wide,ir</wpml:payloadLensIndex>
    </wpml:actionActuatorFuncParam>
  </wpml:action>
</wpml:actionGroup>
```

### 4.2 Lens Index Parameterization (`wpml:payloadLensIndex`)
- `wide`: Visual Wide Angle Camera (EO)
- `zoom`: Optical Zoom Camera
- `ir`: Thermal / Infrared Camera (IR)
- `wide,ir` / `zoom,ir`: Dual visual + thermal simultaneous capture (BOTH)

---

## 5. Validation Criteria (Enforced by DJI Pilot 2)

Before loading a `.kmz` mission package, DJI Pilot 2 and MSDK V5 perform validation checks:

| Validation Rule | Bounds / Requirements | Failure Consequence |
|---|---|---|
| **Waypoint Count** | $2 \le N \le 65535$ waypoints | Mission import rejected |
| **Coordinate Bounds** | Lat: $[-90.0, 90.0]$, Lon: $[-180.0, 180.0]$ | `Invalid Coordinate` error |
| **Execute Height** | Must be $> 0$ and $\le$ max ceiling (default 120m / 400ft) | Height safety lockout |
| **Heading Mode** | Must match aircraft capability (`followWayline`, `fixed`, `smoothTransition`) | Unpredictable yaw movement |
| **Action Index Integrity** | `actionGroupId` and `actionId` must be strictly sequential | Missing photo/gimbal actions |
