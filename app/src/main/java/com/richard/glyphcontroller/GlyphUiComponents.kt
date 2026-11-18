package com.richard.glyphcontroller

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonGroup
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListStatusChip
import ui.feice.mochi.list.MochiSwipeAction
import ui.feice.mochi.list.MochiSwipeRow
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.nothing.nothingDotTextStyle
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

/** Immutable curated lighting preset definition shared across Overview and Studio. */
@Immutable
data class GlyphPresetSpec(
    @param:StringRes val labelRes: Int,
    val iconName: String,
    val snapshot: GlyphSnapshot,
)

val GlyphCuratedPresets: ImmutableList<GlyphPresetSpec> = persistentListOf(
    GlyphPresetSpec(R.string.detail_preset_all, "sunny", GlyphSnapshot.Full),
    GlyphPresetSpec(R.string.detail_preset_soft, "bulb", GlyphSnapshot.from(GlyphZones.presetAll(96))),
    GlyphPresetSpec(R.string.detail_preset_ring, "radio-button-on", GlyphSnapshot.from(GlyphZones.presetRing())),
    GlyphPresetSpec(R.string.detail_preset_lower, "layers", GlyphSnapshot.from(GlyphZones.presetCameraAndLower())),
    GlyphPresetSpec(R.string.detail_preset_starlight, "contrast", GlyphSnapshot.from(GlyphZones.presetStarlight())),
    GlyphPresetSpec(R.string.detail_preset_essential, "pulse", GlyphSnapshot.from(GlyphZones.presetEssential())),
    GlyphPresetSpec(R.string.detail_preset_off, "moon", GlyphSnapshot.Zero),
)

/** Shared stable navigation bar item keys for cross-page Mochi top-bar morphing. */
internal object GlyphNavIds {
    const val ApplyButton = "glyph.apply"
    const val StudioToolsGroup = "glyph.studio-tools"
    const val RevertIcon = "glyph.revert"
    const val ScenesIcon = "glyph.scenes"
    const val ScenesCardSource = "glyph.scenes-card"
}

internal enum class GlyphBarIconKey { Revert, Scenes }

enum class GlyphStatusTone { Active, Ready, Warning, Neutral }

/** Monochrome symbol leading icon for Mochi list rows (`MochiListLeadingStyle.Symbol`). */
@Composable
fun GlyphSymbolLeading(iconName: String) {
    MochiIonicon(
        name = iconName,
        tint = MochiTheme.colorScheme.onSurfaceVariant,
        size = 20.dp,
    )
}

/** Semantic status pill aligned with Mochi's list status chip conventions under Nothing palette. */
@Composable
fun GlyphStatusBadge(
    text: String,
    tone: GlyphStatusTone,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val (container, content) = when (tone) {
        GlyphStatusTone.Active -> cs.primary to cs.onPrimary
        GlyphStatusTone.Ready -> cs.secondaryContainer to cs.onSecondaryContainer
        GlyphStatusTone.Warning -> cs.errorContainer to cs.onErrorContainer
        GlyphStatusTone.Neutral -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }
    MochiListStatusChip(
        text = text,
        modifier = modifier,
        containerColor = container,
        contentColor = content,
    )
}

/**
 * Renders short ASCII/Latin hardware identifiers (`A1`, `C1_12`, `LED 12`) in Nothing's
 * dot-matrix font (`Ndot57`). Never pass localized sentences or CJK strings here.
 */
@Composable
fun GlyphDotCode(
    code: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MochiTheme.textStyles.titleMedium,
    color: Color = MochiTheme.colorScheme.onSurface,
) {
    val dotStyle = remember(style) { nothingDotTextStyle(style) }
    Text(
        text = code,
        modifier = modifier,
        style = dotStyle,
        color = color,
        maxLines = 1,
    )
}

/**
 * Animated mechanical counter rendered in Nothing's dot-matrix numeric font (`Ndot57`).
 * Strictly used for numeric readouts (`0..33`, `0..255`, `%`).
 */
@Composable
fun GlyphDotCounter(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MochiTheme.textStyles.headlineMedium,
    color: Color = MochiTheme.colorScheme.onSurface,
    format: (Long) -> String = { it.toString() },
) {
    val dotStyle = remember(style) { nothingDotTextStyle(style) }
    MochiCounterText(
        value = value,
        modifier = modifier,
        style = dotStyle,
        color = color,
        format = format,
    )
}

