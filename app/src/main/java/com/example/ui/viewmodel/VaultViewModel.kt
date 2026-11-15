package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.LinkStashApplication
import com.example.data.local.AppSettings
import com.example.data.model.DownloadStatus
import com.example.data.model.FolderEntity
import com.example.data.model.FolderWithStats
import com.example.data.model.LinkInspectionResult
import com.example.data.model.MediaCandidate
import com.example.data.model.MediaType
import com.example.data.model.SavedItemEntity
import com.example.data.repository.VaultRepository
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"),
    VIDEO("Videos"),
    IMAGE("Images"),
    AUDIO("Audio"),
    DOCUMENT("Docs"),
    FAVORITES("Favorites")
}

data class AddLinkSheetState(
    val isVisible: Boolean = false,
    val urlInput: String = "",
    val customTitle: String = "",
    val selectedFolderId: Long = 1L,
    val isInspecting: Boolean = false,
    val inspectionResult: LinkInspectionResult? = null,
    val selectedCandidateIndex: Int = 0,
    val inspectionError: String? = null
)

data class VaultUiState(
    val allItems: List<SavedItemEntity> = emptyList(),
    val filteredItems: List<SavedItemEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val foldersWithStats: List<FolderWithStats> = emptyList(),
    val folderLookup: Map<Long, FolderEntity> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val selectedItemIds: Set<Long> = emptySet(),
    val isOnline: Boolean = true
) {
    val isSelectionMode: Boolean get() = selectedItemIds.isNotEmpty()
    val totalOfflineBytes: Long
        get() = allItems.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.fileSizeBytes }
    val offlineReadyCount: Int
        get() = allItems.count { it.status == DownloadStatus.COMPLETED }
    val activeDownloadsCount: Int
        get() = allItems.count {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.RESOLVING ||
                it.status == DownloadStatus.DOWNLOADING
        }
}

