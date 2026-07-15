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

@Entity(tableName = "photo_hashes")
data class PhotoHashEntity(
    @PrimaryKey val photoId: Long,
    val dhash: Long
)

@Entity(tableName = "locked_photos")
data class LockedPhotoEntity(
    @PrimaryKey val photoId: Long
)

@Entity(tableName = "photo_embeddings")
data class PhotoEmbeddingEntity(
    @PrimaryKey val photoId: Long,
    val vector: ByteArray
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

@Dao
interface PhotoHashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hash: PhotoHashEntity)

    @Query("SELECT * FROM photo_hashes")
    suspend fun allHashes(): List<PhotoHashEntity>
}

@Dao
interface PhotoEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: PhotoEmbeddingEntity)

    @Query("SELECT * FROM photo_embeddings")
    fun all(): Flow<List<PhotoEmbeddingEntity>>
}

@Dao
interface LockedPhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun lock(entities: List<LockedPhotoEntity>)

    @Query("DELETE FROM locked_photos WHERE photoId IN (:photoIds)")
    suspend fun unlock(photoIds: List<Long>)

    @Query("SELECT photoId FROM locked_photos")
    fun lockedIds(): Flow<List<Long>>
}

@Database(
    entities = [
        PhotoLabelEntity::class,
        IndexedPhotoEntity::class,
        FavoriteEntity::class,
        PhotoHashEntity::class,
        LockedPhotoEntity::class,
        PhotoEmbeddingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class GalerivaDatabase : RoomDatabase() {
    abstract fun photoLabelDao(): PhotoLabelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun lockedPhotoDao(): LockedPhotoDao
    abstract fun photoEmbeddingDao(): PhotoEmbeddingDao

    companion object {
        fun create(context: Context): GalerivaDatabase =
            Room.databaseBuilder(context, GalerivaDatabase::class.java, "galeriva.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
