package dev.clankyard.core.ui

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.proto.OpenTabProto
import dev.clankyard.core.ui.proto.WorkspaceUiStateProto

fun WorkspaceUiState.toProto(): WorkspaceUiStateProto {
    val builder = WorkspaceUiStateProto.newBuilder()
        .setFilesWeight(filesWeight)
        .setEditorWeight(editorWeight)
        .setClankerWeight(clankerWeight)
        .setBottomWeight(bottomWeight)
        .setFilesCollapsed(filesCollapsed)
        .setClankerVisible(clankerVisible)
        .setBottomCollapsed(bottomCollapsed)
        .setBottomTab(bottomTab.name)
        .setLastSizeClass(lastSizeClass.name)
    workspaceId?.let { builder.workspaceId = it.value }
    activePath?.let { builder.activePath = it.relative }
    tabs.forEach { tab ->
        builder.addTabs(
            OpenTabProto.newBuilder()
                .setPath(tab.path.relative)
                .setCursorLine(tab.cursorLine)
                .setCursorCol(tab.cursorCol)
                .build(),
        )
    }
    return builder.build()
}

fun WorkspaceUiStateProto.toModel(): WorkspaceUiState =
    WorkspaceUiState(
        workspaceId = optionalId(hasWorkspaceId(), workspaceId),
        tabs = tabsList.map(::tabToModel),
        activePath = optionalPath(hasActivePath(), activePath),
        filesWeight = if (hasFilesWeight()) filesWeight else 0.22f,
        editorWeight = if (hasEditorWeight()) editorWeight else 0.50f,
        clankerWeight = if (hasClankerWeight()) clankerWeight else 0.28f,
        bottomWeight = if (hasBottomWeight()) bottomWeight else 0.28f,
        filesCollapsed = if (hasFilesCollapsed()) filesCollapsed else false,
        clankerVisible = if (hasClankerVisible()) clankerVisible else true,
        bottomCollapsed = if (hasBottomCollapsed()) bottomCollapsed else true,
        bottomTab = enumOrDefault(hasBottomTab(), bottomTab, BottomTab.Terminal),
        lastSizeClass = enumOrDefault(hasLastSizeClass(), lastSizeClass, SizeClass.Compact),
    )

private fun optionalId(present: Boolean, value: String): WorkspaceId? =
    value.takeIf { present && it.isNotEmpty() }?.let(::WorkspaceId)

private fun optionalPath(present: Boolean, value: String): WorkspacePath? =
    value.takeIf { present && it.isNotEmpty() }?.let(WorkspacePath::parse)

private fun tabToModel(tab: OpenTabProto): OpenTab =
    OpenTab(
        path = WorkspacePath.parse(tab.path),
        cursorLine = tab.cursorLine,
        cursorCol = tab.cursorCol,
    )

private inline fun <reified T : Enum<T>> enumOrDefault(
    present: Boolean,
    raw: String,
    default: T,
): T {
    if (!present || raw.isEmpty()) return default
    return runCatching { java.lang.Enum.valueOf(T::class.java, raw) }.getOrDefault(default)
}
