// WpmlGenerator.kt
package com.example.atak2drone.utils

import android.content.Context
import android.util.Log
import com.example.atak2drone.model.CameraType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

object WpmlGenerator {

    private const val TAG = "WpmlGenerator"
    private const val WP_NS = "http://www.dji.com/wpmz/1.0.6"
    private const val KML_NS = "http://www.opengis.net/kml/2.2"

    private enum class AltBucket(val feet: Int) { FT200(200), FT400(400) }

    fun generateFromTemplateKmz(
        context: Context,
        missionName: String,
        polygon: List<Any>,
        altitudeMeters: Double,
        cameraType: CameraType,
        outDir: File
    ): File? {
        if (!outDir.exists()) outDir.mkdirs()
        val outKmz = File(outDir.parentFile ?: outDir, "$missionName.kmz")

        // choose template by bucket and camera
        val bucket = pickAltitudeBucket(altitudeMeters)
        val templateAsset = pickTemplateAsset(cameraType, bucket)

        val tmpDir = File(outDir, "_tmp_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            unzipAssetToDir(context, templateAsset, tmpDir)
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            throw IllegalStateException("Unzip failed for $templateAsset: ${e.message}", e)
        }

        try {
            // ---- 1) WAYLINES.WPML ----
            val waylines = locateWaylines(tmpDir)
            val doc = parseXml(waylines)

            fun setAllTexts(localName: String, value: String?) {
                if (value == null) return
                val nodes = doc.getElementsByTagNameNS(WP_NS, localName)
                for (i in 0 until nodes.length) nodes.item(i).textContent = value
            }

            // Aircraft / payload (M3T family)
            setAllTexts("droneEnumValue", "77")
            setAllTexts("payloadEnumValue", "67")
            val sub = when (cameraType) {
                CameraType.EO   -> "0"
                CameraType.IR   -> "2"
                CameraType.BOTH -> "2"
            }
            setAllTexts("payloadSubEnumValue", sub)

            // Heights (meters)
            val h = String.format("%.3f", altitudeMeters)
            setAllTexts("executeHeight", h)
            setAllTexts("executeHeightMode", "relativeToStartPoint")
            setAllTexts("isUseAbsoluteAltitude", "false")
            setAllTexts("takeOffAlt", h)
            setAllTexts("takeOffSecurityHeight", h)
            setAllTexts("globalHeight", h)
            setAllTexts("height", h)
            setAllTexts("uavHeight", h)
            setAllTexts("goHomeHeight", h)
            normalizeAllHeights(doc, h)

            // Replace Placemarks in embedded KML inside waylines.wpml
            replaceFolderPlacemarksInDoc(doc, polygon)

            // Save back
            saveXml(doc, waylines)

            // ---- 2) ANY KML/KMZ ARTIFACTS IN TEMPLATE ----
            // Rewrite coordinates in:
            //   - *.kml files (e.g., template.kml, doc.kml)
            //   - *.kmz files (e.g., template.kmz) by opening and rewriting inner KML
            rewriteAllTemplateKmls(tmpDir, polygon)

            // ---- 3) Rezip ----
            zipDir(tmpDir, outKmz)
            return outKmz.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            Log.e(TAG, "Template mutation failed: ${e.message}", e)
            throw e
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    // ---------- Coordinate writers ----------

    private fun replaceFolderPlacemarksInDoc(doc: Document, polygon: List<Any>) {
        val folders = doc.getElementsByTagNameNS(KML_NS, "Folder")
        if (folders.length == 0) return
        val folder = folders.item(0) as Element

        // remove old Placemarks
        val toRemove = mutableListOf<Element>()
        val children = folder.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element && n.namespaceURI == KML_NS && n.localName == "Placemark") {
                toRemove.add(n)
            }
        }
        toRemove.forEach { folder.removeChild(it) }

        // add new Placemarks for each vertex
        polygon.forEachIndexed { idx, any ->
            val (lat, lon) = readLatLon(any)
            val placemark = doc.createElementNS(KML_NS, "Placemark")
            val name = doc.createElementNS(KML_NS, "name").apply { textContent = "WP ${idx + 1}" }
            val point = doc.createElementNS(KML_NS, "Point")
            val coords = doc.createElementNS(KML_NS, "coordinates").apply {
                textContent = String.format("%.7f,%.7f", lon, lat) // lon,lat
            }
            point.appendChild(coords)
            placemark.appendChild(name)
            placemark.appendChild(point)
            folder.appendChild(placemark)
        }
    }

