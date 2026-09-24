package dev.clankyard.core.ui

import dev.clankyard.core.model.WorkspacePath

/**
 * Window width buckets from WindowManager 1.5 / M3 Adaptive 1.2.
 * Large and Extra-large map onto workshop [SizeClass.Expanded] (three-pane).
 *
 * | Width dp     | WindowSizeClass | Workshop     | Layout |
 * | < 600        | Compact         | Compact      | Destinations |
 * | 600 .. 839   | Medium          | Medium       | List-detail + Clanker overlay |
 * | 840 .. 1199  | Expanded        | Expanded     | Files \| Editor \| Clanker |
 * | 1200 .. 1599 | Large           | Expanded     | three-pane |
 * | ≥ 1600       | Extra-large     | Expanded     | three-pane |
 */
enum class WindowWidthBucket { Compact, Medium, Expanded, Large, ExtraLarge }

enum class WorkshopLayout {
    CompactDestinations,
    MediumListDetail,
    ExpandedThreePane,
}

const val WIDTH_DP_COMPACT_MAX = 600f
const val WIDTH_DP_MEDIUM_MAX = 840f
const val WIDTH_DP_EXPANDED_MAX = 1200f
const val WIDTH_DP_LARGE_MAX = 1600f
const val HEIGHT_DP_COMPACT_MAX = 480f

fun windowWidthBucket(widthDp: Float): WindowWidthBucket = when {
    widthDp < WIDTH_DP_COMPACT_MAX -> WindowWidthBucket.Compact
    widthDp < WIDTH_DP_MEDIUM_MAX -> WindowWidthBucket.Medium
    widthDp < WIDTH_DP_EXPANDED_MAX -> WindowWidthBucket.Expanded
    widthDp < WIDTH_DP_LARGE_MAX -> WindowWidthBucket.Large
    else -> WindowWidthBucket.ExtraLarge
}

fun sizeClassFromWidthDp(widthDp: Float): SizeClass =
    sizeClassFromBucket(windowWidthBucket(widthDp))

/**
 * Landscape is a deliberate three-pane posture for the workshop. A phone in
 * landscape can be narrower than the regular medium breakpoint, but it still
 * has horizontal space for Files, Editor, and Clanker side by side.
 */
fun sizeClassFromWindowDp(widthDp: Float, heightDp: Float): SizeClass =
    if (widthDp > heightDp) SizeClass.Expanded else sizeClassFromWidthDp(widthDp)

fun sizeClassFromBucket(bucket: WindowWidthBucket): SizeClass = when (bucket) {
    WindowWidthBucket.Compact -> SizeClass.Compact
    WindowWidthBucket.Medium -> SizeClass.Medium
    WindowWidthBucket.Expanded,
    WindowWidthBucket.Large,
    WindowWidthBucket.ExtraLarge,
    -> SizeClass.Expanded
}

fun isHeightCompact(heightDp: Float): Boolean = heightDp < HEIGHT_DP_COMPACT_MAX

fun layoutFor(sizeClass: SizeClass): WorkshopLayout = when (sizeClass) {
    SizeClass.Compact -> WorkshopLayout.CompactDestinations
    SizeClass.Medium -> WorkshopLayout.MediumListDetail
    SizeClass.Expanded -> WorkshopLayout.ExpandedThreePane
}

/**
 * Chrome to apply when the window size class changes.
 *
 * | From → To | Files | Editor | Clanker | Bottom | Weights |
 * | Expanded → Compact | Files dest | Editor dest (tabs kept) | Clanker dest; chrome hidden | Terminal/Git dest | stored, not shown |
 * | Compact → Expanded | Restored | Restored | Restored if clankerVisible | Restored if not collapsed | apply stored 0–1 |
 * | Expanded → Medium | List-detail | Detail | Overlay/supporting | Collapsed by default | Files+Editor share; Clanker overlay |
 * | Medium → Expanded | Un-overlay | — | Dock supporting | Restore | Restore floats |
 */
