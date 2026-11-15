package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaType
import com.example.data.model.SavedItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DeviceExportHelper {

    suspend fun exportToDeviceStorage(
        context: Context,
        item: SavedItemEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val localPath = item.localFilePath
                ?: throw IllegalStateException("File is not downloaded yet.")
            val sourceFile = File(localPath)
            if (!sourceFile.exists()) {
                throw IllegalStateException("Offline file not found on device.")
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw IllegalStateException("Use Share on Android 9 or older to save externally.")
            }

            val fileName = sourceFile.name
            val mimeType = item.mimeType.ifBlank { "application/octet-stream" }

            val (collectionUri, relativeDir) = when (item.mediaType) {
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                    "${Environment.DIRECTORY_MOVIES}/LinkStash"
                MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                    "${Environment.DIRECTORY_PICTURES}/LinkStash"
                MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                    "${Environment.DIRECTORY_MUSIC}/LinkStash"
                MediaType.DOCUMENT, MediaType.OTHER -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to
                    "${Environment.DIRECTORY_DOWNLOADS}/LinkStash"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val insertedUri = resolver.insert(collectionUri, values)
                ?: throw IllegalStateException("Could not create MediaStore entry.")

            try {
                resolver.openOutputStream(insertedUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Could not open output stream.")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(insertedUri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(insertedUri, null, null)
                throw e
            }

            "Saved to $relativeDir/$fileName"
        }
    }

    suspend fun importContentUriToVault(
        context: Context,
        uri: Uri,
        folderId: Long,
        downloadEngine: DownloadEngine
    ): Result<SavedItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var displayName = queryDisplayName(context, uri) ?: "imported_${System.currentTimeMillis()}"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: displayName.substringAfterLast('.', "bin")

            if (!displayName.contains('.')) {
                displayName = "$displayName.$ext"
            }

            val folderDir = File(context.filesDir, "vault/folder_$folderId").apply { mkdirs() }
            var outFile = File(folderDir, displayName)
            var counter = 1
            val baseName = displayName.substringBeforeLast('.')
            while (outFile.exists()) {
                outFile = File(folderDir, "${baseName}_$counter.$ext")
                counter++
            }

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")

            val mediaType = MediaType.fromMimeAndUrl(mimeType, outFile.name)
            val size = outFile.length()
            val tempId = System.currentTimeMillis()
            var thumbPath: String? = null
            var durationMs = 0L

            when (mediaType) {
                MediaType.IMAGE -> thumbPath = outFile.absolutePath
                MediaType.VIDEO -> {
                    val meta = downloadEngine.extractVideoMetadata(outFile, tempId)
                    thumbPath = meta.first
                    durationMs = meta.second
                }
                MediaType.AUDIO -> durationMs = downloadEngine.extractAudioDuration(outFile)
                else -> Unit
            }

            SavedItemEntity(
                folderId = folderId,
                title = baseName.replace('_', ' ').replace('-', ' '),
                sourceUrl = "device://local",
                resolvedMediaUrl = uri.toString(),
                domain = "Device Import",
                mediaType = mediaType,
                mimeType = mimeType,
                localFilePath = outFile.absolutePath,
                thumbnailPath = thumbPath,
                fileSizeBytes = size,
                downloadedBytes = size,
                durationMs = durationMs,
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    fun shareOfflineFile(context: Context, item: SavedItemEntity): Result<Unit> = runCatching {
        val file = item.localFilePath?.let(::File)
            ?.takeIf { it.exists() }
            ?: throw IllegalStateException("Offline file is unavailable.")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ${item.title}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openWithExternalApp(context: Context, item: SavedItemEntity): Result<Unit> = runCatching {
        val file = item.localFilePath?.let(::File)
            ?.takeIf { it.exists() }
            ?: throw IllegalStateException("Offline file is unavailable.")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }
}
