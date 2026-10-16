package com.richard.glyphcontroller

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
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
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.detail_title),
                subtitle = stringResource(R.string.detail_subtitle),
                navigationIcon = { GlyphBackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            DetailApplyBar(
                actionText = actionText,
                enabled = ready && !busy,
                onApply = onApply,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HardwareStatusCard(
                hardwareText = hardwareText,
                ready = ready,
                busy = busy,
                onRetry = onRetry,
            )

            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                GlyphPhonePreview(frame = frame, selected = selected, onSelect = onSelect)
                Text(
                    text = stringResource(R.string.detail_preview_hint),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallTitle(text = stringResource(R.string.detail_editor_section))
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.detail_zone_label,
                                GlyphZones.name(selected),
                                GlyphZones.ledIds[selected],
                                stringResource(GlyphZones.groupName(selected)),
                            ),
                            modifier = Modifier.weight(1f),
                            style = MiuixTheme.textStyles.title4,
                        )
                        Text(
                            text = stringResource(R.string.detail_all_zones),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.detail_zone_picker_hint),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    ZoneSelector(
                        selected = selected,
                        onSelect = onSelect,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(R.string.detail_level, frame[selected]),
                        modifier = Modifier.padding(top = 22.dp),
                        style = MiuixTheme.textStyles.title3,
                    )
                    Slider(
                        value = frame[selected].toFloat(),
                        onValueChange = { onLevel(it.roundToInt()) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        valueRange = 0f..255f,
                    )
                    TextButton(
                        text = stringResource(R.string.detail_fill_all),
                        onClick = onFill,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minHeight = GlyphUiSize.Button,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallTitle(text = stringResource(R.string.detail_presets))
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                    PresetSelector(onPreset = onPreset)
                    Text(
                        text = stringResource(R.string.detail_scene_storage),
                        modifier = Modifier.padding(top = 22.dp),
                        style = MiuixTheme.textStyles.title4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            minHeight = GlyphUiSize.Button,
                        ) {
                            Text(stringResource(R.string.detail_save_scene))
                        }
                        Button(
                            onClick = onLoad,
                            modifier = Modifier.weight(1f),
                            minHeight = GlyphUiSize.Button,
                        ) {
                            Text(stringResource(R.string.detail_load_scene))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareStatusCard(
    hardwareText: String,
    ready: Boolean,
    busy: Boolean,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_hardware_section),
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.title4,
            )
            GlyphStatusPill(
                text = stringResource(
                    when {
                        busy -> R.string.home_device_checking
                        ready -> R.string.home_device_ready
                        else -> R.string.home_device_unavailable
                    },
                ),
                active = ready || busy,
            )
        }
        Text(
            text = hardwareText,
            modifier = Modifier.padding(top = 12.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (!ready && !busy) {
            TextButton(
                text = stringResource(R.string.btn_retry_hardware),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                minHeight = GlyphUiSize.Button,
            )
        }
    }
}

@Composable
private fun ZoneSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(GlyphFrame.ZONE_COUNT) { index ->
            Button(
                onClick = { onSelect(index) },
                minWidth = 52.dp,
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                colors = if (index == selected) {
                    ButtonDefaults.buttonColorsPrimary()
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(text = GlyphZones.name(index), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun PresetSelector(onPreset: (IntArray) -> Unit) {
    val presets = listOf(
        R.string.detail_preset_all to GlyphZones.presetAll(),
        R.string.detail_preset_soft to GlyphZones.presetAll(64),
        R.string.detail_preset_ring to GlyphZones.presetRing(),
        R.string.detail_preset_lower to GlyphZones.presetCameraAndLower(),
        R.string.detail_preset_off to GlyphZones.presetAll(0),
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(presets) { (label, preset) ->
            Button(
                onClick = { onPreset(preset) },
                minHeight = GlyphUiSize.Button,
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun DetailApplyBar(
    actionText: String,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = actionText,
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Button(
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            minHeight = GlyphUiSize.PrimaryButton,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(stringResource(R.string.detail_apply))
        }
    }
}
