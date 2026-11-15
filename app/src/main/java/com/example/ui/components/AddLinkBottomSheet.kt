package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FolderEntity
import com.example.ui.viewmodel.AddLinkSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkBottomSheet(
    state: AddLinkSheetState,
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onFolderSelect: (Long) -> Unit,
    onCandidateSelect: (Int) -> Unit,
    onInspectClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCreateNewFolderClick: () -> Unit
) {
    if (!state.isVisible) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.action_save_link),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Save video, image, audio, or document offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.urlInput,
                onValueChange = onUrlChange,
                label = { Text("Share link or media URL") },
                placeholder = { Text("https://example.com/video.mp4") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("link_url_input"),
                trailingIcon = {
                    if (state.urlInput.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear link")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (!clipText.isNullOrBlank()) {
                            onUrlChange(clipText.trim())
                        }
                    },
                    label = { Text(stringResource(R.string.action_paste_clipboard)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("paste_clipboard_chip")
                )

                OutlinedButton(
                    onClick = onInspectClick,
                    enabled = state.urlInput.isNotBlank() && !state.isInspecting,
                    modifier = Modifier.testTag("inspect_link_button")
                ) {
                    if (state.isInspecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Inspecting…")
                    } else {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_inspect_link))
                    }
                }
            }

            AnimatedVisibility(visible = state.inspectionError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = state.inspectionError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            AnimatedVisibility(visible = state.inspectionResult != null) {
                val result = state.inspectionResult
                if (result != null) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Found ${result.candidates.size} downloadable item(s) on ${result.domain}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            result.candidates.take(5).forEachIndexed { idx, candidate ->
                                val selected = idx == state.selectedCandidateIndex
                                Surface(
                                    onClick = { onCandidateSelect(idx) },
                                    shape = MaterialTheme.shapes.small,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = mediaTypeIcon(candidate.mediaType),
                                            contentDescription = null,
                                            tint = mediaTypeTint(candidate.mediaType),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val sizeLabel = if (candidate.estimatedBytes > 0L) {
                                                "${candidate.mediaType.label} · ${formatBytes(candidate.estimatedBytes)}"
                                            } else {
                                                candidate.mediaType.label
                                            }
                                            Text(
                                                text = sizeLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = state.customTitle,
                onValueChange = onTitleChange,
                label = { Text("Title (optional)") },
                placeholder = { Text("Auto-detected from link") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("link_title_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Save to custom folder",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                folders.forEach { folder ->
                    val selected = folder.id == state.selectedFolderId
                    val folderColor = Color(folder.colorHex)
                    FilterChip(
                        selected = selected,
                        onClick = { onFolderSelect(folder.id) },
                        label = { Text(folder.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = folderIconVector(folder.iconKey),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else folderColor,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = folderColor.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.testTag("sheet_folder_chip_${folder.id}")
                    )
                }

                AssistChip(
                    onClick = onCreateNewFolderClick,
                    label = { Text(stringResource(R.string.action_new_folder)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("sheet_new_folder_chip")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDownloadClick,
                enabled = state.urlInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_download_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_download_offline))
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
