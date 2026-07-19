package com.galeriva.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeriva.app.GalerivaApp
import com.galeriva.app.data.MediaStoreRepository
import com.galeriva.app.data.Photo
import com.galeriva.app.data.AlbumExporter
import com.galeriva.app.data.db.FavoriteEntity
import com.galeriva.app.data.db.FolderExclusionEntity
import com.galeriva.app.data.db.LockedPhotoEntity
import com.galeriva.app.data.db.SmartFolderEntity
import com.galeriva.app.semantic.Embeddings
import com.galeriva.app.semantic.QueryTranslator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class QueryBundle(
    val main: FloatArray,
    val rivals: List<FloatArray>
)

private data class CustomFolderInputs(
    val folders: List<SmartFolderEntity>,
    val photos: List<Photo>,
    val embeddings: Map<Long, FloatArray>,
    val distractors: Map<Long, Float>,
    val exclusions: Map<Long, Set<Long>>
)

private data class SearchInputs(
    val photos: List<Photo>,
    val embeddings: Map<Long, FloatArray>,
    val distractors: Map<Long, Float>,
    val query: String
)

/** A smart category derived from CLIP zero-shot classification. */
data class SmartAlbum(
    val id: String,
    val title: String,
    val cover: Photo?,
    val photos: List<Photo>
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GalerivaApp
    private val repository = MediaStoreRepository(application.contentResolver)
    private val labelDao = app.database.photoLabelDao()
    private val favoriteDao = app.database.favoriteDao()
    private val hashDao = app.database.photoHashDao()
    private val lockedDao = app.database.lockedPhotoDao()
    private val embeddingDao = app.database.photoEmbeddingDao()
    private val translator = QueryTranslator()
    private val prefs =
        application.getSharedPreferences("galeriva_ui", android.content.Context.MODE_PRIVATE)

    /** Auto-reload when MediaStore changes (new photo, deletion by other apps). */
    private var pendingRefresh: kotlinx.coroutines.Job? = null
    private val mediaObserver = object : android.database.ContentObserver(
        android.os.Handler(android.os.Looper.getMainLooper())
    ) {
        override fun onChange(selfChange: Boolean) {
            pendingRefresh?.cancel()
            pendingRefresh = viewModelScope.launch {
                kotlinx.coroutines.delay(1_500)
                reloadPhotos()
                app.scheduleIndexing()
            }
        }
    }

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())

    val lockedIds: StateFlow<Set<Long>> =
        lockedDao.lockedIds()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** All photos except those locked in the vault. */
    val photos: StateFlow<List<Photo>> =
        combine(_allPhotos, lockedIds) { all, locked ->
            all.filter { it.id !in locked }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lockedPhotos: StateFlow<List<Photo>> =
        combine(_allPhotos, lockedIds) { all, locked ->
            all.filter { it.id in locked }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val searchQuery = MutableStateFlow("")

    /** photoId -> OCR text + face count (from indexing). */
    val metaById: StateFlow<Map<Long, com.galeriva.app.data.db.PhotoMetaEntity>> =
        app.database.photoMetaDao().all()
            .map { rows -> rows.associateBy { it.photoId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** photoId -> CLIP embedding (L2-normalized, in memory for fast search). */
    private val embeddings: StateFlow<Map<Long, FloatArray>> =
        embeddingDao.all()
            .map { rows -> rows.associate { it.photoId to Embeddings.fromBytes(it.vector) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Contrastive guard: prototypes of common "false friend" photo types.
     * A photo only counts as a search hit if the query beats its best
     * distractor score — this is what keeps ID photos, QR codes, and
     * screenshots out of "meeting" results.
     */
    private val distractorPrototypes = MutableStateFlow<List<FloatArray>>(emptyList())

    /** photoId -> best distractor score (query-independent, computed once). */
    private val distractorScores: StateFlow<Map<Long, Float>> =
        combine(embeddings, distractorPrototypes) { embeds, distractors ->
            if (distractors.isEmpty()) emptyMap()
            else embeds.mapValues { (_, embedding) ->
                distractors.maxOf { Embeddings.cosine(it, embedding) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val indexedCount: StateFlow<Int> =
        labelDao.indexedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> =
        favoriteDao.favoriteIds()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Semantic search: the query (Indonesian or English) is encoded with CLIP
     * and matched against photo embeddings by cosine similarity. Both the raw
     * query and its on-device English translation are tried; the best score
     * per photo wins. Filename matches rank below semantic matches.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: StateFlow<List<Photo>> =
        combine(
            photos,
            embeddings,
            distractorScores,
            searchQuery.debounce(350)
        ) { photoList, embeds, distractors, query ->
            SearchInputs(photoList, embeds, distractors, query)
        }
            .mapLatest { (photoList, embeds, distractors, query) ->
                if (query.isBlank()) return@mapLatest emptyList()
                val bundle = buildQueryBundle(query)
                val queryVector = bundle?.main
                val rawWords = query.lowercase()
                    .split(Regex("\\s+"))
                    .filter { it.length >= 4 }

                // Pass 1: semantic scores above the absolute floor AND beating
                // the photo's best distractor (contrastive zero-shot check).
                val semanticScores = HashMap<Long, Float>()
                if (queryVector != null && bundle != null) {
                    for (photo in photoList) {
                        val embedding = embeds[photo.id] ?: continue
                        val score = Embeddings.cosine(queryVector, embedding)
                        if (score < SEMANTIC_FLOOR) continue
                        val distractor = distractors[photo.id] ?: 0f
                        if (score < distractor) continue
                        if (!beatsRivals(score, embedding, bundle.rivals)) continue
                        semanticScores[photo.id] = score
                    }
                }
                // Pass 2: ratio-based cutoff relative to the best match —
                // scale-free, so it works regardless of the model's score range.
                val best = semanticScores.values.maxOrNull() ?: 0f
                val cutoff = maxOf(SEMANTIC_FLOOR, best * SEARCH_RATIO)

                val meta = metaById.value
                photoList.mapNotNull { photo ->
                    val semantic = semanticScores[photo.id] ?: 0f
                    val nameMatch = rawWords.any {
                        photo.name.lowercase().contains(it) ||
                            photo.bucketName.lowercase().contains(it)
                    }
                    // OCR: the FULL query phrase must appear in the photo's text.
                    // Single-word matching flooded scene searches ("rapat di
                    // kantor") with every letter/schedule containing "rapat".
                    val ocrText = meta[photo.id]?.ocrText.orEmpty()
                    val phrase = query.lowercase().trim()
                    val ocrMatch =
                        phrase.length >= 4 && ocrText.isNotEmpty() && phrase in ocrText
                    when {
                        semantic >= cutoff -> photo to (1f + semantic)
                        ocrMatch -> photo to 0.9f
                        nameMatch -> photo to 0.5f
                        else -> null
                    }
                }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * CLIP understands English only, so the query vector is built from the
     * on-device translation. The raw query is used only when translation is
     * completely unavailable — never alongside it, because a non-English
     * string produces a garbage vector that attracts text-heavy photos
     * (ID photos, QR codes, receipts).
     */
    private suspend fun buildQueryVector(query: String): FloatArray? =
        buildQueryBundle(query)?.main

    /**
     * Query vector plus "color rival" vectors. CLIP is notoriously weak at
     * binding colors to objects ("orange uniform" matches khaki uniforms) —
     * so when the query names a color, rival queries are generated with the
     * other colors, and a photo must beat ALL rivals to count as a match.
     */
    private suspend fun buildQueryBundle(query: String): QueryBundle? {
        val english = translator.toEnglish(query)?.takeIf { it.isNotBlank() }
        val text = english ?: query
        val main = Embeddings.meanNormalized(
            PROMPT_TEMPLATES.mapNotNull { template ->
                runCatching { app.clipEngine.encodeText(template.format(text)) }.getOrNull()
            }
        ) ?: return null

        val lower = text.lowercase()
        val colorInQuery = RIVAL_COLORS.firstOrNull { it in lower }
        val rivals = if (colorInQuery == null) emptyList() else {
            RIVAL_COLORS.filter { it != colorInQuery }.mapNotNull { rivalColor ->
                val rivalText = lower.replace(colorInQuery, rivalColor)
                runCatching {
                    app.clipEngine.encodeText("a photo of $rivalText")
                }.getOrNull()
            }
        }
        return QueryBundle(main, rivals)
    }

    private fun beatsRivals(
        score: Float,
        embedding: FloatArray,
        rivals: List<FloatArray>
    ): Boolean = rivals.all { rival -> Embeddings.cosine(rival, embedding) < score }

    /** Folder albums straight from MediaStore buckets. */
    val folderAlbums: StateFlow<List<SmartAlbum>> =
        photos.map { photos ->
            photos.groupBy { it.bucketName }
                .map { (bucket, items) ->
                    SmartAlbum("folder:$bucket", bucket, items.firstOrNull(), items)
                }
                .sortedByDescending { it.photos.size }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Smart albums via zero-shot CLIP classification: each category is an
     * English prompt encoded once; photos join the category whose prototype
     * they are close enough to.
     */
    private val categoryPrototypes = MutableStateFlow<Map<String, FloatArray>>(emptyMap())

    val smartAlbums: StateFlow<List<SmartAlbum>> =
        combine(photos, embeddings, categoryPrototypes) { photos, embeds, prototypes ->
            SMART_CATEGORIES.mapNotNull { (title, _) ->
                val prototype = prototypes[title] ?: return@mapNotNull null
                val scored = photos.mapNotNull { photo ->
                    val embedding = embeds[photo.id] ?: return@mapNotNull null
                    val score = Embeddings.cosine(embedding, prototype)
                    if (score >= ALBUM_FLOOR) photo to score else null
                }
                val bestScore = scored.maxOfOrNull { it.second } ?: 0f
                val cutoff = maxOf(ALBUM_FLOOR, bestScore * ALBUM_RATIO)
                val items = scored.filter { it.second >= cutoff }.map { it.first }
                if (items.isEmpty()) null
                else SmartAlbum("smart:$title", title, items.first(), items)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        app.contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
        viewModelScope.launch {
            runCatching {
                categoryPrototypes.value = SMART_CATEGORIES.associate { (title, prompt) ->
                    title to app.clipEngine.encodeText(prompt)
                }
                distractorPrototypes.value = DISTRACTOR_PROMPTS.map {
                    app.clipEngine.encodeText(it)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            reloadPhotos()
            _isLoading.value = false
            app.scheduleIndexing()
        }
    }

    private suspend fun reloadPhotos() {
        _allPhotos.value = repository.loadAllPhotos()
    }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(mediaObserver)
    }

    // ----- Collapsed day groups (persisted across sessions) -----

    val collapsedDays = MutableStateFlow(
        prefs.getStringSet("collapsed_days", emptySet()).orEmpty().toSet()
    )

    fun toggleDayCollapsed(dayLabel: String) {
        val next =
            if (dayLabel in collapsedDays.value) collapsedDays.value - dayLabel
            else collapsedDays.value + dayLabel
        collapsedDays.value = next
        prefs.edit().putStringSet("collapsed_days", next).apply()
    }

    // ----- Multi-select -----

    val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    fun toggleSelection(photoId: Long) {
        selectedIds.value =
            if (photoId in selectedIds.value) selectedIds.value - photoId
            else selectedIds.value + photoId
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll(ids: Collection<Long>) {
        selectedIds.value = ids.toSet()
    }

    /**
     * Deletes the given photos. If Android requires user confirmation,
     * [onNeedsConfirm] is invoked with the IntentSender to launch; after the
     * launcher succeeds, call [onDeleteConfirmed].
     */
    fun deletePhotoIds(
        ids: Set<Long>,
        onNeedsConfirm: (android.content.IntentSender) -> Unit
    ) {
        viewModelScope.launch {
            val uris = _allPhotos.value.filter { it.id in ids }.map { it.uri }
            val sender = try {
                repository.deletePhotos(uris)
            } catch (_: SecurityException) {
                null
            }
            if (sender != null) {
                onNeedsConfirm(sender)
            } else {
                onDeleteConfirmed()
            }
        }
    }

    /** Refreshes state after a successful delete (direct or via system dialog). */
    fun onDeleteConfirmed() {
        clearSelection()
        _duplicateGroups.value = emptyList()
        _similarGroups.value = emptyList()
        refresh()
    }

    // ----- Vault (locked photos) -----

    fun lockSelected() {
        viewModelScope.launch {
            lockedDao.lock(selectedIds.value.map { LockedPhotoEntity(it) })
            clearSelection()
        }
    }

    fun unlockPhotos(ids: Set<Long>) {
        viewModelScope.launch {
            lockedDao.unlock(ids.toList())
            clearSelection()
        }
    }

    // ----- Similar photo detection (perceptual hash) -----

    private val _similarGroups = MutableStateFlow<List<List<Photo>>>(emptyList())
    val similarGroups: StateFlow<List<List<Photo>>> = _similarGroups.asStateFlow()

    private val _isScanningSimilar = MutableStateFlow(false)
    val isScanningSimilar: StateFlow<Boolean> = _isScanningSimilar.asStateFlow()

    fun scanSimilar() {
        if (_isScanningSimilar.value) return
        viewModelScope.launch {
            _isScanningSimilar.value = true
            try {
                val photosById = photos.value.associateBy { it.id }
                val hashes = hashDao.allHashes().filter { it.photoId in photosById }
                // Union-find over photos whose dHash differs by <= threshold bits.
                val parent = IntArray(hashes.size) { it }
                fun find(i: Int): Int {
                    var root = i
                    while (parent[root] != root) root = parent[root]
                    return root
                }
                for (i in hashes.indices) {
                    for (j in i + 1 until hashes.size) {
                        val distance =
                            java.lang.Long.bitCount(hashes[i].dhash xor hashes[j].dhash)
                        if (distance <= SIMILARITY_THRESHOLD_BITS) {
                            val ri = find(i)
                            val rj = find(j)
                            if (ri != rj) parent[rj] = ri
                        }
                    }
                }
                _similarGroups.value = hashes.indices
                    .groupBy { find(it) }
                    .values
                    .filter { it.size > 1 }
                    .map { indices ->
                        indices.mapNotNull { photosById[hashes[it].photoId] }
                            .sortedByDescending { it.dateTakenMillis }
                    }
                    .filter { it.size > 1 }
            } finally {
                _isScanningSimilar.value = false
            }
        }
    }

    // ----- Duplicate detection -----

    private val _duplicateGroups = MutableStateFlow<List<List<Photo>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<Photo>>> = _duplicateGroups.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    fun scanDuplicates() {
        if (_isScanningDuplicates.value) return
        viewModelScope.launch {
            _isScanningDuplicates.value = true
            try {
                // Cheap pre-filter: identical size + dimensions, then confirm by MD5.
                val candidates = photos.value
                    .filter { !it.isVideo && it.sizeBytes > 0 }
                    .groupBy { Triple(it.sizeBytes, it.width, it.height) }
                    .values
                    .filter { it.size > 1 }

                val groups = mutableListOf<List<Photo>>()
                for (candidate in candidates) {
                    candidate
                        .mapNotNull { photo ->
                            repository.contentHash(photo.uri)?.let { hash -> hash to photo }
                        }
                        .groupBy({ it.first }, { it.second })
                        .values
                        .filter { it.size > 1 }
                        .forEach { groups += it }
                }
                _duplicateGroups.value = groups
            } finally {
                _isScanningDuplicates.value = false
            }
        }
    }

    // ----- Custom smart folders (user-defined name + query) -----

    private val folderDao = app.database.smartFolderDao()
    private val folderVectorCache = HashMap<String, QueryBundle>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val customFolders: StateFlow<List<SmartAlbum>> =
        combine(
            folderDao.all(),
            photos,
            embeddings,
            distractorScores,
            folderDao.allExclusions()
        ) { folders, photoList, embeds, distractors, exclusions ->
            CustomFolderInputs(
                folders, photoList, embeds, distractors,
                exclusions.groupBy({ it.folderId }, { it.photoId })
                    .mapValues { (_, ids) -> ids.toSet() }
            )
        }
            .mapLatest { (folders, photoList, embeds, distractors, exclusions) ->
                folders.map { folder ->
                    val excluded = exclusions[folder.id].orEmpty()
                    val bundle = folderVectorCache[folder.query]
                        ?: buildQueryBundle(folder.query)?.also {
                            folderVectorCache[folder.query] = it
                        }
                    val items = if (bundle == null) emptyList() else {
                        // Folders are curated collections — precision matters more
                        // than recall, so the floor is stricter than search and a
                        // relative cutoff trims everything far below the best match.
                        // Curation learning: embeddings of kicked-out photos act
                        // as negative exemplars — anything that looks more like a
                        // rejected photo than like the query is rejected too.
                        val negatives = excluded.mapNotNull { embeds[it] }
                        val scored = photoList.mapNotNull { photo ->
                            if (photo.id in excluded) return@mapNotNull null
                            val embedding = embeds[photo.id] ?: return@mapNotNull null
                            val score = Embeddings.cosine(bundle.main, embedding)
                            if (score < FOLDER_FLOOR) return@mapNotNull null
                            if (score < (distractors[photo.id] ?: 0f)) {
                                return@mapNotNull null
                            }
                            if (!beatsRivals(score, embedding, bundle.rivals)) {
                                return@mapNotNull null
                            }
                            val nearestNegative = negatives.maxOfOrNull {
                                Embeddings.cosine(it, embedding)
                            } ?: 0f
                            if (nearestNegative > NEGATIVE_SIMILARITY ||
                                nearestNegative > score
                            ) {
                                return@mapNotNull null
                            }
                            photo to score
                        }
                        val best = scored.maxOfOrNull { it.second } ?: 0f
                        val cutoff = maxOf(FOLDER_FLOOR, best * FOLDER_RATIO)
                        scored.filter { it.second >= cutoff }
                            .sortedByDescending { it.second }
                            .map { it.first }
                    }
                    SmartAlbum("custom:${folder.id}", folder.name, items.firstOrNull(), items)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCustomFolder(name: String, query: String) {
        if (name.isBlank() || query.isBlank()) return
        viewModelScope.launch {
            folderDao.insert(SmartFolderEntity(name = name.trim(), query = query.trim()))
        }
    }

    fun deleteCustomFolder(albumId: String) {
        val folderId = albumId.removePrefix("custom:").toLongOrNull() ?: return
        viewModelScope.launch {
            folderDao.delete(folderId)
            folderDao.clearExclusions(folderId)
        }
    }

    /** Manual curation: kick wrongly-matched photos out of a smart folder forever. */
    fun excludeFromFolder(albumId: String, photoIds: Set<Long>) {
        val folderId = albumId.removePrefix("custom:").toLongOrNull() ?: return
        viewModelScope.launch {
            folderDao.exclude(photoIds.map { FolderExclusionEntity(folderId, it) })
            clearSelection()
        }
    }

    // ----- Copy / move to device folder -----

    /** (done, total) while copying, null when idle. */
    val copyProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /**
     * Copies the selected photos into Pictures/<folderName>. When [move] is
     * true, the originals are then deleted (system confirmation dialog via
     * [onNeedsDeleteConfirm], same flow as normal delete).
     */
    fun copySelectedToFolder(
        folderName: String,
        move: Boolean,
        onNeedsDeleteConfirm: (android.content.IntentSender) -> Unit
    ) {
        if (copyProgress.value != null) return
        val ids = selectedIds.value
        viewModelScope.launch {
            try {
                val targets = _allPhotos.value.filter { it.id in ids }
                copyProgress.value = 0 to targets.size
                val copied = repository.copyPhotosToFolder(targets, folderName)
                copyProgress.value = copied to targets.size
                if (move && copied > 0) {
                    deletePhotoIds(ids, onNeedsDeleteConfirm)
                } else {
                    clearSelection()
                    reloadPhotos()
                }
            } finally {
                copyProgress.value = null
            }
        }
    }

    // ----- Album export (ZIP) -----

    /** (done, total) while an export is running, null when idle. */
    val exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    fun exportAlbum(album: SmartAlbum, onDone: (java.io.File) -> Unit) {
        if (exportProgress.value != null) return
        viewModelScope.launch {
            try {
                val file = AlbumExporter.exportToZip(app, album) { done, total ->
                    exportProgress.value = done to total
                }
                onDone(file)
            } catch (_: Exception) {
                // Export failed (out of space / unreadable photos) — reset quietly.
            } finally {
                exportProgress.value = null
            }
        }
    }

    // ----- Index backup / restore -----

    /** Copies the index database to a shareable file (WAL checkpointed first). */
    fun exportIndex(onDone: (java.io.File) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = runCatching {
                app.database.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
                val source = app.getDatabasePath("galeriva.db")
                val dir = java.io.File(app.cacheDir, "exports").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                java.io.File(dir, "galeriva-cadangan-indeks.db").also {
                    source.copyTo(it, overwrite = true)
                }
            }.getOrNull() ?: return@launch
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(file)
            }
        }
    }

    /** Replaces the index database with a backup file; app must restart after. */
    fun importIndex(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                app.database.close()
                val dbFile = app.getDatabasePath("galeriva.db")
                app.getDatabasePath("galeriva.db-wal").delete()
                app.getDatabasePath("galeriva.db-shm").delete()
                app.contentResolver.openInputStream(uri)?.use { input ->
                    dbFile.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(ok)
            }
        }
    }

    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch {
            if (photoId in favoriteIds.value) favoriteDao.remove(photoId)
            else favoriteDao.add(FavoriteEntity(photoId))
        }
    }

    fun albumById(albumId: String): SmartAlbum? = when (albumId) {
        "all" -> SmartAlbum("all", "Semua Foto", photos.value.firstOrNull(), photos.value)
        else -> (smartAlbums.value + folderAlbums.value + customFolders.value)
            .firstOrNull { it.id == albumId }
    }

    companion object {
        private const val SIMILARITY_THRESHOLD_BITS = 6
        // SigLIP similarity scale: the model's own sigmoid decision boundary
        // sits near cosine 0.11, so thresholds are far lower than CLIP's.
        // Scale-free scoring: low absolute floors (any real match clears
        // them regardless of the model's score range) plus ratio cutoffs
        // relative to the best match, which do the actual precision work.
        private const val SEMANTIC_FLOOR = 0.05f
        private const val SEARCH_RATIO = 0.80f
        private const val FOLDER_FLOOR = 0.06f
        private const val FOLDER_RATIO = 0.78f
        private const val NEGATIVE_SIMILARITY = 0.80f

        private val DISTRACTOR_PROMPTS = listOf(
            "a passport-style ID photo of a face on a plain background",
            "a QR code or barcode",
            "a screenshot of a phone or computer screen",
            "a photo of a document, form, or receipt",
            "a close-up selfie portrait",
            "a photo of food on a plate",
            "a scenic landscape photo",
            "a photo of an animal",
            "a photo of a product on a plain background",
            "a screenshot of a chat conversation with text messages",
            "a certificate, poster, or flyer with printed text",
            "an official ID portrait photo with a solid red or blue background",
            "a formal portrait of a person in a white shirt and tie",
            "a close-up of the ground, floor tiles, or pavement with no people"
        )

        /** Colors used to build rival queries (attribute-binding guard). */
        private val RIVAL_COLORS = listOf(
            "orange", "red", "green", "blue", "yellow",
            "white", "black", "khaki", "brown", "purple", "gray"
        )
        private const val ALBUM_FLOOR = 0.07f
        private const val ALBUM_RATIO = 0.80f
        private val PROMPT_TEMPLATES = listOf(
            "a photo of %s.",
            "a picture showing %s",
            "%s"
        )

        /** Category title -> English prompt for zero-shot classification. */
        private val SMART_CATEGORIES: List<Pair<String, String>> = listOf(
            "Rapat & Kerja" to "a photo of a business meeting, presentation, or people working in an office",
            "Orang" to "a photo of a person or a group of people",
            "Makanan & Minuman" to "a photo of food or drinks",
            "Alam & Pemandangan" to "a photo of nature or a scenic landscape",
            "Hewan" to "a photo of an animal or pet",
            "Kendaraan" to "a photo of a car, motorcycle, or other vehicle",
            "Dokumen & Teks" to "a photo of a document, receipt, or printed text",
            "Kota & Bangunan" to "a photo of buildings, architecture, or a city street"
        )
    }
}
