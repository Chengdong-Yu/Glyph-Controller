package com.richard.glyphcontroller

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiBarGroup
import ui.feice.mochi.basic.MochiBarGroupItem
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonGroup
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSelectFrame
import ui.feice.mochi.basic.MochiSelectionCheck
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiBlurVisibility
import ui.feice.mochi.effect.mask.mochiScrollFadeMask
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.rememberMochiHaptics

private val ZonePillShape = RoundedCornerShape(14.dp)
private val SliderDetents = listOf(64f, 128f, 192f)

/**
 * 33-zone Nothing Phone (2) Studio page hosting the interactive backplate editor,
 * Quick Presets gallery, and 4-slot Saved Scenes card.
 *
 * State reads are strictly partitioned into leaf composables so continuous 120Hz brightness
 * slider dragging never recomposes the scaffold, top bar, preset thumbnails, or non-selected zone pills.
 */
@Composable
fun GlyphStudioPage(
    controller: GlyphWorkbenchController,
    onOpenScenesFromBar: () -> Unit,
    onOpenScenesFromCard: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    MochiLazyCompactTitleScaffold(
        title = stringResource(R.string.detail_title),
        subtitle = controller.studioHardwareText,
        listState = listState,
        navigationIcon = navigationIcon,
        actions = {
            StudioTopBarActions(
                controller = controller,
                onOpenScenesFromBar = onOpenScenesFromBar,
            )
        },
        onRefresh = controller::refreshHardware,
    ) {
        item(key = "studio_backplate_stage") {
            StudioBackplateStageSection(controller = controller)
        }

        item(key = "studio_zone_editor") {
            StudioZoneEditorSection(controller = controller)
        }

        item(key = "studio_quick_presets") {
            StudioQuickPresetsSection(controller = controller)
        }

        item(key = "studio_saved_scenes_card") {
            StudioSavedScenesSection(
                controller = controller,
                onOpenScenesFromCard = onOpenScenesFromCard,
            )
        }
    }
}

@Composable
private fun RowScope.StudioTopBarActions(
    controller: GlyphWorkbenchController,
    onOpenScenesFromBar: () -> Unit,
) {
    MochiBarGroup(navId = GlyphNavIds.StudioToolsGroup) {
        MochiBarGroupItem(
            onClick = controller::revertDraft,
            enabled = controller.isDraftDirty && !controller.busy,
            navId = GlyphNavIds.RevertIcon,
            contentKey = GlyphBarIconKey.Revert,
        ) {
            MochiIonicon(
                name = "arrow-undo",
                contentDescription = stringResource(R.string.detail_revert_short),
            )
        }
        MochiBarGroupItem(
            onClick = onOpenScenesFromBar,
            navId = GlyphNavIds.ScenesIcon,
            contentKey = GlyphBarIconKey.Scenes,
        ) {
            MochiIonicon(
                name = "bookmark",
                contentDescription = stringResource(R.string.vault_title),
            )
        }
    }
    Spacer(Modifier.width(8.dp))
    MochiBarButton(
        text = stringResource(R.string.detail_apply_short),
        onClick = controller::applyDraft,
        enabled = controller.hardwareReady && !controller.busy,
        navId = GlyphNavIds.ApplyButton,
    )
}

