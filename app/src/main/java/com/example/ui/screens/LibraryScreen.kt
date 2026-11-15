package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SavedItemEntity
import com.example.ui.components.MediaGridCard
import com.example.ui.components.MediaListRow
import com.example.ui.components.formatBytes
import com.example.ui.viewmodel.LibraryFilter
import com.example.ui.viewmodel.VaultUiState

@Composable
fun LibraryScreen(
    state: VaultUiState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onToggleGridView: () -> Unit,
    onOpenItem: (SavedItemEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onMoveSingle: (Long) -> Unit,
    onExportSingle: (Long) -> Unit,
    onShareSingle: (Long) -> Unit,
    onDeleteSingle: (Long) -> Unit,
    onRetrySingle: (Long) -> Unit,
    onAddLinkClick: () -> Unit,
    onImportDeviceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Editorial Vault Header or Selection Bar
        if (state.isSelectionMode) {
            SelectionActionHeader(
                count = state.selectedItemIds.size,
                onClear = onClearSelection,
                onMove = onMoveSelected,
                onDelete = onDeleteSelected
            )
        } else {
            VaultHeader(
                offlineCount = state.offlineReadyCount,
                totalBytes = state.totalOfflineBytes,
                isOnline = state.isOnline,
                isGridView = state.settings.isGridView,
                onToggleView = onToggleGridView,
                onImportClick = onImportDeviceClick
            )
        }

        // Search Field
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.cd_search))
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("library_search_input")
        )

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.activeFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag("filter_chip_${filter.name}")
                )
            }
        }

        // Empty State vs Grid/List Content
        if (state.filteredItems.isEmpty()) {
            EmptyLibraryState(
                isFiltered = state.searchQuery.isNotBlank() || state.activeFilter != LibraryFilter.ALL,
                onAddLinkClick = onAddLinkClick,
                onImportDeviceClick = onImportDeviceClick,
                onClearFilters = {
                    onSearchChange("")
                    onFilterChange(LibraryFilter.ALL)
                }
            )
        } else if (state.settings.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 164.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filteredItems, key = { it.id }) { item ->
                    val isSelected = state.selectedItemIds.contains(item.id)
                    MediaGridCard(
                        item = item,
                        folder = state.folderLookup[item.folderId],
                        isSelected = isSelected,
                        onClick = {
                            if (state.isSelectionMode) onToggleSelection(item.id) else onOpenItem(item)
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filteredItems, key = { it.id }) { item ->
                    val isSelected = state.selectedItemIds.contains(item.id)
                    MediaListRow(
                        item = item,
                        folder = state.folderLookup[item.folderId],
                        isSelected = isSelected,
                        onClick = {
                            if (state.isSelectionMode) onToggleSelection(item.id) else onOpenItem(item)
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

@Composable
private fun VaultHeader(
    offlineCount: Int,
    totalBytes: Long,
    isOnline: Boolean,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onImportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LinkStash",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isOnline) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Rounded.OfflinePin else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = if (isOnline) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isOnline) {
                                "$offlineCount offline · ${formatBytes(totalBytes)}"
                            } else {
                                "Offline Mode · $offlineCount ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onImportClick,
                modifier = Modifier.testTag("import_device_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = stringResource(R.string.action_import_device)
                )
            }
            IconButton(
                onClick = onToggleView,
                modifier = Modifier.testTag("toggle_view_button")
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = stringResource(R.string.cd_toggle_view)
                )
            }
        }
    }
}

@Composable
private fun SelectionActionHeader(
    count: Int,
    onClear: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMove, modifier = Modifier.testTag("batch_move_button")) {
                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = "Move selected")
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("batch_delete_button")) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete selected")
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isFiltered: Boolean,
    onAddLinkClick: () -> Unit,
    onImportDeviceClick: () -> Unit,
    onClearFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_vault),
                contentDescription = stringResource(R.string.cd_empty_vault_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(190.dp)
                    .clip(MaterialTheme.shapes.large)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (isFiltered) "No matching offline items" else stringResource(R.string.empty_library_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isFiltered) {
                    "Try clearing your search or switching media filters."
                } else {
                    stringResource(R.string.empty_library_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (isFiltered) {
                OutlinedButton(onClick = onClearFilters) {
                    Text("Reset filters")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onAddLinkClick,
                        modifier = Modifier.testTag("empty_add_link_button")
                    ) {
                        Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save_link))
                    }
                    OutlinedButton(
                        onClick = onImportDeviceClick,
                        modifier = Modifier.testTag("empty_import_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_device))
                    }
                }
            }
        }
    }
}
