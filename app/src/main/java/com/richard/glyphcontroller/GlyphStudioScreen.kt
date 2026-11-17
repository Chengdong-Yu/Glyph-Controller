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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.list.mochiListDivider
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.rememberMochiHaptics

/**
 * 33-zone Nothing Phone (2) Studio page built on Mochi's compact-title lazy scaffold.
 * Shared between the Studio tab in [MainActivity] and standalone [GlyphDetailActivity].
 */
@Composable
fun GlyphStudioPage(
    controller: GlyphWorkbenchController,
    onOpenScenes: () -> Unit,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val selectedZone = controller.selectedZone
    val selectedLevel = controller.studioSnapshot[selectedZone]
    val scopeItems = persistentListOf(
        stringResource(R.string.detail_scope_single),
        stringResource(R.string.detail_scope_group),
    )

    MochiLazyCompactTitleScaffold(
        title = stringResource(R.string.detail_title),
        subtitle = controller.studioHardwareText,
        navigationIcon = navigationIcon,
        actions = {
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
                    onClick = onOpenScenes,
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
        },
        onRefresh = controller::refreshHardware,
    ) {
        item(key = "studio_backplate_stage") {
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
                        snapshot = controller.studioSnapshot,
                        selected = selectedZone,
                        highlightedZones = if (controller.groupEditMode) {
                            GlyphZones.clusterRange(selectedZone)
                        } else {
                            null
                        },
                        onSelect = controller::selectZone,
                    )

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
                                    color = MochiTheme.colorScheme.primary,
                                )
                                GlyphDotCode(
                                    code = "LED ${GlyphZones.ledIds[selectedZone]}",
                                    style = MochiTheme.textStyles.labelMedium,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(GlyphZones.groupName(selectedZone)),
                                style = MochiTheme.textStyles.subhead,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        GlyphDotCounter(
                            value = selectedLevel.toLong(),
                            style = MochiTheme.textStyles.headlineSmall,
                            color = MochiTheme.colorScheme.onSurface,
                            format = { "$it / 255" },
                        )
                    }

                    MochiSegmentedControl(
                        selectedIndex = if (controller.groupEditMode) 1 else 0,
                        onSelect = { index -> controller.setEditScope(index == 1) },
                        items = scopeItems,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "studio_zone_editor") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MochiChipGroup(
                    contentPadding = PaddingValues(0.dp),
                    bleed = 16.dp,
                ) {
                    GlyphZoneGroup.entries.forEach { group ->
                        MochiChip(
                            label = stringResource(group.labelRes),
                            selected = controller.activeFilter == group,
                            onClick = { controller.selectFilter(group) },
                        )
                    }
                }

                MochiListSection(
                    header = stringResource(R.string.detail_editor_section),
                ) {
                    ZoneStripSelectorRow(
                        snapshot = controller.studioSnapshot,
                        indices = controller.activeFilter.indices,
                        selectedZone = selectedZone,
                        onSelectZone = controller::selectZone,
                    )

                    val detents = remember { listOf(64f, 128f, 192f) }
                    MochiListSliderRow(
                        value = selectedLevel.toFloat(),
                        onValueChange = { raw -> controller.setSelectedBrightness(raw.roundToInt()) },
                        valueRange = 0f..255f,
                        detents = detents,
                        title = stringResource(R.string.detail_brightness_title),
                        valueText = "$selectedLevel / 255",
                        leading = { MochiIonicon("moon", size = 18.dp) },
                        trailing = { MochiIonicon("sunny", size = 20.dp) },
                    )

                    FrameBatchOperationsRow(
                        onSyncGroup = controller::syncSelectedCluster,
                        onFillAll = { controller.fillAllZones(255) },
                        onInvert = controller::invertDraft,
                        onDimHalf = controller::dimDraftHalf,
                    )
                }
            }
        }

        item(key = "studio_curated_presets") {
            Column(verticalArrangement = Arrangement.spacedBy(MochiListDefaults.HeaderGap)) {
                MochiListSectionHeader(text = stringResource(R.string.detail_presets))
                val presetScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .mochiScrollFadeMask(presetScroll, vertical = false)
                        .horizontalScroll(presetScroll, flingBehavior = MochiScrollDefaults.flingBehavior()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphCuratedPresets.forEach { preset ->
                        val isSelected = controller.studioSnapshot == preset.snapshot
                        Column(
                            modifier = Modifier.width(86.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MochiSelectFrame(
                                selected = isSelected,
                                onClick = { controller.stagePresetInDraft(preset.snapshot) },
                                modifier = Modifier
                                    .width(86.dp)
                                    .height(142.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MochiTheme.colorScheme.surfaceContainer)
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
                                color = MochiTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MochiSelectionCheck(selected = isSelected)
                        }
                    }
                }
            }
        }

        item(key = "studio_commit_section") {
            MochiListSection(
                header = stringResource(R.string.detail_commit_section),
                footer = controller.studioActionText,
                animateHeight = false,
            ) {
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
        }
    }
}

@Composable
private fun ZoneStripSelectorRow(
    snapshot: GlyphSnapshot,
    indices: IntRange,
    selectedZone: Int,
    onSelectZone: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val haptics = rememberMochiHaptics()
    val cs = MochiTheme.colorScheme
    val pillShape = remember { MochiRoundedRectangle(14.dp) }

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
            val selected = zone == selectedZone
            val level = snapshot[zone]
            val ratio = (level / 255f).coerceIn(0f, 1f)
            val borderColor = if (selected) cs.primary else cs.outlineVariant
            val containerColor = if (selected) {
                cs.primary.copy(alpha = if (cs.isDark) 0.18f else 0.10f)
            } else {
                cs.surfaceContainerHigh
            }

            Column(
                modifier = Modifier
                    .width(56.dp)
                    .clip(pillShape)
                    .background(containerColor, pillShape)
                    .border(if (selected) 1.5.dp else 1.dp, borderColor, pillShape)
                    .clickable(role = Role.RadioButton) {
                        if (!selected) haptics.tick()
                        onSelectZone(zone)
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
                        .background(cs.outlineVariant.copy(alpha = 0.45f), MochiRoundedRectangle(2.dp)),
                ) {
                    if (ratio > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio.coerceAtLeast(0.12f))
                                .height(4.dp)
                                .background(
                                    if (selected) cs.primary else cs.onSurface,
                                    MochiRoundedRectangle(2.dp),
                                ),
                        )
                    }
                }
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
