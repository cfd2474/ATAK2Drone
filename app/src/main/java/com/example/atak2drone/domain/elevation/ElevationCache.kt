package com.example.atak2drone.domain.elevation

import com.example.atak2drone.model.Coordinate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Thread-safe in-memory cache for coordinate elevation data.
 * Quantizes geographic coordinates to ~1-meter precision (5 decimal places)
 * to avoid duplicate API network calls.
 */
class ElevationCache {

    private val cache = ConcurrentHashMap<String, Double>()

    private fun makeKey(coord: Coordinate): String {
        val latKey = (coord.latitude * 100000.0).roundToInt()
        val lonKey = (coord.longitude * 100000.0).roundToInt()
        return "$latKey,$lonKey"
    }

    fun get(coord: Coordinate): Double? {
        return cache[makeKey(coord)]
    }

    fun put(coord: Coordinate, elevationMeters: Double) {
        cache[makeKey(coord)] = elevationMeters
    }

    fun putAll(entries: Map<Coordinate, Double>) {
        entries.forEach { (coord, elev) -> put(coord, elev) }
    }

    fun clear() {
        cache.clear()
    }

    val size: Int get() = cache.size
}
