package com.richard.glyphcontroller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class MainActivity : ComponentActivity() {
    private var hardwareReady by mutableStateOf(false)
    private var checking by mutableStateOf(false)
    private var toggling by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var appliedFrame by mutableStateOf(IntArray(GlyphFrame.ZONE_COUNT))
    private var notifyEnabled by mutableStateOf(true)
    private var pauseNotificationDuringDnd by mutableStateOf(true)
    private var chargerEnabled by mutableStateOf(true)
    private var volumeEnabled by mutableStateOf(true)
    private var listenerStatus by mutableStateOf(GlyphListenerState.Status.OFF)
    private var doNotDisturb by mutableStateOf(false)
    private var unsupportedShown = false
    private var showUnsupported by mutableStateOf(false)
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        unsupportedShown = savedInstanceState?.getBoolean("unsupported_shown") ?: false
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        notifyEnabled = settings.getBoolean("notify_enabled", true)
        pauseNotificationDuringDnd = settings.getBoolean(GlyphDoNotDisturb.PAUSE_NOTIFICATION_SETTING, true)
        chargerEnabled = settings.getBoolean("charger_enabled", true)
        volumeEnabled = settings.getBoolean("volume_enabled", true)
        appliedFrame = GlyphPowerState.getFrame(this)
        statusText = powerStatus(appliedFrame)
        listenerStatus = GlyphListenerState.status(this)
        doNotDisturb = GlyphDoNotDisturb.isActive(this)

        setContent {
            GlyphAppTheme {
                HomeScreen(
                    status = statusText,
                    frame = appliedFrame,
                    ready = hardwareReady,
                    busy = checking || toggling,
                    notifyEnabled = notifyEnabled,
                    pauseNotificationDuringDnd = pauseNotificationDuringDnd,
                    chargerEnabled = chargerEnabled,
                    volumeEnabled = volumeEnabled,
                    listenerStatus = listenerStatus,
                    doNotDisturb = doNotDisturb,
                    onToggle = ::togglePower,
                    onRetry = ::checkHardware,
                    onDetail = { startActivity(Intent(this, GlyphDetailActivity::class.java)) },
                    onNotificationChange = { enabled ->
                        notifyEnabled = enabled
                        settings.edit().putBoolean("notify_enabled", enabled).apply()
                        if (enabled) GlyphRuntime.ensure(applicationContext)
                        listenerStatus = GlyphListenerState.status(this)
                    },
                    onPauseNotificationDuringDndChange = { enabled ->
                        pauseNotificationDuringDnd = enabled
                        settings.edit().putBoolean(GlyphDoNotDisturb.PAUSE_NOTIFICATION_SETTING, enabled).apply()
                    },
                    onChargerChange = { enabled ->
                        chargerEnabled = enabled
                        settings.edit().putBoolean("charger_enabled", enabled).apply()
                    },
                    onVolumeChange = { enabled ->
                        volumeEnabled = enabled
                        settings.edit().putBoolean("volume_enabled", enabled).apply()
                        if (enabled) GlyphVolumeCoordinator.start(applicationContext)
                    },
                    onListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                WindowDialog(
                    title = getString(R.string.glyph_unsupported_title),
                    summary = getString(R.string.glyph_unsupported_message),
                    show = showUnsupported,
                    onDismissRequest = { showUnsupported = false },
                ) {
                    TextButton(
                        text = getString(android.R.string.ok),
                        onClick = { showUnsupported = false },
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = GlyphUiSize.Button,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkHardware()
        startPeriodicRefresh()
        GlyphRuntime.ensure(applicationContext)
        GlyphVolumeCoordinator.start(applicationContext)
    }

    override fun onPause() {
        refreshJob?.cancel()
        refreshJob = null
        super.onPause()
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                listenerStatus = GlyphListenerState.status(this@MainActivity)
                doNotDisturb = GlyphDoNotDisturb.isActive(this@MainActivity)
                delay(1000)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("unsupported_shown", unsupportedShown)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && hardwareReady && !checking && !toggling) refreshPowerState()
    }

    private fun powerStatus(frame: IntArray): String = when {
        frame.all { it == 0 } -> getString(R.string.status_all_off)
        frame.all { it == 255 } -> getString(R.string.status_all_on)
        else -> getString(R.string.status_custom)
    }

    private fun refreshPowerState() {
        appliedFrame = GlyphPowerState.getFrame(this)
        statusText = powerStatus(appliedFrame)
    }

    private fun checkHardware() {
        if (checking || toggling) return
        checking = true
        hardwareReady = false
        statusText = getString(R.string.detail_checking)
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            val result = GlyphEffectCoordinator.checkAndRestore(app)
            if (result.ready) GlyphRuntime.ensure(app)
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) return@withContext
                checking = false
                hardwareReady = result.ready
                if (result.ready) {
                    refreshPowerState()
                } else {
                    statusText = getString(when (result.hardware) {
                        GlyphHardware.Status.NO_ROOT -> R.string.status_no_root
                        GlyphHardware.Status.UNSUPPORTED -> R.string.status_no_glyph
                        GlyphHardware.Status.CHECK_FAILED -> R.string.status_check_failed
                        GlyphHardware.Status.READY -> R.string.status_restore_failed
                    })
                    if (result.hardware == GlyphHardware.Status.UNSUPPORTED && !unsupportedShown) {
                        unsupportedShown = true
                        showUnsupported = true
                    }
                }
            }
        }
    }

    private fun togglePower() {
        if (!hardwareReady || checking || toggling) return
        toggling = true
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            val success = GlyphEffectCoordinator.toggleManual(app)
            withContext(Dispatchers.Main) {
                if (isDestroyed || isFinishing) return@withContext
                toggling = false
                if (success) refreshPowerState() else {
                    hardwareReady = false
                    statusText = getString(R.string.status_write_failed)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    status: String,
    frame: IntArray,
    ready: Boolean,
    busy: Boolean,
    notifyEnabled: Boolean,
    pauseNotificationDuringDnd: Boolean,
    chargerEnabled: Boolean,
    volumeEnabled: Boolean,
    listenerStatus: GlyphListenerState.Status,
    doNotDisturb: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onDetail: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onPauseNotificationDuringDndChange: (Boolean) -> Unit,
    onChargerChange: (Boolean) -> Unit,
    onVolumeChange: (Boolean) -> Unit,
    onListenerSettings: () -> Unit,
) {
    val on = frame.any { it > 0 }
    val listenerText = when {
        listenerStatus == GlyphListenerState.Status.CONNECTED &&
            pauseNotificationDuringDnd && doNotDisturb ->
            R.string.notify_paused_dnd
        else -> when (listenerStatus) {
            GlyphListenerState.Status.OFF -> R.string.notify_off
            GlyphListenerState.Status.CONNECTED -> R.string.notify_ready
            GlyphListenerState.Status.NEEDS_ACCESS -> R.string.notify_need_listener
            GlyphListenerState.Status.RECOVERING -> R.string.notify_recovering
            GlyphListenerState.Status.FAILED -> R.string.notify_disconnected
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(topBar = {
        Text(
            text = context.getString(R.string.app_name),
            modifier = Modifier.fillMaxWidth()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            style = MiuixTheme.textStyles.title2,
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
                Text("GLYPH / 02", style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.primary)
                Text(status, modifier = Modifier.padding(top = 14.dp),
                    style = MiuixTheme.textStyles.title2)
                Text(context.getString(R.string.hint_root), modifier = Modifier.padding(top = 10.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Button(
                    onClick = onToggle,
                    enabled = ready && !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    minHeight = GlyphUiSize.PrimaryButton,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(context.getString(if (on) R.string.btn_turn_off else R.string.btn_turn_on)) }
                if (!ready && !busy) {
                    TextButton(text = context.getString(R.string.btn_retry_hardware),
                        onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minHeight = GlyphUiSize.Button)
                }
            }

            SmallTitle(text = context.getString(R.string.btn_detail))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                Text(context.getString(R.string.detail_intro), style = MiuixTheme.textStyles.body1)
                Button(onClick = onDetail, modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    minHeight = GlyphUiSize.Button) {
                    Text(context.getString(R.string.btn_detail))
                }
            }

            SmallTitle(text = context.getString(R.string.notify_label))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = context.getString(R.string.sw_notify),
                    summary = context.getString(R.string.notify_hint),
                    checked = notifyEnabled,
                    onCheckedChange = onNotificationChange,
                )
                SwitchPreference(
                    title = context.getString(R.string.sw_notify_pause_dnd),
                    summary = context.getString(R.string.notify_pause_dnd_hint),
                    checked = notifyEnabled && pauseNotificationDuringDnd,
                    enabled = notifyEnabled,
                    onCheckedChange = onPauseNotificationDuringDndChange,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Text(context.getString(listenerText),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2)
                TextButton(text = context.getString(R.string.btn_listener), onClick = onListenerSettings,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                    minHeight = GlyphUiSize.Button)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = context.getString(R.string.sw_charger),
                    summary = context.getString(R.string.charger_hint),
                    checked = chargerEnabled,
                    onCheckedChange = onChargerChange,
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = context.getString(R.string.sw_volume),
                    summary = context.getString(R.string.volume_hint),
                    checked = volumeEnabled,
                    onCheckedChange = onVolumeChange,
                )
            }
        }
    }
}