/**
 * Dedicated 4-slot Scene Vault page pushed onto `MochiNavigator` (morphed from the Overview
 * scenes card via `mochiNavSource` or side-pushed from Studio's top bar).
 */
@Composable
fun GlyphSceneVaultPage(
    controller: GlyphWorkbenchController,
    routeTitle: (String) -> String,
    onLoadedIntoStudio: () -> Unit,
) {
    MochiLazyCompactTitleScaffold(
        title = stringResource(R.string.vault_title),
        navigationIcon = { MochiBackButton(history = routeTitle) },
        actions = {
            MochiBarButton(
                text = stringResource(R.string.detail_apply_short),
                onClick = controller::applyDraft,
                enabled = controller.hardwareReady && !controller.busy,
                navId = GlyphNavIds.ApplyButton,
            )
        },
    ) {
        item(key = "vault_slots_section") {
            MochiListSection(
                header = stringResource(R.string.home_scenes_title),
                footer = stringResource(R.string.vault_footer),
            ) {
                for (slot in 0 until GlyphSceneStore.SLOT_COUNT) {
                    val slotSnapshot = controller.savedSlots.getOrNull(slot)
                    SceneSlotSwipeRow(
                        slotIndex = slot,
                        snapshot = slotSnapshot,
                        hardwareReady = controller.hardwareReady && !controller.busy,
                        onSaveDraft = { controller.saveDraftToSlot(slot) },
                        onLoadToStudio = {
                            if (controller.loadSlotToDraft(slot)) {
                                onLoadedIntoStudio()
                            }
                        },
                        onApplyDirect = { controller.applySlotDirectly(slot) },
                        onClearSlot = { controller.clearSlot(slot) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneSlotSwipeRow(
    slotIndex: Int,
    snapshot: GlyphSnapshot?,
    hardwareReady: Boolean,
    onSaveDraft: () -> Unit,
    onLoadToStudio: () -> Unit,
    onApplyDirect: () -> Unit,
    onClearSlot: () -> Unit,
) {
    val clearLabel = stringResource(R.string.detail_clear_slot)
    val destructiveColor = MochiTheme.colorScheme.error
    val actions = if (snapshot != null) {
        listOf(
            MochiSwipeAction(
                label = clearLabel,
                color = destructiveColor,
                removesRow = false,
                onClick = onClearSlot,
                icon = { tint -> MochiIonicon("trash", tint = tint, size = 18.dp) },
            ),
        )
    } else {
        emptyList()
    }

    MochiSwipeRow(
        actions = actions,
        dividerInset = 76.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MochiListDefaults.RowPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 80.dp)
                        .clip(MochiRoundedRectangle(12.dp))
                        .background(Color(0xFF101216))
                        .padding(5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphVectorPreview(
                        snapshot = snapshot ?: GlyphSnapshot.Zero,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.detail_slot_title, slotIndex + 1),
                            style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                            color = MochiTheme.colorScheme.onSurface,
                        )
                        GlyphDotCode(
                            code = "S0${slotIndex + 1}",
                            style = MochiTheme.textStyles.labelMedium,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (snapshot != null) {
                            stringResource(R.string.detail_slot_saved, snapshot.activeCount)
                        } else {
                            stringResource(R.string.detail_slot_empty)
                        },
                        style = MochiTheme.textStyles.subhead,
                        color = if (snapshot != null) {
                            MochiTheme.colorScheme.onSurface
                        } else {
                            MochiTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            MochiButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                spacing = 8.dp,
            ) {
                MochiButton(
                    onClick = onSaveDraft,
                    modifier = Modifier.weight(1f),
                    style = MochiButtonStyle.Tonal,
                    minHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.detail_save_scene),
                        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                if (snapshot != null) {
                    MochiButton(
                        onClick = onLoadToStudio,
                        modifier = Modifier.weight(1f),
                        style = MochiButtonStyle.Tonal,
                        minHeight = 40.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.detail_load_scene),
                            style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                    }
                    MochiButton(
                        onClick = onApplyDirect,
                        enabled = hardwareReady,
                        modifier = Modifier.weight(1f),
                        style = MochiButtonStyle.Filled,
                        minHeight = 40.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.detail_apply_slot),
                            style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
