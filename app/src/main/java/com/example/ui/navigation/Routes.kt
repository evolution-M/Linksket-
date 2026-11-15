package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object FoldersRoute

@Serializable
data class FolderDetailRoute(val folderId: Long)

@Serializable
data object QueueRoute

@Serializable
data class ViewerRoute(val itemId: Long)