    /**
     * Update the FIRST polygon LinearRing/coordinates in a KML DOM to match 'polygon'.
     * If not found, falls back to the first <coordinates> anywhere.
     */
    private fun updateFirstPolygonCoordinates(doc: Document, polygon: List<Any>): Boolean {
        val coordText = buildKmlCoordinatesText(polygon)
        // Try Polygon -> LinearRing -> coordinates
        val rings = doc.getElementsByTagNameNS(KML_NS, "LinearRing")
        for (i in 0 until rings.length) {
            val ring = rings.item(i) as Element
            val coordsList = ring.getElementsByTagNameNS(KML_NS, "coordinates")
            if (coordsList.length > 0) {
                (coordsList.item(0) as Element).textContent = "\n$coordText\n"
                return true
            }
        }
        // Fallback: first coordinates anywhere
        val allCoords = doc.getElementsByTagNameNS(KML_NS, "coordinates")
        if (allCoords.length > 0) {
            (allCoords.item(0) as Element).textContent = "\n$coordText\n"
            return true
        }
        return false
    }

    private fun buildKmlCoordinatesText(polygon: List<Any>): String {
        val sb = StringBuilder()
        polygon.forEach { any ->
            val (lat, lon) = readLatLon(any)
            // KML: lon,lat,alt
            sb.append(String.format("  %.7f,%.7f,0\n", lon, lat))
        }
        return sb.toString().trimEnd()
    }

    private fun rewriteAllTemplateKmls(root: File, polygon: List<Any>) {
        // 1) Update plain .kml files
        root.walkTopDown()
            .filter { it.isFile && it.extension.equals("kml", ignoreCase = true) }
            .forEach { kmlFile ->
                try {
                    val doc = parseXml(kmlFile)
                    if (updateFirstPolygonCoordinates(doc, polygon)) {
                        saveXml(doc, kmlFile)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping KML rewrite for ${kmlFile.name}: ${e.message}")
                }
            }

        // 2) Update nested .kmz files (e.g., template.kmz)
        root.walkTopDown()
            .filter { it.isFile && it.extension.equals("kmz", ignoreCase = true) }
            .forEach { kmzFile ->
                try {
                    val updated = rewriteInnerKmzPolygon(kmzFile.readBytes(), polygon)
                    kmzFile.writeBytes(updated)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping KMZ rewrite for ${kmzFile.name}: ${e.message}")
                }
            }
    }

    /**
     * Reads a KMZ, replaces first polygon coordinates inside the first found KML (prefer doc.kml),
     * and returns new KMZ bytes.
     */
    private fun rewriteInnerKmzPolygon(kmzBytes: ByteArray, polygon: List<Any>): ByteArray {
        // Read in-memory, collect entries
        data class EntryData(val name: String, val bytes: ByteArray)
        val entries = mutableListOf<EntryData>()
        var preferredIndex = -1 // index of doc.kml if found

        ZipInputStream(ByteArrayInputStream(kmzBytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val b = zis.readBytes()
                    entries.add(EntryData(e.name, b))
                    if (e.name.equals("doc.kml", true)) {
                        preferredIndex = entries.lastIndex
                    }
                }
                e = zis.nextEntry
            }
        }

        // Find target KML entry
        val kmlIndex = when {
            preferredIndex >= 0 -> preferredIndex
            else -> entries.indexOfFirst { it.name.lowercase().endsWith(".kml") }
        }
        if (kmlIndex >= 0) {
            val kmlDoc = parseXml(ByteArrayInputStream(entries[kmlIndex].bytes))
            if (updateFirstPolygonCoordinates(kmlDoc, polygon)) {
                entries[kmlIndex] = entries[kmlIndex].copy(bytes = docToBytes(kmlDoc))
            }
        }

        // Write back to KMZ
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { ed ->
                zos.putNextEntry(ZipEntry(ed.name))
                ByteArrayInputStream(ed.bytes).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    // ---------- Helpers: waylines locate/parse/save ----------

    private fun locateWaylines(tmpDir: File): File {
        val candidate = listOf(
            "wpmz/waylines.wpml",
            "wpmz/mission/waylines.wpml",
            "waylines.wpml"
        ).map { File(tmpDir, it) }
            .firstOrNull { it.exists() && it.isFile }
        return candidate ?: throw IllegalStateException(
            "waylines.wpml not found under ${tmpDir.absolutePath}"
        )
    }

    private fun parseXml(file: File): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).apply { documentElement.normalize() }

    private fun parseXml(ins: ByteArrayInputStream): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ins).apply { documentElement.normalize() }

