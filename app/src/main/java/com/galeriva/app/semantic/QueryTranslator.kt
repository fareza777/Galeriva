package com.galeriva.app.semantic

import com.galeriva.app.search.SearchKeywords
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Translates Indonesian queries to English on-device (ML Kit Translate).
 * The translation model (~30 MB) is downloaded once by Play services; until
 * it is ready — or on devices without it — the keyword dictionary fallback
 * in [SearchKeywords] keeps search working.
 */
class QueryTranslator {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.INDONESIAN)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    @Volatile
    private var modelReady = false

    suspend fun toEnglish(query: String): String? {
        return try {
            if (!modelReady) {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                modelReady = true
            }
            translator.translate(query).await()
        } catch (_: Exception) {
            SearchKeywords.toEnglish(query)
        }
    }
}
