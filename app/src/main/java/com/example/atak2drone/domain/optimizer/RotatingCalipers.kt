package com.example.atak2drone.domain.optimizer

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.model.OptimizationMetrics
import com.example.atak2drone.domain.model.SurveyConfig
import kotlin.math.*

/**
 * Implements the Rotating Calipers algorithm to find the optimal flight orientation angle θ_min
 * that minimizes polygon bounding width W(θ), turn count N_turns(θ), and total flight duration T(θ).
 */
object RotatingCalipers {

    data class CalipersResult(
        val optimalAngleDegrees: Double,
        val minWidthMeters: Double,
        val metrics: OptimizationMetrics
    )

    /**
     * Finds the minimum flight duration orientation angle for a polygon and survey configuration.
     *
     * @param localPolygon Polygon boundary in local Cartesian meters.
     * @param config Survey parameters (flight speed, turn speed, turn delay, line spacing).
     * @return [CalipersResult] containing the optimal angle and mission performance metrics.
     */
    fun findOptimalAngle(localPolygon: List<Point2D>, config: SurveyConfig): CalipersResult {
        val hull = ConvexHull.compute(localPolygon)
        require(hull.size >= 3) { "Convex hull must contain at least 3 points." }

        val lineSpacing = config.calculateLineSpacingMeters()

        // If user provided a manual custom angle override, calculate metrics directly for that angle
        if (config.customAngleDegrees != null) {
            val customAngle = (config.customAngleDegrees % 180.0 + 180.0) % 180.0
            val metrics = evaluateAngle(localPolygon, customAngle, lineSpacing, config, isOptimized = false)
            return CalipersResult(
                optimalAngleDegrees = customAngle,
                minWidthMeters = metrics.boundingBoxWidthMeters,
                metrics = metrics
            )
        }

        // By Freeman & Shapira theorem, the minimum bounding box shares an edge with the convex hull.
        // We evaluate each edge angle of the convex hull.
        var bestAngle = 0.0
        var bestMetrics: OptimizationMetrics? = null
        var minDuration = Double.MAX_VALUE

        val n = hull.size
        for (i in 0 until n) {
            val p1 = hull[i]
            val p2 = hull[(i + 1) % n]

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            if (dx * dx + dy * dy < 1e-6) continue

            // Angle of the edge relative to X-axis in degrees [0, 180)
            val edgeAngleRad = atan2(dy, dx)
            var edgeAngleDeg = Math.toDegrees(edgeAngleRad)
            edgeAngleDeg = (edgeAngleDeg % 180.0 + 180.0) % 180.0

            val metrics = evaluateAngle(localPolygon, edgeAngleDeg, lineSpacing, config, isOptimized = true)
            if (metrics.estimatedFlightDurationSeconds < minDuration) {
                minDuration = metrics.estimatedFlightDurationSeconds
                bestAngle = edgeAngleDeg
                bestMetrics = metrics
            }
        }

        val finalMetrics = bestMetrics ?: evaluateAngle(localPolygon, 0.0, lineSpacing, config, isOptimized = true)
        return CalipersResult(
            optimalAngleDegrees = bestAngle,
            minWidthMeters = finalMetrics.boundingBoxWidthMeters,
            metrics = finalMetrics
        )
    }

    /**
     * Evaluates flight duration and distance metrics for a given flight orientation angle θ.
     *
     * Total mission execution time formulation:
     * T(θ) = (L_transect / v_flight) + (N_turns * d_spacing / v_turn) + (N_turns * τ_turn)
     */
    fun evaluateAngle(
        polygon: List<Point2D>,
        angleDegrees: Double,
        lineSpacingMeters: Double,
        config: SurveyConfig,
        isOptimized: Boolean
    ): OptimizationMetrics {
        val angleRad = Math.toRadians(angleDegrees)

        // Rotate polygon by -angleRad so flight transects become horizontal lines parallel to X-axis
        val rotatedPolygon = polygon.map { it.rotate(-angleRad) }

        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (p in rotatedPolygon) {
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val boundingWidth = (maxY - minY).coerceAtLeast(0.0)
        val numTransects = max(1, ceil(boundingWidth / lineSpacingMeters).toInt())
        val numTurns = max(0, numTransects - 1)

        // Calculate total transect length across all parallel flight lines
        var totalTransectLength = 0.0
        val actualSpacing = if (numTransects > 1) boundingWidth / numTransects else lineSpacingMeters
        var currentY = minY + (actualSpacing / 2.0)

        while (currentY <= maxY) {
            val intersections = GeometryUtils.intersectHorizontalLineWithPolygon(currentY, rotatedPolygon)
            // Intersections come in pairs (entry and exit)
            var i = 0
            while (i + 1 < intersections.size) {
                totalTransectLength += (intersections[i + 1] - intersections[i]).coerceAtLeast(0.0)
                i += 2
            }
            currentY += actualSpacing
        }

        // Flight duration calculation
        val transectTime = totalTransectLength / config.speedMps
        val turnDistance = numTurns * actualSpacing
        val turnTravelTime = turnDistance / config.turnSpeedMps
        val turnDelayTime = numTurns * config.turnTimePenaltySeconds

        val totalDurationSeconds = transectTime + turnTravelTime + turnDelayTime
        val totalDistance = totalTransectLength + turnDistance

        return OptimizationMetrics(
            optimalAngleDegrees = angleDegrees,
            isOptimized = isOptimized,
            boundingBoxWidthMeters = boundingWidth,
            numberOfTurns = numTurns,
            numberOfTransects = numTransects,
            totalTransectDistanceMeters = totalTransectLength,
            totalTurnDistanceMeters = turnDistance,
            totalDistanceMeters = totalDistance,
            estimatedFlightDurationSeconds = totalDurationSeconds
        )
    }
}
