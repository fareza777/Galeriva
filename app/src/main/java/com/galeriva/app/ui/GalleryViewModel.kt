package com.galeriva.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeriva.app.GalerivaApp
import com.galeriva.app.data.MediaStoreRepository
import com.galeriva.app.data.Photo
import com.galeriva.app.data.db.FavoriteEntity
import com.galeriva.app.data.db.LockedPhotoEntity
import com.galeriva.app.data.db.PhotoLabelEntity
import com.galeriva.app.search.SearchKeywords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A smart category derived from ML labels. */
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

    /** photoId -> labels */
    val labelsByPhoto: StateFlow<Map<Long, List<PhotoLabelEntity>>> =
        labelDao.allLabels()
            .map { rows -> rows.groupBy { it.photoId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val indexedCount: StateFlow<Int> =
        labelDao.indexedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> =
        favoriteDao.favoriteIds()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val searchResults: StateFlow<List<Photo>> =
        combine(photos, labelsByPhoto, searchQuery) { photos, labels, query ->
            if (query.isBlank()) return@combine emptyList()
            val terms = SearchKeywords.expand(query)
            photos.filter { photo ->
                val photoLabels = labels[photo.id].orEmpty()
                photoLabels.any { row -> terms.any { term -> row.label.contains(term) } } ||
                    terms.any { term -> photo.name.lowercase().contains(term) } ||
                    terms.any { term -> photo.bucketName.lowercase().contains(term) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Folder albums straight from MediaStore buckets. */
    val folderAlbums: StateFlow<List<SmartAlbum>> =
        photos.map { photos ->
            photos.groupBy { it.bucketName }
                .map { (bucket, items) ->
                    SmartAlbum("folder:$bucket", bucket, items.firstOrNull(), items)
                }
                .sortedByDescending { it.photos.size }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Smart albums from ML labels (Orang, Makanan, Dokumen, Alam, ...). */
    val smartAlbums: StateFlow<List<SmartAlbum>> =
        combine(photos, labelsByPhoto) { photos, labels ->
            SMART_CATEGORIES.mapNotNull { (title, matchLabels) ->
                val items = photos.filter { photo ->
                    labels[photo.id].orEmpty().any { row ->
                        matchLabels.any { row.label.contains(it) }
                    }
                }
                if (items.isEmpty()) null
                else SmartAlbum("smart:$title", title, items.first(), items)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _allPhotos.value = repository.loadAllPhotos()
            _isLoading.value = false
            app.scheduleIndexing()
        }
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
                    .filter { it.sizeBytes > 0 }
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

    fun toggleFavorite(photoId: Long) {
        viewModelScope.launch {
            if (photoId in favoriteIds.value) favoriteDao.remove(photoId)
            else favoriteDao.add(FavoriteEntity(photoId))
        }
    }

    fun albumById(albumId: String): SmartAlbum? =
        (smartAlbums.value + folderAlbums.value).firstOrNull { it.id == albumId }

    companion object {
        private const val SIMILARITY_THRESHOLD_BITS = 6

        private val SMART_CATEGORIES: List<Pair<String, List<String>>> = listOf(
            "Rapat & Kerja" to listOf("meeting", "whiteboard", "presentation", "computer", "paper", "office", "desk"),
            "Orang" to listOf("person", "people", "selfie", "smile", "crowd", "face"),
            "Makanan & Minuman" to listOf("food", "dish", "cuisine", "dessert", "drink", "coffee", "fruit", "cake"),
            "Alam & Pemandangan" to listOf("landscape", "mountain", "beach", "sea", "sky", "sunset", "nature", "tree", "flower"),
            "Hewan" to listOf("animal", "cat", "dog", "bird", "pet"),
            "Kendaraan" to listOf("car", "motorcycle", "vehicle", "bicycle", "airplane"),
            "Dokumen & Teks" to listOf("paper", "document", "text", "receipt", "book"),
            "Kota & Bangunan" to listOf("building", "city", "architecture", "street", "skyscraper")
        )
    }
}
