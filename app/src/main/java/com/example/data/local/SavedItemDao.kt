package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadStatus
import com.example.data.model.SavedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedItemDao {
    @Query("SELECT * FROM saved_items ORDER BY createdAt DESC")
    fun observeAllItems(): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun observeItemsByFolder(folderId: Long): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE id = :id")
    fun observeItemById(id: Long): Flow<SavedItemEntity?>

    @Query("SELECT * FROM saved_items WHERE id = :id")
    suspend fun getItemById(id: Long): SavedItemEntity?

    @Query("SELECT * FROM saved_items WHERE folderId = :folderId")
    suspend fun getItemsByFolderOnce(folderId: Long): List<SavedItemEntity>

    @Query("SELECT * FROM saved_items")
    suspend fun getAllItemsOnce(): List<SavedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SavedItemEntity): Long

    @Update
    suspend fun updateItem(item: SavedItemEntity)

    @Query(
        "UPDATE saved_items SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, fileSizeBytes = :totalBytes WHERE id = :id"
    )
    suspend fun updateDownloadProgress(
        id: Long,
        status: DownloadStatus,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long
    )

    @Query(
        "UPDATE saved_items SET status = :status, errorMessage = :error WHERE id = :id"
    )
    suspend fun updateStatusError(id: Long, status: DownloadStatus, error: String?)

    @Query("UPDATE saved_items SET folderId = :targetFolderId WHERE id IN (:itemIds)")
    suspend fun moveItemsToFolder(itemIds: List<Long>, targetFolderId: Long)

    @Query("UPDATE saved_items SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE saved_items SET title = :title, notes = :notes WHERE id = :id")
    suspend fun updateTitleAndNotes(id: Long, title: String, notes: String)

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM saved_items WHERE id IN (:itemIds)")
    suspend fun deleteItemsByIds(itemIds: List<Long>)

    @Query("DELETE FROM saved_items WHERE status = 'COMPLETED' AND id IN (:itemIds)")
    suspend fun deleteCompletedByIds(itemIds: List<Long>)
}
