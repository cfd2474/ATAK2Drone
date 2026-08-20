package com.example.atak2drone.domain.geometry

import com.example.atak2drone.model.Coordinate
import kotlin.math.*

/**
 * High-precision 2D Cartesian point in meters.
 */
data class Point2D(
    val x: Double,
    val y: Double
) {
    fun distanceTo(other: Point2D): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Rotates this point around origin (0, 0) by angle in radians.
     */
    fun rotate(rad: Double): Point2D {
        val cosA = cos(rad)
        val sinA = sin(rad)
        return Point2D(
            x = x * cosA - y * sinA,
            y = x * sinA + y * cosA
        )
    }

    /**
     * Rotates this point around a specified center point by angle in radians.
     */
    fun rotateAround(center: Point2D, rad: Double): Point2D {
        val translated = Point2D(x - center.x, y - center.y)
        val rotated = translated.rotate(rad)
        return Point2D(rotated.x + center.x, rotated.y + center.y)
    }
}

/**
 * 2D Line segment between two points in local Cartesian space.
 */
data class Segment2D(
    val start: Point2D,
    val end: Point2D
) {
    val length: Double get() = start.distanceTo(end)
}

/**
 * Geographic and geometric mathematics utility.
 * Implements local flat-Earth / equirectangular tangent projection around a reference coordinate,
 * yielding millimeter-level precision across operational survey areas (up to several tens of kilometers).
 */
object GeometryUtils {

    // WGS84 constants
    private const val WGS84_A = 6378137.0 // Semi-major axis (meters)
    private const val WGS84_E2 = 0.00669437999014 // First eccentricity squared

    /**
     * Calculates the geographical centroid of a list of coordinates.
     */
    fun computeCentroid(coords: List<Coordinate>): Coordinate {
        require(coords.isNotEmpty()) { "Coordinates list must not be empty." }
        var sumLat = 0.0
        var sumLon = 0.0
        for (c in coords) {
            sumLat += c.latitude
            sumLon += c.longitude
        }
        return Coordinate(sumLat / coords.size, sumLon / coords.size)
    }

    /**
     * Projects a geographic [Coordinate] (lat, lon) to a local Cartesian [Point2D] (x, y in meters)
     * relative to [origin] using high-accuracy ellipsoidal scale factors.
     */
    fun projectToLocalCartesian(coord: Coordinate, origin: Coordinate): Point2D {
        val latRad = Math.toRadians(origin.latitude)
        val sinLat = sin(latRad)

        // Radii of curvature
        val n = WGS84_A / sqrt(1.0 - WGS84_E2 * sinLat * sinLat)
        val m = WGS84_A * (1.0 - WGS84_E2) / (1.0 - WGS84_E2 * sinLat * sinLat).pow(1.5)

        val dLatRad = Math.toRadians(coord.latitude - origin.latitude)
        val dLonRad = Math.toRadians(coord.longitude - origin.longitude)

        val y = dLatRad * m
        val x = dLonRad * n * cos(latRad)

        return Point2D(x, y)
    }

    /**
     * Converts a local Cartesian [Point2D] (x, y in meters) back to geographic [Coordinate]
     * relative to [origin].
     */
    fun projectToGeographic(point: Point2D, origin: Coordinate): Coordinate {
        val latRad = Math.toRadians(origin.latitude)
        val sinLat = sin(latRad)

        val n = WGS84_A / sqrt(1.0 - WGS84_E2 * sinLat * sinLat)
        val m = WGS84_A * (1.0 - WGS84_E2) / (1.0 - WGS84_E2 * sinLat * sinLat).pow(1.5)

        val dLatRad = point.y / m
        val dLonRad = point.x / (n * cos(latRad))

        val lat = origin.latitude + Math.toDegrees(dLatRad)
        val lon = origin.longitude + Math.toDegrees(dLonRad)

        return Coordinate(latitude = lat, longitude = lon)
    }

    /**
     * Computes the 2D cross product of vectors (b - a) and (c - a).
     * Positive: Counter-clockwise turn
     * Negative: Clockwise turn
     * Zero: Collinear
     */
    fun crossProduct(a: Point2D, b: Point2D, c: Point2D): Double {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    }

