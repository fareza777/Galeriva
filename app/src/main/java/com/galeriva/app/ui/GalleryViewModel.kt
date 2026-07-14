package com.galeriva.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeriva.app.GalerivaApp
import com.galeriva.app.data.MediaStoreRepository
import com.galeriva.app.data.Photo
import com.galeriva.app.data.db.FavoriteEntity
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

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

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
        combine(_photos, labelsByPhoto, searchQuery) { photos, labels, query ->
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
        _photos.map { photos ->
            photos.groupBy { it.bucketName }
                .map { (bucket, items) ->
                    SmartAlbum("folder:$bucket", bucket, items.firstOrNull(), items)
                }
                .sortedByDescending { it.photos.size }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Smart albums from ML labels (Orang, Makanan, Dokumen, Alam, ...). */
    val smartAlbums: StateFlow<List<SmartAlbum>> =
        combine(_photos, labelsByPhoto) { photos, labels ->
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
            _photos.value = repository.loadAllPhotos()
            _isLoading.value = false
            app.scheduleIndexing()
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
