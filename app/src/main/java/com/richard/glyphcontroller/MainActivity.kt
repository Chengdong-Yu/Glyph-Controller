package com.richard.glyphcontroller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
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
    private var keepAliveStatus by mutableStateOf(RootKeepAlive.Status.UNKNOWN)
    private var keepAliveBusy by mutableStateOf(false)
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
                    keepAliveStatus = keepAliveStatus,
                    keepAliveBusy = keepAliveBusy,
                    onToggle = ::togglePower,
                    onRetry = ::checkHardware,
                    onDetail = { startActivity(Intent(this, GlyphDetailActivity::class.java)) },
                    onNotificationChange = { enabled ->
                        notifyEnabled = enabled
                        settings.edit { putBoolean("notify_enabled", enabled) }
                        if (enabled) GlyphRuntime.ensure(applicationContext)
                        listenerStatus = GlyphListenerState.status(this)
                    },
                    onPauseNotificationDuringDndChange = { enabled ->
                        pauseNotificationDuringDnd = enabled
                        settings.edit { putBoolean(GlyphDoNotDisturb.PAUSE_NOTIFICATION_SETTING, enabled) }
                    },
                    onChargerChange = { enabled ->
                        chargerEnabled = enabled
                        settings.edit { putBoolean("charger_enabled", enabled) }
                    },
                    onVolumeChange = { enabled ->
                        volumeEnabled = enabled
                        settings.edit { putBoolean("volume_enabled", enabled) }
                        if (enabled) GlyphVolumeCoordinator.start(applicationContext)
                    },
                    onListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onInstallKeepAlive = ::installKeepAlive,
                    onRemoveKeepAlive = ::removeKeepAlive,
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
        refreshKeepAliveStatus()
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

    private fun refreshKeepAliveStatus() {
        if (keepAliveBusy) return
        keepAliveBusy = true
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (RootKeepAlive.autoInstallEnabled(app)) {
                    RootKeepAlive.ensureInstalled(app)
                }
                val status = RootKeepAlive.status()
                withContext(Dispatchers.Main) {
                    if (isDestroyed || isFinishing) return@withContext
                    keepAliveStatus = status
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { keepAliveBusy = false }
            }
        }
    }

    private fun installKeepAlive() = changeKeepAlive(install = true)

    private fun removeKeepAlive() = changeKeepAlive(install = false)

    private fun changeKeepAlive(install: Boolean) {
        if (keepAliveBusy) return
        keepAliveBusy = true
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val success = if (install) RootKeepAlive.install(app) else RootKeepAlive.remove(app)
                val status = RootKeepAlive.status()
                withContext(Dispatchers.Main) {
                    if (isDestroyed || isFinishing) return@withContext
                    keepAliveStatus = status
                    Toast.makeText(
                        this@MainActivity,
                        when {
                            install && success -> R.string.keepalive_install_success
                            install -> R.string.keepalive_install_failed
                            success -> R.string.keepalive_remove_success
                            else -> R.string.keepalive_remove_failed
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { keepAliveBusy = false }
            }
        }
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
    keepAliveStatus: RootKeepAlive.Status,
    keepAliveBusy: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onDetail: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onPauseNotificationDuringDndChange: (Boolean) -> Unit,
    onChargerChange: (Boolean) -> Unit,
    onVolumeChange: (Boolean) -> Unit,
    onListenerSettings: () -> Unit,
    onInstallKeepAlive: () -> Unit,
    onRemoveKeepAlive: () -> Unit,
) {
    val on = frame.any { it > 0 }
    val activeZones = frame.count { it > 0 }
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
    val scrollBehavior = MiuixScrollBehavior()
    val availabilityText = stringResource(
        when {
            busy -> R.string.home_device_checking
            ready -> R.string.home_device_ready
            else -> R.string.home_device_unavailable
        },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.home_subtitle),
                scrollBehavior = scrollBehavior,
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
            DeviceControlCard(
                status = status,
                availability = availabilityText,
                activeZones = activeZones,
                ready = ready,
                busy = busy,
                on = on,
                onToggle = onToggle,
                onRetry = onRetry,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallTitle(text = stringResource(R.string.home_control_section))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.home_studio_title),
                        summary = stringResource(R.string.home_studio_summary),
                        onClick = onDetail,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallTitle(text = stringResource(R.string.home_effects_section))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.sw_notify),
                        summary = stringResource(R.string.notify_hint),
                        checked = notifyEnabled,
                        onCheckedChange = onNotificationChange,
                    )
                    AnimatedVisibility(visible = notifyEnabled) {
                        SwitchPreference(
                            title = stringResource(R.string.sw_notify_pause_dnd),
                            summary = stringResource(R.string.notify_pause_dnd_hint),
                            checked = pauseNotificationDuringDnd,
                            onCheckedChange = onPauseNotificationDuringDndChange,
                        )
                    }
                    ArrowPreference(
                        title = stringResource(R.string.home_access_title),
                        summary = stringResource(listenerText),
                        onClick = onListenerSettings,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.sw_charger),
                        summary = stringResource(R.string.charger_hint),
                        checked = chargerEnabled,
                        onCheckedChange = onChargerChange,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.sw_volume),
                        summary = stringResource(R.string.volume_hint),
                        checked = volumeEnabled,
                        onCheckedChange = onVolumeChange,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallTitle(text = stringResource(R.string.keepalive_section))
                KeepAliveCard(
                    status = keepAliveStatus,
                    busy = keepAliveBusy,
                    onInstall = onInstallKeepAlive,
                    onRemove = onRemoveKeepAlive,
                )
            }

            Text(
                text = stringResource(R.string.home_system_note),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun KeepAliveCard(
    status: RootKeepAlive.Status,
    busy: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val statusText = stringResource(
        when {
            busy -> R.string.keepalive_working
            status == RootKeepAlive.Status.RUNNING -> R.string.keepalive_running
            status == RootKeepAlive.Status.INSTALLED -> R.string.keepalive_installed
            status == RootKeepAlive.Status.NOT_INSTALLED -> R.string.keepalive_not_installed
            else -> R.string.keepalive_unknown
        },
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.keepalive_title),
                    style = MiuixTheme.textStyles.title4,
                )
                Text(
                    text = stringResource(R.string.keepalive_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            GlyphStatusPill(
                text = statusText,
                active = busy || status == RootKeepAlive.Status.RUNNING,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onInstall,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                minHeight = GlyphUiSize.Button,
            ) {
                Text(stringResource(R.string.keepalive_install))
            }
            TextButton(
                text = stringResource(R.string.keepalive_remove),
                onClick = onRemove,
                enabled = !busy && status != RootKeepAlive.Status.NOT_INSTALLED,
                modifier = Modifier.weight(1f),
                minHeight = GlyphUiSize.Button,
            )
        }
    }
}

@Composable
private fun DeviceControlCard(
    status: String,
    availability: String,
    activeZones: Int,
    ready: Boolean,
    busy: Boolean,
    on: Boolean,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(24.dp),
    ) {
        GlyphStatusPill(
            text = availability,
            active = ready || busy,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_device_label),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(text = status, style = MiuixTheme.textStyles.title2)
                Text(
                    text = stringResource(R.string.home_active_zones, activeZones),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            GlyphMiniature(
                lit = on,
                modifier = Modifier.size(width = 104.dp, height = 120.dp),
            )
        }
        Button(
            onClick = onToggle,
            enabled = ready && !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            minHeight = GlyphUiSize.PrimaryButton,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(stringResource(if (on) R.string.btn_turn_off else R.string.btn_turn_on))
        }
        if (!ready && !busy) {
            TextButton(
                text = stringResource(R.string.btn_retry_hardware),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minHeight = GlyphUiSize.Button,
            )
        }
    }
}
