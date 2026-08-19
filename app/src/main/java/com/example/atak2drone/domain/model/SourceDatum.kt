package com.example.atak2drone.domain.model

/**
 * Supported source coordinate reference system (CRS) / datum definitions for imported geometries.
 */
enum class SourceDatum(val displayName: String, val epsgCode: String) {
    WGS84("WGS 84 (Standard GPS)", "EPSG:4326"),
    NAD83("NAD83 (US CONUS GIS)", "EPSG:4269"),
    NAD27("NAD27 (Legacy US)", "EPSG:4267"),
    GCJ02("GCJ-02 (China Mars)", "GCJ-02"),
    ETRS89("ETRS89 (European)", "EPSG:4258"),
    AUTO_DETECT("Auto-Detect Datum", "AUTO")
}
