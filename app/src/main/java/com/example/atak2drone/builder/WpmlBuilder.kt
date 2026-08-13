package com.example.atak2drone.builder

import android.content.Context
import com.example.atak2drone.BuildConfig
import com.example.atak2drone.domain.interfaces.IWpmlBuilder
import com.example.atak2drone.domain.model.MissionPlan
import com.example.atak2drone.model.CameraType
import com.example.atak2drone.model.Coordinate
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
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

/**
 * Single Responsibility Principle (SRP) & Dependency Inversion Principle (DIP):
 * Constructs and mutates WPML 1.0.6 compliant directory bundles from a [MissionPlan].
 */
class WpmlBuilder(
    private val context: Context
) : IWpmlBuilder {

    companion object {
        private const val WP_NS = "http://www.dji.com/wpmz/1.0.6"
        private const val KML_NS = "http://www.opengis.net/kml/2.2"
    }

    override fun buildWpmlBundle(plan: MissionPlan, targetDirectory: File): Result<File> {
        return runCatching {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()

            val altitudeMeters = plan.config.altitudeMeters
            val cameraType = plan.config.cameraType
            val templateAsset = pickTemplateAsset(cameraType, altitudeMeters)

            // Extract template KMZ into targetDirectory
            unzipAssetToDir(context, templateAsset, targetDirectory)

            // 1. Mutate waylines.wpml
            val waylinesFile = locateWaylines(targetDirectory)
            val doc = parseXml(waylinesFile)

            val h = String.format("%.3f", altitudeMeters)
            val speedStr = String.format("%.1f", plan.config.speedMps)

            // Set dynamic heights and parameters
            setAllTexts(doc, "executeHeight", h)
            setAllTexts(doc, "executeHeightMode", "relativeToStartPoint")
            setAllTexts(doc, "isUseAbsoluteAltitude", "false")
            setAllTexts(doc, "takeOffAlt", h)
            setAllTexts(doc, "takeOffSecurityHeight", h)
            setAllTexts(doc, "globalHeight", h)
            setAllTexts(doc, "height", h)
            setAllTexts(doc, "uavHeight", h)
            setAllTexts(doc, "goHomeHeight", h)
            setAllTexts(doc, "autoFlightSpeed", speedStr)
            setAllTexts(doc, "globalTransitionalSpeed", speedStr)
            normalizeAllHeights(doc, h)

            // Configure drone & payload enums if available from BuildConfig
            try {
                val droneEnum = BuildConfig.DRONE_ENUM
                val droneSubEnum = BuildConfig.DRONE_SUB_ENUM
                val payloadEnum = BuildConfig.PAYLOAD_ENUM
                if (droneEnum > 0) setAllTexts(doc, "droneEnumValue", droneEnum.toString())
                if (droneSubEnum > 0) setAllTexts(doc, "droneSubEnumValue", droneSubEnum.toString())
                if (payloadEnum > 0) setAllTexts(doc, "payloadEnumValue", payloadEnum.toString())
            } catch (_: Throwable) {
                // BuildConfig fields may not be present in pure test harness
            }

            // Replace placemarks with plan waypoints
            replaceFolderPlacemarksInDoc(doc, plan.waypoints, plan.config.speedMps)

            saveXml(doc, waylinesFile)

            // 2. Synchronize template.kml / inner KMZ polygon boundary
            rewriteAllTemplateKmls(targetDirectory, plan.polygon)

            targetDirectory
        }
    }

    private fun setAllTexts(doc: Document, localName: String, value: String?) {
        if (value == null) return
        val nodes = doc.getElementsByTagNameNS(WP_NS, localName)
        for (i in 0 until nodes.length) {
            nodes.item(i).textContent = value
        }
    }

    private fun replaceFolderPlacemarksInDoc(
        doc: Document,
        waypoints: List<Coordinate>,
        speedMps: Double
    ) {
        val folders = doc.getElementsByTagNameNS(KML_NS, "Folder")
        if (folders.length == 0) return
        val folder = folders.item(0) as Element

        val existing = mutableListOf<Element>()
        val children = folder.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n is Element && n.namespaceURI == KML_NS && n.localName == "Placemark") {
                existing.add(n)
            }
        }
        val prototype = existing.firstOrNull { pm ->
            val point = firstChildByLocalName(pm, KML_NS, "Point")
            val coords = point?.let { firstChildByLocalName(it, KML_NS, "coordinates") }
            coords != null
        }

        if (prototype == null) {
            existing.forEach { folder.removeChild(it) }
            waypoints.forEachIndexed { idx, coord ->
                val placemark = doc.createElementNS(KML_NS, "Placemark")
                val name = doc.createElementNS(KML_NS, "name").apply { textContent = "WP ${idx + 1}" }
                val point = doc.createElementNS(KML_NS, "Point")
                val coords = doc.createElementNS(KML_NS, "coordinates").apply {
                    textContent = String.format("%.7f,%.7f", coord.longitude, coord.latitude)
                }
                point.appendChild(coords)
                placemark.appendChild(name)
                placemark.appendChild(point)
                folder.appendChild(placemark)
            }
            return
        }

        existing.forEach { folder.removeChild(it) }

        val speedStr = String.format("%.1f", speedMps)
        waypoints.forEachIndexed { idx, coord ->
            val clone = prototype.cloneNode(true) as Element

            firstChildByLocalName(clone, KML_NS, "name")?.apply {
                textContent = "WP ${idx + 1}"
            }

            firstChildByLocalName(clone, KML_NS, "Point")?.let { pt ->
                firstChildByLocalName(pt, KML_NS, "coordinates")?.apply {
                    textContent = String.format("%.7f,%.7f", coord.longitude, coord.latitude)
                }
            }

            firstChildByLocalName(clone, WP_NS, "index")?.apply {
                textContent = idx.toString()
            }

            firstChildByLocalName(clone, WP_NS, "waypointSpeed")?.apply {
                textContent = speedStr
            }

            folder.appendChild(clone)
        }
    }

    private fun firstChildByLocalName(parent: Element, ns: String, local: String): Element? {
        val list = parent.getElementsByTagNameNS(ns, local)
        for (i in 0 until list.length) {
            val el = list.item(i) as? Element ?: continue
            return el
        }
        return null
    }

    private fun updateFirstPolygonCoordinates(doc: Document, polygon: List<Coordinate>): Boolean {
        val coordText = buildKmlCoordinatesText(polygon)
        val rings = doc.getElementsByTagNameNS(KML_NS, "LinearRing")
        for (i in 0 until rings.length) {
            val ring = rings.item(i) as Element
            val coordsList = ring.getElementsByTagNameNS(KML_NS, "coordinates")
            if (coordsList.length > 0) {
                (coordsList.item(0) as Element).textContent = "\n$coordText\n"
                return true
            }
        }
        val allCoords = doc.getElementsByTagNameNS(KML_NS, "coordinates")
        if (allCoords.length > 0) {
            (allCoords.item(0) as Element).textContent = "\n$coordText\n"
            return true
        }
        return false
    }

    private fun buildKmlCoordinatesText(polygon: List<Coordinate>): String {
        val sb = StringBuilder()
        polygon.forEach { c ->
            sb.append(String.format("  %.7f,%.7f,0\n", c.longitude, c.latitude))
        }
        return sb.toString().trimEnd()
    }

    private fun rewriteAllTemplateKmls(root: File, polygon: List<Coordinate>) {
        root.walkTopDown()
            .filter { it.isFile && it.extension.equals("kml", ignoreCase = true) }
            .forEach { kmlFile ->
                try {
                    val doc = parseXml(kmlFile)
                    if (updateFirstPolygonCoordinates(doc, polygon)) {
                        saveXml(doc, kmlFile)
                    }
                } catch (_: Exception) {}
            }

        root.walkTopDown()
            .filter { it.isFile && it.extension.equals("kmz", ignoreCase = true) }
            .forEach { kmzFile ->
                try {
                    val updated = rewriteInnerKmzPolygon(kmzFile.readBytes(), polygon)
                    kmzFile.writeBytes(updated)
                } catch (_: Exception) {}
            }
    }

    private fun rewriteInnerKmzPolygon(kmzBytes: ByteArray, polygon: List<Coordinate>): ByteArray {
        data class EntryData(val name: String, val bytes: ByteArray)
        val entries = mutableListOf<EntryData>()
        var preferredIndex = -1

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

        val kmlIndex = if (preferredIndex >= 0) preferredIndex
        else entries.indexOfFirst { it.name.lowercase().endsWith(".kml") }

        if (kmlIndex >= 0) {
            val kmlDoc = parseXml(ByteArrayInputStream(entries[kmlIndex].bytes))
            if (updateFirstPolygonCoordinates(kmlDoc, polygon)) {
                entries[kmlIndex] = entries[kmlIndex].copy(bytes = docToBytes(kmlDoc))
            }
        }

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

    private fun locateWaylines(tmpDir: File): File {
        val candidate = listOf(
            "wpmz/waylines.wpml",
            "wpmz/mission/waylines.wpml",
            "waylines.wpml"
        ).map { File(tmpDir, it) }
            .firstOrNull { it.exists() && it.isFile }
        return candidate ?: throw IllegalStateException("waylines.wpml not found under ${tmpDir.absolutePath}")
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

    private fun pickTemplateAsset(cameraType: CameraType, altitudeMeters: Double): String {
        val ft = altitudeMeters / 0.3048
        val baseDir = if (ft < 300.0) "templates/200ft" else "templates/400ft"
        val file = when (cameraType) {
            CameraType.EO   -> "Test3correct.kmz"
            CameraType.IR   -> "Test3correctIR.kmz"
            CameraType.BOTH -> "Test3correctBoth.kmz"
        }
        return "$baseDir/$file"
    }
}
