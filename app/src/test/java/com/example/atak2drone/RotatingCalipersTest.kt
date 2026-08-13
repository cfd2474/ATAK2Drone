package com.example.atak2drone

import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.optimizer.RotatingCalipers
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RotatingCalipersTest {

    @Test
    fun testOptimalAngleForLongRectangle() {
        // Rectangle 1000m long along X-axis, 100m wide along Y-axis
        // The minimum bounding width should be 100m, achieved by flying along the long edge (0° or 180°).
        val rect = listOf(
            Point2D(0.0, 0.0),
            Point2D(1000.0, 0.0),
            Point2D(1000.0, 100.0),
            Point2D(0.0, 100.0)
        )

        val config = SurveyConfig(
            missionName = "TestMission",
            altitudeMeters = 60.96, // 200ft
            speedMps = 10.0,
            turnSpeedMps = 3.0,
            turnTimePenaltySeconds = 4.0
        )

        val result = RotatingCalipers.findOptimalAngle(rect, config)

        // Optimal angle should align with the longest edge (0° / 180°)
        val angle = result.optimalAngleDegrees
        assertTrue("Expected angle near 0 or 180, but got $angle", abs(angle) < 1.0 || abs(angle - 180.0) < 1.0)
        assertEquals(100.0, result.minWidthMeters, 1e-3)
        assertTrue(result.metrics.isOptimized)
    }

    @Test
    fun testOptimalAngleMinimizesFlightDurationVsPerpendicular() {
        // Long rectangle along X-axis (1000m x 200m)
        val rect = listOf(
            Point2D(0.0, 0.0),
            Point2D(1000.0, 0.0),
            Point2D(1000.0, 200.0),
            Point2D(0.0, 200.0)
        )

        val configOptimal = SurveyConfig(
            missionName = "OptimalTest",
            altitudeMeters = 60.96,
            speedMps = 8.0,
            turnSpeedMps = 3.0,
            turnTimePenaltySeconds = 4.0
        )

        val optimalResult = RotatingCalipers.findOptimalAngle(rect, configOptimal)

        // Force a 90° (perpendicular / suboptimal) angle override
        val configSuboptimal = configOptimal.copy(customAngleDegrees = 90.0)
        val suboptimalResult = RotatingCalipers.findOptimalAngle(rect, configSuboptimal)

        // The optimal angle (flying long ways) must have fewer turns and lower total flight duration
        assertTrue(
            "Optimal turns (${optimalResult.metrics.numberOfTurns}) must be <= suboptimal turns (${suboptimalResult.metrics.numberOfTurns})",
            optimalResult.metrics.numberOfTurns <= suboptimalResult.metrics.numberOfTurns
        )
        assertTrue(
            "Optimal duration (${optimalResult.metrics.estimatedFlightDurationSeconds}) must be < suboptimal duration (${suboptimalResult.metrics.estimatedFlightDurationSeconds})",
            optimalResult.metrics.estimatedFlightDurationSeconds < suboptimalResult.metrics.estimatedFlightDurationSeconds
        )
    }
}