    /**
     * Computes the signed area of a 2D polygon using the Shoelace formula.
     * Positive indicates counter-clockwise vertex winding order.
     */
    fun polygonSignedArea(points: List<Point2D>): Double {
        if (points.size < 3) return 0.0
        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += (points[i].x * points[j].y - points[j].x * points[i].y)
        }
        return area / 2.0
    }

    /**
     * Computes the perimeter length of a polygon in meters.
     */
    fun polygonPerimeterMeters(points: List<Point2D>): Double {
        if (points.size < 2) return 0.0
        var dist = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            dist += points[i].distanceTo(points[j])
        }
        return dist
    }

    /**
     * Computes intersection points of a horizontal line (y = constY) with a closed polygon.
     * Returns the sorted x-coordinates of all intersection points.
     */
    fun intersectHorizontalLineWithPolygon(constY: Double, polygon: List<Point2D>): List<Double> {
        val xIntersections = mutableListOf<Double>()
        val n = polygon.size
        if (n < 3) return xIntersections

        for (i in 0 until n) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % n]

            val yMin = min(p1.y, p2.y)
            val yMax = max(p1.y, p2.y)

            // Check if horizontal line intersects the vertical span of this edge (half-open to prevent double-counting vertices)
            if (constY >= yMin && constY < yMax && abs(p2.y - p1.y) > 1e-9) {
                val t = (constY - p1.y) / (p2.y - p1.y)
                val x = p1.x + t * (p2.x - p1.x)
                xIntersections.add(x)
            }
        }

        xIntersections.sort()
        return xIntersections
    }

    /**
     * Computes Great Circle / Haversine distance between two coordinates in meters.
     */
    fun haversineDistanceMeters(c1: Coordinate, c2: Coordinate): Double {
        val lat1Rad = Math.toRadians(c1.latitude)
        val lat2Rad = Math.toRadians(c2.latitude)
        val dLat = Math.toRadians(c2.latitude - c1.latitude)
        val dLon = Math.toRadians(c2.longitude - c1.longitude)

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return WGS84_A * c
    }

    /**
     * Offsets a 2D closed polygon by [offsetMeters] in Cartesian coordinates.
     * - Positive offset: Outward expansion / Exterior buffer (outset)
     * - Negative offset: Inward contraction / Interior buffer (inset)
     * - Zero offset: Original polygon
     *
     * Uses normal vector displacement and miter intersection with miter clamping.
     * Returns null if inward offset collapses the polygon.
     */
    fun offsetPolygon(
        polygon: List<Point2D>,
        offsetMeters: Double,
        miterLimit: Double = 3.0
    ): List<Point2D>? {
        if (abs(offsetMeters) < 1e-3) return polygon
        val n = polygon.size
        if (n < 3) return null

        // Ensure CCW winding
        val signedArea = polygonSignedArea(polygon)
        val ccwPolygon = if (signedArea < 0) polygon.reversed() else polygon

        // 1. Calculate edge unit vectors and outward normals
        data class OffsetLine(val p1: Point2D, val p2: Point2D, val dir: Point2D, val normal: Point2D)
        val lines = ArrayList<OffsetLine>(n)

        for (i in 0 until n) {
            val p1 = ccwPolygon[i]
            val p2 = ccwPolygon[(i + 1) % n]
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-6) continue

            val dir = Point2D(dx / len, dy / len)
            // Outward normal for CCW polygon is (dy, -dx)
            val normal = Point2D(dir.y, -dir.x)

            // Shift edge by offsetMeters * normal
            val offP1 = Point2D(p1.x + offsetMeters * normal.x, p1.y + offsetMeters * normal.y)
            val offP2 = Point2D(p2.x + offsetMeters * normal.x, p2.y + offsetMeters * normal.y)

            lines.add(OffsetLine(offP1, offP2, dir, normal))
        }

        if (lines.size < 3) return null

        // 2. Intersect adjacent offset lines to compute new vertices
        val offsetVertices = ArrayList<Point2D>(lines.size)
        val lineCount = lines.size

        for (i in 0 until lineCount) {
            val prevLine = lines[(i - 1 + lineCount) % lineCount]
            val curLine = lines[i]
            val originalVertex = ccwPolygon[i]

            // Intersect prevLine and curLine
            // prevLine: p = prevLine.p1 + t * prevLine.dir
            // curLine:  q = curLine.p1 + s * curLine.dir
            val det = prevLine.dir.x * curLine.dir.y - prevLine.dir.y * curLine.dir.x

            val newVertex = if (abs(det) > 1e-6) {
                val dx = curLine.p1.x - prevLine.p1.x
                val dy = curLine.p1.y - prevLine.p1.y
                val t = (dx * curLine.dir.y - dy * curLine.dir.x) / det
                val intersect = Point2D(prevLine.p1.x + t * prevLine.dir.x, prevLine.p1.y + t * prevLine.dir.y)

                // Miter limit check
                val miterDist = intersect.distanceTo(originalVertex)
                val maxAllowed = abs(offsetMeters) * miterLimit
                if (miterDist > maxAllowed && miterDist > 1e-6) {
                    val scale = maxAllowed / miterDist
                    Point2D(
                        originalVertex.x + (intersect.x - originalVertex.x) * scale,
                        originalVertex.y + (intersect.y - originalVertex.y) * scale
                    )
                } else {
                    intersect
                }
            } else {
                curLine.p1
            }

            offsetVertices.add(newVertex)
        }

        // Validate area of resulting polygon
        val newArea = polygonSignedArea(offsetVertices)
        // If inward offset caused polygon to invert or collapse, return null
        if (newArea <= 0) return null

        return offsetVertices
    }

    /**
     * Offsets a 2D closed polygon by a list of variable per-edge offsets [offsetMetersList] in Cartesian coordinates.
     * Each edge [i] is displaced by [offsetMetersList[i]] along its outward normal.
     */
    fun offsetPolygonVariable(
        polygon: List<Point2D>,
        offsetMetersList: List<Double>,
        miterLimit: Double = 3.0
    ): List<Point2D>? {
        val n = polygon.size
        if (n < 3 || offsetMetersList.size != n) return null

        val signedArea = polygonSignedArea(polygon)
        val ccwPolygon = if (signedArea < 0) polygon.reversed() else polygon
        val ccwOffsets = if (signedArea < 0) offsetMetersList.reversed() else offsetMetersList

        data class OffsetLine(val p1: Point2D, val p2: Point2D, val dir: Point2D, val normal: Point2D, val offset: Double)
        val lines = ArrayList<OffsetLine>(n)

        for (i in 0 until n) {
            val p1 = ccwPolygon[i]
            val p2 = ccwPolygon[(i + 1) % n]
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-6) continue

            val dir = Point2D(dx / len, dy / len)
            val normal = Point2D(dir.y, -dir.x)
            val offsetMeters = ccwOffsets[i]

            val offP1 = Point2D(p1.x + offsetMeters * normal.x, p1.y + offsetMeters * normal.y)
            val offP2 = Point2D(p2.x + offsetMeters * normal.x, p2.y + offsetMeters * normal.y)

            lines.add(OffsetLine(offP1, offP2, dir, normal, offsetMeters))
        }

        if (lines.size < 3) return null

        val offsetVertices = ArrayList<Point2D>(lines.size)
        val lineCount = lines.size

        for (i in 0 until lineCount) {
            val prevLine = lines[(i - 1 + lineCount) % lineCount]
            val curLine = lines[i]
            val originalVertex = ccwPolygon[i]

            val det = prevLine.dir.x * curLine.dir.y - prevLine.dir.y * curLine.dir.x

            val newVertex = if (abs(det) > 1e-6) {
                val dx = curLine.p1.x - prevLine.p1.x
                val dy = curLine.p1.y - prevLine.p1.y
                val t = (dx * curLine.dir.y - dy * curLine.dir.x) / det
                val intersect = Point2D(prevLine.p1.x + t * prevLine.dir.x, prevLine.p1.y + t * prevLine.dir.y)

                val avgOffset = (abs(prevLine.offset) + abs(curLine.offset)) / 2.0
                val miterDist = intersect.distanceTo(originalVertex)
                val maxAllowed = avgOffset * miterLimit
                if (miterDist > maxAllowed && miterDist > 1e-6) {
                    val scale = maxAllowed / miterDist
                    Point2D(
                        originalVertex.x + (intersect.x - originalVertex.x) * scale,
                        originalVertex.y + (intersect.y - originalVertex.y) * scale
                    )
                } else {
                    intersect
                }
            } else {
                curLine.p1
            }

            offsetVertices.add(newVertex)
        }

        val newArea = polygonSignedArea(offsetVertices)
        if (newArea <= 0) return null

        return offsetVertices
    }

    /**
     * Stage 1 Baseline Subdivision: Subdivides polygon edges longer than [maxSegmentLengthMeters]
     * into equal baseline sub-segments. Preserves exact perimeter geometry while densifying vertices
     * so long edges can adapt dynamically to localized terrain slope variations along their length.
     */
    fun subdividePolygonEdges(
        polygon: List<Coordinate>,
        maxSegmentLengthMeters: Double = 40.0
    ): List<Coordinate> {
        if (polygon.size < 3 || maxSegmentLengthMeters <= 0.0) return polygon

        val origin = computeCentroid(polygon)
        val cartesian = polygon.map { projectToLocalCartesian(it, origin) }
        val n = cartesian.size
        val resultCartesian = mutableListOf<Point2D>()

        for (i in 0 until n) {
            val p1 = cartesian[i]
            val p2 = cartesian[(i + 1) % n]
            resultCartesian.add(p1)

            val edgeLen = p1.distanceTo(p2)
            if (edgeLen > maxSegmentLengthMeters) {
                val numSegments = ceil(edgeLen / maxSegmentLengthMeters).toInt()
                for (k in 1 until numSegments) {
                    val fraction = k.toDouble() / numSegments
                    val subX = p1.x + fraction * (p2.x - p1.x)
                    val subY = p1.y + fraction * (p2.y - p1.y)
                    resultCartesian.add(Point2D(subX, subY))
                }
            }
        }

        return resultCartesian.map { projectToGeographic(it, origin) }
    }

    /**
     * Stage 2 Steep-Slope Refinement: Identifies sub-segments with slope >= [steepSlopeThreshold]
     * (or high slope variance) and adaptively subdivides them further into fine [fineMaxSegmentLength] sub-segments.
     *
     * @return Refined polygon vertices with high-density vertices in steep slope zones.
     */
    fun refineHighSlopeSegments(
        polygon: List<Coordinate>,
        slopes: List<Double>,
        steepSlopeThreshold: Double = 50.0,
        fineMaxSegmentLength: Double = 15.0
    ): List<Coordinate> {
        if (polygon.size < 3 || slopes.size != polygon.size || fineMaxSegmentLength <= 0.0) {
            return polygon
        }

        val origin = computeCentroid(polygon)
        val cartesian = polygon.map { projectToLocalCartesian(it, origin) }
        val n = cartesian.size
        val resultCartesian = mutableListOf<Point2D>()

        for (i in 0 until n) {
            val p1 = cartesian[i]
            val p2 = cartesian[(i + 1) % n]
            resultCartesian.add(p1)

            val slope = slopes[i]
            val prevSlope = slopes[(i - 1 + n) % n]
            val slopeVariance = abs(slope - prevSlope)

            // Trigger fine subdivision if slope >= threshold (50%) or adjacent slope variance >= 15%
            val isSteep = slope >= steepSlopeThreshold || slopeVariance >= 15.0
            val edgeLen = p1.distanceTo(p2)

            if (isSteep && edgeLen > fineMaxSegmentLength) {
                val numSegments = ceil(edgeLen / fineMaxSegmentLength).toInt()
                for (k in 1 until numSegments) {
                    val fraction = k.toDouble() / numSegments
                    val subX = p1.x + fraction * (p2.x - p1.x)
                    val subY = p1.y + fraction * (p2.y - p1.y)
                    resultCartesian.add(Point2D(subX, subY))
                }
            }
        }

        return resultCartesian.map { projectToGeographic(it, origin) }
    }
}

