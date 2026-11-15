package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.download.DeviceExportHelper
import com.example.data.download.DownloadEngine
import com.example.data.download.LinkResolver
import com.example.data.local.AppSettings
import com.example.data.local.FolderDao
import com.example.data.local.SavedItemDao
import com.example.data.local.UserPreferences
import com.example.data.model.DownloadStatus
import com.example.data.model.FolderEntity
import com.example.data.model.FolderWithStats
import com.example.data.model.LinkInspectionResult
import com.example.data.model.MediaCandidate
import com.example.data.model.MediaType
import com.example.data.model.SavedItemEntity
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File

class VaultRepository(
    private val context: Context,
    private val folderDao: FolderDao,
    private val savedItemDao: SavedItemDao,
    private val linkResolver: LinkResolver,
    private val downloadEngine: DownloadEngine,
    private val userPreferences: UserPreferences
) {
    val allFolders: Flow<List<FolderEntity>> = folderDao.observeAllFolders()
    val allItems: Flow<List<SavedItemEntity>> = savedItemDao.observeAllItems()
    val settings: Flow<AppSettings> = userPreferences.settingsFlow

    val foldersWithStats: Flow<List<FolderWithStats>> = combine(allFolders, allItems) { folders, items ->
        val grouped = items.groupBy { it.folderId }
        folders.map { folder ->
            val folderItems = grouped[folder.id].orEmpty()
            val completed = folderItems.filter { it.status == DownloadStatus.COMPLETED }
            val previews = completed.mapNotNull { it.thumbnailPath ?: it.localFilePath }
                .filter { File(it).exists() }
                .take(4)
            FolderWithStats(
                folder = folder,
                itemCount = folderItems.size,
                totalBytes = completed.sumOf { it.fileSizeBytes },
                previewThumbnails = previews
            )
        }
    }

    fun observeFolder(folderId: Long): Flow<FolderEntity?> = folderDao.observeFolderById(folderId)

    fun observeItemsInFolder(folderId: Long): Flow<List<SavedItemEntity>> =
        savedItemDao.observeItemsByFolder(folderId)

    fun observeItem(itemId: Long): Flow<SavedItemEntity?> = savedItemDao.observeItemById(itemId)

    suspend fun ensureSeededFolders() {
        if (folderDao.countFolders() == 0) {
            val defaults = listOf(
                FolderEntity(
                    name = "Inbox",
                    iconKey = "bookmark",
                    colorHex = 0xFF3B82F6,
                    isPinned = true,
                    sortOrder = 0
                ),
                FolderEntity(
                    name = "Videos",
                    iconKey = "video",
                    colorHex = 0xFFFF6B4A,
                    isPinned = false,
                    sortOrder = 1
                ),
                FolderEntity(
                    name = "Inspiration",
                    iconKey = "photo",
                    colorHex = 0xFF10B981,
                    isPinned = false,
                    sortOrder = 2
                ),
                FolderEntity(
                    name = "Audio & Docs",
                    iconKey = "music",
                    colorHex = 0xFF8B5CF6,
                    isPinned = false,
                    sortOrder = 3
                )
            )
            folderDao.insertFolders(defaults)
            val first = folderDao.getFirstFolder()
            if (first != null) {
                userPreferences.setDefaultFolderId(first.id)
            }
        }
    }

    fun extractFirstUrl(rawText: String): String? = linkResolver.extractFirstUrl(rawText)

    suspend fun inspectLink(url: String): Result<LinkInspectionResult> = linkResolver.inspectLink(url)

    suspend fun startDownloadFromLink(
        sourceUrl: String,
        folderId: Long,
        customTitle: String?,
        candidate: MediaCandidate? = null
    ): Long {
        val cleanUrl = linkResolver.extractFirstUrl(sourceUrl) ?: sourceUrl.trim()
        val domain = linkResolver.extractDomain(cleanUrl)
        val initialTitle = customTitle?.takeIf { it.isNotBlank() }
            ?: candidate?.title
            ?: linkResolver.deriveTitleFromUrl(cleanUrl, domain)
        val initialType = candidate?.mediaType ?: MediaType.fromMimeAndUrl(null, cleanUrl)

        val entity = SavedItemEntity(
            folderId = folderId,
            title = initialTitle,
            sourceUrl = cleanUrl,
            resolvedMediaUrl = candidate?.url.orEmpty(),
            domain = domain,
            mediaType = initialType,
            mimeType = candidate?.mimeType.orEmpty(),
            status = DownloadStatus.QUEUED
        )
        val id = savedItemDao.insertItem(entity)
        val prefs = userPreferences.settingsFlow.first()

        downloadEngine.enqueueDownload(
            itemId = id,
            directUrl = candidate?.url,
            wifiOnly = prefs.wifiOnlyDownloads,
            onCompleted = { completedItem ->
                if (prefs.autoExportToGallery) {
                    DeviceExportHelper.exportToDeviceStorage(context, completedItem)
                }
            }
        )
        return id
    }

    suspend fun retryDownload(itemId: Long) {
        val item = savedItemDao.getItemById(itemId) ?: return
        val prefs = userPreferences.settingsFlow.first()
        downloadEngine.enqueueDownload(
            itemId = itemId,
            directUrl = item.resolvedMediaUrl.takeIf { it.isNotBlank() },
            wifiOnly = prefs.wifiOnlyDownloads
        )
    }

    fun cancelDownload(itemId: Long) {
        downloadEngine.cancelDownload(itemId)
    }

    suspend fun createFolder(name: String, iconKey: String, colorHex: Long, isPinned: Boolean): Long {
        val count = folderDao.countFolders()
        return folderDao.insertFolder(
            FolderEntity(
                name = name.trim(),
                iconKey = iconKey,
                colorHex = colorHex,
                isPinned = isPinned,
                sortOrder = count
            )
        )
    }

    suspend fun updateFolder(folder: FolderEntity) {
        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folderId: Long) {
        val items = savedItemDao.getItemsByFolderOnce(folderId)
        items.forEach { deleteLocalFiles(it) }
        folderDao.deleteFolderById(folderId)
    }

    suspend fun moveItemsToFolder(itemIds: List<Long>, targetFolderId: Long) {
        if (itemIds.isNotEmpty()) {
            savedItemDao.moveItemsToFolder(itemIds, targetFolderId)
        }
    }

    suspend fun toggleFavorite(itemId: Long) {
        savedItemDao.toggleFavorite(itemId)
    }

    suspend fun updateItemDetails(itemId: Long, title: String, notes: String) {
        savedItemDao.updateTitleAndNotes(itemId, title.trim(), notes.trim())
    }

    suspend fun deleteItems(itemIds: List<Long>) {
        itemIds.forEach { id ->
            savedItemDao.getItemById(id)?.let { item ->
                downloadEngine.cancelDownload(id)
                deleteLocalFiles(item)
            }
        }
        savedItemDao.deleteItemsByIds(itemIds)
    }

    suspend fun clearCompletedFromQueue(completedIds: List<Long>) {
        // Keep items in library? Wait: in Queue tab we allow removing failed/cancelled or clearing all completed files if user confirms
        deleteItems(completedIds)
    }

    suspend fun importDeviceUri(uri: Uri, folderId: Long): Result<Long> {
        return DeviceExportHelper.importContentUriToVault(context, uri, folderId, downloadEngine)
            .map { entity -> savedItemDao.insertItem(entity) }
    }

    suspend fun exportToDevice(itemId: Long): Result<String> {
        val item = savedItemDao.getItemById(itemId)
            ?: return Result.failure(IllegalStateException("Item not found"))
        return DeviceExportHelper.exportToDeviceStorage(context, item)
    }

    suspend fun shareItem(itemId: Long): Result<Unit> {
        val item = savedItemDao.getItemById(itemId)
            ?: return Result.failure(IllegalStateException("Item not found"))
        return DeviceExportHelper.shareOfflineFile(context, item)
    }

    suspend fun openExternal(itemId: Long): Result<Unit> {
        val item = savedItemDao.getItemById(itemId)
            ?: return Result.failure(IllegalStateException("Item not found"))
        return DeviceExportHelper.openWithExternalApp(context, item)
    }

    suspend fun setGridView(isGrid: Boolean) = userPreferences.setGridView(isGrid)
    suspend fun setWifiOnly(wifiOnly: Boolean) = userPreferences.setWifiOnly(wifiOnly)
    suspend fun setAutoExport(autoExport: Boolean) = userPreferences.setAutoExport(autoExport)
    suspend fun setThemePreference(theme: ThemePreference) = userPreferences.setThemePreference(theme)
    suspend fun setDefaultFolderId(folderId: Long) = userPreferences.setDefaultFolderId(folderId)

    private fun deleteLocalFiles(item: SavedItemEntity) {
        item.localFilePath?.let { path -> runCatching { File(path).delete() } }
        item.thumbnailPath?.let { path ->
            if (path != item.localFilePath) {
                runCatching { File(path).delete() }
            }
        }
    }
}
