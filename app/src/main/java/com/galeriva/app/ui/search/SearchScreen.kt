package com.galeriva.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galeriva.app.data.Photo
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.ui.gallery.PhotoThumbnail

private val SUGGESTIONS = listOf(
    "rapat", "makanan", "pantai", "dokumen", "selfie", "kucing", "gunung", "kota", "bunga"
)

@Composable
fun SearchScreen(
    viewModel: GalleryViewModel,
    onPhotoClick: (Photo) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val indexedCount by viewModel.indexedCount.collectAsStateWithLifecycle()
    val totalPhotos by viewModel.photos.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = { Text("Cari: rapat, makanan, pantai…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SUGGESTIONS) { suggestion ->
                AssistChip(
                    onClick = { viewModel.searchQuery.value = suggestion },
                    label = { Text(suggestion) }
                )
            }
        }

        when {
            query.isBlank() -> CenteredHint(
                if (indexedCount < totalPhotos.size)
                    "Ketik kata kunci untuk mencari.\nIndeks pintar: $indexedCount/${totalPhotos.size} foto."
                else
                    "Ketik kata kunci, misalnya \"rapat\" atau \"makanan\"."
            )
            results.isEmpty() -> CenteredHint("Tidak ada hasil untuk \"$query\".")
            else -> {
                Text(
                    "${results.size} foto ditemukan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(results, key = { it.id }) { photo ->
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
}

@Composable
private fun CenteredHint(text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
