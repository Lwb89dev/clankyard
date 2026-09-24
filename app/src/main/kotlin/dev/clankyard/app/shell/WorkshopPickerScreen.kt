package dev.clankyard.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.ui.R
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.core.ui.theme.WorkshopHazardStrip
import dev.clankyard.core.ui.theme.WorkshopPanel
import dev.clankyard.core.ui.theme.WorkshopSectionHeader
import dev.clankyard.core.ui.theme.WorkshopStatusPill
import dev.clankyard.workspace.WorkspaceRecord

@Composable
fun WorkshopPickerScreen(
    records: List<WorkspaceRecord>,
    onOpen: (WorkspaceId) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("workshop") }
    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        WorkshopPanel(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            accent = true,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.clanker_still),
                        contentDescription = null,
                        modifier = Modifier.size(76.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CLANKYARD",
                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        )
                        Text("Select a fabrication bay or weld a new one.")
                    }
                    WorkshopStatusPill("local")
                }
                WorkshopHazardStrip(Modifier.fillMaxWidth().height(5.dp))
                WorkshopSectionHeader(
                    kicker = "bay control",
                    title = "New workshop",
                )
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
                    Text("+ FABRICATE WORKSHOP")
                }
                WorkshopSectionHeader(
                    kicker = "mounted bays",
                    title = "Workshops",
                    trailing = { WorkshopStatusPill("${records.size} found", active = records.isNotEmpty()) },
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(records, key = { it.id.value }) { rec ->
                        WorkshopPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onOpen(rec.id) }
                                .semantics { contentDescription = "Open ${rec.displayName}" },
                        ) {
                            Text(
                                text = "> ${rec.displayName}",
                                style = PathTextStyle,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