class VaultViewModel(
    application: Application,
    private val repository: VaultRepository
) : AndroidViewModel(application) {

    private val searchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow(LibraryFilter.ALL)
    private val selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    private val isOnline = MutableStateFlow(checkCurrentNetwork(application))

    private val _addLinkState = MutableStateFlow(AddLinkSheetState())
    val addLinkState: StateFlow<AddLinkSheetState> = _addLinkState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isOnline.value = true
        }

        override fun onLost(network: Network) {
            isOnline.value = checkCurrentNetwork(getApplication())
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
    }

    private val filterControlsFlow = combine(
        searchQuery,
        activeFilter,
        selectedItemIds,
        isOnline
    ) { query, filter, selected, online ->
        FilterControls(query, filter, selected, online)
    }

    val uiState: StateFlow<VaultUiState> = combine(
        repository.allItems,
        repository.allFolders,
        repository.foldersWithStats,
        repository.settings,
        filterControlsFlow
    ) { items, folders, folderStats, settings, controls ->
        val folderMap = folders.associateBy { it.id }
        val q = controls.query.trim().lowercase()

        val filtered = items.filter { item ->
            val matchesFilter = when (controls.filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.VIDEO -> item.mediaType == MediaType.VIDEO
                LibraryFilter.IMAGE -> item.mediaType == MediaType.IMAGE
                LibraryFilter.AUDIO -> item.mediaType == MediaType.AUDIO
                LibraryFilter.DOCUMENT -> item.mediaType == MediaType.DOCUMENT || item.mediaType == MediaType.OTHER
                LibraryFilter.FAVORITES -> item.isFavorite
            }
            val folderName = folderMap[item.folderId]?.name?.lowercase().orEmpty()
            val matchesQuery = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.domain.lowercase().contains(q) ||
                item.notes.lowercase().contains(q) ||
                folderName.contains(q)
            matchesFilter && matchesQuery
        }

        VaultUiState(
            allItems = items,
            filteredItems = filtered,
            folders = folders,
            foldersWithStats = folderStats,
            folderLookup = folderMap,
            settings = settings,
            searchQuery = controls.query,
            activeFilter = controls.filter,
            selectedItemIds = controls.selectedIds,
            isOnline = controls.isOnline
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    fun observeFolder(folderId: Long) = repository.observeFolder(folderId)
    fun observeItemsInFolder(folderId: Long) = repository.observeItemsInFolder(folderId)
    fun observeItem(itemId: Long) = repository.observeItem(itemId)

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onFilterChange(filter: LibraryFilter) {
        activeFilter.value = filter
    }

    fun toggleItemSelection(itemId: Long) {
        selectedItemIds.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun clearSelection() {
        selectedItemIds.value = emptySet()
    }

    fun selectAll(ids: List<Long>) {
        selectedItemIds.value = ids.toSet()
    }

    fun openAddLinkSheet(prefillUrl: String = "", targetFolderId: Long? = null) {
        val defaultFolder = targetFolderId
            ?: uiState.value.folders.firstOrNull { it.id == uiState.value.settings.defaultFolderId }?.id
            ?: uiState.value.folders.firstOrNull()?.id
            ?: 1L
        val extracted = if (prefillUrl.isNotBlank()) {
            repository.extractFirstUrl(prefillUrl) ?: prefillUrl.trim()
        } else ""

        _addLinkState.value = AddLinkSheetState(
            isVisible = true,
            urlInput = extracted,
            selectedFolderId = defaultFolder
        )
        if (extracted.isNotBlank()) {
            inspectCurrentUrl()
        }
    }

    fun closeAddLinkSheet() {
        _addLinkState.update { it.copy(isVisible = false, isInspecting = false) }
    }

    fun updateUrlInput(value: String) {
        _addLinkState.update {
            it.copy(
                urlInput = value,
                inspectionError = null,
                inspectionResult = null,
                selectedCandidateIndex = 0
            )
        }
    }

    fun updateCustomTitle(value: String) {
        _addLinkState.update { it.copy(customTitle = value) }
    }

    fun updateTargetFolder(folderId: Long) {
        _addLinkState.update { it.copy(selectedFolderId = folderId) }
    }

    fun selectCandidate(index: Int) {
        _addLinkState.update { state ->
            val candidate = state.inspectionResult?.candidates?.getOrNull(index)
            state.copy(
                selectedCandidateIndex = index,
                customTitle = candidate?.title ?: state.customTitle
            )
        }
    }

    fun inspectCurrentUrl() {
        val raw = _addLinkState.value.urlInput
        if (raw.isBlank()) {
            _addLinkState.update { it.copy(inspectionError = "Paste a link first.") }
            return
        }
        viewModelScope.launch {
            _addLinkState.update { it.copy(isInspecting = true, inspectionError = null) }
            val result = repository.inspectLink(raw)
            result.onSuccess { info ->
                _addLinkState.update { state ->
                    state.copy(
                        isInspecting = false,
                        inspectionResult = info,
                        selectedCandidateIndex = 0,
                        customTitle = state.customTitle.ifBlank { info.candidates.firstOrNull()?.title ?: info.pageTitle }
                    )
                }
            }.onFailure { err ->
                _addLinkState.update {
                    it.copy(
                        isInspecting = false,
                        inspectionError = err.message ?: "Could not preview this link."
                    )
                }
            }
        }
    }

    fun startDownloadFromSheet() {
        val state = _addLinkState.value
        val cleanUrl = repository.extractFirstUrl(state.urlInput)
        if (cleanUrl == null) {
            _addLinkState.update { it.copy(inspectionError = "Please enter a valid http or https link.") }
            return
        }

        val candidate: MediaCandidate? = state.inspectionResult?.candidates?.getOrNull(state.selectedCandidateIndex)
        val folderId = state.selectedFolderId
        val customTitle = state.customTitle.ifBlank { null }

        viewModelScope.launch {
            repository.setDefaultFolderId(folderId)
            repository.startDownloadFromLink(
                sourceUrl = cleanUrl,
                folderId = folderId,
                customTitle = customTitle,
                candidate = candidate
            )
            _addLinkState.value = AddLinkSheetState(isVisible = false)
            _snackbarMessages.tryEmit("Download started for offline access")
        }
    }

    fun handleIncomingSharedText(sharedText: String) {
        openAddLinkSheet(prefillUrl = sharedText)
    }

    fun handleIncomingSharedUri(uri: Uri, targetFolderId: Long? = null) {
        viewModelScope.launch {
            val folderId = targetFolderId
                ?: uiState.value.folders.firstOrNull()?.id
                ?: 1L
            repository.importDeviceUri(uri, folderId)
                .onSuccess {
                    _snackbarMessages.tryEmit("Saved media into offline folder")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.message ?: "Could not import shared media")
                }
        }
    }

    fun importMultipleUris(uris: List<Uri>, folderId: Long) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            uris.forEach { uri ->
                if (repository.importDeviceUri(uri, folderId).isSuccess) {
                    successCount++
                }
            }
            if (successCount > 0) {
                _snackbarMessages.tryEmit("Imported $successCount item(s) to offline folder")
            } else {
                _snackbarMessages.tryEmit("Could not import selected media")
            }
        }
    }

    fun createFolder(
        name: String,
        iconKey: String,
        colorHex: Long,
        isPinned: Boolean,
        alsoSelectForSheet: Boolean = false
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createFolder(name, iconKey, colorHex, isPinned)
            if (alsoSelectForSheet) {
                _addLinkState.update { it.copy(selectedFolderId = newId) }
            }
            _snackbarMessages.tryEmit("Created folder \"$name\"")
        }
    }

    fun updateFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.updateFolder(folder)
            _snackbarMessages.tryEmit("Updated folder \"${folder.name}\"")
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            _snackbarMessages.tryEmit("Folder deleted")
        }
    }

    fun moveItemsToFolder(itemIds: List<Long>, targetFolderId: Long) {
        viewModelScope.launch {
            repository.moveItemsToFolder(itemIds, targetFolderId)
            clearSelection()
            val folderName = uiState.value.folderLookup[targetFolderId]?.name ?: "folder"
            _snackbarMessages.tryEmit("Moved ${itemIds.size} item(s) to $folderName")
        }
    }

    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
        }
    }

    fun updateItemDetails(itemId: Long, title: String, notes: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.updateItemDetails(itemId, title, notes)
            _snackbarMessages.tryEmit("Saved changes")
        }
    }

    fun deleteItems(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(itemIds)
            clearSelection()
            _snackbarMessages.tryEmit("Removed ${itemIds.size} item(s) from device")
        }
    }

    fun retryDownload(itemId: Long) {
        viewModelScope.launch {
            repository.retryDownload(itemId)
            _snackbarMessages.tryEmit("Retrying download…")
        }
    }

    fun cancelDownload(itemId: Long) {
        repository.cancelDownload(itemId)
    }

    fun exportToDevice(itemId: Long) {
        viewModelScope.launch {
            repository.exportToDevice(itemId)
                .onSuccess { msg -> _snackbarMessages.tryEmit(msg) }
                .onFailure { err -> _snackbarMessages.tryEmit(err.message ?: "Export failed") }
        }
    }

    fun shareItem(itemId: Long) {
        viewModelScope.launch {
            repository.shareItem(itemId)
                .onFailure { err -> _snackbarMessages.tryEmit(err.message ?: "Unable to share") }
        }
    }

    fun openExternal(itemId: Long) {
        viewModelScope.launch {
            repository.openExternal(itemId)
                .onFailure { err -> _snackbarMessages.tryEmit(err.message ?: "No compatible app found") }
        }
    }

    fun toggleGridView() {
        viewModelScope.launch {
            repository.setGridView(!uiState.value.settings.isGridView)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { repository.setWifiOnly(enabled) }
    }

    fun setAutoExport(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoExport(enabled) }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { repository.setThemePreference(theme) }
    }

    private data class FilterControls(
        val query: String,
        val filter: LibraryFilter,
        val selectedIds: Set<Long>,
        val isOnline: Boolean
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LinkStashApplication
                VaultViewModel(app, app.repository)
            }
        }

        private fun checkCurrentNetwork(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
