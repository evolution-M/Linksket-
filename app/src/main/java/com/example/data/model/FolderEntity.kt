package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconKey: String = "folder",
    val colorHex: Long = 0xFF3B82F6,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class FolderWithStats(
    val folder: FolderEntity,
    val itemCount: Int,
    val totalBytes: Long,
    val previewThumbnails: List<String>
)

enum class FolderIconOption(val key: String, val label: String) {
    FOLDER("folder", "Folder"),
    VIDEO("video", "Reels & Clips"),
    PHOTO("photo", "Gallery"),
    MUSIC("music", "Audio"),
    BOOKMARK("bookmark", "Saved"),
    STAR("star", "Favorites"),
    WORK("work", "Projects"),
    ARCHIVE("archive", "Archive")
}
