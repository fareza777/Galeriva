package com.galeriva.app.indexing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.galeriva.app.GalerivaApp
import com.galeriva.app.data.MediaStoreRepository
import com.galeriva.app.data.db.IndexedPhotoEntity
import com.galeriva.app.data.db.PhotoEmbeddingEntity
import com.galeriva.app.data.db.PhotoHashEntity
import com.galeriva.app.semantic.Embeddings

/**
 * Indexes photos in the background: computes a CLIP embedding (semantic
 * search + smart albums) and a dHash (similar-photo detection) per photo.
 * Everything runs offline — no photo ever leaves the device.
 */
class PhotoIndexWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GalerivaApp
        val dao = app.database.photoLabelDao()
        val hashDao = app.database.photoHashDao()
        val embeddingDao = app.database.photoEmbeddingDao()
        val repository = MediaStoreRepository(applicationContext.contentResolver)

        val allPhotos = repository.loadAllPhotos()
        val indexed = dao.indexedPhotoIds().toHashSet()
        val pending = allPhotos.filter { it.id !in indexed }
        if (pending.isEmpty()) return Result.success()

        // Run as a foreground worker so aggressive OEM battery managers
        // (MIUI, ColorOS, ...) don't kill indexing when the app is swiped away.
        try {
            setForeground(createForegroundInfo(0, pending.size))
        } catch (_: Exception) {
            // Foreground not permitted right now — continue as best-effort background work.
        }

        for ((index, photo) in pending.withIndex()) {
            if (isStopped) return Result.success()
            if (index % 10 == 0 && index > 0) {
                try {
                    setForeground(createForegroundInfo(index, pending.size))
                } catch (_: Exception) {
                }
            }
            try {
                val bitmap = loadDownsampledBitmap(photo.id) ?: continue
                hashDao.insert(PhotoHashEntity(photo.id, dHash(bitmap)))
                val embedding = app.clipEngine.encodeImage(bitmap)
                bitmap.recycle()
                embeddingDao.insert(
                    PhotoEmbeddingEntity(photo.id, Embeddings.toBytes(embedding))
                )
            } catch (_: Exception) {
                // Skip unreadable/corrupt images; still mark as indexed below
            } finally {
                dao.markIndexed(IndexedPhotoEntity(photo.id))
            }
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0)

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Pengindeksan foto",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Galeriva mengindeks foto")
            .setContentText(if (total > 0) "$done dari $total foto" else "Menyiapkan…")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun loadDownsampledBitmap(photoId: Long): Bitmap? {
        val uri = android.content.ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(applicationContext.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = maxOf(info.size.width, info.size.height) / TARGET_SIZE.toFloat()
                    if (scale > 1f) {
                        decoder.setTargetSize(
                            (info.size.width / scale).toInt(),
                            (info.size.height / scale).toInt()
                        )
                    }
                }
            } else {
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 64-bit difference hash: photos that look alike get hashes with a small
     * Hamming distance, which powers the "similar photos" cleanup tool.
     */
    private fun dHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (luminance(scaled.getPixel(x, y)) > luminance(scaled.getPixel(x + 1, y))) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return hash
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    companion object {
        const val WORK_NAME = "galeriva_photo_indexing"
        private const val TARGET_SIZE = 640
        private const val CHANNEL_ID = "galeriva_indexing"
        private const val NOTIFICATION_ID = 41
    }
}
