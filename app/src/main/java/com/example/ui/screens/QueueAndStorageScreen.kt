package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaType
import com.example.data.model.SavedItemEntity
import com.example.ui.components.formatBytes
import com.example.ui.components.formatShortDate
import com.example.ui.components.mediaTypeIcon
import com.example.ui.components.mediaTypeTint
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.VaultUiState

@Composable
fun QueueAndStorageScreen(
    state: VaultUiState,
    onAddLinkClick: () -> Unit,
    onRetryItem: (Long) -> Unit,
    onCancelItem: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onOpenItem: (SavedItemEntity) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAutoExportChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAndFailed = state.allItems.filter { it.status != DownloadStatus.COMPLETED }
    val recentCompleted = state.allItems.filter { it.status == DownloadStatus.COMPLETED }.take(8)

    val completedItems = state.allItems.filter { it.status == DownloadStatus.COMPLETED }
    val videoBytes = completedItems.filter { it.mediaType == MediaType.VIDEO }.sumOf { it.fileSizeBytes }
    val imageBytes = completedItems.filter { it.mediaType == MediaType.IMAGE }.sumOf { it.fileSizeBytes }
    val audioBytes = completedItems.filter { it.mediaType == MediaType.AUDIO }.sumOf { it.fileSizeBytes }
    val docBytes = completedItems.filter {
        it.mediaType == MediaType.DOCUMENT || it.mediaType == MediaType.OTHER
    }.sumOf { it.fileSizeBytes }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Downloads & Storage",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Manage active transfers & offline space",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddLinkClick,
                    modifier = Modifier.testTag("queue_add_link_button")
                ) {
                    Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Link")
                }
            }
        }

        // Offline Storage Breakdown Card
        item {
            StorageBreakdownCard(
                totalBytes = state.totalOfflineBytes,
                totalCount = state.offlineReadyCount,
                videoBytes = videoBytes,
                imageBytes = imageBytes,
                audioBytes = audioBytes,
                docBytes = docBytes
            )
        }

        // Active / Failed Queue Section
        item {
            Text(
                text = "Active & Pending (${activeAndFailed.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (activeAndFailed.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "All downloads are saved offline on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(activeAndFailed, key = { "q_${it.id}" }) { item ->
                QueueItemCard(
                    item = item,
                    onRetry = { onRetryItem(item.id) },
                    onCancel = { onCancelItem(item.id) },
                    onDelete = { onDeleteItem(item.id) }
                )
            }
        }

        // Offline Preferences Card
        item {
            Text(
                text = "Download & Vault Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi only downloads", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Pause link downloads when on cellular data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.wifiOnlyDownloads,
                            onCheckedChange = onWifiOnlyChange,
                            modifier = Modifier.testTag("wifi_only_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-save to Device Gallery", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Also mirror downloaded media to Pictures/Movies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.settings.autoExportToGallery,
                            onCheckedChange = onAutoExportChange,
                            modifier = Modifier.testTag("auto_export_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemePreference.entries.forEachIndexed { index, pref ->
                            SegmentedButton(
                                selected = state.settings.themePreference == pref,
                                onClick = { onThemeChange(pref) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemePreference.entries.size
                                ),
                                label = {
                                    Text(
                                        when (pref) {
                                            ThemePreference.SYSTEM -> "Auto"
                                            ThemePreference.DARK -> "Dark"
                                            ThemePreference.LIGHT -> "Light"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (recentCompleted.isNotEmpty()) {
            item {
                Text(
                    text = "Recently Saved Offline",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(recentCompleted, key = { "recent_${it.id}" }) { item ->
                Surface(
                    onClick = { onOpenItem(item) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = mediaTypeIcon(item.mediaType),
                            contentDescription = null,
                            tint = mediaTypeTint(item.mediaType)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatBytes(item.fileSizeBytes)} · ${formatShortDate(item.completedAt ?: item.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownCard(
    totalBytes: Long,
    totalCount: Int,
    videoBytes: Long,
    imageBytes: Long,
    audioBytes: Long,
    docBytes: Long
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Offline Vault Storage", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = formatBytes(totalBytes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$totalCount file(s) stored locally for offline playback",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            val safeTotal = totalBytes.coerceAtLeast(1L).toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (videoBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((videoBytes / safeTotal).coerceAtLeast(0.03f))
                            .fillMaxSize()
                            .background(mediaTypeTint(MediaType.VIDEO))
                    )
                }
                if (imageBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((imageBytes / safeTotal).coerceAtLeast(0.03f))
                            .fillMaxSize()
                            .background(mediaTypeTint(MediaType.IMAGE))
                    )
                }
                if (audioBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((audioBytes / safeTotal).coerceAtLeast(0.03f))
                            .fillMaxSize()
                            .background(mediaTypeTint(MediaType.AUDIO))
                    )
                }
                if (docBytes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((docBytes / safeTotal).coerceAtLeast(0.03f))
                            .fillMaxSize()
                            .background(mediaTypeTint(MediaType.DOCUMENT))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageLegendItem("Videos", videoBytes, mediaTypeTint(MediaType.VIDEO))
                StorageLegendItem("Images", imageBytes, mediaTypeTint(MediaType.IMAGE))
                StorageLegendItem("Audio", audioBytes, mediaTypeTint(MediaType.AUDIO))
                StorageLegendItem("Docs", docBytes, mediaTypeTint(MediaType.DOCUMENT))
            }
        }
    }
}

@Composable
private fun StorageLegendItem(label: String, bytes: Long, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatBytes(bytes), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QueueItemCard(
    item: SavedItemEntity,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.status == DownloadStatus.FAILED) Icons.Rounded.ErrorOutline else mediaTypeIcon(item.mediaType),
                    contentDescription = null,
                    tint = if (item.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else mediaTypeTint(item.mediaType)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.domain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.status == DownloadStatus.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                    }
                } else {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.status == DownloadStatus.FAILED) {
                Text(
                    text = item.errorMessage ?: "Download failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                val label = when {
                    item.status == DownloadStatus.RESOLVING -> "Inspecting share link…"
                    item.progress > 0f -> "${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.fileSizeBytes)}"
                    else -> "Downloading ${formatBytes(item.downloadedBytes)}…"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (item.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}
