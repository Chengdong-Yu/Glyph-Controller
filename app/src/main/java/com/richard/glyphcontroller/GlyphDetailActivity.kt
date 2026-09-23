package com.richard.glyphcontroller

import android.app.Activity
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/** Edits one 33-zone frame without changing the overview's automatic features. */
class GlyphDetailActivity : Activity() {
    private lateinit var preview: GlyphZonePreviewView
    private lateinit var zoneGrid: GridLayout
    private lateinit var selectedLabel: TextView
    private lateinit var levelLabel: TextView
    private lateinit var hardwareStatus: TextView
    private lateinit var actionStatus: TextView
    private lateinit var applyButton: MaterialButton
    private lateinit var slider: Slider
    private val zoneButtons = arrayOfNulls<MaterialButton>(GlyphPowerState.ZONE_COUNT)

    private var frame = IntArray(GlyphPowerState.ZONE_COUNT)
    private var selectedZone = 0
    private var hardwareReady = false
    private var applying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        window.setDecorFitsSystemWindows(false)
        setContentView(R.layout.activity_glyph_detail)
        val lightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        window.insetsController?.setSystemBarsAppearance(
            if (lightTheme) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )

        val root = findViewById<View>(R.id.detailRoot)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        root.requestApplyInsets()

        preview = findViewById(R.id.zonePreview)
        zoneGrid = findViewById(R.id.zoneGrid)
        selectedLabel = findViewById(R.id.tvSelectedZone)
        levelLabel = findViewById(R.id.tvZoneLevel)
        hardwareStatus = findViewById(R.id.tvHardwareStatus)
        actionStatus = findViewById(R.id.tvDetailStatus)
        applyButton = findViewById(R.id.btnApplyFrame)
        slider = findViewById(R.id.sliderZone)
        slider.valueFrom = 0f
        slider.valueTo = 255f
        slider.stepSize = 1f
        frame = GlyphPowerState.getFrame(this)

