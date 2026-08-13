package com.example.atak2drone.domain.model

import com.example.atak2drone.model.CameraType

/**
 * Configuration parameters for generating a mission plan.
 *
 * @property missionName Sanitized mission identifier.
 * @property altitudeMeters Target flight altitude in meters AGL.
 * @property cameraType Camera sensor mode (EO, IR, or BOTH).
 * @property speedMps Nominal cruising flight speed along straight transects (m/s). Default 8.0 m/s.
 * @property turnSpeedMps Reduced speed during turns (m/s). Default 3.0 m/s.
 * @property turnTimePenaltySeconds Fixed deceleration, rotation, and acceleration delay per turn (s). Default 4.0 s.
 * @property sideOverlapRatio Forward/side overlap percentage for grid survey (0.1 to 0.9). Default 0.70 (70%).
 * @property cameraFovDegrees Horizontal field of view of the sensor in degrees. Default 84.0 deg (M3T wide camera).
 * @property customAngleDegrees Optional manual grid angle override in degrees. If null, optimal angle θ_min is computed.
 * @property perimeterInteriorOffsetFt Interior width buffer to cover inside the perimeter (feet). Default 100.0 ft.
 * @property perimeterExteriorOffsetFt Exterior width buffer to cover outside the perimeter (feet). Default 50.0 ft.
 */
data class SurveyConfig(
    val missionName: String,
    val altitudeMeters: Double,
    val cameraType: CameraType = CameraType.EO,
    val speedMps: Double = 8.0,
    val turnSpeedMps: Double = 3.0,
    val turnTimePenaltySeconds: Double = 4.0,
    val sideOverlapRatio: Double = 0.70,
    val cameraFovDegrees: Double = 84.0,
    val customAngleDegrees: Double? = null,
    val perimeterInteriorOffsetFt: Double = 100.0,
    val perimeterExteriorOffsetFt: Double = 50.0
) {
    init {
        require(altitudeMeters > 0) { "Altitude must be greater than 0 meters." }
        require(speedMps > 0) { "Flight speed must be greater than 0 m/s." }
        require(sideOverlapRatio in 0.05..0.95) { "Side overlap must be between 5% and 95%." }
        require(perimeterInteriorOffsetFt >= 0) { "Interior offset must be non-negative." }
        require(perimeterExteriorOffsetFt >= 0) { "Exterior offset must be non-negative." }
    }

    val perimeterInteriorOffsetMeters: Double
        get() = perimeterInteriorOffsetFt * 0.3048

    val perimeterExteriorOffsetMeters: Double
        get() = perimeterExteriorOffsetFt * 0.3048

    /**
     * Computes the line spacing between adjacent parallel grid / perimeter ring passes (meters)
     * derived from altitude and camera horizontal field of view.
     *
     * Line Spacing = Ground Footprint Width * (1 - Overlap Ratio)
     * Ground Footprint Width = 2 * Altitude * tan(FOV / 2)
     */
    fun calculateLineSpacingMeters(): Double {
        val groundFootprint = 2.0 * altitudeMeters * Math.tan(Math.toRadians(cameraFovDegrees / 2.0))
        return (groundFootprint * (1.0 - sideOverlapRatio)).coerceAtLeast(2.0)
    }
}
