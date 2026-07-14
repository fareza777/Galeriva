package com.galeriva.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val grouped = remember(photos) { groupByDay(photos) }

    Column(Modifier.fillMaxSize()) {
        if (indexedCount < photos.size && photos.isNotEmpty()) {
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
                        onClick = { onPhotoClick(photo) }
                    )
                }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mengindeks foto untuk pencarian pintar… $done/$total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}

@Composable
fun PhotoThumbnail(
    photo: Photo,
    isFavorite: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isFavorite) {
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
