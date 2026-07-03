package com.vsp.inspection.feature.imagedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vsp.core.model.DamageType
import com.vsp.core.model.Severity

@Composable
fun AddAnnotationDialog(
    onDismiss: () -> Unit,
    onConfirm: (DamageType, Severity, String?) -> Unit,
) {
    var selectedType by remember { mutableStateOf(DamageType.DENT) }
    var selectedSeverity by remember { mutableStateOf(Severity.MEDIUM) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add annotation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Severity", style = MaterialTheme.typography.labelLarge)
                Row {
                    Severity.entries.forEach { severity ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(
                                selected = selectedSeverity == severity,
                                onClick = { selectedSeverity = severity },
                            ),
                        ) {
                            RadioButton(selected = selectedSeverity == severity, onClick = { selectedSeverity = severity })
                            Text(severity.name, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
                Text("Damage type", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp).fillMaxWidth()) {
                    items(DamageType.entries.toList()) { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = selectedType == type, onClick = { selectedType = type }),
                        ) {
                            RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                            Text(type.name.replace('_', ' '))
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedType, selectedSeverity, comment.ifBlank { null }) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
