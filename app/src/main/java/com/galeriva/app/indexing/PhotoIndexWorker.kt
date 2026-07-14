package com.galeriva.app.indexing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.galeriva.app.GalerivaApp
import com.galeriva.app.data.MediaStoreRepository
import com.galeriva.app.data.db.IndexedPhotoEntity
import com.galeriva.app.data.db.PhotoLabelEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * Indexes photos in the background with on-device ML Kit image labeling.
 * Labels are stored locally in Room and power search + smart albums.
 * Everything runs offline — no photo ever leaves the device.
 */
class PhotoIndexWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GalerivaApp
        val dao = app.database.photoLabelDao()
        val repository = MediaStoreRepository(applicationContext.contentResolver)

        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build()
        )

        try {
            val allPhotos = repository.loadAllPhotos()
            val indexed = dao.indexedPhotoIds().toHashSet()
            val pending = allPhotos.filter { it.id !in indexed }

            for (photo in pending) {
                if (isStopped) return Result.success()
                try {
                    val bitmap = loadDownsampledBitmap(photo.id) ?: continue
                    val labels = labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
                    bitmap.recycle()
                    if (labels.isNotEmpty()) {
                        dao.insertLabels(labels.map {
                            PhotoLabelEntity(photo.id, it.text.lowercase(), it.confidence)
                        })
                    }
                } catch (_: Exception) {
                    // Skip unreadable/corrupt images; still mark as indexed below
                } finally {
                    dao.markIndexed(IndexedPhotoEntity(photo.id))
                }
            }
            return Result.success()
        } finally {
            labeler.close()
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

    companion object {
        const val WORK_NAME = "galeriva_photo_indexing"
        private const val MIN_CONFIDENCE = 0.6f
        private const val TARGET_SIZE = 640
    }
}
