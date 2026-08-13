package com.example.atak2drone

import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.optimizer.ConvexHull
import org.junit.Assert.*
import org.junit.Test

class ConvexHullTest {

    @Test
    fun testConvexHullSquareWithInteriorPoints() {
        val points = listOf(
            Point2D(0.0, 0.0),
            Point2D(100.0, 0.0),
            Point2D(100.0, 100.0),
            Point2D(0.0, 100.0),
            Point2D(50.0, 50.0),  // Interior point
            Point2D(25.0, 25.0)   // Interior point
        )

        val hull = ConvexHull.compute(points)
        assertEquals(4, hull.size)
        assertTrue(hull.any { it.x == 0.0 && it.y == 0.0 })
        assertTrue(hull.any { it.x == 100.0 && it.y == 0.0 })
        assertTrue(hull.any { it.x == 100.0 && it.y == 100.0 })
        assertTrue(hull.any { it.x == 0.0 && it.y == 100.0 })
    }

    @Test
    fun testConvexHullConcavePolygon() {
        // Star / arrow shape
        val points = listOf(
            Point2D(0.0, 0.0),
            Point2D(50.0, 20.0),  // Concave indentation
            Point2D(100.0, 0.0),
            Point2D(50.0, 100.0)
        )

        val hull = ConvexHull.compute(points)
        assertEquals(3, hull.size)
        // Indentation point (50, 20) should be excluded from convex hull
        assertFalse(hull.any { it.x == 50.0 && it.y == 20.0 })
    }
}
