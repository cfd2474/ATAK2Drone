package com.example.atak2drone.domain.interfaces

import com.example.atak2drone.model.Coordinate
import java.io.InputStream

/**
 * Interface Segregation Principle (ISP):
 * Dedicated interface strictly responsible for parsing KML / KMZ inputs.
 */
interface IKmlParser {
    /**
     * Parses a single outer polygon ring from a KML input stream.
     *
     * @param kmlInputStream The stream containing XML/KML content.
     * @return List of [Coordinate] vertices forming the boundary.
     * @throws IllegalArgumentException if no valid polygon or less than 3 vertices are found.
     */
    fun parsePolygon(kmlInputStream: InputStream): List<Coordinate>

    /**
     * Formats a boundary polygon into a minimal compliant KML Document string.
     */
    fun buildMinimalPolygonKml(polygon: List<Coordinate>, name: String, description: String? = null): String
}
