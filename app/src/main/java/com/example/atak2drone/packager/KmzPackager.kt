package com.example.atak2drone.packager

import com.example.atak2drone.domain.interfaces.IKmzPackager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Single Responsibility Principle (SRP):
 * Dedicated class responsible strictly for compressing directories into KMZ/ZIP archives.
 */
class KmzPackager : IKmzPackager {

    override fun packageToKmz(sourceDirectory: File, outputKmzFile: File): File {
        require(sourceDirectory.exists() && sourceDirectory.isDirectory) {
            "Source directory ${sourceDirectory.absolutePath} must exist and be a directory."
        }

        outputKmzFile.parentFile?.mkdirs()

        ZipOutputStream(outputKmzFile.outputStream()).use { zos ->
            fun addEntry(f: File, base: String) {
                val entryPath = if (base.isEmpty()) f.name else "$base/${f.name}"
                if (f.isDirectory) {
                    f.listFiles()?.forEach { child -> addEntry(child, entryPath) }
                } else {
                    zos.putNextEntry(ZipEntry(entryPath))
                    f.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            sourceDirectory.listFiles()?.forEach { child -> addEntry(child, "") }
        }

        return outputKmzFile
    }
}
