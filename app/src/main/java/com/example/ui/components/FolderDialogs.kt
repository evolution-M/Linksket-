package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FolderEntity
import com.example.data.model.FolderIconOption
import com.example.data.model.SavedItemEntity
import com.example.ui.theme.FolderPalette

@Composable
fun FolderEditorDialog(
    initialFolder: FolderEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, colorHex: Long, isPinned: Boolean) -> Unit
) {
    var name by remember(initialFolder) { mutableStateOf(initialFolder?.name.orEmpty()) }
    var selectedIcon by remember(initialFolder) { mutableStateOf(initialFolder?.iconKey ?: "folder") }
    var selectedColor by remember(initialFolder) {
        mutableLongStateOf(initialFolder?.colorHex ?: FolderPalette.first())
    }
    var isPinned by remember(initialFolder) { mutableStateOf(initialFolder?.isPinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialFolder == null) "Create Custom Folder" else "Edit Folder",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g., Travel Clips, Receipts") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Color accent", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FolderPalette.forEach { hex ->
                            val selected = hex == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected color",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Folder icon", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FolderIconOption.entries.forEach { option ->
                            val selected = option.key == selectedIcon
                            Surface(
                                onClick = { selectedIcon = option.key },
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) {
                                    Color(selectedColor).copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(selectedColor) else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Icon(
                                    imageVector = folderIconVector(option.key),
                                    contentDescription = option.label,
                                    tint = if (selected) Color(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pin folder to top", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        modifier = Modifier.testTag("folder_pin_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedIcon, selectedColor, isPinned) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_folder_button")
            ) {
                Text(if (initialFolder == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToFolderSheet(
    folders: List<FolderEntity>,
    itemCount: Int,
    onDismiss: () -> Unit,
    onSelectFolder: (Long) -> Unit,
    onCreateNewFolder: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Move $itemCount item${if (itemCount == 1) "" else "s"} to folder",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folders.forEach { folder ->
                    val tint = Color(folder.colorHex)
                    Surface(
                        onClick = { onSelectFolder(folder.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("move_target_folder_${folder.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = tint.copy(alpha = 0.16f)
                            ) {
                                Icon(
                                    imageVector = folderIconVector(folder.iconKey),
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCreateNewFolder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_new_folder))
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun EditItemDetailsDialog(
    item: SavedItemEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var notes by remember(item.id) { mutableStateOf(item.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Offline Item", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Offline notes") },
                    placeholder = { Text("Add context, tags, or reminders…") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, notes) },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
