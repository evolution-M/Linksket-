package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DownloadStatus
import com.example.data.model.FolderEntity
import com.example.data.model.MediaType
import com.example.data.model.SavedItemEntity
import com.example.ui.components.EditItemDetailsDialog
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.mediaTypeIcon
import com.example.ui.components.mediaTypeTint
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    item: SavedItemEntity?,
    folder: FolderEntity?,
    onBack: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onMoveToFolder: (Long) -> Unit,
    onExportToDevice: (Long) -> Unit,
    onShareFile: (Long) -> Unit,
    onOpenExternal: (Long) -> Unit,
    onUpdateDetails: (Long, String, String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onRetryItem: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (item == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isAvailableOffline) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = buildString {
                                append(if (item.isAvailableOffline) "Offline" else item.status.name)
                                if (item.fileSizeBytes > 0L) append(" · ${formatBytes(item.fileSizeBytes)}")
                                if (folder != null) append(" · ${folder.name}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = { onToggleFavorite(item.id) }) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (item.isFavorite) Color(0xFFFF6B4A) else Color.White
                    )
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit title and notes",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0E131F))
        )

        // Main Offline Media Stage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val localFile = item.localFilePath?.let(::File)?.takeIf { it.exists() }

            if (item.status != DownloadStatus.COMPLETED || localFile == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (item.status == DownloadStatus.FAILED) {
                            item.errorMessage ?: "Download failed"
                        } else {
                            "Downloading for offline access…"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (item.status == DownloadStatus.FAILED) {
                        Button(onClick = { onRetryItem(item.id) }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                when (item.mediaType) {
                    MediaType.VIDEO -> OfflineVideoPlayer(file = localFile)
                    MediaType.IMAGE -> ZoomableOfflineImage(file = localFile, title = item.title)
                    MediaType.AUDIO -> OfflineAudioPlayer(file = localFile, item = item)
                    MediaType.DOCUMENT, MediaType.OTHER -> OfflineDocumentStage(
                        item = item,
                        onOpenExternal = { onOpenExternal(item.id) }
                    )
                }
            }
        }

        // Notes preview if present
        if (item.notes.isNotBlank()) {
            Surface(
                color = Color(0xFF141B29),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(10.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom Offline Action Bar
        Surface(
            color = Color(0xFF111827),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerActionItem(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    label = "Folder",
                    onClick = { onMoveToFolder(item.id) }
                )
                if (item.isAvailableOffline) {
                    ViewerActionItem(
                        icon = Icons.Rounded.Download,
                        label = "Save",
                        onClick = { onExportToDevice(item.id) }
                    )
                    ViewerActionItem(
                        icon = Icons.Rounded.Share,
                        label = "Share",
                        onClick = { onShareFile(item.id) }
                    )
                    ViewerActionItem(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = "Open",
                        onClick = { onOpenExternal(item.id) }
                    )
                }
                ViewerActionItem(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Delete",
                    tint = Color(0xFFF87171),
                    onClick = {
                        onDeleteItem(item.id)
                        onBack()
                    }
                )
            }
        }
    }

    if (showEditDialog) {
        EditItemDetailsDialog(
            item = item,
            onDismiss = { showEditDialog = false },
            onSave = { title, notes ->
                onUpdateDetails(item.id, title, notes)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ViewerActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun OfflineVideoPlayer(file: File) {
    val context = LocalContext.current
    val exoPlayer = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("offline_video_player")
    )
}

@Composable
private fun OfflineAudioPlayer(file: File, item: SavedItemEntity) {
    val context = LocalContext.current
    val exoPlayer = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = mediaTypeTint(MediaType.AUDIO).copy(alpha = 0.18f),
            border = BorderStroke(2.dp, mediaTypeTint(MediaType.AUDIO).copy(alpha = 0.45f)),
            modifier = Modifier.size(132.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = mediaTypeIcon(MediaType.AUDIO),
                    contentDescription = "Offline Audio",
                    tint = mediaTypeTint(MediaType.AUDIO),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (item.durationMs > 0L) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Duration ${formatDuration(item.durationMs)} · Offline Audio",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 0
                    controllerAutoShow = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
    }
}

@Composable
private fun ZoomableOfflineImage(file: File, title: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .testTag("offline_image_viewer")
        )
    }
}

@Composable
private fun OfflineDocumentStage(
    item: SavedItemEntity,
    onOpenExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = mediaTypeTint(item.mediaType).copy(alpha = 0.16f)
        ) {
            Icon(
                imageVector = mediaTypeIcon(item.mediaType),
                contentDescription = item.mediaType.label,
                tint = mediaTypeTint(item.mediaType),
                modifier = Modifier
                    .padding(28.dp)
                    .size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${item.mimeType} · ${formatBytes(item.fileSizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(22.dp))
        OutlinedButton(onClick = onOpenExternal) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_external))
        }
    }
}
