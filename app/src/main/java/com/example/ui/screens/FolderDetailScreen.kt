package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DownloadStatus
import com.example.data.model.FolderEntity
import com.example.data.model.SavedItemEntity
import com.example.ui.components.MediaGridCard
import com.example.ui.components.MediaListRow
import com.example.ui.components.folderIconVector
import com.example.ui.components.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folder: FolderEntity?,
    items: List<SavedItemEntity>,
    isGridView: Boolean,
    selectedItemIds: Set<Long>,
    onBack: () -> Unit,
    onEditFolder: (FolderEntity) -> Unit,
    onAddLinkToFolder: (Long) -> Unit,
    onImportToFolder: (Long) -> Unit,
    onOpenItem: (SavedItemEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onMoveSingle: (Long) -> Unit,
    onExportSingle: (Long) -> Unit,
    onShareSingle: (Long) -> Unit,
    onDeleteSingle: (Long) -> Unit,
    onRetrySingle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = folder?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary
    val totalBytes = items.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.fileSizeBytes }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (folder != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = accent.copy(alpha = 0.18f)
                        ) {
                            Icon(
                                imageVector = folderIconVector(folder.iconKey),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = folder?.name ?: "Folder",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${items.size} items · ${formatBytes(totalBytes)} offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("folder_detail_back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            actions = {
                if (folder != null) {
                    IconButton(onClick = { onEditFolder(folder) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit folder")
                    }
                    IconButton(onClick = { onImportToFolder(folder.id) }) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.action_import_device))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = folderIconVector(folder?.iconKey ?: "folder"),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .padding(24.dp)
                                .size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.empty_folder_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_folder_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (folder != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { onAddLinkToFolder(folder.id) }) {
                                Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_save_link))
                            }
                            OutlinedButton(onClick = { onImportToFolder(folder.id) }) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_import_device))
                            }
                        }
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 164.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    MediaGridCard(
                        item = item,
                        folder = null,
                        isSelected = selectedItemIds.contains(item.id),
                        onClick = {
                            if (selectedItemIds.isNotEmpty()) onToggleSelection(item.id) else onOpenItem(item)
                        },
                        onLongClick = { onToggleSelection(item.id) },
                        onToggleFavorite = { onToggleFavorite(item.id) },
                        onMoveClick = { onMoveSingle(item.id) },
                        onExportClick = { onExportSingle(item.id) },
                        onShareClick = { onShareSingle(item.id) },
                        onDeleteClick = { onDeleteSingle(item.id) },
                        onRetryClick = { onRetrySingle(item.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    MediaListRow(
                        item = item,
                        folder = null,
                        isSelected = selectedItemIds.contains(item.id),
                        onClick = {
                            if (selectedItemIds.isNotEmpty()) onToggleSelection(item.id) else onOpenItem(item)
                        },
                        onLongClick = { onToggleSelection(item.id) },
                        onToggleFavorite = { onToggleFavorite(item.id) },
                        onMoveClick = { onMoveSingle(item.id) },
                        onExportClick = { onExportSingle(item.id) },
                        onShareClick = { onShareSingle(item.id) },
                        onDeleteClick = { onDeleteSingle(item.id) },
                        onRetryClick = { onRetrySingle(item.id) }
                    )
                }
            }
        }
    }
}
