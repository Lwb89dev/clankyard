package dev.clankyard.feature.workspacepicker

import android.content.Intent

object WorkspacePickerIntents {
    fun openTree(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    fun createZip(): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "project.zip")
        }
}
