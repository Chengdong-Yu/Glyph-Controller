package com.richard.glyphcontroller

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The 33-zone editor only changes the hardware when Apply is pressed. */
class GlyphDetailActivity : ComponentActivity() {
    private lateinit var draft: GlyphDraft
    private var frame by mutableStateOf(IntArray(GlyphFrame.ZONE_COUNT))
    private var selectedZone by mutableIntStateOf(0)
    private var hardwareReady by mutableStateOf(false)
    private var checking by mutableStateOf(false)
    private var applying by mutableStateOf(false)
    private var hardwareText by mutableStateOf("")
    private var actionText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val restored = savedInstanceState?.getIntArray("draft_frame")?.takeIf(GlyphFrame::valid)
        draft = GlyphDraft(restored ?: GlyphPowerState.getFrame(this),
            savedInstanceState?.getLong("draft_revision") ?: 0L)
        frame = draft.frame.copyOf()
        selectedZone = (savedInstanceState?.getInt("draft_zone") ?: 0).coerceIn(frame.indices)
        actionText = getString(if (frame.contentEquals(GlyphPowerState.getFrame(this)))
            R.string.detail_current else R.string.detail_unsaved)
        hardwareText = getString(R.string.detail_checking)

        setContent {
            GlyphAppTheme {
                DetailScreen(
                    frame = frame,
                    selected = selectedZone,
                    hardwareText = hardwareText,
                    actionText = actionText,
                    ready = hardwareReady,
                    busy = checking || applying,
                    onBack = ::finish,
                    onRetry = ::checkHardware,
                    onSelect = { selectedZone = it },
                    onLevel = { level ->
                        draft.setLevel(selectedZone, level)
                        refreshDraft()
                    },
                    onFill = {
                        draft.replace(IntArray(GlyphFrame.ZONE_COUNT) { frame[selectedZone] })
                        refreshDraft()
                    },
                    onPreset = { preset ->
                        draft.replace(preset)
                        refreshDraft()
                    },
                    onSave = ::saveScene,
                    onLoad = ::loadScene,
                    onApply = ::applyFrame,
                )
            }
        }
        checkHardware()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putIntArray("draft_frame", frame.copyOf())
        outState.putLong("draft_revision", draft.revision)
        outState.putInt("draft_zone", selectedZone)
        super.onSaveInstanceState(outState)
    }

    private fun refreshDraft() {
        frame = draft.frame.copyOf()
        actionText = getString(R.string.detail_unsaved)
    }

    private fun checkHardware() {
        if (checking || applying) return
        checking = true
        hardwareReady = false
        hardwareText = getString(R.string.detail_checking)
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            val result = GlyphEffectCoordinator.checkAndRestore(app)
            if (result.ready) GlyphRuntime.ensure(app)
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) return@withContext
                checking = false
                hardwareReady = result.ready
                hardwareText = getString(when (result.hardware) {
                    GlyphHardware.Status.NO_ROOT -> R.string.detail_no_root
                    GlyphHardware.Status.UNSUPPORTED -> R.string.detail_no_glyph
                    GlyphHardware.Status.CHECK_FAILED -> R.string.status_check_failed
                    GlyphHardware.Status.READY -> if (result.ready) R.string.detail_ready
                        else R.string.status_restore_failed
                })
            }
        }
    }

    private fun saveScene() {
        val snapshot = frame.copyOf()
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            val saved = GlyphSceneStore.save(app, snapshot)
            withContext(Dispatchers.Main) {
                if (!isDestroyed && !isFinishing) {
                    Toast.makeText(this@GlyphDetailActivity,
                        if (saved) R.string.detail_scene_saved else R.string.detail_scene_save_failed,
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadScene() {
        val saved = GlyphSceneStore.load(this)
        if (saved == null) {
            Toast.makeText(this, R.string.detail_scene_missing, Toast.LENGTH_SHORT).show()
        } else {
            draft.replace(saved)
            refreshDraft()
        }
    }

    private fun applyFrame() {
        if (!hardwareReady || applying) return
        applying = true
        actionText = getString(R.string.detail_applying)
        val snapshot = draft.snapshot()
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            val success = GlyphEffectCoordinator.applyManualFrame(app, snapshot.frame)
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) return@withContext
                applying = false
                actionText = getString(when {
                    !success -> R.string.detail_apply_failed
                    !draft.matches(snapshot) -> R.string.detail_unsaved
                    else -> R.string.detail_applied
                })
                if (!success) hardwareReady = false
            }
        }
    }
}

