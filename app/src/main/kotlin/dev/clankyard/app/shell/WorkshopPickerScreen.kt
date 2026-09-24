package dev.clankyard.app.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.workspace.WorkspaceRecord

@Composable
fun WorkshopPickerScreen(
    records: List<WorkspaceRecord>,
    onOpen: (WorkspaceId) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("workshop") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Clankyard", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("Open a local workshop. Useful with no AI key configured.")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("New workshop name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
        )
        Button(
            onClick = { onCreate(name) },
            modifier = Modifier.semantics { contentDescription = "Create workshop" },
        ) {
            Text("Create workshop")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(records, key = { it.id.value }) { rec ->
                Text(
                    text = rec.displayName,
                    style = PathTextStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(rec.id) }
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = "Open ${rec.displayName}" },
                )
            }
        }
    }
}
