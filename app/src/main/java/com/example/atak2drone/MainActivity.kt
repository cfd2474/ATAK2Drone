package com.example.atak2drone

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.example.atak2drone.controller.MissionController
import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.model.CameraType
import com.example.atak2drone.parser.KmlParser
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectDestination: Button
    private lateinit var textViewDestinationPath: TextView

    private lateinit var editTextMissionName: EditText
    private lateinit var iconMissionNameHelp: ImageView
    private lateinit var btnSelectKml: Button
    private lateinit var textViewKmlStatus: TextView

    // Flight Pattern Strategy
    private lateinit var radioGroupMissionType: RadioGroup
    private lateinit var radioPatternGrid: RadioButton
    private lateinit var radioPatternPerimeter: RadioButton

    // Perimeter Corridor Settings
    private lateinit var layoutPerimeterSettings: LinearLayout
    private lateinit var editTextInteriorOffset: EditText
    private lateinit var seekBarInteriorOffset: SeekBar
    private lateinit var editTextExteriorOffset: EditText
    private lateinit var seekBarExteriorOffset: SeekBar

    // Altitude presets & Custom
    private lateinit var radioGroupAltitude: RadioGroup
    private lateinit var radioAlt200: RadioButton
    private lateinit var radioAlt400: RadioButton
    private lateinit var radioAltCustom: RadioButton
    private lateinit var editTextCustomAltitude: EditText

    private lateinit var radioGroupCamera: RadioGroup
    private lateinit var radioCameraEO: RadioButton
    private lateinit var radioCameraIR: RadioButton
    private lateinit var radioCameraBoth: RadioButton

    private lateinit var btnGenerate: Button
    private lateinit var textViewMissingRequirements: TextView
    private lateinit var textViewMetricsSummary: TextView

    private var pickedKmlUri: Uri? = null
    private var destTreeUri: Uri? = null
    private var polygonLooksValid = false

    private val kmlParser = KmlParser()
    private val missionController = MissionController()

    // Guards to prevent infinite recursion during two-way slider <-> edittext sync
    private var isUpdatingInterior = false
    private var isUpdatingExterior = false

    // Mission name rules: start with a letter; then A–Z, a–z, 0–9, _ or -; max 32 chars
    private val NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_-]{0,31}$")

    // ---- Pickers ----
    private val kmlPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            pickedKmlUri = null
            polygonLooksValid = false
            textViewKmlStatus.text = getString(R.string.label_no_kml)
            updateGenerateEnabled()
            return@registerForActivityResult
        }

        pickedKmlUri = uri
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        try {
            val tmp = copyUriToTemp(uri, suggestedExt = guessExtForUri(uri))
            val polygon = tmp.inputStream().use { kmlParser.parsePolygon(it) }
            polygonLooksValid = polygon.size >= 3

            val name = displayName(uri) ?: uri.lastPathSegment ?: "KML"
            if (polygonLooksValid) {
                textViewKmlStatus.text = getString(R.string.kml_loaded, name)
            } else {
                textViewKmlStatus.text = getString(R.string.error_invalid_polygon)
            }
            tmp.delete()
        } catch (e: Exception) {
            polygonLooksValid = false
            textViewKmlStatus.text = getString(R.string.toast_invalid_kml)
            Toast.makeText(this, getString(R.string.toast_invalid_kml), Toast.LENGTH_LONG).show()
        }
        updateGenerateEnabled()
    }

    private val destPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            textViewDestinationPath.text = getString(R.string.label_no_destination)
            destTreeUri = null
            updateGenerateEnabled()
            return@registerForActivityResult
        }
        destTreeUri = uri
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        val doc = DocumentFile.fromTreeUri(this, uri)
        val name = doc?.name ?: uri.lastPathSegment ?: ""
        val prettyPath = prettyPathForTreeUri(uri)
        textViewDestinationPath.text = "Name: $name\nPath: $prettyPath"

        updateGenerateEnabled()
    }

    // ---- Lifecycle ----
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        wireUi()
        setupPerimeterSync()
        setDefaults()
        installMissionNameValidation()
        updateGenerateEnabled()
    }

    private fun bindViews() {
        btnSelectDestination = findViewById(R.id.btnSelectDestination)
        textViewDestinationPath = findViewById(R.id.textViewDestinationPath)

        editTextMissionName = findViewById(R.id.editTextMissionName)
        iconMissionNameHelp = findViewById(R.id.iconMissionNameHelp)

        btnSelectKml = findViewById(R.id.btnSelectKml)
        textViewKmlStatus = findViewById(R.id.textViewKmlStatus)

        radioGroupMissionType = findViewById(R.id.radioGroupMissionType)
        radioPatternGrid = findViewById(R.id.radioPatternGrid)
        radioPatternPerimeter = findViewById(R.id.radioPatternPerimeter)

        layoutPerimeterSettings = findViewById(R.id.layoutPerimeterSettings)
        editTextInteriorOffset = findViewById(R.id.editTextInteriorOffset)
        seekBarInteriorOffset = findViewById(R.id.seekBarInteriorOffset)
        editTextExteriorOffset = findViewById(R.id.editTextExteriorOffset)
        seekBarExteriorOffset = findViewById(R.id.seekBarExteriorOffset)

        radioGroupAltitude = findViewById(R.id.radioGroupAltitude)
        radioAlt200 = findViewById(R.id.radioAlt200)
        radioAlt400 = findViewById(R.id.radioAlt400)
        radioAltCustom = findViewById(R.id.radioAltCustom)
        editTextCustomAltitude = findViewById(R.id.editTextCustomAltitude)

        radioGroupCamera = findViewById(R.id.radioGroupCamera)
        radioCameraEO = findViewById(R.id.radioCameraEO)
        radioCameraIR = findViewById(R.id.radioCameraIR)
        radioCameraBoth = findViewById(R.id.radioCameraBoth)

        btnGenerate = findViewById(R.id.btnGenerate)
        textViewMissingRequirements = findViewById(R.id.textViewMissingRequirements)
        textViewMetricsSummary = findViewById(R.id.textViewMetricsSummary)
    }

    private fun wireUi() {
        btnSelectDestination.setOnClickListener { destPicker.launch(null) }

        btnSelectKml.setOnClickListener {
            kmlPicker.launch(
                arrayOf(
                    "application/vnd.google-earth.kml+xml",
                    "application/vnd.google-earth.kmz",
                    "application/zip",
                    "application/octet-stream",
                    "text/xml",
                    "application/xml"
                )
            )
        }

        radioGroupMissionType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioPatternPerimeter) {
                layoutPerimeterSettings.visibility = View.VISIBLE
            } else {
                layoutPerimeterSettings.visibility = View.GONE
            }
        }

        radioGroupAltitude.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioAltCustom) {
                editTextCustomAltitude.visibility = View.VISIBLE
                editTextCustomAltitude.requestFocus()
            } else {
                editTextCustomAltitude.visibility = View.GONE
            }
            updateGenerateEnabled()
        }

        editTextCustomAltitude.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateGenerateEnabled()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnGenerate.setOnClickListener { onGenerateClicked() }

        iconMissionNameHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Mission Name Requirements")
                .setMessage(
                    """
                    • Must start with a letter
                    • Use A–Z, 0–9, _ or - only
                    • Max 32 characters
                    • No spaces or special characters
                    """.trimIndent()
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setupPerimeterSync() {
        // Interior Offset 2-way sync
        seekBarInteriorOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingInterior) {
                    isUpdatingInterior = true
                    editTextInteriorOffset.setText(progress.toString())
                    isUpdatingInterior = false
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        editTextInteriorOffset.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingInterior) {
                    val value = s?.toString()?.toIntOrNull()
                    if (value != null && value in 0..500) {
                        isUpdatingInterior = true
                        seekBarInteriorOffset.progress = value
                        isUpdatingInterior = false
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Exterior Offset 2-way sync
        seekBarExteriorOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdatingExterior) {
                    isUpdatingExterior = true
                    editTextExteriorOffset.setText(progress.toString())
                    isUpdatingExterior = false
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        editTextExteriorOffset.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isUpdatingExterior) {
                    val value = s?.toString()?.toIntOrNull()
                    if (value != null && value in 0..500) {
                        isUpdatingExterior = true
                        seekBarExteriorOffset.progress = value
                        isUpdatingExterior = false
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setDefaults() {
        textViewKmlStatus.text = getString(R.string.kml_not_loaded)
        radioPatternGrid.isChecked = true
        layoutPerimeterSettings.visibility = View.GONE
        radioCameraEO.isChecked = true
        radioAlt200.isChecked = true
        editTextCustomAltitude.visibility = View.GONE
        btnGenerate.isEnabled = false

        editTextInteriorOffset.setText("100")
        seekBarInteriorOffset.progress = 100
        editTextExteriorOffset.setText("50")
        seekBarExteriorOffset.progress = 50

        editTextMissionName.text.clear()
        editTextMissionName.error = null
    }

    // ---- Validation & gating ----
    private fun installMissionNameValidation() {
        editTextMissionName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = s?.toString()?.trim().orEmpty()
                val ok = NAME_REGEX.matches(name)
                if (name.isNotEmpty() && !ok) {
                    editTextMissionName.error =
                        "Invalid name: Letter first, A–Z/0–9/_/- only, max 32 chars."
                } else {
                    editTextMissionName.error = null
                }
                updateGenerateEnabled()
            }
        })
    }

    private fun missionNameValid(): Boolean {
        val name = editTextMissionName.text.toString().trim()
        return NAME_REGEX.matches(name)
    }

    private fun altitudeValid(): Boolean {
        return when (radioGroupAltitude.checkedRadioButtonId) {
            R.id.radioAlt200, R.id.radioAlt400 -> true
            R.id.radioAltCustom -> {
                val alt = editTextCustomAltitude.text.toString().toDoubleOrNull()
                alt != null && alt in 20.0..400.0
            }
            else -> false
        }
    }

    private fun updateGenerateEnabled() {
        val missing = mutableListOf<String>()
        if (pickedKmlUri == null || !polygonLooksValid) {
            missing.add("Select KML")
        }
        if (!missionNameValid()) {
            missing.add("Name output (no spaces)")
        }
        if (destTreeUri == null) {
            missing.add("Select destination")
        }
        if (!altitudeValid()) {
            missing.add("Valid altitude (20–400 ft)")
        }

        if (missing.isEmpty()) {
            btnGenerate.isEnabled = true
            textViewMissingRequirements.visibility = View.GONE
        } else {
            btnGenerate.isEnabled = false
            textViewMissingRequirements.text = "⚠️ Required: " + missing.joinToString(" • ")
            textViewMissingRequirements.visibility = View.VISIBLE
        }
    }

    // ---- Generate ----
    private fun onGenerateClicked() {
        val kmlUri = pickedKmlUri ?: return
        val missionName = editTextMissionName.text.toString().trim()
        val treeUri = destTreeUri ?: return

        val altitudeFt = when (radioGroupAltitude.checkedRadioButtonId) {
            R.id.radioAlt200 -> 200.0
            R.id.radioAlt400 -> 400.0
            R.id.radioAltCustom -> {
                val alt = editTextCustomAltitude.text.toString().toDoubleOrNull()
                if (alt == null || alt !in 20.0..400.0) {
                    Toast.makeText(this, getString(R.string.error_invalid_altitude), Toast.LENGTH_LONG).show()
                    return
                }
                alt
            }
            else -> return
        }

        val cameraType = when (radioGroupCamera.checkedRadioButtonId) {
            R.id.radioCameraBoth -> CameraType.BOTH
            R.id.radioCameraIR -> CameraType.IR
            else -> CameraType.EO
        }

        val isPerimeter = radioGroupMissionType.checkedRadioButtonId == R.id.radioPatternPerimeter
        val missionType = if (isPerimeter) MissionType.VERTEX_PERIMETER else MissionType.GRID_SURVEY

        val interiorOffsetFt = editTextInteriorOffset.text.toString().toDoubleOrNull() ?: 100.0
        val exteriorOffsetFt = editTextExteriorOffset.text.toString().toDoubleOrNull() ?: 50.0

        val altitudeMeters = altitudeFt * 0.3048
        val config = SurveyConfig(
            missionName = missionName,
            altitudeMeters = altitudeMeters,
            cameraType = cameraType,
            perimeterInteriorOffsetFt = interiorOffsetFt,
            perimeterExteriorOffsetFt = exteriorOffsetFt
        )

        try {
            val tmp = copyUriToTemp(kmlUri, suggestedExt = guessExtForUri(kmlUri))

            val result = tmp.inputStream().use { input ->
                missionController.createMission(
                    context = this,
                    kmlInputStream = input,
                    config = config,
                    missionType = missionType
                )
            }

            tmp.delete()

            result.onSuccess { genResult ->
                val copied = copyToDestination(genResult.kmzFilePath, "$missionName.kmz")
                val base = prettyPathForTreeUri(treeUri)
                textViewKmlStatus.text = getString(R.string.kml_ready)
                Toast.makeText(this, getString(R.string.toast_wpml_success), Toast.LENGTH_SHORT).show()

                val shown = if (base.startsWith("URI:")) {
                    "Saved: ${copied?.name ?: "$missionName.kmz"}"
                } else {
                    "Saved: $base/${copied?.name ?: "$missionName.kmz"}"
                }
                textViewDestinationPath.text = shown

                // Display optimization & mission metrics summary
                val metrics = genResult.plan.metrics
                if (metrics != null) {
                    val summaryText = buildString {
                        append("★ Mission Metrics:\n")
                        if (missionType == MissionType.GRID_SURVEY) {
                            append("• Pattern: Area Mapping (Optimized)\n")
                            append("• Optimal Flight Angle: %.1f°\n".format(metrics.optimalAngleDegrees))
                            append("• Transects: ${metrics.numberOfTransects} | Turns: ${metrics.numberOfTurns}\n")
                        } else {
                            append("• Pattern: Perimeter Survey (In: %.0f ft, Out: %.0f ft)\n".format(interiorOffsetFt, exteriorOffsetFt))
                            append("• Concentric Rings: ${metrics.numberOfTransects}\n")
                        }
                        append("• Total Distance: %.0f m\n".format(metrics.totalDistanceMeters))
                        append("• Est. Duration: %.1f min (%.0f s)\n".format(metrics.estimatedDurationMinutes, metrics.estimatedFlightDurationSeconds))
                    }
                    textViewMetricsSummary.text = summaryText
                    textViewMetricsSummary.visibility = View.VISIBLE
                }

                showLaunchPromptIfInstalled(
                    appLabel = "DJI Pilot 2",
                    packageName = "com.dji.industry.pilot"
                )
            }.onFailure { err ->
                textViewKmlStatus.text = "${getString(R.string.toast_wpml_failure)}: ${err.message}"
                Toast.makeText(this, "${getString(R.string.toast_wpml_failure)}: ${err.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            textViewKmlStatus.text = "${getString(R.string.toast_wpml_failure)}: ${e.message}"
            Toast.makeText(this, "${getString(R.string.toast_wpml_failure)}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---- Post-generation app prompt helpers ----
    private fun showLaunchPromptIfInstalled(
        appLabel: String = "DJI Pilot 2",
        packageName: String = "com.dji.industry.pilot"
    ) {
        if (isAppInstalled(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Open $appLabel?")
                .setMessage("Your flight plan is ready. Do you want to launch $appLabel now?")
                .setPositiveButton("Open") { _, _ -> launchApp(packageName) }
                .setNegativeButton("Not now", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("$appLabel not installed")
                .setMessage("$appLabel is not installed on this device.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Unable to launch app.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Pretty path for tree URIs ----
    private fun prettyPathForTreeUri(uri: Uri): String {
        return try {
            if (uri.authority == "com.android.externalstorage.documents") {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val parts = docId?.split(":") ?: emptyList()
                val volume = parts.getOrNull(0) ?: return "URI: $uri"
                val relPath = parts.getOrNull(1).orEmpty()

                val base = if (volume.equals("primary", true)) {
                    "/storage/emulated/0"
                } else {
                    "/storage/$volume"
                }
                if (relPath.isNotEmpty()) "$base/$relPath" else base
            } else {
                "URI: $uri"
            }
        } catch (_: Exception) {
            "URI: $uri"
        }
    }

    private fun copyUriToTemp(uri: Uri, suggestedExt: String): File {
        val ext = if (suggestedExt.isNotBlank()) suggestedExt else ".kml"
        val tmp = File.createTempFile("atak2drone_input_", ext, cacheDir)
        contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IOException("Unable to open selected file.")
        return tmp
    }

    private fun guessExtForUri(uri: Uri): String {
        val mime = contentResolver.getType(uri) ?: ""
        return when {
            mime.equals("application/vnd.google-earth.kml+xml", ignoreCase = true) -> ".kml"
            mime.equals("application/vnd.google-earth.kmz", ignoreCase = true) -> ".kmz"
            mime.equals("application/zip", ignoreCase = true) -> ".kmz"
            else -> {
                val n = displayName(uri)?.lowercase().orEmpty()
                when {
                    n.endsWith(".kmz") -> ".kmz"
                    n.endsWith(".kml") -> ".kml"
                    else -> ".kml"
                }
            }
        }
    }

    private fun copyToDestination(internalPath: String, desiredName: String): DocumentFile? {
        val tree = destTreeUri ?: return null
        val parent = DocumentFile.fromTreeUri(this, tree) ?: return null
        val src = File(internalPath)
        if (!src.exists()) return null
        parent.findFile(desiredName)?.delete()
        val outDoc = parent.createFile("application/vnd.google-earth.kmz", desiredName.removeSuffix(".kmz"))
            ?: return null
        contentResolver.openOutputStream(outDoc.uri)?.use { out ->
            src.inputStream().use { input -> input.copyTo(out) }
        } ?: return null
        return outDoc
    }

    private fun displayName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }
}