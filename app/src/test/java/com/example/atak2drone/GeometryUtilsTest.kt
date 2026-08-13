package com.example.atak2drone

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.model.Coordinate
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GeometryUtilsTest {

    @Test
    fun testCentroidComputation() {
        val coords = listOf(
            Coordinate(10.0, 20.0),
            Coordinate(20.0, 40.0),
            Coordinate(30.0, 60.0)
        )
        val centroid = GeometryUtils.computeCentroid(coords)
        assertEquals(20.0, centroid.latitude, 1e-6)
        assertEquals(40.0, centroid.longitude, 1e-6)
    }

    @Test
    fun testCartesianProjectionRoundTrip() {
        val origin = Coordinate(37.7749, -122.4194) // San Francisco
        val target = Coordinate(37.7780, -122.4150)

        val localPoint = GeometryUtils.projectToLocalCartesian(target, origin)
        val recovered = GeometryUtils.projectToGeographic(localPoint, origin)

        assertEquals(target.latitude, recovered.latitude, 1e-7)
        assertEquals(target.longitude, recovered.longitude, 1e-7)
    }

    @Test
    fun testShoelaceAreaAndPerimeter() {
        // 100m x 50m rectangle
        val rect = listOf(
            Point2D(0.0, 0.0),
            Point2D(100.0, 0.0),
            Point2D(100.0, 50.0),
            Point2D(0.0, 50.0)
        )
        val area = abs(GeometryUtils.polygonSignedArea(rect))
        val perimeter = GeometryUtils.polygonPerimeterMeters(rect)

        assertEquals(5000.0, area, 1e-4)
        assertEquals(300.0, perimeter, 1e-4)
    }

    @Test
    fun testHorizontalLineIntersection() {
        // Triangle from (0,0), (100, 0), (50, 100)
        val triangle = listOf(
            Point2D(0.0, 0.0),
            Point2D(100.0, 0.0),
            Point2D(50.0, 100.0)
        )

        // Line at y = 50 should intersect at x = 25 and x = 75
        val intersections = GeometryUtils.intersectHorizontalLineWithPolygon(50.0, triangle)
        assertEquals(2, intersections.size)
        assertEquals(25.0, intersections[0], 1e-4)
        assertEquals(75.0, intersections[1], 1e-4)
    }
}
