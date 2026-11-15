package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_items",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class SavedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val folderId: Long,
    val title: String,
    val sourceUrl: String,
    val resolvedMediaUrl: String = "",
    val domain: String,
    val mediaType: MediaType,
    val mimeType: String,
    val localFilePath: String? = null,
    val thumbnailPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val durationMs: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isAvailableOffline: Boolean
        get() = status == DownloadStatus.COMPLETED && !localFilePath.isNullOrBlank()
}
