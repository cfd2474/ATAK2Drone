package com.example.atak2drone.domain.interfaces

import com.example.atak2drone.domain.model.SourceDatum
import com.example.atak2drone.model.Coordinate

/**
 * Dependency Inversion Principle (DIP):
 * Abstraction for auto-detecting and converting coordinate points/polygons from
 * source datums into standard WGS 84 (EPSG:4326).
 */
interface IDatumConverter {
    /**
     * Auto-detects the source datum from KML content text and metadata tags.
     */
    fun detectDatumFromKml(kmlContent: String): SourceDatum

    /**
     * Converts a single [Coordinate] from [sourceDatum] into WGS 84.
     */
    fun convertToWgs84(coord: Coordinate, sourceDatum: SourceDatum): Coordinate

    /**
     * Converts a list of polygon [Coordinate]s from [sourceDatum] into WGS 84.
     */
    fun convertPolygonToWgs84(polygon: List<Coordinate>, sourceDatum: SourceDatum): List<Coordinate>
}
