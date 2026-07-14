package com.galeriva.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photo_labels", primaryKeys = ["photoId", "label"])
data class PhotoLabelEntity(
    val photoId: Long,
    val label: String,
    val confidence: Float
)

@Entity(tableName = "indexed_photos")
data class IndexedPhotoEntity(
    @PrimaryKey val photoId: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val photoId: Long
)

@Dao
interface PhotoLabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabels(labels: List<PhotoLabelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markIndexed(entity: IndexedPhotoEntity)

    @Query("SELECT photoId FROM indexed_photos")
    suspend fun indexedPhotoIds(): List<Long>

    @Query("SELECT * FROM photo_labels")
    fun allLabels(): Flow<List<PhotoLabelEntity>>

    @Query("SELECT COUNT(*) FROM indexed_photos")
    fun indexedCount(): Flow<Int>

    @Query("DELETE FROM photo_labels WHERE photoId = :photoId")
    suspend fun deleteLabels(photoId: Long)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE photoId = :photoId")
    suspend fun remove(photoId: Long)

    @Query("SELECT photoId FROM favorites")
    fun favoriteIds(): Flow<List<Long>>
}

@Database(
    entities = [PhotoLabelEntity::class, IndexedPhotoEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GalerivaDatabase : RoomDatabase() {
    abstract fun photoLabelDao(): PhotoLabelDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        fun create(context: Context): GalerivaDatabase =
            Room.databaseBuilder(context, GalerivaDatabase::class.java, "galeriva.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
