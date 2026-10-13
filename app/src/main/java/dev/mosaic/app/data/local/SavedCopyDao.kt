package dev.mosaic.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCopyDao {
    @Query("SELECT * FROM saved_copies ORDER BY savedAtMillis DESC, id DESC")
    fun observeSavedCopies(): Flow<List<SavedCopyEntity>>

    @Insert
    suspend fun insertSavedCopy(copy: SavedCopyEntity)
}
