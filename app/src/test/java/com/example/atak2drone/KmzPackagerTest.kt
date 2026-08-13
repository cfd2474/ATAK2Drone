package com.example.atak2drone

import com.example.atak2drone.packager.KmzPackager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class KmzPackagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPackageDirectoryToKmz() {
        val packager = KmzPackager()
        val stageDir = tempFolder.newFolder("stage_dir")

        // Create dummy files inside staging directory
        val wpmzDir = File(stageDir, "wpmz").apply { mkdirs() }
        File(wpmzDir, "waylines.wpml").writeText("<wpml></wpml>")
        File(stageDir, "template.kml").writeText("<kml></kml>")

        val outKmz = File(tempFolder.root, "mission.kmz")
        val resultFile = packager.packageToKmz(stageDir, outKmz)

        assertTrue(resultFile.exists())
        assertTrue(resultFile.length() > 0)

        // Verify ZIP contents
        ZipFile(resultFile).use { zip ->
            val entries = zip.entries().toList().map { it.name }
            assertTrue(entries.any { it == "template.kml" })
            assertTrue(entries.any { it.endsWith("waylines.wpml") })
        }
    }
}
