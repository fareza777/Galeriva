package com.galeriva.app.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTakenMillis: Long,
    val bucketName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val isVideo: Boolean = false,
    val durationMillis: Long = 0
)
