package dev.clankyard.feature.workspacepicker

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.clankyard.workspace.TreeExportPlan

fun describeExportPlan(plan: TreeExportPlan): String =
    "${plan.created} files will be created, ${plan.overwritten} overwritten, " +
        "${plan.extraDest} extra dest files left untouched."

@Composable
fun ExportConfirmDialog(
    plan: TreeExportPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export to folder") },
        text = { Text(describeExportPlan(plan)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