@Composable
private fun StudioBackplateStageSection(controller: GlyphWorkbenchController) {
    val scopeItems = persistentListOf(
        stringResource(R.string.detail_scope_single),
        stringResource(R.string.detail_scope_group),
    )

    MochiListSection(
        footer = stringResource(R.string.detail_preview_hint),
        animateHeight = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .mochiListDivider(MochiListDefaults.RowPadding)
                .padding(MochiListDefaults.RowPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlyphPhonePreview(
                snapshotProvider = { controller.studioSnapshot },
                selectedProvider = { controller.selectedZone },
                highlightedZonesProvider = {
                    if (controller.groupEditMode) {
                        GlyphZones.clusterRange(controller.selectedZone)
                    } else {
                        null
                    }
                },
                onSelect = controller::selectZone,
            )

            SelectedZoneSummaryRow(controller = controller)

            MochiSegmentedControl(
                selectedIndex = if (controller.groupEditMode) 1 else 0,
                onSelect = { index -> controller.setEditScope(index == 1) },
                items = scopeItems,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SelectedZoneSummaryRow(controller: GlyphWorkbenchController) {
    val selectedZone = controller.selectedZone
    val cs = MochiTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphDotCode(
                    code = GlyphZones.name(selectedZone),
                    style = MochiTheme.textStyles.headlineSmall,
                    color = cs.primary,
                )
                GlyphDotCode(
                    code = "LED ${GlyphZones.ledIds[selectedZone]}",
                    style = MochiTheme.textStyles.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(GlyphZones.groupName(selectedZone)),
                style = MochiTheme.textStyles.subhead,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SelectedZoneLevelReadout(controller = controller)
    }
}

@Composable
private fun SelectedZoneLevelReadout(controller: GlyphWorkbenchController) {
    val selectedLevel = controller.studioSnapshot[controller.selectedZone]
    GlyphDotCode(
        code = "$selectedLevel / 255",
        style = MochiTheme.textStyles.headlineSmall,
        color = MochiTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StudioZoneEditorSection(controller: GlyphWorkbenchController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZoneGroupFilterBar(controller = controller)

        MochiListSection(
            header = stringResource(R.string.detail_editor_section),
            footer = controller.studioActionText,
            animateHeight = false,
        ) {
            ZoneStripSelectorRow(controller = controller)
            ZoneBrightnessSliderRow(controller = controller)
            FrameBatchOperationsRow(
                onSyncGroup = controller::syncSelectedCluster,
                onFillAll = { controller.fillAllZones(255) },
                onInvert = controller::invertDraft,
                onDimHalf = controller::dimDraftHalf,
            )
            StudioApplyActionsBlock(controller = controller)
        }
    }
}

@Composable
private fun ZoneGroupFilterBar(controller: GlyphWorkbenchController) {
    val activeFilter = controller.activeFilter
    MochiChipGroup(
        contentPadding = PaddingValues(0.dp),
        bleed = 16.dp,
    ) {
        GlyphZoneGroup.entries.forEach { group ->
            MochiChip(
                label = stringResource(group.labelRes),
                selected = activeFilter == group,
                onClick = { controller.selectFilter(group) },
            )
        }
    }
}

@Composable
private fun ZoneBrightnessSliderRow(controller: GlyphWorkbenchController) {
    val selectedLevel = controller.studioSnapshot[controller.selectedZone]
    MochiListSliderRow(
        value = selectedLevel.toFloat(),
        onValueChange = { raw -> controller.setSelectedBrightness(raw.roundToInt()) },
        valueRange = 0f..255f,
        detents = SliderDetents,
        title = stringResource(R.string.detail_brightness_title),
        valueText = "$selectedLevel / 255",
        leading = { MochiIonicon("moon", size = 18.dp) },
        trailing = { MochiIonicon("sunny", size = 20.dp) },
    )
}

@Composable
private fun StudioApplyActionsBlock(controller: GlyphWorkbenchController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mochiListDivider(MochiListDefaults.RowPadding)
            .padding(MochiListDefaults.RowPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MochiButton(
            onClick = controller::applyDraft,
            enabled = controller.hardwareReady && !controller.busy,
            modifier = Modifier.fillMaxWidth(),
            style = MochiButtonStyle.Filled,
        ) {
            MochiIonicon("flash", size = 18.dp)
            Text(
                text = stringResource(R.string.detail_apply),
                style = MochiTheme.textStyles.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }

        MochiBlurVisibility(
            visible = controller.isDraftDirty,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MochiButton(
                onClick = controller::revertDraft,
                enabled = !controller.busy,
                modifier = Modifier.fillMaxWidth(),
                style = MochiButtonStyle.Tonal,
            ) {
                MochiIonicon("arrow-undo", size = 18.dp)
                Text(
                    text = stringResource(R.string.detail_reset_draft),
                    style = MochiTheme.textStyles.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }

    if (!controller.hardwareReady) {
        MochiListButton(
            text = stringResource(R.string.btn_retry_hardware),
            onClick = controller::checkHardware,
        )
    }
}

@Composable
private fun StudioQuickPresetsSection(controller: GlyphWorkbenchController) {
    MochiListSection(
        header = stringResource(R.string.home_presets_section),
    ) {
        val presetScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .mochiListDivider(MochiListDefaults.RowPadding)
                .mochiScrollFadeMask(presetScroll, vertical = false)
                .horizontalScroll(presetScroll, flingBehavior = MochiScrollDefaults.flingBehavior())
                .padding(horizontal = MochiListDefaults.RowPadding, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphCuratedPresets.forEach { preset ->
                PresetGalleryCard(
                    controller = controller,
                    preset = preset,
                )
            }
        }
    }
}

@Composable
private fun PresetGalleryCard(
    controller: GlyphWorkbenchController,
    preset: GlyphPresetSpec,
) {
    val isSelected by remember(controller, preset) {
        derivedStateOf { controller.studioSnapshot == preset.snapshot }
    }
    val cs = MochiTheme.colorScheme

    Column(
        modifier = Modifier.width(82.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MochiSelectFrame(
            selected = isSelected,
            onClick = { controller.applyQuickPreset(preset.snapshot) },
            modifier = Modifier
                .width(82.dp)
                .height(132.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101216))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlyphVectorPreview(
                    snapshot = preset.snapshot,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = stringResource(preset.labelRes),
            style = MochiTheme.textStyles.footnote,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MochiSelectionCheck(selected = isSelected)
    }
}

@Composable
private fun StudioSavedScenesSection(
    controller: GlyphWorkbenchController,
    onOpenScenesFromCard: () -> Unit,
) {
    val savedSlots = controller.savedSlots
    val savedCount = remember(savedSlots) { savedSlots.count { it != null } }
    val cs = MochiTheme.colorScheme
    val slotShape = remember { MochiRoundedRectangle(14.dp) }
    val miniPreviewShape = remember { MochiRoundedRectangle(10.dp) }

    Column(verticalArrangement = Arrangement.spacedBy(MochiListDefaults.HeaderGap)) {
        MochiListSectionHeader(text = stringResource(R.string.home_scenes_section))
        MochiListCard(
            modifier = Modifier.mochiNavSource(
                key = GlyphNavIds.ScenesCardSource,
                cornerRadius = MochiListDefaults.SectionCorner,
                color = MochiDefaults.groupedContainer(),
            ),
        ) {
            MochiListRow(
                title = stringResource(R.string.home_scenes_title),
                subtitle = stringResource(R.string.home_scenes_summary),
                value = stringResource(R.string.home_scenes_count, savedCount),
                leading = { GlyphSymbolLeading("bookmark") },
                leadingStyle = MochiListLeadingStyle.Symbol,
                chevron = true,
                onClick = onOpenScenesFromCard,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenScenesFromCard)
                    .padding(horizontal = MochiListDefaults.RowPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (slot in 0 until GlyphSceneStore.SLOT_COUNT) {
                    val slotSnap = savedSlots.getOrNull(slot)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(cs.surfaceContainerHigh, slotShape)
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 66.dp)
                                .background(Color(0xFF101216), miniPreviewShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphVectorPreview(
                                snapshot = slotSnap ?: GlyphSnapshot.Zero,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        GlyphDotCode(
                            code = "S0${slot + 1}",
                            style = MochiTheme.textStyles.labelSmall,
                            color = if (slotSnap != null) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneStripSelectorRow(controller: GlyphWorkbenchController) {
    val indices = controller.activeFilter.indices
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .mochiListDivider(MochiListDefaults.RowPadding)
            .mochiScrollFadeMask(scrollState, vertical = false)
            .horizontalScroll(scrollState, flingBehavior = MochiScrollDefaults.flingBehavior())
            .padding(horizontal = MochiListDefaults.RowPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (zone in indices) {
            key(zone) {
                ZoneStripPill(
                    controller = controller,
                    zone = zone,
                )
            }
        }
    }
}

@Composable
private fun ZoneStripPill(
    controller: GlyphWorkbenchController,
    zone: Int,
) {
    val selected by remember(controller, zone) {
        derivedStateOf { controller.selectedZone == zone }
    }
    val level by remember(controller, zone) {
        derivedStateOf { controller.studioSnapshot[zone] }
    }
    val haptics = rememberMochiHaptics()
    val cs = MochiTheme.colorScheme
    val ratio = (level / 255f).coerceIn(0f, 1f)
    val borderColor = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.6f)
    val containerColor = if (selected) cs.secondaryContainer else cs.surfaceContainerHigh

    Column(
        modifier = Modifier
            .width(56.dp)
            .clip(ZonePillShape)
            .background(containerColor, ZonePillShape)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, ZonePillShape)
            .clickable(role = Role.RadioButton) {
                if (!selected) haptics.tick()
                controller.selectZone(zone)
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphDotCode(
            code = GlyphZones.name(zone),
            style = MochiTheme.textStyles.labelMedium,
            color = if (selected) cs.primary else cs.onSurface,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(cs.outlineVariant.copy(alpha = 0.45f), CircleShape),
        ) {
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceAtLeast(0.12f))
                        .height(4.dp)
                        .background(
                            if (selected) cs.primary else cs.onSurface,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FrameBatchOperationsRow(
    onSyncGroup: () -> Unit,
    onFillAll: () -> Unit,
    onInvert: () -> Unit,
    onDimHalf: () -> Unit,
) {
    val opsScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .mochiListDivider(MochiListDefaults.RowPadding)
            .mochiScrollFadeMask(opsScroll, vertical = false)
            .horizontalScroll(opsScroll, flingBehavior = MochiScrollDefaults.flingBehavior())
            .padding(horizontal = MochiListDefaults.RowPadding, vertical = 12.dp),
    ) {
        MochiButtonGroup(spacing = 8.dp) {
            BatchOpButton(label = stringResource(R.string.detail_fill_group), onClick = onSyncGroup)
            BatchOpButton(label = stringResource(R.string.detail_fill_all), onClick = onFillAll)
            BatchOpButton(label = stringResource(R.string.detail_invert_all), onClick = onInvert)
            BatchOpButton(label = stringResource(R.string.detail_dim_half), onClick = onDimHalf)
        }
    }
}

@Composable
private fun BatchOpButton(
    label: String,
    onClick: () -> Unit,
) {
    MochiButton(
        onClick = onClick,
        style = MochiButtonStyle.Tonal,
        minHeight = 38.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}