        findViewById<MaterialButton>(R.id.btnDetailBack).setOnClickListener { finish() }
        preview.onZoneSelected = { selectZone(it) }
        buildZoneGrid()
        selectZone(0)
        renderFrame()

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                frame[selectedZone] = value.roundToInt().coerceIn(0, 255)
                renderFrame()
                actionStatus.setText(R.string.detail_unsaved)
            }
        }

        findViewById<MaterialButton>(R.id.btnFillAll).setOnClickListener {
            val level = frame[selectedZone]
            frame.fill(level)
            renderFrame()
            actionStatus.setText(R.string.detail_unsaved)
        }
        findViewById<MaterialButton>(R.id.btnPresetAll).setOnClickListener {
            loadDraft(GlyphZones.presetAll())
        }
        findViewById<MaterialButton>(R.id.btnPresetSoft).setOnClickListener {
            loadDraft(GlyphZones.presetAll(64))
        }
        findViewById<MaterialButton>(R.id.btnPresetRing).setOnClickListener {
            loadDraft(GlyphZones.presetRing())
        }
        findViewById<MaterialButton>(R.id.btnPresetLower).setOnClickListener {
            loadDraft(GlyphZones.presetCameraAndLower())
        }
        findViewById<MaterialButton>(R.id.btnPresetOff).setOnClickListener {
            loadDraft(GlyphZones.presetAll(0))
        }
        findViewById<MaterialButton>(R.id.btnSaveScene).setOnClickListener {
            val saved = GlyphSceneStore.save(this, frame)
            Toast.makeText(
                this,
                if (saved) R.string.detail_scene_saved else R.string.detail_scene_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
        findViewById<MaterialButton>(R.id.btnLoadScene).setOnClickListener {
            val saved = GlyphSceneStore.load(this)
            if (saved == null) {
                Toast.makeText(this, R.string.detail_scene_missing, Toast.LENGTH_SHORT).show()
            } else {
                loadDraft(saved)
            }
        }
        applyButton.setOnClickListener { applyFrame() }
        updateApplyEnabled()
        checkHardware()
    }

    private fun buildZoneGrid() {
        val density = resources.displayMetrics.density
        val availableDp = resources.displayMetrics.widthPixels / density - 40f
        val sideDp = (availableDp / 6f - 4f).coerceIn(40f, 48f)
        val side = (sideDp * density).roundToInt()
        val margin = (2f * density).roundToInt()
        for (index in 0 until GlyphPowerState.ZONE_COUNT) {
            val button = MaterialButton(this)
            button.text = GlyphZones.name(index)
            button.textSize = 11f
            button.isAllCaps = false
            button.minWidth = 0
            button.minimumWidth = 0
            button.minHeight = 0
            button.minimumHeight = 0
            button.setPadding(0, 0, 0, 0)
            button.insetTop = 0
            button.insetBottom = 0
            button.cornerRadius = (12f * density).roundToInt()
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 6), GridLayout.spec(index % 6)
            )
            params.width = side
            params.height = side
            params.setMargins(margin, margin, margin, margin)
            zoneGrid.addView(button, params)
            zoneButtons[index] = button
            button.setOnClickListener { selectZone(index) }
        }
    }

    private fun selectZone(index: Int) {
        if (index !in frame.indices) return
        selectedZone = index
        preview.selectZone(index)
        selectedLabel.text = getString(
            R.string.detail_zone_label,
            GlyphZones.name(index),
            GlyphZones.ledIds[index],
            getString(GlyphZones.groupName(index))
        )
        slider.value = frame[index].toFloat()
        renderFrame()
    }

    private fun loadDraft(values: IntArray) {
        frame = values.copyOf()
        slider.value = frame[selectedZone].toFloat()
        renderFrame()
        actionStatus.setText(R.string.detail_unsaved)
    }

    private fun renderFrame() {
        preview.showFrame(frame)
        levelLabel.text = getString(R.string.detail_level, frame[selectedZone])
        val active = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY
        )
        val idle = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceVariant, Color.DKGRAY
        )
        val outline = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.WHITE
        )
        val mutedOutline = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY
        )
        val text = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE
        )
        for (index in frame.indices) {
            val button = zoneButtons[index] ?: continue
            button.backgroundTintList = ColorStateList.valueOf(blend(idle, active, frame[index] / 255f))
            button.strokeColor = ColorStateList.valueOf(if (index == selectedZone) outline else mutedOutline)
            button.strokeWidth = (resources.displayMetrics.density * if (index == selectedZone) 2f else 1f).roundToInt()
            button.setTextColor(text)
            button.contentDescription = getString(
                R.string.detail_zone_button,
                GlyphZones.name(index),
                getString(GlyphZones.groupName(index)),
                frame[index]
            )
        }
    }

    private fun blend(from: Int, to: Int, fraction: Float): Int {
        fun channel(a: Int, b: Int) = (a + (b - a) * fraction).roundToInt()
        return Color.rgb(
            channel(Color.red(from), Color.red(to)),
            channel(Color.green(from), Color.green(to)),
            channel(Color.blue(from), Color.blue(to))
        )
    }

    private fun checkHardware() {
        Thread {
            val client = RootGlyphClient()
            val root = client.isRootAvailable()
            val supported = if (root) GlyphHardware.isSupported(rootAvailable = true) else false
            client.destroy()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                hardwareReady = root && supported == true
                hardwareStatus.setText(
                    when {
                        !root -> R.string.detail_no_root
                        supported != true -> R.string.detail_no_glyph
                        else -> R.string.detail_ready
                    }
                )
                updateApplyEnabled()
            }
        }.start()
    }

    private fun updateApplyEnabled() {
        applyButton.isEnabled = hardwareReady && !applying
    }

    private fun applyFrame() {
        if (!hardwareReady || applying) return
        applying = true
        updateApplyEnabled()
        actionStatus.setText(R.string.detail_applying)
        val snapshot = frame.copyOf()
        Thread {
            val success = GlyphEffectCoordinator.applyManualFrame(applicationContext, snapshot)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                applying = false
                updateApplyEnabled()
                actionStatus.setText(if (success) R.string.detail_applied else R.string.detail_apply_failed)
            }
        }.start()
    }
}
