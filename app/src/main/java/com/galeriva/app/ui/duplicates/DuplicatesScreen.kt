package com.galeriva.app.ui.duplicates

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galeriva.app.data.Photo
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.ui.gallery.PhotoThumbnail

@Composable
fun DuplicatesScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val groups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningDuplicates.collectAsStateWithLifecycle()
    CleanupScreen(
        title = "Foto Duplikat",
        emptyText = "Pindai galeri untuk menemukan foto yang persis sama " +
            "dan bebaskan ruang penyimpanan.",
        groupLabel = { group ->
            "${group.size} foto sama — ${formatSize(group.first().sizeBytes)} per foto"
        },
        groups = groups,
        isScanning = isScanning,
        onScan = { viewModel.scanDuplicates() },
        viewModel = viewModel,
        onBack = onBack
    )
}

@Composable
fun SimilarScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val groups by viewModel.similarGroups.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningSimilar.collectAsStateWithLifecycle()
    CleanupScreen(
        title = "Foto Mirip",
        emptyText = "Pindai galeri untuk menemukan foto yang tampak mirip " +
            "(mis. jepretan beruntun) dan pilih yang terbaik.",
        groupLabel = { group -> "${group.size} foto mirip" },
        groups = groups,
        isScanning = isScanning,
        onScan = { viewModel.scanSimilar() },
        viewModel = viewModel,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupScreen(
    title: String,
    emptyText: String,
    groupLabel: (List<Photo>) -> String,
    groups: List<List<Photo>>,
    isScanning: Boolean,
    onScan: () -> Unit,
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val selected by viewModel.selectedIds.collectAsStateWithLifecycle()

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onDeleteConfirmed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelection(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.deletePhotoIds(selected) { sender ->
                                deleteLauncher.launch(
                                    IntentSenderRequest.Builder(sender).build()
                                )
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                "Hapus ${selected.size} foto",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isScanning -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        "Memindai…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            groups.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    emptyText,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onScan, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Pindai")
                }
            }

            else -> LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        "${groups.size} grup ditemukan. Ketuk foto yang ingin " +
                            "dihapus (sisakan satu per grup).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(groups, key = { it.first().id }) { group ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            groupLabel(group),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(group, key = { it.id }) { photo ->
                                Box(Modifier.width(110.dp)) {
                                    PhotoThumbnail(
                                        photo = photo,
                                        isSelected = photo.id in selected,
                                        onClick = { viewModel.toggleSelection(photo.id) },
                                        onLongClick = { viewModel.toggleSelection(photo.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