data class SizeClassRestore(
    val from: SizeClass,
    val to: SizeClass,
    val layout: WorkshopLayout,
    val tabs: List<OpenTab>,
    val activePath: WorkspacePath?,
    val filesWeight: Float,
    val editorWeight: Float,
    val clankerWeight: Float,
    val bottomWeight: Float,
    val filesCollapsed: Boolean,
    val clankerVisible: Boolean,
    val bottomCollapsed: Boolean,
    val bottomTab: BottomTab,
    val showWeights: Boolean,
    val applyStoredWeights: Boolean,
    val clankerOverlay: Boolean,
    val clankerDocked: Boolean,
    val filesAsDestination: Boolean,
    val editorAsDestination: Boolean,
    val clankerAsDestination: Boolean,
    val bottomAsDestinations: Boolean,
)

fun restoreSizeClass(
    from: SizeClass,
    to: SizeClass,
    stored: WorkspaceUiState,
): SizeClassRestore = when (to) {
    SizeClass.Compact -> compactRestore(from, stored)
    SizeClass.Medium -> mediumRestore(from, stored)
    SizeClass.Expanded -> expandedRestore(from, stored)
}

private fun compactRestore(from: SizeClass, stored: WorkspaceUiState) = SizeClassRestore(
    from = from,
    to = SizeClass.Compact,
    layout = WorkshopLayout.CompactDestinations,
    tabs = stored.tabs,
    activePath = stored.activePath,
    filesWeight = stored.filesWeight,
    editorWeight = stored.editorWeight,
    clankerWeight = stored.clankerWeight,
    bottomWeight = stored.bottomWeight,
    filesCollapsed = stored.filesCollapsed,
    clankerVisible = stored.clankerVisible,
    bottomCollapsed = stored.bottomCollapsed,
    bottomTab = stored.bottomTab,
    showWeights = false,
    applyStoredWeights = false,
    clankerOverlay = false,
    clankerDocked = false,
    filesAsDestination = true,
    editorAsDestination = true,
    clankerAsDestination = true,
    bottomAsDestinations = true,
)

private fun mediumRestore(from: SizeClass, stored: WorkspaceUiState): SizeClassRestore {
    val arriving = from != SizeClass.Medium
    return SizeClassRestore(
        from = from,
        to = SizeClass.Medium,
        layout = WorkshopLayout.MediumListDetail,
        tabs = stored.tabs,
        activePath = stored.activePath,
        filesWeight = stored.filesWeight,
        editorWeight = stored.editorWeight,
        clankerWeight = stored.clankerWeight,
        bottomWeight = stored.bottomWeight,
        filesCollapsed = stored.filesCollapsed,
        clankerVisible = stored.clankerVisible,
        bottomCollapsed = if (arriving) true else stored.bottomCollapsed,
        bottomTab = stored.bottomTab,
        showWeights = true,
        applyStoredWeights = from == SizeClass.Expanded || from == SizeClass.Medium,
        clankerOverlay = stored.clankerVisible,
        clankerDocked = false,
        filesAsDestination = false,
        editorAsDestination = false,
        clankerAsDestination = false,
        bottomAsDestinations = false,
    )
}

private fun expandedRestore(from: SizeClass, stored: WorkspaceUiState) = SizeClassRestore(
    from = from,
    to = SizeClass.Expanded,
    layout = WorkshopLayout.ExpandedThreePane,
    tabs = stored.tabs,
    activePath = stored.activePath,
    filesWeight = stored.filesWeight,
    editorWeight = stored.editorWeight,
    clankerWeight = stored.clankerWeight,
    bottomWeight = stored.bottomWeight,
    filesCollapsed = stored.filesCollapsed,
    clankerVisible = stored.clankerVisible,
    bottomCollapsed = stored.bottomCollapsed,
    bottomTab = stored.bottomTab,
    showWeights = true,
    applyStoredWeights = true,
    clankerOverlay = false,
    clankerDocked = stored.clankerVisible,
    filesAsDestination = false,
    editorAsDestination = false,
    clankerAsDestination = false,
    bottomAsDestinations = false,
)
