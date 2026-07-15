package com.galeriva.app

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.galeriva.app.data.db.GalerivaDatabase
import com.galeriva.app.indexing.PhotoIndexWorker
import com.galeriva.app.semantic.ClipEngine

class GalerivaApp : Application() {

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
