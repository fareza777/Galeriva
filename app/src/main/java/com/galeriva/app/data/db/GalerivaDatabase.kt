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

@Entity(tableName = "smart_folders")
data class SmartFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val query: String
)

/** A photo the user manually kicked out of a smart folder — never returns. */
@Entity(tableName = "folder_exclusions", primaryKeys = ["folderId", "photoId"])
data class FolderExclusionEntity(
    val folderId: Long,
    val photoId: Long
)

/** Per-photo metadata from indexing: OCR text + detected face count. */
@Entity(tableName = "photo_meta")
data class PhotoMetaEntity(
    @PrimaryKey val photoId: Long,
    val ocrText: String,
    val faceCount: Int
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
interface PhotoMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meta: PhotoMetaEntity)

    @Query("SELECT * FROM photo_meta")
    fun all(): Flow<List<PhotoMetaEntity>>
}

@Dao
interface PhotoEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: PhotoEmbeddingEntity)

    @Query("SELECT * FROM photo_embeddings")
    fun all(): Flow<List<PhotoEmbeddingEntity>>
}

@Dao
interface SmartFolderDao {
    @Insert
    suspend fun insert(folder: SmartFolderEntity)

    @Query("DELETE FROM smart_folders WHERE id = :folderId")
    suspend fun delete(folderId: Long)

    @Query("SELECT * FROM smart_folders ORDER BY id")
    fun all(): Flow<List<SmartFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun exclude(exclusions: List<FolderExclusionEntity>)

    @Query("SELECT * FROM folder_exclusions")
    fun allExclusions(): Flow<List<FolderExclusionEntity>>

    @Query("DELETE FROM folder_exclusions WHERE folderId = :folderId")
    suspend fun clearExclusions(folderId: Long)
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
        PhotoEmbeddingEntity::class,
        SmartFolderEntity::class,
        FolderExclusionEntity::class,
        PhotoMetaEntity::class
    ],
    // v4: smart folders + embedding space switch to ViT-B/16 (forces reindex
    // via destructive migration — B/32 and B/16 vectors are incompatible).
    // v5: folder_exclusions (non-destructive migration, index preserved).
    // v6: photo_meta (OCR text + face count), non-destructive.
    // v7: embedding space switch to SigLIP — destructive on purpose so all
    //     media reindex (SigLIP and CLIP vectors are incompatible; this also
    //     backfills OCR/face data for old photos).
    version = 7,
    exportSchema = false
)
abstract class GalerivaDatabase : RoomDatabase() {
    abstract fun photoLabelDao(): PhotoLabelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun lockedPhotoDao(): LockedPhotoDao
    abstract fun photoEmbeddingDao(): PhotoEmbeddingDao
    abstract fun photoMetaDao(): PhotoMetaDao
    abstract fun smartFolderDao(): SmartFolderDao

    companion object {
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS folder_exclusions (" +
                        "folderId INTEGER NOT NULL, photoId INTEGER NOT NULL, " +
                        "PRIMARY KEY(folderId, photoId))"
                )
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS photo_meta (" +
                        "photoId INTEGER NOT NULL PRIMARY KEY, " +
                        "ocrText TEXT NOT NULL, faceCount INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): GalerivaDatabase =
            Room.databaseBuilder(context, GalerivaDatabase::class.java, "galeriva.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
    }
}
