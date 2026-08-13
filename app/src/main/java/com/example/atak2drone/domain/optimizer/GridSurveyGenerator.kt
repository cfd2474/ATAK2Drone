package com.example.atak2drone.domain.optimizer

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.model.MissionPlan
import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.model.Coordinate
import kotlin.math.ceil
import kotlin.math.max

/**
 * Generates an optimal boustrophedon (lawnmower) grid survey flight path inside a geographic polygon boundary.
 */
object GridSurveyGenerator {

    /**
     * Generates a complete coverage flight plan with optimized flight orientation angle.
     *
     * @param polygon Geographic coordinates of the survey boundary.
     * @param config Survey parameters (altitude, speed, overlap, FOV, optional angle).
     * @return [MissionPlan] containing the ordered flight waypoints and optimization metrics.
     */
    fun generateGridPlan(polygon: List<Coordinate>, config: SurveyConfig): MissionPlan {
        require(polygon.size >= 3) { "Survey polygon must have at least 3 points." }

        val origin = GeometryUtils.computeCentroid(polygon)
        val localPolygon = polygon.map { GeometryUtils.projectToLocalCartesian(it, origin) }

        // Find optimal orientation angle and metrics via Rotating Calipers
        val calipersResult = RotatingCalipers.findOptimalAngle(localPolygon, config)
        val optimalAngleDeg = calipersResult.optimalAngleDegrees
        val optimalAngleRad = Math.toRadians(optimalAngleDeg)

        // Rotate polygon by -theta to align survey lines horizontally
        val rotatedPolygon = localPolygon.map { it.rotate(-optimalAngleRad) }

        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (p in rotatedPolygon) {
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val boundingWidth = maxY - minY
        val lineSpacing = config.calculateLineSpacingMeters()
        val numTransects = max(1, ceil(boundingWidth / lineSpacing).toInt())
        val actualSpacing = if (numTransects > 1) boundingWidth / numTransects else lineSpacing

        val rotatedWaypoints = mutableListOf<Point2D>()
        var reverseDirection = false
        var currentY = minY + (actualSpacing / 2.0)

        while (currentY <= maxY) {
            val intersections = GeometryUtils.intersectHorizontalLineWithPolygon(currentY, rotatedPolygon)

            // Intersected x coordinates in pairs: [entry1, exit1, entry2, exit2, ...]
            val segments = mutableListOf<Pair<Point2D, Point2D>>()
            var i = 0
            while (i + 1 < intersections.size) {
                val pStart = Point2D(intersections[i], currentY)
                val pEnd = Point2D(intersections[i + 1], currentY)
                segments.add(Pair(pStart, pEnd))
                i += 2
            }

            if (segments.isNotEmpty()) {
                if (reverseDirection) {
                    // Traverse from right to left
                    for (seg in segments.reversed()) {
                        rotatedWaypoints.add(seg.second)
                        rotatedWaypoints.add(seg.first)
                    }
                } else {
                    // Traverse from left to right
                    for (seg in segments) {
                        rotatedWaypoints.add(seg.first)
                        rotatedWaypoints.add(seg.second)
                    }
                }
                reverseDirection = !reverseDirection
            }

            currentY += actualSpacing
        }

        // If no internal transects were generated (e.g. tiny polygon), fallback to hull vertices
        val finalCartesianWaypoints = if (rotatedWaypoints.isEmpty()) {
            localPolygon
        } else {
            // Rotate points back by +theta to original coordinate frame
            rotatedWaypoints.map { it.rotate(optimalAngleRad) }
        }

        // Project local Cartesian points back to geographic Coordinates (lat, lon)
        val geographicWaypoints = finalCartesianWaypoints.map {
            GeometryUtils.projectToGeographic(it, origin)
        }

        return MissionPlan(
            missionName = config.missionName,
            missionType = MissionType.GRID_SURVEY,
            polygon = polygon,
            waypoints = geographicWaypoints,
            config = config,
            metrics = calipersResult.metrics
        )
    }
}
