package dev.clankyard.feature.workspacepicker

import dev.clankyard.workspace.TreeExportPlan

object ExportPlanCopy {
    fun describe(plan: TreeExportPlan): String =
        "${plan.created} files will be created, ${plan.overwritten} overwritten, " +
            "${plan.extraDest} extra dest files left untouched."

    fun requiresOverwriteConfirm(plan: TreeExportPlan): Boolean = plan.overwritten > 0
}

object WorkshopLocationCopy {
    const val NOT_USER_VISIBLE =
        "This path is app-specific storage. It is not shown in Files-by-Google. " +
            "Use Export, MTP, or ADB to copy files out."

    fun settingsPath(absolutePath: String): String = "Workshop: $absolutePath\n$NOT_USER_VISIBLE"
}
