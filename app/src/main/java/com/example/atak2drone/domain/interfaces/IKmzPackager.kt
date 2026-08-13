package com.example.atak2drone.domain.interfaces

import java.io.File

/**
 * Interface Segregation Principle (ISP):
 * Dedicated interface for packaging directory bundles into compressed KMZ archives.
 */
interface IKmzPackager {
    /**
     * Packages a directory containing KML/WPML files into a single `.kmz` archive.
     *
     * @param sourceDirectory Directory containing WPML files.
     * @param outputKmzFile Target destination `.kmz` file.
     * @return Output [File] on success.
     */
    fun packageToKmz(sourceDirectory: File, outputKmzFile: File): File
}
