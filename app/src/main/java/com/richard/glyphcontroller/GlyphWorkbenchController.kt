package com.richard.glyphcontroller

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lifecycle-aware state controller shared by [MainActivity] and [GlyphDetailActivity].
 * Replaces duplicated coroutines and 1-second polling loops with reactive StateFlow and broadcast observers.
 */
@Stable
class GlyphWorkbenchController(
    private val activity: ComponentActivity,
    savedInstanceState: Bundle?,
) {
    private val appContext: Context = activity.applicationContext
    private val settingsPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var colorMode by mutableStateOf(GlyphAppearancePrefs.getColorMode(activity))
        private set
    var useMonet by mutableStateOf(GlyphAppearancePrefs.getUseMonet(activity))
        private set
    var styleVariant by mutableStateOf(GlyphAppearancePrefs.getStyleVariant(activity))
        private set
    var seedColor by mutableStateOf(GlyphAppearancePrefs.getSeedColor(activity))
        private set
    var serifTitles by mutableStateOf(GlyphAppearancePrefs.getSerifTitles(activity))
        private set

    var hardwareReady by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var toggling by mutableStateOf(false)
        private set
    var applying by mutableStateOf(false)
        private set
    var previewingEffect by mutableStateOf(false)
        private set

    val busy: Boolean get() = checking || toggling || applying

    var appliedSnapshot by mutableStateOf(GlyphPowerState.getSnapshot(activity))
        private set
    private val studioDraft: GlyphDraft
    var studioSnapshot by mutableStateOf(GlyphSnapshot.Zero)
        private set

    var selectedZone by mutableIntStateOf(0)
        private set
    var groupEditMode by mutableStateOf(false)
        private set
    var activeFilter by mutableStateOf(GlyphZoneGroup.ALL)
        private set

    var savedSlots by mutableStateOf(GlyphSceneStore.loadAllSnapshots(activity))
        private set

    var statusText by mutableStateOf("")
        private set
    var studioHardwareText by mutableStateOf("")
        private set
    var studioActionText by mutableStateOf("")
        private set

    var notifyEnabled by mutableStateOf(settingsPrefs.getBoolean("notify_enabled", true))
        private set
    var pauseNotificationDuringDnd by mutableStateOf(
        settingsPrefs.getBoolean(GlyphDoNotDisturb.PAUSE_NOTIFICATION_SETTING, true),
    )
        private set
    var chargerEnabled by mutableStateOf(settingsPrefs.getBoolean("charger_enabled", true))
        private set
    var volumeEnabled by mutableStateOf(settingsPrefs.getBoolean("volume_enabled", true))
        private set

    var listenerStatus by mutableStateOf(GlyphListenerState.Status.OFF)
        private set
    var doNotDisturb by mutableStateOf(false)
        private set
    var keepAliveStatus by mutableStateOf(RootKeepAlive.Status.UNKNOWN)
        private set
    var keepAliveBusy by mutableStateOf(false)
        private set

    private var unsupportedShown: Boolean = savedInstanceState?.getBoolean(KEY_UNSUPPORTED_SHOWN) ?: false
    var showUnsupportedDialog by mutableStateOf(false)
    var showRemoveKeepAliveDialog by mutableStateOf(false)

    val isDraftDirty: Boolean get() = studioSnapshot != appliedSnapshot

    /** Optional UI callback connected to `MochiToastState.show` while the host screen is composed. */
    var onFeedback: ((title: String, iconName: String?) -> Unit)? = null

    private var foregroundJobs: List<Job> = emptyList()
    private var dndReceiverRegistered = false

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                doNotDisturb = GlyphDoNotDisturb.isActive(activity)
            }
        }
    }

    init {
        val restoredFrame = savedInstanceState?.getIntArray(KEY_DRAFT_FRAME)?.takeIf(GlyphFrame::valid)
        studioDraft = GlyphDraft(
            initial = restoredFrame ?: appliedSnapshot.toIntArray(),
            initialRevision = savedInstanceState?.getLong(KEY_DRAFT_REVISION) ?: 0L,
        )
        studioSnapshot = GlyphSnapshot.from(studioDraft.frame)
        selectedZone = (savedInstanceState?.getInt(KEY_SELECTED_ZONE) ?: 0)
            .coerceIn(0, GlyphFrame.ZONE_COUNT - 1)
        groupEditMode = savedInstanceState?.getBoolean(KEY_GROUP_EDIT_MODE) ?: false

        statusText = powerStatusText(appliedSnapshot)
        studioHardwareText = activity.getString(R.string.detail_checking)
        studioActionText = activity.getString(
            if (isDraftDirty) R.string.detail_unsaved else R.string.detail_current,
        )
        listenerStatus = GlyphListenerState.status(activity)
        doNotDisturb = GlyphDoNotDisturb.isActive(activity)
    }

    fun onResume() {
        colorMode = GlyphAppearancePrefs.getColorMode(activity)
        useMonet = GlyphAppearancePrefs.getUseMonet(activity)
        styleVariant = GlyphAppearancePrefs.getStyleVariant(activity)
        seedColor = GlyphAppearancePrefs.getSeedColor(activity)
        serifTitles = GlyphAppearancePrefs.getSerifTitles(activity)

        notifyEnabled = settingsPrefs.getBoolean("notify_enabled", true)
        pauseNotificationDuringDnd = settingsPrefs.getBoolean(GlyphDoNotDisturb.PAUSE_NOTIFICATION_SETTING, true)
        chargerEnabled = settingsPrefs.getBoolean("charger_enabled", true)
        volumeEnabled = settingsPrefs.getBoolean("volume_enabled", true)
        savedSlots = GlyphSceneStore.loadAllSnapshots(activity)
        listenerStatus = GlyphListenerState.status(activity)
        doNotDisturb = GlyphDoNotDisturb.isActive(activity)

        registerForegroundObservers()

        RootCommand.beginRootAttemptWindow()
        checkHardware()
        GlyphRuntime.ensure(appContext)
        GlyphVolumeCoordinator.start(appContext)
        refreshKeepAliveStatus()
    }

    fun onPause() {
        unregisterForegroundObservers()
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus && hardwareReady && !busy) {
            syncAppliedSnapshot(GlyphPowerState.getSnapshot(activity), syncCleanDraft = true)
            listenerStatus = GlyphListenerState.status(activity)
            doNotDisturb = GlyphDoNotDisturb.isActive(activity)
        }
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UNSUPPORTED_SHOWN, unsupportedShown)
        outState.putIntArray(KEY_DRAFT_FRAME, studioDraft.frame.copyOf())
        outState.putLong(KEY_DRAFT_REVISION, studioDraft.revision)
        outState.putInt(KEY_SELECTED_ZONE, selectedZone)
        outState.putBoolean(KEY_GROUP_EDIT_MODE, groupEditMode)
    }

    private fun registerForegroundObservers() {
        unregisterForegroundObservers()
        if (!dndReceiverRegistered) {
            ContextCompat.registerReceiver(
                activity,
                dndReceiver,
                IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            dndReceiverRegistered = true
        }
        val powerJob = activity.lifecycleScope.launch {
            GlyphPowerState.snapshotFlow.collect { latest ->
                if (latest != null && !busy && latest != appliedSnapshot) {
                    syncAppliedSnapshot(latest, syncCleanDraft = true)
                }
            }
        }
        val listenerJob = activity.lifecycleScope.launch {
            GlyphListenerState.changes.collect {
                listenerStatus = GlyphListenerState.status(activity)
            }
        }
        foregroundJobs = listOf(powerJob, listenerJob)
    }

    private fun unregisterForegroundObservers() {
        foregroundJobs.forEach(Job::cancel)
        foregroundJobs = emptyList()
        if (dndReceiverRegistered) {
            try {
                activity.unregisterReceiver(dndReceiver)
            } catch (_: IllegalArgumentException) {
            }
            dndReceiverRegistered = false
        }
    }

    private fun syncAppliedSnapshot(nextApplied: GlyphSnapshot, syncCleanDraft: Boolean) {
        val wasClean = !isDraftDirty
        appliedSnapshot = nextApplied
        statusText = powerStatusText(nextApplied)
        if (syncCleanDraft && wasClean) {
            studioDraft.replace(nextApplied.toIntArray())
            studioSnapshot = nextApplied
        }
        studioActionText = activity.getString(
            if (isDraftDirty) R.string.detail_unsaved else R.string.detail_current,
        )
    }

    private fun refreshDraftSnapshot() {
        studioSnapshot = GlyphSnapshot.from(studioDraft.frame)
        studioActionText = activity.getString(
            if (isDraftDirty) R.string.detail_unsaved else R.string.detail_current,
        )
    }

    private fun powerStatusText(snapshot: GlyphSnapshot): String = when {
        snapshot.allOff -> activity.getString(R.string.status_all_off)
        snapshot.allMax -> activity.getString(R.string.status_all_on)
        else -> activity.getString(R.string.status_custom)
    }

    private fun notifyToast(title: String, iconName: String? = null) {
        onFeedback?.invoke(title, iconName)
    }

    fun checkHardware() {
        if (busy) return
        activity.lifecycleScope.launch {
            refreshHardware()
        }
    }

    suspend fun refreshHardware() {
        if (busy) return
        checking = true
        hardwareReady = false
        statusText = activity.getString(R.string.detail_checking)
        studioHardwareText = activity.getString(R.string.detail_checking)

        val result = withContext(Dispatchers.Default) {
            GlyphEffectCoordinator.checkAndRestore(appContext).also { readiness ->
                if (readiness.ready) GlyphRuntime.ensure(appContext)
            }
        }
        if (activity.isDestroyed || activity.isFinishing) return
        checking = false
        hardwareReady = result.ready
        if (result.ready) {
            studioHardwareText = activity.getString(R.string.detail_ready)
            syncAppliedSnapshot(GlyphPowerState.getSnapshot(activity), syncCleanDraft = true)
        } else {
            statusText = activity.getString(
                when (result.hardware) {
                    GlyphHardware.Status.NO_ROOT -> R.string.status_no_root
                    GlyphHardware.Status.UNSUPPORTED -> R.string.status_no_glyph
                    GlyphHardware.Status.CHECK_FAILED -> R.string.status_check_failed
                    GlyphHardware.Status.READY -> R.string.status_restore_failed
                },
            )
            studioHardwareText = activity.getString(
                when (result.hardware) {
                    GlyphHardware.Status.NO_ROOT -> R.string.detail_no_root
                    GlyphHardware.Status.UNSUPPORTED -> R.string.detail_no_glyph
                    GlyphHardware.Status.CHECK_FAILED -> R.string.status_check_failed
                    GlyphHardware.Status.READY -> R.string.status_restore_failed
                },
            )
            if (result.hardware == GlyphHardware.Status.UNSUPPORTED && !unsupportedShown) {
                unsupportedShown = true
                showUnsupportedDialog = true
            }
        }
    }

    fun toggleAllPower() {
        if (!hardwareReady || busy) return
        toggling = true
        activity.lifecycleScope.launch {
            val success = withContext(Dispatchers.Default) {
                GlyphEffectCoordinator.toggleManual(appContext)
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            toggling = false
            if (success) {
                syncAppliedSnapshot(GlyphPowerState.getSnapshot(activity), syncCleanDraft = true)
            } else {
                hardwareReady = false
                statusText = activity.getString(R.string.status_write_failed)
                notifyToast(statusText, "warning")
            }
        }
    }

    fun applyQuickPreset(snapshot: GlyphSnapshot) {
        if (!hardwareReady || busy) {
            studioDraft.replace(snapshot.toIntArray())
            refreshDraftSnapshot()
            return
        }
        toggling = true
        val targetArray = snapshot.toIntArray()
        activity.lifecycleScope.launch {
            val success = withContext(Dispatchers.Default) {
                GlyphEffectCoordinator.applyManualFrame(appContext, targetArray)
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            toggling = false
            if (success) {
                appliedSnapshot = snapshot
                statusText = powerStatusText(snapshot)
                studioDraft.replace(targetArray)
                studioSnapshot = snapshot
                studioActionText = activity.getString(R.string.detail_applied)
            } else {
                hardwareReady = false
                statusText = activity.getString(R.string.status_write_failed)
                notifyToast(statusText, "warning")
            }
        }
    }

    fun selectZone(zone: Int) {
        selectedZone = zone.coerceIn(0, GlyphFrame.ZONE_COUNT - 1)
        if (activeFilter != GlyphZoneGroup.ALL && selectedZone !in activeFilter.indices) {
            activeFilter = GlyphZoneGroup.ALL
        }
    }

    fun selectFilter(group: GlyphZoneGroup) {
        activeFilter = group
        if (selectedZone !in group.indices) {
            selectedZone = group.indices.first
        }
    }

    fun setEditScope(wholeGroup: Boolean) {
        groupEditMode = wholeGroup
    }

    fun setSelectedBrightness(level: Int) {
        val clamped = level.coerceIn(0, 255)
        if (groupEditMode) {
            val next = GlyphZones.fillRange(
                source = studioDraft.frame,
                range = GlyphZones.clusterRange(selectedZone),
                level = clamped,
            )
            studioDraft.replace(next)
        } else {
            studioDraft.setLevel(selectedZone, clamped)
        }
        refreshDraftSnapshot()
    }

    fun syncSelectedCluster() {
        val level = studioSnapshot[selectedZone]
        val next = GlyphZones.fillRange(
            source = studioDraft.frame,
            range = GlyphZones.clusterRange(selectedZone),
            level = level,
        )
        studioDraft.replace(next)
        refreshDraftSnapshot()
    }

    fun fillAllZones(level: Int = 255) {
        studioDraft.replace(GlyphZones.presetAll(level))
        refreshDraftSnapshot()
    }

    fun invertDraft() {
        studioDraft.replace(GlyphZones.invert(studioDraft.frame))
        refreshDraftSnapshot()
    }

    fun dimDraftHalf() {
        studioDraft.replace(GlyphZones.scale(studioDraft.frame, 0.5f))
        refreshDraftSnapshot()
    }

    fun stagePresetInDraft(snapshot: GlyphSnapshot) {
        studioDraft.replace(snapshot.toIntArray())
        refreshDraftSnapshot()
    }

    fun revertDraft() {
        studioDraft.replace(appliedSnapshot.toIntArray())
        refreshDraftSnapshot()
    }

    fun applyDraft() {
        if (!hardwareReady || busy) return
        applying = true
        studioActionText = activity.getString(R.string.detail_applying)
        val draftSnap = studioDraft.snapshot()
        activity.lifecycleScope.launch {
            val success = withContext(Dispatchers.Default) {
                GlyphEffectCoordinator.applyManualFrame(appContext, draftSnap.frame)
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            applying = false
            if (success) {
                val applied = GlyphSnapshot.from(draftSnap.frame)
                appliedSnapshot = applied
                statusText = powerStatusText(applied)
                studioActionText = activity.getString(
                    if (studioDraft.matches(draftSnap)) R.string.detail_applied else R.string.detail_unsaved,
                )
                notifyToast(activity.getString(R.string.detail_applied), "checkmark-circle")
            } else {
                hardwareReady = false
                studioActionText = activity.getString(R.string.detail_apply_failed)
                notifyToast(studioActionText, "warning")
            }
        }
    }

    fun saveDraftToSlot(slot: Int) {
        val frameCopy = studioSnapshot.toIntArray()
        activity.lifecycleScope.launch {
            val (saved, updated) = withContext(Dispatchers.Default) {
                val ok = GlyphSceneStore.saveSlot(appContext, slot, frameCopy)
                ok to GlyphSceneStore.loadAllSnapshots(appContext)
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            savedSlots = updated
            if (saved) {
                notifyToast(activity.getString(R.string.detail_scene_saved, slot + 1), "bookmark")
            } else {
                notifyToast(activity.getString(R.string.detail_scene_save_failed), "warning")
            }
        }
    }

    fun loadSlotToDraft(slot: Int): Boolean {
        val loaded = GlyphSceneStore.loadSlot(activity, slot)
        if (loaded == null) {
            notifyToast(activity.getString(R.string.detail_scene_missing, slot + 1), "warning")
            return false
        }
        studioDraft.replace(loaded)
        refreshDraftSnapshot()
        notifyToast(activity.getString(R.string.detail_scene_loaded, slot + 1), "checkmark-circle")
        return true
    }

    fun applySlotDirectly(slot: Int) {
        val loaded = GlyphSceneStore.loadSlot(activity, slot)
        if (loaded == null) {
            notifyToast(activity.getString(R.string.detail_scene_missing, slot + 1), "warning")
            return
        }
        applyQuickPreset(GlyphSnapshot.from(loaded))
    }

    fun clearSlot(slot: Int) {
        activity.lifecycleScope.launch {
            val (cleared, updated) = withContext(Dispatchers.Default) {
                val ok = GlyphSceneStore.clearSlot(appContext, slot)
                ok to GlyphSceneStore.loadAllSnapshots(appContext)
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            savedSlots = updated
            if (cleared) {
                notifyToast(activity.getString(R.string.detail_scene_cleared, slot + 1), "trash")
            }
        }
    }

    fun setNotificationFlash(enabled: Boolean) {
        notifyEnabled = enabled
        settingsPrefs.edit { putBoolean("notify_enabled", enabled) }
        if (enabled) GlyphRuntime.ensure(appContext)
        listenerStatus = GlyphListenerState.status(activity)
    }

    fun setPauseInDnd(enabled: Boolean) {
        pauseNotificationDuringDnd = enabled
        settingsPrefs.edit { putBoolean(GlyphDoNotDisturb.PAUSE_NOTIFICATION_SETTING, enabled) }
    }

    fun setChargingSweep(enabled: Boolean) {
        chargerEnabled = enabled
        settingsPrefs.edit { putBoolean("charger_enabled", enabled) }
    }

    fun setVolumeRing(enabled: Boolean) {
        volumeEnabled = enabled
        settingsPrefs.edit { putBoolean("volume_enabled", enabled) }
        if (enabled) {
            GlyphVolumeCoordinator.start(appContext)
        } else {
            GlyphVolumeCoordinator.stop(appContext)
        }
    }

    fun openNotificationAccessSettings() {
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun testNotificationFlash() {
        runEffectPreview { GlyphEffects.playNotificationFlash(appContext) }
    }

    fun testChargingSweep() {
        runEffectPreview { GlyphEffects.playChargingSweep(appContext) }
    }

    private fun runEffectPreview(block: suspend () -> Boolean) {
        if (!hardwareReady || previewingEffect) {
            notifyToast(activity.getString(R.string.effect_test_busy), "warning")
            return
        }
        previewingEffect = true
        notifyToast(activity.getString(R.string.effect_test_started), "flash")
        activity.lifecycleScope.launch(Dispatchers.Default) {
            try {
                block()
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    previewingEffect = false
                }
            }
        }
    }

    fun refreshKeepAliveStatus() {
        if (keepAliveBusy) return
        activity.lifecycleScope.launch {
            val status = withContext(Dispatchers.Default) { RootKeepAlive.status() }
            if (!activity.isDestroyed && !activity.isFinishing) {
                keepAliveStatus = status
            }
        }
    }

    fun installOrRestartKeepAlive() {
        if (keepAliveBusy) return
        keepAliveBusy = true
        activity.lifecycleScope.launch {
            val (installed, status) = withContext(Dispatchers.Default) {
                val ok = RootKeepAlive.install(appContext)
                ok to RootKeepAlive.status()
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            keepAliveBusy = false
            keepAliveStatus = status
            notifyToast(
                activity.getString(
                    if (installed) R.string.keepalive_install_success else R.string.keepalive_install_failed,
                ),
                if (installed) "shield-checkmark" else "warning",
            )
        }
    }

    fun confirmRemoveKeepAlive() {
        if (keepAliveBusy) return
        keepAliveBusy = true
        activity.lifecycleScope.launch {
            val (removed, status) = withContext(Dispatchers.Default) {
                val ok = RootKeepAlive.remove(appContext)
                ok to RootKeepAlive.status()
            }
            if (activity.isDestroyed || activity.isFinishing) return@launch
            keepAliveBusy = false
            keepAliveStatus = status
            notifyToast(
                activity.getString(
                    if (removed) R.string.keepalive_remove_success else R.string.keepalive_remove_failed,
                ),
                if (removed) "trash" else "warning",
            )
        }
    }

    fun updateColorMode(next: GlyphColorMode) {
        colorMode = next
        GlyphAppearancePrefs.setColorMode(activity, next)
    }

    fun updateUseMonet(enabled: Boolean) {
        useMonet = enabled
        GlyphAppearancePrefs.setUseMonet(activity, enabled)
    }

    fun updateStyleVariant(variant: GlyphStyleVariant) {
        styleVariant = variant
        GlyphAppearancePrefs.setStyleVariant(activity, variant)
    }

    fun updateSeedColor(color: Color) {
        val opaque = color.copy(alpha = 1f)
        seedColor = opaque
        useMonet = false
        GlyphAppearancePrefs.setSeedColor(activity, opaque)
        GlyphAppearancePrefs.setUseMonet(activity, false)
    }

    fun updateSerifTitles(enabled: Boolean) {
        serifTitles = enabled
        GlyphAppearancePrefs.setSerifTitles(activity, enabled)
    }

    companion object {
        private const val KEY_UNSUPPORTED_SHOWN = "unsupported_shown"
        private const val KEY_DRAFT_FRAME = "studio_draft_frame"
        private const val KEY_DRAFT_REVISION = "studio_draft_revision"
        private const val KEY_SELECTED_ZONE = "studio_selected_zone"
        private const val KEY_GROUP_EDIT_MODE = "studio_group_edit_mode"
    }
}
