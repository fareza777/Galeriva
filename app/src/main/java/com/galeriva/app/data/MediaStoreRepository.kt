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

    /**
     * Copies photos into a device folder under Pictures/<folderName>.
     * Returns the number of photos copied successfully.
     */
    suspend fun copyPhotosToFolder(
        photos: List<Photo>,
        folderName: String
    ): Int = withContext(Dispatchers.IO) {
        val safeName = folderName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        if (safeName.isEmpty()) return@withContext 0
        var copied = 0
        for (photo in photos) {
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, photo.name.ifBlank { "foto_${photo.id}.jpg" })
                    put(MediaStore.Images.Media.MIME_TYPE, mimeFromName(photo.name))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$safeName")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = java.io.File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_PICTURES
                            ),
                            safeName
                        ).apply { mkdirs() }
                        @Suppress("DEPRECATION")
                        put(
                            MediaStore.Images.Media.DATA,
                            java.io.File(dir, photo.name.ifBlank { "foto_${photo.id}.jpg" }).absolutePath
                        )
                    }
                }
                val target = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: continue
                contentResolver.openInputStream(photo.uri)?.use { input ->
                    contentResolver.openOutputStream(target)?.use { output ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.update(
                        target,
                        android.content.ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        },
                        null,
                        null
                    )
                }
                copied++
            } catch (_: Exception) {
                // Skip photos that fail to copy; report the successful count.
            }
        }
        copied
    }

    private fun mimeFromName(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
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

            val maxPlausibleMillis = System.currentTimeMillis() + 2 * 24 * 3_600_000L
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = normalizeToMillis(cursor.getLong(takenCol), maxPlausibleMillis)
                    ?: normalizeToMillis(cursor.getLong(addedCol), maxPlausibleMillis)
                    ?: 0L
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
     * MediaStore timestamps are inconsistent across vendors: DATE_TAKEN is
     * documented as millis and DATE_ADDED as seconds, but real devices mix
     * seconds, millis, and even microseconds. Detect the unit by magnitude
     * and only accept results in a plausible range (1980 .. now+2 days) —
     * anything else returns null so the caller can try the next field.
     */
    private fun normalizeToMillis(raw: Long, maxPlausibleMillis: Long): Long? {
        if (raw <= 0L) return null
        var value = raw
        while (value > maxPlausibleMillis) value /= 1000 // micro/nano -> millis
        if (value < MIN_PLAUSIBLE_MILLIS) value *= 1000  // seconds -> millis
        return value.takeIf { it in MIN_PLAUSIBLE_MILLIS..maxPlausibleMillis }
    }

    private companion object {
        // 1 Jan 1980 UTC — no real photo predates this.
        const val MIN_PLAUSIBLE_MILLIS = 315_532_800_000L
    }
}