    private fun saveXml(doc: Document, file: File) {
        val tf = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        tf.transform(DOMSource(doc), StreamResult(file))
    }

    private fun docToBytes(doc: Document): ByteArray {
        val baos = ByteArrayOutputStream()
        val tf = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        tf.transform(DOMSource(doc), StreamResult(baos))
        return baos.toByteArray()
    }

    // ---------- Height normalizer ----------

    private fun normalizeAllHeights(doc: Document, metersStr: String) {
        fun fixNode(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val inWp = (el.namespaceURI == WP_NS)
                if (inWp) {
                    val ln = el.localName ?: el.tagName
                    if (ln.equals("height", true) || ln.endsWith("Height", true)) {
                        el.textContent = metersStr
                    }
                    val attrs = el.attributes
                    for (i in 0 until attrs.length) {
                        val a = attrs.item(i)
                        val name = a.localName ?: a.nodeName
                        if (name.equals("height", true) || name.endsWith("Height", true)) {
                            a.nodeValue = metersStr
                        }
                    }
                }
            }
            var child = node.firstChild
            while (child != null) {
                fixNode(child)
                child = child.nextSibling
            }
        }
        fixNode(doc.documentElement)
    }

    // ---------- Asset I/O & zipping ----------

    private fun unzipAssetToDir(context: Context, assetPath: String, outDir: File) {
        context.assets.open(assetPath).use { ins ->
            ZipInputStream(ins).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(outDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zos -> zis.copyTo(zos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun zipDir(srcDir: File, outFile: File) {
        ZipOutputStream(outFile.outputStream()).use { zos ->
            fun add(f: File, base: String) {
                val name = if (base.isEmpty()) f.name else "$base/${f.name}"
                if (f.isDirectory) {
                    f.listFiles()?.forEach { add(it, name) }
                } else {
                    zos.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            srcDir.listFiles()?.forEach { add(it, "") }
        }
    }

    // ---------- Misc ----------

    private fun pickAltitudeBucket(altitudeMeters: Double): AltBucket {
        val ft = altitudeMeters / 0.3048
        return if (ft < 300.0) AltBucket.FT200 else AltBucket.FT400
    }

    private fun pickTemplateAsset(cameraType: CameraType, bucket: AltBucket): String {
        val baseDir = when (bucket) {
            AltBucket.FT200 -> "templates/200ft"
            AltBucket.FT400 -> "templates/400ft"
        }
        val file = when (cameraType) {
            CameraType.EO   -> "Test3correct.kmz"
            CameraType.IR   -> "Test3correctIR.kmz"
            CameraType.BOTH -> "Test3correctBoth.kmz"
        }
        return "$baseDir/$file"
    }

    /** Reflection reader for lat/lon: lat/latitude/latDeg + lon/lng/long/longitude */
    private fun readLatLon(obj: Any): Pair<Double, Double> {
        fun read(names: List<String>): Double {
            val c = obj.javaClass
            for (n in names) {
                try {
                    val f = c.getDeclaredField(n)
                    f.isAccessible = true
                    val v = f.get(obj)
                    if (v is Number) return v.toDouble()
                } catch (_: NoSuchFieldException) {}
            }
            for (n in names) {
                val gn = "get" + n.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                try {
                    val m = c.getDeclaredMethod(gn)
                    m.isAccessible = true
                    val v = m.invoke(obj)
                    if (v is Number) return v.toDouble()
                } catch (_: NoSuchMethodException) {}
            }
            throw IllegalArgumentException("Missing numeric field among ${names.joinToString("/")}")
        }
        val lat = read(listOf("lat", "latitude", "latDeg"))
        val lon = read(listOf("lon", "lng", "long", "longitude"))
        return lat to lon
    }
}