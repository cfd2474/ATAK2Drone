package com.example.atak2drone.domain.model

/**
 * Encapsulates the algorithmic optimization results for a flight mission.
 *
 * @property optimalAngleDegrees The optimal flight orientation angle (0° to 180°).
 * @property isOptimized Whether the angle was calculated by rotating calipers (true) or user-specified (false).
 * @property boundingBoxWidthMeters The perpendicular span of the polygon at this angle.
 * @property numberOfTurns Total number of 180° turns required.
 * @property numberOfTransects Total number of parallel flight lines.
 * @property totalTransectDistanceMeters Total distance flown in straight survey lines.
 * @property totalTurnDistanceMeters Total distance flown during turn transitions between transects.
 * @property totalDistanceMeters Total distance of the survey path.
 * @property estimatedFlightDurationSeconds Estimated total mission duration in seconds.
 */
data class OptimizationMetrics(
    val optimalAngleDegrees: Double,
    val isOptimized: Boolean,
    val boundingBoxWidthMeters: Double,
    val numberOfTurns: Int,
    val numberOfTransects: Int,
    val totalTransectDistanceMeters: Double,
    val totalTurnDistanceMeters: Double,
    val totalDistanceMeters: Double,
    val estimatedFlightDurationSeconds: Double
) {
    val estimatedDurationMinutes: Double
        get() = estimatedFlightDurationSeconds / 60.0
}
