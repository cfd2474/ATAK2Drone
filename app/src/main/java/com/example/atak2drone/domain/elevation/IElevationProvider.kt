package com.example.atak2drone.domain.elevation

import com.example.atak2drone.model.Coordinate

/**
 * Interface for querying terrain elevation data from open-source APIs or local DEM sources.
 */
interface IElevationProvider {
    /**
     * Retrieves terrain elevation in meters above sea level (MSL) for a list of coordinates.
     */
    suspend fun getElevations(coordinates: List<Coordinate>): List<Double>

    /**
     * Calculates the local transverse slope percentage at a list of coordinates given outward normal azimuth angles.
     * Sampling is performed at distance [sampleDistanceMeters] along the normal direction.
     *
     * @return List of slope percentages (e.g. 50.0 for 50% slope / 26.6° angle).
     */
    suspend fun getTransverseSlopes(
        coordinates: List<Coordinate>,
        normalAzimuthsDeg: List<Double>,
        sampleDistanceMeters: Double = 10.0
    ): List<Double>
}
