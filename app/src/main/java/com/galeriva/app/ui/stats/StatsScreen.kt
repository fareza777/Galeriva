package com.galeriva.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galeriva.app.ui.GalleryViewModel
import com.galeriva.app.ui.calendar.localDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val LOCALE_ID = Locale("id", "ID")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val indexedCount by viewModel.indexedCount.collectAsStateWithLifecycle()
    val meta by viewModel.metaById.collectAsStateWithLifecycle()
    val smartAlbums by viewModel.smartAlbums.collectAsStateWithLifecycle()

    val videoCount = remember(photos) { photos.count { it.isVideo } }
    val facePhotoCount = remember(meta) { meta.values.count { it.faceCount > 0 } }
    val textPhotoCount = remember(meta) { meta.values.count { it.ocrText.isNotBlank() } }

    val monthly = remember(photos) {
        val now = YearMonth.now()
        (11 downTo 0).map { offset ->
            val month = now.minusMonths(offset.toLong())
            month to photos.count {
                it.dateTakenMillis > 0 && YearMonth.from(it.localDate()) == month
            }
        }
    }
    val maxMonthly = (monthly.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Foto", "${photos.size - videoCount}", Modifier.weight(1f))
                StatCard("Video", "$videoCount", Modifier.weight(1f))
                StatCard("Terindeks", "$indexedCount", Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                StatCard("Berisi Wajah", "$facePhotoCount", Modifier.weight(1f))
                StatCard("Berisi Teks", "$textPhotoCount", Modifier.weight(1f))
            }

            Text(
                "Foto per Bulan",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                monthly.forEach { (month, count) ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction = (count / maxMonthly.toFloat()).coerceIn(0.02f, 1f))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (count == 0) 0.15f else 0.85f
                                    ),
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        Text(
                            month.month.getDisplayName(TextStyle.NARROW, LOCALE_ID),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "Kategori Terbesar",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
            smartAlbums.sortedByDescending { it.photos.size }.take(6).forEach { album ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        album.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${album.photos.size} foto",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
