package com.galeriva.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.galeriva.app.ui.SmartAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exports an album's photos into a single ZIP and shares it via the system sheet. */
object AlbumExporter {

    suspend fun exportToZip(
        context: Context,
        album: SmartAlbum,
        onProgress: (done: Int, total: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        // One export at a time; clear stale files so the cache never piles up.
        exportDir.listFiles()?.forEach { it.delete() }

        val zipFile = File(exportDir, "${sanitize(album.title)}.zip")
        val total = album.photos.size
        val usedNames = HashSet<String>()

        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            album.photos.forEachIndexed { index, photo ->
                val name = uniqueName(photo.name.ifBlank { "foto_${photo.id}.jpg" }, usedNames)
                try {
                    context.contentResolver.openInputStream(photo.uri)?.use { input ->
                        zip.putNextEntry(ZipEntry(name))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                } catch (_: Exception) {
                    // Skip unreadable photo, keep the rest of the archive.
                }
                onProgress(index + 1, total)
            }
        }
        zipFile
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ekspor \"${file.nameWithoutExtension}\""))
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _\\-&]"), "").trim().ifBlank { "album" }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        var i = 1
        while (true) {
            val candidate = "${name.substringBeforeLast('.')}_$i.${name.substringAfterLast('.', "jpg")}"
            if (used.add(candidate)) return candidate
            i++
        }
    }
}
