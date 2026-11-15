package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.FolderEntity
import com.example.ui.components.AddLinkBottomSheet
import com.example.ui.components.FolderEditorDialog
import com.example.ui.components.MoveToFolderSheet
import com.example.ui.navigation.FolderDetailRoute
import com.example.ui.navigation.FoldersRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.QueueRoute
import com.example.ui.navigation.ViewerRoute
import com.example.ui.screens.FolderDetailScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.QueueAndStorageScreen
import com.example.ui.theme.LinkStashTheme
import com.example.ui.viewmodel.VaultViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VaultViewModel by viewModels { VaultViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            processShareIntent(intent)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LinkStashTheme(themePreference = uiState.settings.themePreference) {
                LinkStashApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processShareIntent(intent)
    }

    private fun processShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val type = intent.type.orEmpty()
        if (type.startsWith("text/")) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleIncomingSharedText(sharedText)
            }
        } else if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                viewModel.handleIncomingSharedUri(uri)
            }
        }
    }
}

@Composable
fun LinkStashApp(viewModel: VaultViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addLinkState by viewModel.addLinkState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var showFolderEditor by remember { mutableStateOf(false) }
    var folderBeingEdited by remember { mutableStateOf<FolderEntity?>(null) }
    var selectCreatedFolderInSheet by remember { mutableStateOf(false) }
    var moveSheetItemIds by remember { mutableStateOf<List<Long>?>(null) }
    var importTargetFolderId by remember { mutableLongStateOf(1L) }

    // Zero-permission Android Photo Picker for importing existing device media into offline folders
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleUris(uris, importTargetFolderId)
        }
    }

    fun launchImportForFolder(folderId: Long) {
        importTargetFolderId = folderId
        mediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    val isViewerRoute = currentDestination?.hasRoute<ViewerRoute>() == true
    val isTopLevel = currentDestination?.hasRoute<LibraryRoute>() == true ||
        currentDestination?.hasRoute<FoldersRoute>() == true ||
        currentDestination?.hasRoute<QueueRoute>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp && !isViewerRoute

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(
                    header = {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                ) {
                    NavigationRailItem(
                        selected = currentDestination?.hasRoute<LibraryRoute>() == true,
                        onClick = {
                            navController.navigate(LibraryRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentDestination?.hasRoute<LibraryRoute>() == true) {
                                    Icons.Rounded.VideoLibrary
                                } else {
                                    Icons.Outlined.VideoLibrary
                                },
                                contentDescription = stringResource(R.string.nav_library)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_library)) }
                    )
                    NavigationRailItem(
                        selected = currentDestination?.hasRoute<FoldersRoute>() == true ||
                            currentDestination?.hasRoute<FolderDetailRoute>() == true,
                        onClick = {
                            navController.navigate(FoldersRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentDestination?.hasRoute<FoldersRoute>() == true) {
                                    Icons.Rounded.Folder
                                } else {
                                    Icons.Outlined.Folder
                                },
                                contentDescription = stringResource(R.string.nav_folders)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_folders)) }
                    )
                    NavigationRailItem(
                        selected = currentDestination?.hasRoute<QueueRoute>() == true,
                        onClick = {
                            navController.navigate(QueueRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.activeDownloadsCount > 0) {
                                        Badge { Text("${uiState.activeDownloadsCount}") }
                                    }
                                }
                            ) {
                                Icon(
                                    if (currentDestination?.hasRoute<QueueRoute>() == true) {
                                        Icons.Rounded.DownloadForOffline
                                    } else {
                                        Icons.Outlined.DownloadForOffline
                                    },
                                    contentDescription = stringResource(R.string.nav_queue)
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.nav_queue)) }
                    )
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = if (isViewerRoute) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    if (!isViewerRoute && !uiState.isSelectionMode) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                val currentFolderArg = if (currentDestination?.hasRoute<FolderDetailRoute>() == true) {
                                    navBackStackEntry?.toRoute<FolderDetailRoute>()?.folderId
                                } else null
                                viewModel.openAddLinkSheet(targetFolderId = currentFolderArg)
                            },
                            icon = { Icon(Icons.Rounded.AddLink, contentDescription = null) },
                            text = { Text(stringResource(R.string.action_save_link)) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_save_link")
                        )
                    }
                },
                bottomBar = {
                    if (!useNavigationRail && isTopLevel) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<LibraryRoute>() == true,
                                onClick = {
                                    navController.navigate(LibraryRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (currentDestination?.hasRoute<LibraryRoute>() == true) {
                                            Icons.Rounded.VideoLibrary
                                        } else {
                                            Icons.Outlined.VideoLibrary
                                        },
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_library)) },
                                modifier = Modifier.testTag("nav_library_tab")
                            )

                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<FoldersRoute>() == true,
                                onClick = {
                                    navController.navigate(FoldersRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (currentDestination?.hasRoute<FoldersRoute>() == true) {
                                            Icons.Rounded.Folder
                                        } else {
                                            Icons.Outlined.Folder
                                        },
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_folders)) },
                                modifier = Modifier.testTag("nav_folders_tab")
                            )

                            NavigationBarItem(
                                selected = currentDestination?.hasRoute<QueueRoute>() == true,
                                onClick = {
                                    navController.navigate(QueueRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (uiState.activeDownloadsCount > 0) {
                                                Badge { Text("${uiState.activeDownloadsCount}") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (currentDestination?.hasRoute<QueueRoute>() == true) {
                                                Icons.Rounded.DownloadForOffline
                                            } else {
                                                Icons.Outlined.DownloadForOffline
                                            },
                                            contentDescription = null
                                        )
                                    }
                                },
                                label = { Text(stringResource(R.string.nav_queue)) },
                                modifier = Modifier.testTag("nav_queue_tab")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            state = uiState,
                            onSearchChange = viewModel::onSearchQueryChange,
                            onFilterChange = viewModel::onFilterChange,
                            onToggleGridView = viewModel::toggleGridView,
                            onOpenItem = { item -> navController.navigate(ViewerRoute(item.id)) },
                            onToggleSelection = viewModel::toggleItemSelection,
                            onClearSelection = viewModel::clearSelection,
                            onMoveSelected = { moveSheetItemIds = uiState.selectedItemIds.toList() },
                            onDeleteSelected = { viewModel.deleteItems(uiState.selectedItemIds.toList()) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onMoveSingle = { id -> moveSheetItemIds = listOf(id) },
                            onExportSingle = viewModel::exportToDevice,
                            onShareSingle = viewModel::shareItem,
                            onDeleteSingle = { id -> viewModel.deleteItems(listOf(id)) },
                            onRetrySingle = viewModel::retryDownload,
                            onAddLinkClick = { viewModel.openAddLinkSheet() },
                            onImportDeviceClick = {
                                val target = uiState.folders.firstOrNull()?.id ?: 1L
                                launchImportForFolder(target)
                            }
                        )
                    }

                    composable<FoldersRoute> {
                        FoldersScreen(
                            foldersWithStats = uiState.foldersWithStats,
                            onFolderClick = { folderId -> navController.navigate(FolderDetailRoute(folderId)) },
                            onCreateFolderClick = {
                                folderBeingEdited = null
                                selectCreatedFolderInSheet = false
                                showFolderEditor = true
                            },
                            onEditFolderClick = { folder ->
                                folderBeingEdited = folder
                                selectCreatedFolderInSheet = false
                                showFolderEditor = true
                            },
                            onDeleteFolderClick = { folder ->
                                viewModel.deleteFolder(folder.id)
                            }
                        )
                    }

                    composable<FolderDetailRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<FolderDetailRoute>()
                        val folder by viewModel.observeFolder(route.folderId).collectAsStateWithLifecycle(initialValue = null)
                        val items by viewModel.observeItemsInFolder(route.folderId).collectAsStateWithLifecycle(initialValue = emptyList())

                        FolderDetailScreen(
                            folder = folder,
                            items = items,
                            isGridView = uiState.settings.isGridView,
                            selectedItemIds = uiState.selectedItemIds,
                            onBack = { navController.popBackStack() },
                            onEditFolder = { f ->
                                folderBeingEdited = f
                                selectCreatedFolderInSheet = false
                                showFolderEditor = true
                            },
                            onAddLinkToFolder = { fId -> viewModel.openAddLinkSheet(targetFolderId = fId) },
                            onImportToFolder = { fId -> launchImportForFolder(fId) },
                            onOpenItem = { item -> navController.navigate(ViewerRoute(item.id)) },
                            onToggleSelection = viewModel::toggleItemSelection,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onMoveSingle = { id -> moveSheetItemIds = listOf(id) },
                            onExportSingle = viewModel::exportToDevice,
                            onShareSingle = viewModel::shareItem,
                            onDeleteSingle = { id -> viewModel.deleteItems(listOf(id)) },
                            onRetrySingle = viewModel::retryDownload
                        )
                    }

                    composable<QueueRoute> {
                        QueueAndStorageScreen(
                            state = uiState,
                            onAddLinkClick = { viewModel.openAddLinkSheet() },
                            onRetryItem = viewModel::retryDownload,
                            onCancelItem = viewModel::cancelDownload,
                            onDeleteItem = { id -> viewModel.deleteItems(listOf(id)) },
                            onOpenItem = { item -> navController.navigate(ViewerRoute(item.id)) },
                            onWifiOnlyChange = viewModel::setWifiOnly,
                            onAutoExportChange = viewModel::setAutoExport,
                            onThemeChange = viewModel::setThemePreference
                        )
                    }

                    composable<ViewerRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ViewerRoute>()
                        val item by viewModel.observeItem(route.itemId).collectAsStateWithLifecycle(initialValue = null)
                        val folder = item?.let { uiState.folderLookup[it.folderId] }

                        MediaViewerScreen(
                            item = item,
                            folder = folder,
                            onBack = { navController.popBackStack() },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onMoveToFolder = { id -> moveSheetItemIds = listOf(id) },
                            onExportToDevice = viewModel::exportToDevice,
                            onShareFile = viewModel::shareItem,
                            onOpenExternal = viewModel::openExternal,
                            onUpdateDetails = viewModel::updateItemDetails,
                            onDeleteItem = { id -> viewModel.deleteItems(listOf(id)) },
                            onRetryItem = viewModel::retryDownload
                        )
                    }
                }
            }
        }
    }

    // Add Link Modal Sheet (also opened automatically from Android Share Sheet!)
    AddLinkBottomSheet(
        state = addLinkState,
        folders = uiState.folders,
        onDismiss = viewModel::closeAddLinkSheet,
        onUrlChange = viewModel::updateUrlInput,
        onTitleChange = viewModel::updateCustomTitle,
        onFolderSelect = viewModel::updateTargetFolder,
        onCandidateSelect = viewModel::selectCandidate,
        onInspectClick = viewModel::inspectCurrentUrl,
        onDownloadClick = viewModel::startDownloadFromSheet,
        onCreateNewFolderClick = {
            folderBeingEdited = null
            selectCreatedFolderInSheet = true
            showFolderEditor = true
        }
    )

    // Create / Edit Custom Folder Dialog
    if (showFolderEditor) {
        FolderEditorDialog(
            initialFolder = folderBeingEdited,
            onDismiss = { showFolderEditor = false },
            onConfirm = { name, iconKey, colorHex, isPinned ->
                val existing = folderBeingEdited
                if (existing == null) {
                    viewModel.createFolder(
                        name = name,
                        iconKey = iconKey,
                        colorHex = colorHex,
                        isPinned = isPinned,
                        alsoSelectForSheet = selectCreatedFolderInSheet
                    )
                } else {
                    viewModel.updateFolder(
                        existing.copy(
                            name = name,
                            iconKey = iconKey,
                            colorHex = colorHex,
                            isPinned = isPinned
                        )
                    )
                }
                showFolderEditor = false
            }
        )
    }

    // Move Items to Custom Folder Sheet
    val pendingMoveIds = moveSheetItemIds
    if (pendingMoveIds != null) {
        MoveToFolderSheet(
            folders = uiState.folders,
            itemCount = pendingMoveIds.size,
            onDismiss = { moveSheetItemIds = null },
            onSelectFolder = { targetFolderId ->
                viewModel.moveItemsToFolder(pendingMoveIds, targetFolderId)
                moveSheetItemIds = null
            },
            onCreateNewFolder = {
                moveSheetItemIds = null
                folderBeingEdited = null
                selectCreatedFolderInSheet = false
                showFolderEditor = true
            }
        )
    }
}
