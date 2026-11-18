package com.richard.glyphcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonGroup
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiLinearGauge
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiToastState
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.crossfadeOrChange
import ui.feice.mochi.effect.content.revealIfShadeChanges
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.icons.MochiIonicons
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.mochiEnableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var controller: GlyphWorkbenchController

    override fun onCreate(savedInstanceState: Bundle?) {
        mochiEnableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            MochiIonicons.preload(applicationContext)
        }

        controller = GlyphWorkbenchController(this, savedInstanceState)

        setContent {
            GlyphAppTheme(
                colorMode = controller.colorMode,
                useMonet = controller.useMonet,
                styleVariant = controller.styleVariant,
                seedColor = controller.seedColor,
                serifTitles = controller.serifTitles,
            ) {
                MainWorkbenchRoot(controller = controller)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        controller.onWindowFocusChanged(hasFocus)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        controller.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}

private const val ROUTE_ROOT = "root"
private const val ROUTE_SCENES = "scenes"

@Composable
private fun MainWorkbenchRoot(controller: GlyphWorkbenchController) {
    val nav = rememberMochiNavigator(ROUTE_ROOT)
    val tabState = rememberMochiTabScaffoldState()
    val toastState = rememberMochiToastState()

    DisposableEffect(controller, toastState) {
        controller.onFeedback = { title, iconName ->
            toastState.show(
                title = title,
                icon = iconName?.let { captured ->
                    { tint -> MochiIonicon(captured, tint = tint, size = 20.dp) }
                },
            )
        }
        onDispose {
            controller.onFeedback = null
        }
    }

    BackHandler(enabled = !nav.canPop && tabState.selectedIndex != 0) {
        tabState.select(0)
    }

    val overviewLabel = stringResource(R.string.tab_overview)
    val studioLabel = stringResource(R.string.tab_studio)
    val settingsLabel = stringResource(R.string.tab_settings)
    val vaultLabel = stringResource(R.string.vault_title)
    val studioBadge = if (controller.isDraftDirty) "" else null

    val tabItems = remember(overviewLabel, studioLabel, settingsLabel, studioBadge) {
        persistentListOf(
            MochiTabItem(
                label = overviewLabel,
                selectedIcon = { tint -> MochiIonicon("radio-button-on", style = MochiIconStyle.Filled, tint = tint) },
                icon = { tint -> MochiIonicon("radio-button-on", style = MochiIconStyle.Outline, tint = tint) },
            ),
            MochiTabItem(
                label = studioLabel,
                badge = studioBadge,
                selectedIcon = { tint -> MochiIonicon("options", style = MochiIconStyle.Filled, tint = tint) },
                icon = { tint -> MochiIonicon("options", style = MochiIconStyle.Outline, tint = tint) },
            ),
            MochiTabItem(
                label = settingsLabel,
                selectedIcon = { tint -> MochiIonicon("settings", style = MochiIconStyle.Filled, tint = tint) },
                icon = { tint -> MochiIonicon("settings", style = MochiIconStyle.Outline, tint = tint) },
            ),
        )
    }

    val routeTitle: (String) -> String = { route ->
        when (route) {
            ROUTE_SCENES -> vaultLabel
            else -> when (tabState.selectedIndex) {
                1 -> studioLabel
                2 -> settingsLabel
                else -> overviewLabel
            }
        }
    }

    MochiNavHost(
        navigator = nav,
        modifier = Modifier.fillMaxSize(),
    ) { route ->
        when (route) {
            ROUTE_ROOT -> MochiTabScaffold(
                items = tabItems,
                state = tabState,
            ) { page ->
                when (page) {
                    0 -> OverviewPage(
                        controller = controller,
                        onOpenStudio = { tabState.select(1) },
                    )

                    1 -> GlyphStudioPage(
                        controller = controller,
                        onOpenScenesFromBar = { nav.push(ROUTE_SCENES) },
                        onOpenScenesFromCard = { nav.push(ROUTE_SCENES, from = GlyphNavIds.ScenesCardSource) },
                    )

                    else -> SettingsPage(controller = controller)
                }
            }

            ROUTE_SCENES -> GlyphSceneVaultPage(
                controller = controller,
                routeTitle = routeTitle,
                onLoadedIntoStudio = {
                    nav.pop()
                    tabState.select(1)
                },
            )
        }
    }

    GlyphWorkbenchDialogs(controller = controller)
    MochiToastHost(state = toastState)
}

@Composable
private fun OverviewPage(
    controller: GlyphWorkbenchController,
    onOpenStudio: () -> Unit,
) {
    val applied = controller.appliedSnapshot
    val telemetry = remember(applied) { GlyphZones.telemetry(applied) }
    val cs = MochiTheme.colorScheme
    val podShape = remember { MochiRoundedRectangle(20.dp) }

    MochiLazyCompactTitleScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            MochiBarIconButton(
                onClick = controller::checkHardware,
                enabled = !controller.busy,
                navId = "glyph.refresh",
                contentKey = "refresh",
            ) {
                MochiIonicon(
                    name = "refresh",
                    contentDescription = stringResource(R.string.btn_retry_hardware),
                )
            }
        },
        onRefresh = controller::refreshHardware,
    ) {
        item(key = "overview_hero") {
            MochiListCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .mochiListDivider(MochiListDefaults.RowPadding)
                        .padding(MochiListDefaults.RowPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MochiIonicon(
                                name = "radio-button-on",
                                tint = cs.primary,
                                size = 18.dp,
                            )
                            Text(
                                text = stringResource(R.string.home_device_model),
                                style = MochiTheme.textStyles.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                                maxLines = 1,
                            )
                        }
                        val (badgeLabel, badgeTone) = when {
                            controller.checking -> stringResource(R.string.home_device_checking) to GlyphStatusTone.Neutral
                            controller.hardwareReady -> stringResource(R.string.home_device_ready) to GlyphStatusTone.Active
                            else -> stringResource(R.string.home_device_unavailable) to GlyphStatusTone.Warning
                        }
                        GlyphStatusBadge(text = badgeLabel, tone = badgeTone)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 112.dp, height = 192.dp)
                                .clip(podShape)
                                .background(Color(0xFF101216), podShape)
                                .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), podShape)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphVectorPreview(
                                snapshot = applied,
                                modifier = Modifier.fillMaxSize(),
                                onTapZone = { zone ->
                                    controller.selectZone(zone)
                                    onOpenStudio()
                                },
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.home_active_zones),
                                    style = MochiTheme.textStyles.footnote,
                                    color = cs.onSurfaceVariant,
                                )
                                GlyphDotCounter(
                                    value = telemetry.activeZones.toLong(),
                                    style = MochiTheme.textStyles.headlineMedium,
                                    color = cs.onSurface,
                                    format = { "$it / 33" },
                                )
                                MochiBlurText(
                                    text = controller.statusText,
                                    style = MochiTheme.textStyles.subhead,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            MochiLinearGauge(
                                value = telemetry.meanPercent.toFloat(),
                                range = 0f..100f,
                                label = stringResource(R.string.home_mean_output),
                                valueSuffix = "%",
                            )
                        }
                    }

                    MochiButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        spacing = 8.dp,
                    ) {
                        if (controller.hardwareReady) {
                            MochiButton(
                                onClick = controller::toggleAllPower,
                                enabled = !controller.busy,
                                modifier = Modifier.weight(1f),
                                style = MochiButtonStyle.Filled,
                            ) {
                                MochiIonicon(
                                    name = if (applied.anyLit) "moon" else "sunny",
                                    size = 18.dp,
                                )
                                Text(
                                    text = stringResource(
                                        if (applied.anyLit) R.string.btn_turn_off else R.string.btn_turn_on,
                                    ),
                                    style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                )
                            }
                        } else {
                            MochiButton(
                                onClick = controller::checkHardware,
                                enabled = !controller.busy,
                                modifier = Modifier.weight(1f),
                                style = MochiButtonStyle.Filled,
                            ) {
                                MochiIonicon("refresh", size = 18.dp)
                                Text(
                                    text = stringResource(R.string.btn_retry_hardware),
                                    style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                )
                            }
                        }

                        MochiButton(
                            onClick = onOpenStudio,
                            modifier = Modifier.weight(1f),
                            style = MochiButtonStyle.Tonal,
                        ) {
                            MochiIonicon("options", size = 18.dp)
                            Text(
                                text = stringResource(R.string.home_studio_open),
                                style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        item(key = "overview_effects") {
            MochiListSection(
                header = stringResource(R.string.home_effects_section),
                footer = stringResource(R.string.home_effects_footer),
            ) {
                MochiListRow(
                    title = stringResource(R.string.sw_notify),
                    subtitle = stringResource(R.string.notify_hint),
                    leading = { GlyphSymbolLeading("notifications") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        MochiSwitch(
                            checked = controller.notifyEnabled,
                            onCheckedChange = controller::setNotificationFlash,
                        )
                    },
                )

                if (controller.notifyEnabled && controller.listenerStatus == GlyphListenerState.Status.NEEDS_ACCESS) {
                    MochiListRow(
                        title = stringResource(R.string.home_access_title),
                        status = {
                            GlyphStatusBadge(
                                text = stringResource(R.string.notify_need_listener),
                                tone = GlyphStatusTone.Warning,
                            )
                        },
                        chevron = true,
                        onClick = controller::openNotificationAccessSettings,
                    )
                }

                if (controller.notifyEnabled) {
                    MochiListRow(
                        title = stringResource(R.string.sw_notify_pause_dnd),
                        subtitle = stringResource(R.string.notify_pause_dnd_hint),
                        trailing = {
                            MochiSwitch(
                                checked = controller.pauseNotificationDuringDnd,
                                onCheckedChange = controller::setPauseInDnd,
                            )
                        },
                    )
                }

                MochiListRow(
                    title = stringResource(R.string.sw_charger),
                    subtitle = stringResource(R.string.charger_hint),
                    leading = { GlyphSymbolLeading("flash") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        MochiSwitch(
                            checked = controller.chargerEnabled,
                            onCheckedChange = controller::setChargingSweep,
                        )
                    },
                )

                MochiListRow(
                    title = stringResource(R.string.sw_volume),
                    subtitle = stringResource(R.string.volume_hint),
                    leading = { GlyphSymbolLeading("volume-high") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        MochiSwitch(
                            checked = controller.volumeEnabled,
                            onCheckedChange = controller::setVolumeRing,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(controller: GlyphWorkbenchController) {
    val revealState = LocalGlyphThemeReveal.current
    val systemDark = isSystemInDarkTheme()
    val cs = MochiTheme.colorScheme

    val colorOptions = persistentListOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.theme_mode_light),
        stringResource(R.string.theme_mode_dark),
    )
    val styleOptions = persistentListOf(
        stringResource(R.string.palette_style_tonal),
        stringResource(R.string.palette_style_expressive),
        stringResource(R.string.palette_style_vibrant),
        stringResource(R.string.palette_style_monochrome),
    )

    MochiLazyCompactTitleScaffold(
        title = stringResource(R.string.tab_settings),
        onRefresh = controller::refreshHardware,
    ) {
        item(key = "settings_runtime_status") {
            MochiListSection(
                header = stringResource(R.string.home_runtime_section),
            ) {
                val (hwBadge, hwTone) = when {
                    controller.checking -> stringResource(R.string.home_device_checking) to GlyphStatusTone.Neutral
                    controller.hardwareReady -> stringResource(R.string.home_device_ready) to GlyphStatusTone.Active
                    else -> stringResource(R.string.home_device_unavailable) to GlyphStatusTone.Warning
                }
                MochiListRow(
                    title = stringResource(R.string.runtime_hardware_title),
                    subtitle = controller.studioHardwareText,
                    leading = { GlyphSymbolLeading("hardware-chip") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    loading = controller.checking,
                    status = { GlyphStatusBadge(text = hwBadge, tone = hwTone) },
                    onClick = controller::checkHardware,
                )

                val (notifyLabel, notifyTone) = resolveNotificationStatus(controller)
                MochiListRow(
                    title = stringResource(R.string.home_access_title),
                    leading = { GlyphSymbolLeading("notifications") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    status = { GlyphStatusBadge(text = notifyLabel, tone = notifyTone) },
                    chevron = true,
                    onClick = controller::openNotificationAccessSettings,
                )

                val (watchdogLabel, watchdogTone) = resolveWatchdogStatus(controller)
                MochiListRow(
                    title = stringResource(R.string.keepalive_title),
                    leading = { GlyphSymbolLeading("shield-checkmark") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    loading = controller.keepAliveBusy,
                    status = { GlyphStatusBadge(text = watchdogLabel, tone = watchdogTone) },
                )
            }
        }

        item(key = "settings_appearance") {
            MochiListSection(
                header = stringResource(R.string.appearance_section),
                footer = stringResource(R.string.theme_seed_footer),
            ) {
                MochiListPickerRow(
                    title = stringResource(R.string.theme_mode_label),
                    options = colorOptions,
                    selectedIndex = controller.colorMode.ordinal,
                    leading = { GlyphSymbolLeading("contrast") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    onSelect = { index ->
                        val nextMode = GlyphColorMode.entries[index]
                        val fromDark = controller.colorMode.resolveDark(systemDark)
                        val toDark = nextMode.resolveDark(systemDark)
                        revealState.revealIfShadeChanges(fromDark = fromDark, toDark = toDark) {
                            controller.updateColorMode(nextMode)
                        }
                    },
                )

                MochiListRow(
                    title = stringResource(R.string.monet_dynamic_label),
                    subtitle = stringResource(R.string.monet_dynamic_hint),
                    leading = { GlyphSymbolLeading("color-palette") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        MochiSwitch(
                            checked = controller.useMonet,
                            onCheckedChange = controller::updateUseMonet,
                        )
                    },
                )

                MochiListPickerRow(
                    title = stringResource(R.string.palette_style_label),
                    options = styleOptions,
                    selectedIndex = controller.styleVariant.ordinal,
                    leading = { GlyphSymbolLeading("layers") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    onSelect = { index ->
                        val nextVariant = GlyphStyleVariant.entries[index]
                        val togglingMono = (controller.styleVariant == GlyphStyleVariant.MONOCHROME) !=
                            (nextVariant == GlyphStyleVariant.MONOCHROME)
                        if (togglingMono) {
                            revealState.crossfadeOrChange { controller.updateStyleVariant(nextVariant) }
                        } else {
                            controller.updateStyleVariant(nextVariant)
                        }
                    },
                )

                MochiListRow(
                    title = stringResource(R.string.serif_titles_label),
                    subtitle = stringResource(R.string.serif_titles_hint),
                    leading = { GlyphSymbolLeading("text") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        MochiSwitch(
                            checked = controller.serifTitles,
                            onCheckedChange = { enabled ->
                                revealState.crossfadeOrChange {
                                    controller.updateSerifTitles(enabled)
                                }
                            },
                        )
                    },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .mochiListDivider(MochiListDefaults.RowPadding)
                        .padding(horizontal = MochiListDefaults.RowPadding, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.theme_seed_title),
                            style = MochiTheme.textStyles.body,
                            color = cs.onSurface,
                        )
                        if (controller.useMonet) {
                            GlyphStatusBadge(
                                text = stringResource(R.string.theme_seed_monet_active),
                                tone = GlyphStatusTone.Active,
                            )
                        } else {
                            val hex = remember(controller.seedColor) {
                                String.format(Locale.US, "#%06X", controller.seedColor.toArgb() and 0xFFFFFF)
                            }
                            GlyphDotCode(
                                code = hex,
                                style = MochiTheme.textStyles.labelMedium,
                                color = cs.primary,
                            )
                        }
                    }

                    MochiSeedPalette(
                        selected = if (controller.useMonet) null else controller.seedColor,
                        onSelect = controller::updateSeedColor,
                        seeds = GlyphSeedSwatches,
                        style = controller.styleVariant.toMochiPaletteStyle(),
                        custom = true,
                        swatchSize = 46.dp,
                    )
                }
            }
        }

        item(key = "settings_hardware_test") {
            MochiListSection(
                header = stringResource(R.string.effect_preview_section),
                footer = stringResource(R.string.effect_preview_footer),
            ) {
                MochiListButton(
                    text = stringResource(R.string.effect_test_notify),
                    onClick = controller::testNotificationFlash,
                )
                MochiListButton(
                    text = stringResource(R.string.effect_test_charger),
                    onClick = controller::testChargingSweep,
                )
            }
        }

        item(key = "settings_watchdog") {
            MochiListSection(
                header = stringResource(R.string.keepalive_section),
                footer = stringResource(R.string.keepalive_hint),
            ) {
                MochiListButton(
                    text = stringResource(R.string.keepalive_install),
                    onClick = controller::installOrRestartKeepAlive,
                )
                MochiListButton(
                    text = stringResource(R.string.keepalive_remove),
                    destructive = true,
                    onClick = { controller.showRemoveKeepAliveDialog = true },
                )
            }
        }
    }
}

@Composable
internal fun GlyphWorkbenchDialogs(controller: GlyphWorkbenchController) {
    val dismissLabel = stringResource(R.string.dialog_dismiss)
    val unsupportedActions = remember(dismissLabel) {
        persistentListOf(
            MochiDialogAction(
                label = dismissLabel,
                role = MochiDialogActionRole.Primary,
            ),
        )
    }
    MochiDialog(
        visible = controller.showUnsupportedDialog,
        onDismissRequest = { controller.showUnsupportedDialog = false },
        title = stringResource(R.string.glyph_unsupported_title),
        message = stringResource(R.string.glyph_unsupported_message),
        icon = {
            MochiIonicon(
                name = "warning",
                tint = MochiTheme.colorScheme.error,
                size = 44.dp,
            )
        },
        actions = unsupportedActions,
    )

    val guideLaterLabel = stringResource(R.string.notify_access_guide_later)
    val guideConfirmLabel = stringResource(R.string.notify_access_guide_confirm)
    val guideActions = remember(guideLaterLabel, guideConfirmLabel, controller) {
        persistentListOf(
            MochiDialogAction(
                label = guideLaterLabel,
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = guideConfirmLabel,
                role = MochiDialogActionRole.Primary,
                onClick = controller::openNotificationAccessSettings,
            ),
        )
    }
    MochiDialog(
        visible = controller.showNotifyAccessGuideDialog,
        onDismissRequest = { controller.showNotifyAccessGuideDialog = false },
        title = stringResource(R.string.notify_access_guide_title),
        message = stringResource(R.string.notify_access_guide_message),
        icon = {
            MochiIonicon(
                name = "notifications",
                tint = MochiTheme.colorScheme.primary,
                size = 44.dp,
            )
        },
        actions = guideActions,
    )

    val cancelLabel = stringResource(R.string.keepalive_cancel)
    val removeLabel = stringResource(R.string.keepalive_remove)
    val removeActions = remember(cancelLabel, removeLabel, controller) {
        persistentListOf(
            MochiDialogAction(
                label = cancelLabel,
                role = MochiDialogActionRole.Cancel,
            ),
            MochiDialogAction(
                label = removeLabel,
                role = MochiDialogActionRole.Destructive,
                onClick = controller::confirmRemoveKeepAlive,
            ),
        )
    }
    MochiDialog(
        visible = controller.showRemoveKeepAliveDialog,
        onDismissRequest = { controller.showRemoveKeepAliveDialog = false },
        title = stringResource(R.string.keepalive_remove_confirm_title),
        message = stringResource(R.string.keepalive_remove_confirm_message),
        icon = {
            MochiIonicon(
                name = "warning",
                tint = MochiTheme.colorScheme.error,
                size = 44.dp,
            )
        },
        actions = removeActions,
    )
}

@Composable
private fun resolveNotificationStatus(controller: GlyphWorkbenchController): Pair<String, GlyphStatusTone> {
    if (!controller.notifyEnabled) {
        return stringResource(R.string.notify_off) to GlyphStatusTone.Neutral
    }
    if (controller.pauseNotificationDuringDnd && controller.doNotDisturb) {
        return stringResource(R.string.notify_paused_dnd) to GlyphStatusTone.Ready
    }
    return when (controller.listenerStatus) {
        GlyphListenerState.Status.OFF -> stringResource(R.string.notify_off) to GlyphStatusTone.Neutral
        GlyphListenerState.Status.CONNECTED -> stringResource(R.string.notify_ready) to GlyphStatusTone.Active
        GlyphListenerState.Status.NEEDS_ACCESS -> stringResource(R.string.notify_need_listener) to GlyphStatusTone.Warning
        GlyphListenerState.Status.RECOVERING -> stringResource(R.string.notify_recovering) to GlyphStatusTone.Ready
        GlyphListenerState.Status.FAILED -> stringResource(R.string.notify_disconnected) to GlyphStatusTone.Warning
    }
}

@Composable
private fun resolveWatchdogStatus(controller: GlyphWorkbenchController): Pair<String, GlyphStatusTone> {
    if (controller.keepAliveBusy) {
        return stringResource(R.string.keepalive_working) to GlyphStatusTone.Ready
    }
    return when (controller.keepAliveStatus) {
        RootKeepAlive.Status.RUNNING -> stringResource(R.string.keepalive_running) to GlyphStatusTone.Active
        RootKeepAlive.Status.INSTALLED -> stringResource(R.string.keepalive_installed) to GlyphStatusTone.Ready
        RootKeepAlive.Status.NOT_INSTALLED -> stringResource(R.string.keepalive_not_installed) to GlyphStatusTone.Neutral
        RootKeepAlive.Status.UNKNOWN -> stringResource(R.string.keepalive_unknown) to GlyphStatusTone.Warning
    }
}
