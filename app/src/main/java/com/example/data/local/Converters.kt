package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaType

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.OTHER)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.QUEUED)
}
