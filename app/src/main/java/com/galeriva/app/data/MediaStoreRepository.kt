package com.galeriva.app.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class MediaStoreRepository(private val contentResolver: ContentResolver) {

    /**
     * Deletes photos. On Android 11+ this returns an IntentSender that must be
     * launched so the user confirms via the system dialog; on older versions the
     * delete happens directly and null is returned.
     */
    suspend fun deletePhotos(uris: List<Uri>): IntentSender? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        }
        try {
            uris.forEach { contentResolver.delete(it, null, null) }
            null
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            } else {
                throw e
            }
        }
    }

    /** MD5 of the full file content; null if unreadable. Used for duplicate detection. */
    suspend fun contentHash(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadAllPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = normalizeTakenMillis(
                    cursor.getLong(takenCol),
                    cursor.getLong(addedCol)
                )
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    ),
                    name = cursor.getString(nameCol) ?: "",
                    dateTakenMillis = taken,
                    bucketName = cursor.getString(bucketCol) ?: "Lainnya",
                    sizeBytes = cursor.getLong(sizeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol)
                )
            }
        }
        // Re-sort in memory: DATE_TAKEN normalization may reorder rows
        // relative to the SQL sort (some vendors store seconds, not millis).
        photos.sortedByDescending { it.dateTakenMillis }
    }

    /**
     * DATE_TAKEN should be epoch millis, but some camera/chat apps store
     * seconds — which would sort those photos into 1970 and make them
     * "disappear" from the top of the gallery. Values below ~1973 in millis
     * are treated as seconds; zero/negative falls back to DATE_ADDED.
     */
    private fun normalizeTakenMillis(takenRaw: Long, addedSeconds: Long): Long = when {
        takenRaw <= 0L -> addedSeconds * 1000
        takenRaw < 100_000_000_000L -> takenRaw * 1000
        else -> takenRaw
    }
}
