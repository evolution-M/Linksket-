package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val totalSeconds = ms / 1000L
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatShortDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

fun folderIconVector(key: String): ImageVector = when (key) {
    "video" -> Icons.Rounded.PlayCircle
    "photo" -> Icons.Rounded.PhotoLibrary
    "music" -> Icons.Rounded.MusicNote
    "bookmark" -> Icons.Rounded.Bookmark
    "star" -> Icons.Rounded.Star
    "work" -> Icons.Rounded.Work
    "archive" -> Icons.Rounded.Archive
    else -> Icons.Rounded.Folder
}

fun mediaTypeIcon(type: MediaType): ImageVector = when (type) {
    MediaType.VIDEO -> Icons.Rounded.PlayCircle
    MediaType.IMAGE -> Icons.Rounded.Image
    MediaType.AUDIO -> Icons.Rounded.Audiotrack
    MediaType.DOCUMENT -> Icons.Rounded.Description
    MediaType.OTHER -> Icons.Rounded.InsertDriveFile
}

fun mediaTypeTint(type: MediaType): Color = when (type) {
    MediaType.VIDEO -> Color(0xFFFF6B4A)
    MediaType.IMAGE -> Color(0xFF3B82F6)
    MediaType.AUDIO -> Color(0xFF8B5CF6)
    MediaType.DOCUMENT -> Color(0xFF10B981)
    MediaType.OTHER -> Color(0xFF64748B)
}
