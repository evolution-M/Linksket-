package com.example.data.download

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.MimeTypeMap
import com.example.data.local.SavedItemDao
import com.example.data.model.DownloadStatus
import com.example.data.model.MediaType
import com.example.data.model.SavedItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class DownloadEngine(
    private val context: Context,
    private val client: OkHttpClient,
    private val linkResolver: LinkResolver,
    private val savedItemDao: SavedItemDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val vaultRootDir: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    private val thumbsDir: File
        get() = File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun enqueueDownload(
        itemId: Long,
        directUrl: String?,
        wifiOnly: Boolean,
        onCompleted: (suspend (SavedItemEntity) -> Unit)? = null
    ) {
        activeJobs[itemId]?.cancel()
        val job = scope.launch {
            runCatching {
                executeDownload(itemId, directUrl, wifiOnly, onCompleted)
            }.onFailure { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) {
                    savedItemDao.updateStatusError(itemId, DownloadStatus.FAILED, "Download cancelled")
                } else {
                    savedItemDao.updateStatusError(
                        itemId,
                        DownloadStatus.FAILED,
                        throwable.message ?: "Download failed"
                    )
                }
            }
            activeJobs.remove(itemId)
        }
        activeJobs[itemId] = job
    }

    fun cancelDownload(itemId: Long) {
        activeJobs[itemId]?.cancel()
        activeJobs.remove(itemId)
    }

    private suspend fun executeDownload(
        itemId: Long,
        overrideDirectUrl: String?,
        wifiOnly: Boolean,
        onCompleted: (suspend (SavedItemEntity) -> Unit)?
    ) {
        val item = savedItemDao.getItemById(itemId) ?: return

        if (wifiOnly && !isOnWifi()) {
            savedItemDao.updateStatusError(
                itemId,
                DownloadStatus.FAILED,
                "Wi-Fi only mode is enabled. Connect to Wi-Fi and tap Retry."
            )
            return
        }

        savedItemDao.updateDownloadProgress(itemId, DownloadStatus.RESOLVING, 0f, 0L, 0L)

        var targetUrl = overrideDirectUrl?.takeIf { it.isNotBlank() } ?: item.resolvedMediaUrl
        var resolvedTitle = item.title
        var resolvedType = item.mediaType

        if (targetUrl.isBlank()) {
            val inspection = linkResolver.inspectLink(item.sourceUrl).getOrThrow()
            val primary = inspection.candidates.firstOrNull()
                ?: throw IllegalStateException("No downloadable media found at link.")
            targetUrl = primary.url
            if (resolvedTitle.isBlank() || resolvedTitle == item.domain) {
                resolvedTitle = primary.title
            }
            resolvedType = primary.mediaType
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
            )
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} while downloading")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentType = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?: item.mimeType.ifBlank { "application/octet-stream" }

            val finalMediaType = MediaType.fromMimeAndUrl(contentType, targetUrl)
                .takeIf { it != MediaType.OTHER } ?: resolvedType

            val totalBytes = body.contentLength().takeIf { it > 0L } ?: 0L
            val extension = resolveExtension(contentType, targetUrl, finalMediaType)
            val folderDir = File(vaultRootDir, "folder_${item.folderId}").apply { mkdirs() }
            val safeName = sanitizeFileName(resolvedTitle).ifBlank { "media_$itemId" }
            val outFile = uniqueFile(folderDir, safeName, extension)

            savedItemDao.updateDownloadProgress(itemId, DownloadStatus.DOWNLOADING, 0f, 0L, totalBytes)

            var downloaded = 0L
            var lastEmit = 0L

            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 150L) {
                            lastEmit = now
                            val progress = if (totalBytes > 0L) {
                                (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
                            } else {
                                -1f // Indeterminate
                            }
                            savedItemDao.updateDownloadProgress(
                                itemId,
                                DownloadStatus.DOWNLOADING,
                                progress,
                                downloaded,
                                maxOf(totalBytes, downloaded)
                            )
                        }
                    }
                    output.flush()
                }
            }

            val finalSize = outFile.length()
            var thumbPath: String? = null
            var durationMs = 0L

            when (finalMediaType) {
                MediaType.IMAGE -> thumbPath = outFile.absolutePath
                MediaType.VIDEO -> {
                    val meta = extractVideoMetadata(outFile, itemId)
                    thumbPath = meta.first
                    durationMs = meta.second
                }
                MediaType.AUDIO -> {
                    durationMs = extractAudioDuration(outFile)
                }
                else -> Unit
            }

            val updated = item.copy(
                title = resolvedTitle,
                resolvedMediaUrl = targetUrl,
                mediaType = finalMediaType,
                mimeType = contentType,
                localFilePath = outFile.absolutePath,
                thumbnailPath = thumbPath,
                fileSizeBytes = finalSize,
                downloadedBytes = finalSize,
                durationMs = durationMs,
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                errorMessage = null,
                completedAt = System.currentTimeMillis()
            )
            savedItemDao.updateItem(updated)
            onCompleted?.invoke(updated)
        }
    }

    fun extractVideoMetadata(videoFile: File, itemId: Long): Pair<String?, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            var thumbFilePath: String? = null
            if (frame != null) {
                val thumbFile = File(thumbsDir, "thumb_${itemId}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(thumbFile).use { fos ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                }
                frame.recycle()
                thumbFilePath = thumbFile.absolutePath
            }
            thumbFilePath to duration
        } catch (_: Exception) {
            null to 0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun extractAudioDuration(audioFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resolveExtension(mimeType: String, url: String, mediaType: MediaType): String {
        val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (!extFromMime.isNullOrBlank()) return extFromMime

        val cleanPath = url.substringBefore('?').substringBefore('#')
        val extFromUrl = cleanPath.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        if (extFromUrl != null) return extFromUrl

        return when (mediaType) {
            MediaType.VIDEO -> "mp4"
            MediaType.IMAGE -> "jpg"
            MediaType.AUDIO -> "mp3"
            MediaType.DOCUMENT -> "pdf"
            MediaType.OTHER -> "bin"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[^a-zA-Z0-9._ -]"""), "_")
            .trim()
            .take(48)
    }

    private fun uniqueFile(dir: File, baseName: String, ext: String): File {
        var candidate = File(dir, "$baseName.$ext")
        var idx = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$idx.$ext")
            idx++
        }
        return candidate
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
