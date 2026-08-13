package com.example.atak2drone.domain.optimizer

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D

/**
 * Computes the 2D Convex Hull of a set of points using Andrew's Monotone Chain algorithm.
 * Guarantees counter-clockwise (CCW) vertex ordering without redundant collinear boundary points.
 * Time Complexity: O(N log N).
 */
object ConvexHull {

    /**
     * Computes the convex hull of a list of Cartesian [Point2D] points.
     *
     * @param points Input list of points.
     * @return List of [Point2D] representing the convex hull in counter-clockwise order.
     */
    fun compute(points: List<Point2D>): List<Point2D> {
        val uniquePoints = points.distinctBy { Pair((it.x * 10000).toLong(), (it.y * 10000).toLong()) }
        val n = uniquePoints.size
        if (n <= 2) return uniquePoints

        // 1. Sort points lexicographically by x (then by y)
        val sorted = uniquePoints.sortedWith(compareBy({ it.x }, { it.y }))

        // 2. Build lower hull
        val lower = mutableListOf<Point2D>()
        for (p in sorted) {
            while (lower.size >= 2 && GeometryUtils.crossProduct(lower[lower.size - 2], lower[lower.size - 1], p) <= 1e-9) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        // 3. Build upper hull
        val upper = mutableListOf<Point2D>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && GeometryUtils.crossProduct(upper[upper.size - 2], upper[upper.size - 1], p) <= 1e-9) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        // Concatenate lower and upper hull to produce full CCW polygon (remove duplicate endpoints)
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)

        val hull = ArrayList<Point2D>(lower.size + upper.size)
        hull.addAll(lower)
        hull.addAll(upper)

        // Ensure CCW orientation
        if (GeometryUtils.polygonSignedArea(hull) < 0) {
            hull.reverse()
        }

        return hull
    }
}
