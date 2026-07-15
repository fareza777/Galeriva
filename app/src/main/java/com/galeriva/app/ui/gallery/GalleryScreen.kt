package com.galeriva.app.ui.gallery

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.galeriva.app.data.Photo
import com.galeriva.app.ui.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (Photo) -> Unit
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val indexedCount by viewModel.indexedCount.collectAsStateWithLifecycle()
    val selected by viewModel.selectedIds.collectAsStateWithLifecycle()

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val grouped = remember(photos) { groupByDay(photos) }
    val selectionMode = selected.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionActionBar(viewModel = viewModel, photos = photos, selected = selected)
        } else if (indexedCount < photos.size && photos.isNotEmpty()) {
            IndexingBanner(indexedCount, photos.size)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            grouped.forEach { (dayLabel, dayPhotos) ->
                item(key = "header-$dayLabel", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
                items(dayPhotos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        isFavorite = photo.id in favorites,
                        isSelected = photo.id in selected,
                        onClick = {
                            if (selectionMode) viewModel.toggleSelection(photo.id)
                            else onPhotoClick(photo)
                        },
                        onLongClick = { viewModel.toggleSelection(photo.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionActionBar(
    viewModel: GalleryViewModel,
    photos: List<Photo>,
    selected: Set<Long>
) {
    val context = LocalContext.current
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onDeleteConfirmed()
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearSelection() }) {
                Icon(Icons.Filled.Close, "Batal pilih")
            }
            Text(
                "${selected.size} dipilih",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val uris = ArrayList(photos.filter { it.id in selected }.map { it.uri })
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Bagikan foto"))
            }) {
                Icon(Icons.Filled.Share, "Bagikan")
            }
            IconButton(onClick = { viewModel.lockSelected() }) {
                Icon(Icons.Filled.Lock, "Masukkan ke Brankas")
            }
            IconButton(onClick = {
                viewModel.deletePhotoIds(selected) { sender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }) {
                Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IndexingBanner(done: Int, total: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "Mengindeks foto untuk pencarian pintar… $done/$total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoThumbnail(
    photo: Photo,
    isFavorite: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) Modifier.border(
                    3.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Terpilih",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
            )
        }
        if (isFavorite && !isSelected) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorit",
                tint = Color(0xFFFF6B81),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
    }
}

private fun groupByDay(photos: List<Photo>): List<Pair<String, List<Photo>>> {
    val dayFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
    return photos
        .groupBy { dayFormat.format(Date(it.dateTakenMillis)) }
        .toList()
}
