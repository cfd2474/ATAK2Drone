package com.example.atak2drone.domain.strategy

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.interfaces.IMissionStrategy
import com.example.atak2drone.domain.model.MissionPlan
import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.OptimizationMetrics
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.model.Coordinate
import kotlin.math.ceil
import kotlin.math.max

/**
 * Generates an advanced multi-ring perimeter corridor mission.
 * Covers user-defined interior width (inset) and exterior width (outset) buffers
 * using concentric offset flight rings.
 */
class VertexPathStrategy : IMissionStrategy {

    override val missionType: MissionType = MissionType.VERTEX_PERIMETER

    override fun generatePlan(polygon: List<Coordinate>, config: SurveyConfig): MissionPlan {
        require(polygon.size >= 3) { "Polygon must have at least 3 vertices." }

        val origin = GeometryUtils.computeCentroid(polygon)
        val localPolygon = polygon.map { GeometryUtils.projectToLocalCartesian(it, origin) }

        val extOffsetMeters = config.perimeterExteriorOffsetMeters
        val intOffsetMeters = config.perimeterInteriorOffsetMeters
        val lineSpacing = config.calculateLineSpacingMeters()

        // Calculate offset distances for exterior passes (outset)
        val extPasses = if (extOffsetMeters > 1.0) max(1, ceil(extOffsetMeters / lineSpacing).toInt()) else 0
        val intPasses = if (intOffsetMeters > 1.0) max(1, ceil(intOffsetMeters / lineSpacing).toInt()) else 0

        val rings = mutableListOf<List<Point2D>>()

        val edgeFactors = config.edgeSlopeFactors
        val isAutoDem = config.slopeMode == com.example.atak2drone.domain.model.SlopeMode.AUTO_DEM_OPEN_SOURCE &&
                edgeFactors != null && edgeFactors.size == localPolygon.size

        if (isAutoDem && edgeFactors != null) {
            // Convert slope percentages to slope factors k_i = cos(arctan(S_i / 100))
            val kFactors = edgeFactors.map { slope ->
                val ratio = slope / 100.0
                1.0 / kotlin.math.sqrt(1.0 + ratio * ratio)
            }

            // Exterior passes (outset)
            if (extPasses > 0) {
                val baseExtMeters = config.perimeterExteriorOffsetMeters
                for (p in extPasses downTo 1) {
                    val ratio = p.toDouble() / extPasses
                    val perEdgeOffsets = kFactors.map { k -> ratio * baseExtMeters * k }
                    val ring = GeometryUtils.offsetPolygonVariable(localPolygon, perEdgeOffsets)
                    if (ring != null && ring.size >= 3) rings.add(ring)
                }
            }

            // Primary perimeter ring (0 offset)
            rings.add(localPolygon)

            // Interior passes (inset)
            if (intPasses > 0) {
                val baseIntMeters = config.perimeterInteriorOffsetMeters
                for (p in 1..intPasses) {
                    val ratio = p.toDouble() / intPasses
                    val perEdgeOffsets = kFactors.map { k -> -ratio * baseIntMeters * k }
                    val ring = GeometryUtils.offsetPolygonVariable(localPolygon, perEdgeOffsets)
                    if (ring != null && ring.size >= 3) rings.add(ring)
                }
            }
        } else {
            // Standard scalar offsetting (OFF / Flat 2D)
            val extOffsets = mutableListOf<Double>()
            if (extPasses > 0) {
                val step = extOffsetMeters / extPasses
                for (p in extPasses downTo 1) {
                    extOffsets.add(p * step)
                }
            }

            val intOffsets = mutableListOf<Double>()
            if (intPasses > 0) {
                val step = intOffsetMeters / intPasses
                for (p in 1..intPasses) {
                    intOffsets.add(-p * step)
                }
            }

            val allOffsets = mutableListOf<Double>()
            allOffsets.addAll(extOffsets)
            allOffsets.add(0.0)
            allOffsets.addAll(intOffsets)

            for (offset in allOffsets) {
                val ring = GeometryUtils.offsetPolygon(localPolygon, offset)
                if (ring != null && ring.size >= 3) {
                    rings.add(ring)
                }
            }
        }

        // Fallback if no rings could be computed
        val finalRings = if (rings.isEmpty()) listOf(localPolygon) else rings

        // Chain rings together into a single continuous waypoint sequence with 3D altitudes
        val chainedWaypoints = mutableListOf<Pair<Point2D, Double?>>()
        var lastPoint: Point2D? = null

        for (ring in finalRings) {
            // Find vertex on this ring closest to lastPoint to minimize transition distance
            val n = ring.size
            val startIndex = if (lastPoint != null) {
                ring.indices.minByOrNull { ring[it].distanceTo(lastPoint!!) } ?: 0
            } else {
                0
            }

            // Add ring vertices starting from closest index, closing the loop
            for (i in 0 until n) {
                val idx = (startIndex + i) % n
                val alt = if (idx < polygon.size) polygon[idx].altitudeMeters else null
                chainedWaypoints.add(Pair(ring[idx], alt))
            }
            // Close the ring back to the first point of this ring
            val startAlt = if (startIndex < polygon.size) polygon[startIndex].altitudeMeters else null
            chainedWaypoints.add(Pair(ring[startIndex], startAlt))
            lastPoint = ring[startIndex]
        }

        // Convert Cartesian waypoints back to geographic Coordinates with 3D altitudes
        val geographicWaypoints = chainedWaypoints.map { (pt, alt) ->
            val geo = GeometryUtils.projectToGeographic(pt, origin)
            if (alt != null) geo.copy(altitudeMeters = alt) else geo
        }

        val chainedCartesianWaypoints = chainedWaypoints.map { it.first }

        // Compute accurate mission metrics
        var totalDistanceMeters = 0.0
        for (i in 0 until chainedCartesianWaypoints.size - 1) {
            totalDistanceMeters += chainedCartesianWaypoints[i].distanceTo(chainedCartesianWaypoints[i + 1])
        }

        val totalTurns = chainedCartesianWaypoints.size
        val flightTime = totalDistanceMeters / config.speedMps
        val turnTime = totalTurns * config.turnTimePenaltySeconds
        val totalDuration = flightTime + turnTime

        val minSlope = edgeFactors?.minOrNull() ?: 0.0
        val maxSlope = edgeFactors?.maxOrNull() ?: 0.0
        val avgSlope = edgeFactors?.average() ?: 0.0

        val metrics = OptimizationMetrics(
            optimalAngleDegrees = 0.0,
            isOptimized = false,
            boundingBoxWidthMeters = extOffsetMeters + intOffsetMeters,
            numberOfTurns = totalTurns,
            numberOfTransects = finalRings.size,
            totalTransectDistanceMeters = totalDistanceMeters,
            totalTurnDistanceMeters = 0.0,
            totalDistanceMeters = totalDistanceMeters,
            estimatedFlightDurationSeconds = totalDuration,
            slopeMode = if (isAutoDem) com.example.atak2drone.domain.model.SlopeMode.AUTO_DEM_OPEN_SOURCE else com.example.atak2drone.domain.model.SlopeMode.OFF,
            minSlopePercent = minSlope,
            maxSlopePercent = maxSlope,
            avgSlopePercent = avgSlope
        )

        return MissionPlan(
            missionName = config.missionName,
            missionType = MissionType.VERTEX_PERIMETER,
            polygon = polygon,
            waypoints = geographicWaypoints,
            config = config,
            metrics = metrics
        )
    }
}