@Composable
private fun DetailScreen(
    frame: IntArray,
    selected: Int,
    hardwareText: String,
    actionText: String,
    ready: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (Int) -> Unit,
    onLevel: (Int) -> Unit,
    onFill: () -> Unit,
    onPreset: (IntArray) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onApply: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(topBar = {
        SmallTopAppBar(title = context.getString(R.string.detail_title),
            navigationIcon = {
                TextButton(text = context.getString(R.string.detail_back), onClick = onBack,
                    minHeight = GlyphUiSize.Button)
            })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(context.getString(R.string.detail_intro), style = MiuixTheme.textStyles.body2)
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                GlyphPhonePreview(frame = frame, selected = selected, onSelect = onSelect)
                Text(context.getString(R.string.detail_preview_hint),
                    modifier = Modifier.padding(top = 12.dp), style = MiuixTheme.textStyles.footnote1)
            }
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                Text(hardwareText, style = MiuixTheme.textStyles.body1)
                if (!ready && !busy) {
                    TextButton(text = context.getString(R.string.btn_retry_hardware),
                        onClick = onRetry, modifier = Modifier.padding(top = 10.dp),
                        minHeight = GlyphUiSize.Button)
                }
            }
            SmallTitle(text = context.getString(R.string.detail_all_zones))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                for (row in (0 until GlyphFrame.ZONE_COUNT).chunked(6)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { index ->
                            Button(onClick = { onSelect(index) }, modifier = Modifier.weight(1f),
                                minWidth = 0.dp, minHeight = GlyphUiSize.ZoneButton,
                                insideMargin = PaddingValues(2.dp),
                                colors = if (index == selected) ButtonDefaults.buttonColorsPrimary()
                                    else ButtonDefaults.buttonColors()) {
                                Text(GlyphZones.name(index),
                                    style = MiuixTheme.textStyles.footnote2.copy(
                                        fontWeight = FontWeight.Medium))
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                Text(context.getString(R.string.detail_zone_label,
                    GlyphZones.name(selected), GlyphZones.ledIds[selected],
                    context.getString(GlyphZones.groupName(selected))),
                    style = MiuixTheme.textStyles.title4)
                Text(context.getString(R.string.detail_level, frame[selected]),
                    modifier = Modifier.padding(top = 10.dp), style = MiuixTheme.textStyles.body1)
                Slider(value = frame[selected].toFloat(), onValueChange = { onLevel(it.roundToInt()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp), valueRange = 0f..255f)
                TextButton(text = context.getString(R.string.detail_fill_all), onClick = onFill,
                    modifier = Modifier.padding(top = 8.dp), minHeight = GlyphUiSize.Button)
            }
            SmallTitle(text = context.getString(R.string.detail_presets))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetButton(R.string.detail_preset_all) { onPreset(GlyphZones.presetAll()) }
                    PresetButton(R.string.detail_preset_soft) { onPreset(GlyphZones.presetAll(64)) }
                    PresetButton(R.string.detail_preset_ring) { onPreset(GlyphZones.presetRing()) }
                    PresetButton(R.string.detail_preset_lower) { onPreset(GlyphZones.presetCameraAndLower()) }
                    PresetButton(R.string.detail_preset_off) { onPreset(GlyphZones.presetAll(0)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f),
                    minHeight = GlyphUiSize.Button) {
                    Text(context.getString(R.string.detail_save_scene))
                }
                Button(onClick = onLoad, modifier = Modifier.weight(1f),
                    minHeight = GlyphUiSize.Button) {
                    Text(context.getString(R.string.detail_load_scene))
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                Text(actionText, style = MiuixTheme.textStyles.body2)
                Button(onClick = onApply, enabled = ready && !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    minHeight = GlyphUiSize.PrimaryButton,
                    colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(context.getString(R.string.detail_apply))
                }
            }
        }
    }
}

@Composable
private fun PresetButton(label: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    TextButton(text = context.getString(label), onClick = onClick,
        modifier = Modifier.fillMaxWidth(), minHeight = GlyphUiSize.Button)
}
