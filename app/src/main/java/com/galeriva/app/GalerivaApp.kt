package com.galeriva.app

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.galeriva.app.data.db.GalerivaDatabase
import com.galeriva.app.indexing.PhotoIndexWorker
import com.galeriva.app.semantic.ClipEngine

class GalerivaApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    val database: GalerivaDatabase by lazy { GalerivaDatabase.create(this) }
    val clipEngine: ClipEngine by lazy { ClipEngine(this) }

    fun scheduleIndexing() {
        val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            PhotoIndexWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
